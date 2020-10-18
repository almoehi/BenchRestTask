package models

import io.circe.Decoder
import sttp.client.circe.asJson
import sttp.client.{Identity, NothingT, SttpBackend, basicRequest}
import sttp.model.Uri
import scala.concurrent.Future

/**
 * a very basic model of a RESTful service
 */
trait RESTful {
  def base: String
  def uri: String

  override def hashCode(): Int = uri.hashCode
  override def equals(obj: Any): Boolean = obj match {
    case o:RESTful => o.uri.equals(uri)
    case _ => false
  }

  override def toString: String = uri
}

// a POST endpoint
trait POST extends RESTful {
  def POST[T](implicit fmt: Decoder[T], sttp: SttpBackend[Future, Nothing, NothingT]) = ???
  def POSTsync[T](implicit fmt: Decoder[T], sttp: SttpBackend[Identity, Nothing, NothingT]) = ???
}

// a GET endpoint
trait GET extends RESTful {
  /**
   * perform async GET request on endpoint
   * @param fmt
   * @param sttp
   * @tparam T
   * @return
   */
  def GET[T](implicit fmt: Decoder[T], sttp: SttpBackend[Future, Nothing, NothingT]) = {
    basicRequest
      .get(Uri.apply(java.net.URI.create(uri)))
      .response(asJson[T])
      .send()
  }

  /**
   * perform a sync. GET request to endpoint
   * @param fmt
   * @param sttp
   * @tparam T
   * @return
   */
  def GETsync[T](implicit fmt: Decoder[T], sttp: SttpBackend[Identity, Nothing, NothingT]) = {
    basicRequest
      .get(Uri.apply(java.net.URI.create(uri)))
      .response(asJson[T])
      .send()
  }
}

/**
 * mixin for paginated endpoints
 * @tparam A
 */
trait Paginated[A <: Paginated[A]] {
  def page: Long
  def withPage(p: Long): A
}
