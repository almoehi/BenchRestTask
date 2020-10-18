package models
import java.text.DateFormat
import io.circe._
import scala.util.Try
import java.text.SimpleDateFormat


sealed trait BenchAPI
sealed trait Payload extends BenchAPI
sealed trait Entity extends BenchAPI

sealed trait BenchRESTful extends RESTful {
  val base = "https://resttest.bench.co"  // not optimal - should be injectable
}

// list of typed API endpoints
protected[this] object BenchApiEndpoints {
  case class Transactions(page: Long) extends BenchRESTful with GET with Paginated[Transactions] {
    val uri = s"$base/transactions/$page.json"
    def withPage(p: Long) = this.copy(page = p)
  }
}

object BenchAPI {

  private val transactionDateFormatter = new SimpleDateFormat("yyyy-MM-dd")

  // list of callable RESTful endpoints
  val Transactions = BenchApiEndpoints.Transactions(1)

  // useful type aliases
  type Amount = BigDecimal
  type CompanyName = String
  type LedgerName = String
  type TransactionDateString = String
  type TransactionDate = java.util.Date

  // Transaction domain model
  case class Transaction(date: TransactionDateString, ledger: LedgerName, amount: Amount, company: CompanyName) extends Entity {
    // parse date string as date object - caution: date strings can be empty / invalid !
    lazy val theDate: Either[Throwable,TransactionDate] = Try(transactionDateFormatter.parse(date)).toEither

    // get instance with custom date format - caution: dates can be empty / invalid !
    def withDateFormat(targetFormat: DateFormat) =
      theDate.toOption.map(d => copy(date = targetFormat.format(d)))
  }
  // collection of transactions domain model
  case class TransactionsResponse(transactions: List[Transaction]) extends Payload

  // typed domain model for generic, paginated API endpoint and RawJson response
  case class PaginatedReponse[T <: Payload](totalCount: Long, page: Long, pageData: T) extends BenchAPI
  case class RawJsonResponse(json: Json) extends Payload {
    def as[T](implicit fmt: Decoder[T]) = json.as[T]
  }

  // JSON codec for transaction entity
  // could be substituted by automatic codec derivation,
  // but would require more effort given the mixed naming patterns of JSON fields
  implicit val decodeTransaction: Decoder[Transaction] =
    Decoder.forProduct4("Date", "Ledger", "Amount", "Company")(Transaction.apply)

  // JSON codec for transactions response
  implicit val decodeTransactions: Decoder[TransactionsResponse] =
    Decoder.forProduct1("transactions")(TransactionsResponse.apply)

  // JSON codec for typed paginated response
  implicit def decodePaginatedResponse[T <: Payload](implicit fmt: Decoder[T]): Decoder[PaginatedReponse[T]] = (c: HCursor) => for {
    totalCount <- c.downField("totalCount").as[Long]
    page <- c.downField("page").as[Long]
    payload <- c.as[T]
  } yield PaginatedReponse(totalCount, page, payload)

  // JSON codec for paginated response with arbitrary JSON payload
  implicit val decodePaginatedResponseJson: Decoder[PaginatedReponse[RawJsonResponse]] = (c: HCursor) => for {
    totalCount <- c.downField("totalCount").as[Long]
    page <- c.downField("page").as[Long]
  } yield PaginatedReponse(totalCount, page, RawJsonResponse(c.value))
}
