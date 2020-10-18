package models

import models.BenchAPI.Transaction

object BenchAPITestSource {

  val testData = Seq(
    Seq(5d, 0.5d, -0.5d),
    Seq(-8d, 0.5d, -0.5d),
    Seq(7d, 0.5d, -0.5d),
  )

  lazy val transactions = testData
    .zipWithIndex
    .map {
      case (xs, i) => xs.map(n => Transaction(s"2013-12-${i+1}", s"xxx_$i", BigDecimal(n), s"yyy_$i"))
    }.flatten

  val runningBalances = Seq(5d, -3d, 4d)

  def genSampleResponse(pages: Seq[Seq[Double]]) = {
    val total = pages.flatten.size
    pages.zipWithIndex.map {
      case (items, p) =>

        val payload = items.zipWithIndex.map {
          case (el, i) =>
            s"""
               |{
               |      "Date": "2013-12-${p + 1}",
               |      "Ledger": "xxx$i",
               |      "Amount": "${BigDecimal(el)}",
               |      "Company": "xxx$i"
               |    }
               |""".stripMargin
        }.mkString(",")

        s"""
           |{
           |  "totalCount": ${total},
           |  "page": ${p+1},
           |  "transactions": [
           |   $payload
           |  ]
           |}
           |""".stripMargin
    }
  }
}
