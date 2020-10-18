package service

import akka.actor.ActorSystem
import models.BenchAPI.Transaction
import models.BenchAPITestSource
import org.scalatest._
import org.scalatest.matchers.should._
import org.scalatest.wordspec.AsyncWordSpec
import sttp.client._
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.testing.SttpBackendStub
import sttp.model._

import scala.concurrent.Future

/**
 * test API models and JSON decodingscala
 */
class RunningBalanceServiceSpec extends AsyncWordSpec
  with Matchers
  with BeforeAndAfterAll
  with EitherValues {

  val system = ActorSystem("TestSystem")
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  val sampleResponses = BenchAPITestSource.genSampleResponse(BenchAPITestSource.testData).map(Response.ok[String](_))

  val mockBackendWithFailure = SttpBackendStub.asynchronousFuture
    .whenRequestMatches(_.uri.path.endsWith(List("1.json")))
    .thenRespond(sampleResponses(0))
    .whenRequestMatches(_.uri.path.endsWith(List("2.json")))
    .thenRespond(sampleResponses(1))
    .whenRequestMatches(_.uri.path.endsWith(List("3.json")))
    .thenRespond(Response("error", StatusCode.InternalServerError, "Something went wrong"))
    .whenRequestMatches(_.uri.path.endsWith(List("4.json")))
    .thenRespond(Response("", StatusCode.NotFound, "Not found"))


    "BenchAPI RunningBalanceService" should {

      "correctly compute running balances" in {
        for {
          r <- Future.successful(PartitionedBalances(Map.empty, RunningBalancePeriods.daily).withTransactions(BenchAPITestSource.transactions))
        } yield {
          r.running.map(_._2) should contain theSameElementsInOrderAs(BenchAPITestSource.runningBalances.map(BigDecimal(_)))
        }
      }

      "compute running daily balance from resttest.bench.io" in {
        val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend()
        val sv = RunningBalanceService(backend, system, 3)

        for {
          res <- sv.daily
        } yield {
          res.size should be > 0
          res.last._2.doubleValue should be > 0.0 // resttest.bench.io returns positive balance on last day
        }
      }

      "compute correct daily running balances with failed pages" in {
        val sv = RunningBalanceService(mockBackendWithFailure, system, 2)

        for {
          res <- sv.daily
        } yield {
          res.size should be > 0
          res.map(_._2) should contain theSameElementsInOrderAs(BenchAPITestSource.runningBalances.take(2))
        }
      }
  }
}
