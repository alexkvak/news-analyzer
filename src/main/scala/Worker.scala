import scala.concurrent.Future
import scala.util.Try

import akka.actor.{ActorLogging, ActorSystem, Actor}
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{HttpResponse, HttpRequest}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.pattern.pipe
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source, Flow}
import com.typesafe.config.Config


class Worker(config: Config) extends Actor with ActorLogging with ProtocolImplicits {
  import Protocol._

  override def receive: Receive = {
    case AnalyzeNewsRequestPart(query, number) ⇒
      fetchNewsSearchResult(query, number) pipeTo sender()
  }

  val requestPathPrefix = config.getString("google.pathPrefix")

  implicit val system = ActorSystem()
  implicit val executionContext = system.dispatcher
  implicit val materializer = ActorMaterializer()

  val googleApiConnectionFlow: Flow[HttpRequest, HttpResponse, Any] =
    Http().outgoingConnection(config.getString("google.host"), config.getInt("google.port"))

  private[this] def doRequest(request: HttpRequest): Future[HttpResponse] =
    Source.single(request).via(googleApiConnectionFlow).runWith(Sink.head)

  private[this] def fetchNewsSearchResult(query: String, number: Int): Future[SearchResult] = {
    def toInt(s: String): Int = {
      try {
        s.toInt
      } catch {
        case e: Exception ⇒ 0
      }
    }

    def iterateOverPages(start: Int, results: List[GoogleSearchResult]): Future[SearchResult] = {
      val uri = Uri(s"$requestPathPrefix&${Query("q" → query, "start" → start.toString).toString()}")

      doRequest(RequestBuilding.Get(uri)).flatMap { response ⇒
        response.status match {
          case OK ⇒
            Unmarshal(response.entity).to[GoogleSearchResponse].flatMap { response ⇒
              val responseData = response.responseData
              // let's look at cursor field
              responseData.cursor.pages.lift(responseData.cursor.currentPageIndex + 1) match {
                case Some(cursorPage) if toInt(cursorPage.start) < number ⇒
                  // next step is needed
                  iterateOverPages(toInt(cursorPage.start), results ::: responseData.results)
                case Some(cursorPage) ⇒
                  // all results are gathered
                  Future.successful(Try(responseData.copy(results = results ::: responseData.results.slice(0, toInt(cursorPage.start) - number))))
                case _ ⇒
                  // there are no further results
                  Future.successful(Try(responseData.copy(results = results ::: responseData.results)))
              }
            }

          // request failed
          case BadRequest ⇒
            Future.failed(new RuntimeException(s"Something went wrong with query: $query"))
          case _ ⇒ Unmarshal(response.entity).to[String].flatMap { entity ⇒
            Future.failed(new RuntimeException(s"Google News request failed with status code ${response.status} and entity $entity"))
          }
        }
      }
    }

    iterateOverPages(0, Nil)
  }
}
