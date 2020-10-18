import akka.actor.ActorSystem
import scala.concurrent.Future
import sttp.client._
import service.RunningBalanceService
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend

object Main extends App {
  val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend()
  val system = ActorSystem("RunningBalancesService")
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  println(s"** fetching balances ...")

  val sv = RunningBalanceService(backend, system, 4)
  for {
    result <- sv.daily
  } yield {
    result.map(el => println(s"${el._1}\t\t\t${el._2}"))
    println(s"** done")
    backend.close()
    system.terminate()
  }
}
