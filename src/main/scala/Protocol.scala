import scala.util.Try

import akka.http.scaladsl.model.{HttpCharsets, MediaType}
import akka.http.scaladsl.model.MediaTypes._
import akka.http.scaladsl.unmarshalling._
import akka.stream.Materializer
import spray.json.{JsonParser, ParserInput, JsValue, DefaultJsonProtocol}


// определим специальный Unmarshaller для поддержки отдаваемого Google ответа с Content-Type text/javascript
trait SprayJsonSupport extends akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport {
  override implicit def sprayJsValueUnmarshaller(implicit mat: Materializer): FromEntityUnmarshaller[JsValue] =
    Unmarshaller.byteStringUnmarshaller.forContentTypes(
      `application/json`,
      MediaType.custom("text/javascript", MediaType.Encoding.Fixed(HttpCharsets.`UTF-8`)))
      .mapWithCharset { (data, charset) ⇒
      val input =
        if (charset == HttpCharsets.`UTF-8`) ParserInput(data.toArray)
        else ParserInput(data.decodeString(charset.nioCharset.name)) // FIXME: identify charset by instance, not by name!
      JsonParser(input)
    }
}

object Protocol {
  // Incoming request case classes
  case class AnalyzeNewsRequestPart(query: String, number: Int)
  case class AnalyzeNewsRequest(news: List[AnalyzeNewsRequestPart])

  // Incoming request response case classes
  case class AnalyzeNewsResponse(date: String, result: Map[String, Int])

  // Request to Google API case classes
  case class GoogleSearchResult(unescapedUrl: String)
  case class GoogleSearchResponseCursorPage(start: String, label: Int)
  case class GoogleSearchResponseCursor(pages: List[GoogleSearchResponseCursorPage], currentPageIndex: Int)
  case class GoogleSearchResponseData(results: List[GoogleSearchResult], cursor: GoogleSearchResponseCursor)
  case class GoogleSearchResponse(responseData: GoogleSearchResponseData)

  type SearchResult = Try[GoogleSearchResponseData]
}

trait ProtocolImplicits extends DefaultJsonProtocol with SprayJsonSupport {
  import Protocol._

  implicit val jsonNewsRequestPart = jsonFormat2(AnalyzeNewsRequestPart.apply)
  implicit val jsonNewsRequest = jsonFormat1(AnalyzeNewsRequest.apply)
  implicit val jsonAnalyzeNewsResponse = jsonFormat2(AnalyzeNewsResponse.apply)
  implicit val jsonSearchResult = jsonFormat1(GoogleSearchResult.apply)
  implicit val jsonSearchResponseCursorPage = jsonFormat2(GoogleSearchResponseCursorPage.apply)
  implicit val jsonSearchResponseCursor = jsonFormat2(GoogleSearchResponseCursor.apply)
  implicit val jsonResponseData = jsonFormat2(GoogleSearchResponseData.apply)
  implicit val jsonResponse = jsonFormat1(GoogleSearchResponse.apply)
}
