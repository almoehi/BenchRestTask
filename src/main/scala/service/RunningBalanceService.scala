package service

import java.text.{DateFormat, SimpleDateFormat}
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl._
import models.BenchAPI.{PaginatedReponse, Transaction, TransactionDate, TransactionsResponse}
import models._
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future

// in-memory result of balances partitioned by some period
// this scales with number of periods to compute running balances for
sealed case class PartitionedBalances(balances: Map[String,BigDecimal], by: DateFormat) {
  def withTransactions(transactions: Seq[Transaction]) = {
    // filter out all transactions with empty date
    val updated = transactions.map(_.withDateFormat(by))
      .collect{
        case Some(t) => t
      }
      .groupBy(_.date)
      .map(el => (el._1, el._2.map(_.amount).sum))

    // fold updates into in-memory cache
    this.copy(updated.foldLeft(balances){
      case (b, el) => b + (el._1 -> (b.get(el._1).getOrElse(BigDecimal(0)) + el._2))
    })
  }

  /**
   * compute running balances as-is from raw data
   * migh contain temporal gaps if there are no transactions
   * @return
   */
  def running = balances.toSeq
    .sortBy(_._1)
    .foldLeft(Seq.empty[(String,BigDecimal)]){
      case (Nil, head) => Seq(head)
      case (acc, (period, amount)) => acc :+ (period, acc.last._2 + amount)
    }

  /**
   * compute daily running balances and augment missing days
   * only works for daily balances right now
   * @return
   */
  def runningDailyAugmented = balances.toSeq
    .sortBy(_._1)
    .map(el => (by.parse(el._1), el._2))
    .foldLeft(Seq.empty[(TransactionDate,BigDecimal)]){
      case (Nil, head) => Seq(head)
      case (acc, (period, amount)) =>
        val lastDate = acc.last._1

        if (lastDate.toInstant.plus(1, ChronoUnit.DAYS).equals(period.toInstant)) {
          acc :+ (period, acc.last._2 + amount)
        } else {
          // augment missing data
          val lastAmount = acc.last._2
          val padByDays = TimeUnit.DAYS.convert(scala.math.abs(period.getTime - lastDate.getTime), TimeUnit.MILLISECONDS).intValue
          val pad = (1 until padByDays).map(delta => (java.util.Date.from(lastDate.toInstant.plus(delta.longValue, ChronoUnit.DAYS)), lastAmount))
          acc ++ pad ++ Seq((period, acc.last._2 + amount))
        }
    }.map(el => (by.format(el._1), el._2))
}

// available periods to compute running balances over
private object RunningBalancePeriods {
  val daily = new SimpleDateFormat("yyyy-MM-dd")
  val monthly = new SimpleDateFormat("yyyy-MM")
  val yearly = new SimpleDateFormat("yyyy")
}

case class RunningBalanceService(sttpBackend: SttpBackend[Future, Nothing, NothingT], system: ActorSystem, parallelizeBy: Int = 1) {
  implicit val backend = sttpBackend   //: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend()
  implicit val sys = system
  implicit val ec = system.dispatcher //scala.concurrent.ExecutionContext.Implicits.global
  implicit val materializer = ActorMaterializer()

  /**
   * Fetch a given pageNo from Transactions API
   * Gracefully handles 4xx errors and maps them to empty collections
   * Note: there's currently no retry for 5xx errors
   * @param page
   * @return
   */
  protected def fetchPage(page: Long) = {
    for {
      p <- BenchAPI.Transactions.withPage(page).GET[PaginatedReponse[TransactionsResponse]]
    } yield p.body match {
      case Right(ok) => Right(Some(ok)) // successfully fetched a page result
      case Left(_) if p.code.isClientError => Right(None)  // encountered a 4xx error - handle this gracefully
      case Left(ex) => Left((page, ex.getLocalizedMessage)) // encountered a 5xx error
    }
  }

  /**
   * fetch available pages and partition transactions into an in-memory cache
   * the task is casted into a stream processing problem and uses akka-streams
   * to parallelize fetching of pages with back-pressure
   * this streaming approach allows to easily integrate advanced features like rate limiting, infinite / non-paginated results etc.
   * @param result
   * @return
   */
  protected def stream(result: PartitionedBalances) = {

    // we fetch page 1 to check if & how many pages are available at all
    // down side: we 'waste' the result from this initial request - this could be fixed though
    val src = Source.fromFutureSource(fetchPage(1).map{
      case Right(Some(p)) => Source(1L to scala.math.ceil(p.totalCount.toDouble / p.pageData.transactions.size).longValue)
      case _ => Source.empty[Long]
    })

    /*
    val failedPagesLogSink = Flow[Either[(Long,String), _]].collect{
      case Left(err) => err
    }.to(Sink.foreach(el => println(s"[E] failed page: ${el._1} reason: ${el._2}")))
    */

    // pipeline to extract transactions from successfully fetched pages - ignores failed pages
    val pipeline = Flow[Long]
      .mapAsync(parallelizeBy)(fetchPage) // parallelize the fetching process to speed things up
      //.alsoTo(failedPagesLogSink) // might want to send failed pages to some log (async.)
      .collect{
        case Right(Some(page)) => page.pageData.transactions
      }

    // fold transactions into in-memory cache
    // this could be improved to work without in-memory cache (i.e. for real-time streaming)
    val sink = Sink.fold[PartitionedBalances,Seq[Transaction]](result){
      case (acc, transactions) => acc.withTransactions(transactions)
    }

    // run the stream graph
    src.async.via(pipeline).runWith(sink)
  }

  /**
   * get running daily balances
   * @return
   */
  def daily: Future[Seq[(String,BigDecimal)]] = for {
    res <- stream(PartitionedBalances(Map.empty, RunningBalancePeriods.daily))
  } yield res.runningDailyAugmented

  /**
   * get running monthly balances
   * @return
   */
  def monthly: Future[Seq[(String,BigDecimal)]] = for {
    res <- stream(PartitionedBalances(Map.empty, RunningBalancePeriods.monthly))
  } yield res.running
}
