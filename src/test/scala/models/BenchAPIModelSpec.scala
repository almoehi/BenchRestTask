package models

import org.scalatest._
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should._
import org.scalatest.wordspec.{AnyWordSpec, AsyncWordSpec}
import io.circe._
import io.circe.syntax._
import io.circe.parser.decode
import models.BenchAPI.{RawJsonResponse, PaginatedReponse, Transaction, TransactionsResponse}
import models._

/**
 * test data models and JSON codecs under some commonly occouring conditions
 */
class BenchAPIModelSpec extends AnyWordSpec
  with Matchers
  with BeforeAndAfterAll
  with EitherValues {

  val emptyResponse = ""
  val nullResponse = "null"
  val transactionJson =
    """
      |{
      |      "Date": "2013-12-22",
      |      "Ledger": "Phone & Internet Expense",
      |      "Amount": "-110.71",
      |      "Company": "SHAW CABLESYSTEMS CALGARY AB"
      |}
      |""".stripMargin

  val emptyPayloadResponse =
    """
      |{
      |  "totalCount": 0,
      |  "page": 1,
      |  "transactions": [
      |  ]
      |}
      |""".stripMargin

  val validResponse =
    """
      |{
      |  "totalCount": 32,
      |  "page": 1,
      |  "transactions": [
      |    {
      |      "Date": "2013-12-22",
      |      "Ledger": "Phone & Internet Expense",
      |      "Amount": "-110.71",
      |      "Company": "SHAW CABLESYSTEMS CALGARY AB"
      |    },
      |  {
      |    "Date": "2013-12-12",
      |    "Ledger": "Office Expense",
      |    "Amount": "-25.05",
      |    "Company": "AA OFFICE SUPPLIES"
      |  },
      |  {
      |    "Date": "2013-12-12",
      |    "Ledger": "Insurance Expense",
      |    "Amount": "-20",
      |    "Company": "AA OFFICE SUPPLIES"
      |  },
      |  {
      |    "Date": "2013-12-13",
      |    "Ledger": "Business Meals & Entertainment Expense",
      |    "Amount": "-10.5",
      |    "Company": "MCDONALDS RESTAURANT"
      |  },
      |  {
      |    "Date": "2013-12-14",
      |    "Ledger": "Credit Card - 1234",
      |    "Amount": "25",
      |    "Company": "PAYMENT - THANK YOU"
      |  }
      |  ]
      |}
      |""".stripMargin


  "BenchAPI model" should {

    "decode transaction JSON entities" in {
      for {
        o <- decode[Transaction](transactionJson)
      } yield {
        o.amount shouldBe -110.71
        o.amount should not be -110.72
        o.date shouldBe "2013-12-22"
        o.company shouldBe "SHAW CABLESYSTEMS CALGARY AB"
        o.ledger shouldBe "Phone & Internet Expense"
      }
    }

    "decode paginated API responses with arbitrary JSON payload" in {
      for {
        o <- decode[PaginatedReponse[RawJsonResponse]](validResponse)
      } yield {
        o.page shouldBe 1
        o.totalCount shouldBe 32
      }
    }

    "decode paginated API responses of transactions" in {
      for {
        o <- decode[PaginatedReponse[TransactionsResponse]](validResponse)
        o2 <- decode[PaginatedReponse[TransactionsResponse]](emptyPayloadResponse)
      } yield {
        o.page shouldBe 1
        o.totalCount shouldBe 32
        o.pageData.transactions.size shouldBe 5

        o2.page shouldBe 1
        o2.totalCount shouldBe 0
      }
    }

    "fail JSON decoding on empty or null responses" in {
      decode[PaginatedReponse[RawJsonResponse]](emptyResponse).isLeft shouldBe true
      decode[PaginatedReponse[RawJsonResponse]](nullResponse).isLeft shouldBe true
      decode[PaginatedReponse[TransactionsResponse]](emptyResponse).isLeft shouldBe true
      decode[PaginatedReponse[TransactionsResponse]](nullResponse).isLeft shouldBe true
    }

    "decode paginated API responses of transactions with zero elements" in {
      for {
        o <- decode[PaginatedReponse[TransactionsResponse]](emptyPayloadResponse)
      } yield {
        o.page shouldBe 1
        o.totalCount shouldBe 0
        o.pageData.transactions.size shouldBe 0
      }
    }
  }
}
