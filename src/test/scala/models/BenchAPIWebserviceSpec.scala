package models

import org.scalatest._
import org.scalatest.matchers.should._
import org.scalatest.wordspec.AsyncWordSpec
import sttp.client.testing.SttpBackendStub
import sttp.client.{Identity, NothingT}
import sttp.client._
import sttp.model._
import sttp.client.testing._
import java.io.File

import models.BenchAPI.{PaginatedReponse, TransactionsResponse}

import scala.concurrent.Future

/**
 * test API models and JSON decodingscala
 */
class BenchAPIWebserviceSpec extends AsyncWordSpec
  with Matchers
  with BeforeAndAfterAll
  with EitherValues {

  //implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()
  implicit val ec = scala.concurrent.ExecutionContext.Implicits.global

  val sampleResponses = BenchAPITestSource.genSampleResponse(BenchAPITestSource.testData).map(Response.ok[String](_))

  implicit val mockBackend = SttpBackendStub.asynchronousFuture
    .whenRequestMatches(_.uri.path.endsWith(List("0.json")))
    .thenRespond(Response("error", StatusCode.InternalServerError, "Something went wrong"))
    .whenRequestMatches(_.uri.path.endsWith(List("1.json")))
    .thenRespond(sampleResponses(0))
    .whenRequestMatches(_.uri.path.endsWith(List("2.json")))
    .thenRespond(sampleResponses(1))
    .whenRequestMatches(_.uri.path.endsWith(List("3.json")))
    .thenRespond(sampleResponses(2))
    .whenRequestMatches(_.uri.path.endsWith(List("4.json")))
    .thenRespond(Response("", StatusCode.NotFound, "Not found"))


  "BenchAPI webservice" should {

      "fetch a single page from /transactions endpoint" in {
        for {
          p <- BenchAPI.Transactions.withPage(1).GET[PaginatedReponse[TransactionsResponse]]
        } yield {
          p.code.code shouldBe 200
          p.body.toOption.map(_.page) should be (Some(1))
          p.body.toOption.map(_.pageData.transactions.map(_.amount)).getOrElse(Seq.empty) should contain theSameElementsInOrderAs BenchAPITestSource.testData.head
          p.body.toOption.map(_.pageData.transactions.size) should be (Some(3))
        }
      }

      "fetch multiple pages from /transactions endpoint" in {
        val pages = 1 to 3
        for {
          p <- Future.sequence(pages.map(p => BenchAPI.Transactions.withPage(p.longValue).GET[PaginatedReponse[TransactionsResponse]]))
        } yield {
          all (p.map(_.code.code)) should be (200)
          p.map(_.body.toOption.map(_.page).getOrElse(-1L)) shouldBe sorted
          all (p.map(_.body.toOption.map(_.pageData.transactions.size).getOrElse(-1))) shouldBe >= (0)
        }
      }

      "fail on 5xx errors" in {
        for {
          p <- BenchAPI.Transactions.withPage(0).GET[PaginatedReponse[TransactionsResponse]]
        } yield {
          p.code.code shouldBe 500
          p.body.isLeft should be (true)
        }
      }

      "fail on 4xx errors" in {
        for {
          p <- BenchAPI.Transactions.withPage(4).GET[PaginatedReponse[TransactionsResponse]]
        } yield {
          p.code.code shouldBe 404
          p.body.isLeft should be (true)
        }
      }
  }
}
