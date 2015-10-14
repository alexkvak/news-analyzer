import akka.http.scaladsl.model.HttpResponse

import scala.concurrent.{Future, ExecutionContextExecutor}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

import akka.actor.{Props, ActorSystem}
import akka.event.LoggingAdapter
import akka.http.scaladsl.model.StatusCodes.{BadRequest, InternalServerError}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{UnsupportedRequestContentTypeRejection, RejectionHandler, MalformedRequestContentRejection}
import akka.pattern.ask
import akka.routing.FromConfig
import akka.stream.Materializer
import akka.util.Timeout
import com.typesafe.config.{ConfigFactory, Config}

import org.joda.time.DateTime
import org.joda.time.format.DateTimeFormat


trait Service extends ProtocolImplicits {
  import Protocol._
  implicit val askTimeout = Timeout(30 seconds)

  implicit val system: ActorSystem
  implicit def executor: ExecutionContextExecutor
  implicit val materializer: Materializer

  def config: Config
  val logger: LoggingAdapter

  val workerSystem = ActorSystem.create("Router", ConfigFactory.load().getConfig("Router"))
  lazy val worker = workerSystem.actorOf(Props(classOf[Worker], config).withRouter(FromConfig()), name = "WorkerPoolActor")

  implicit val myRejectionHandler = RejectionHandler.newBuilder()
    .handle {
    case UnsupportedRequestContentTypeRejection(supportedContentTypes) ⇒
      val errorMsg = s"Unsupported content-type. Supported are: ${supportedContentTypes.mkString(", ")}"
      logger.error(errorMsg)
      complete(HttpResponse(BadRequest, entity = errorMsg))
    case MalformedRequestContentRejection(errorMsg, _) ⇒
      logger.error(errorMsg)
      complete(HttpResponse(BadRequest, entity = errorMsg))
    case x ⇒
      val errorMsg = x.toString
      logger.error(errorMsg)
      complete(HttpResponse(InternalServerError, entity = errorMsg))
  }
    .result()

  val routes = {
    logRequestResult("news-analyzer") {
      pathPrefix("analyzenews") {
        (post & entity(as[AnalyzeNewsRequest])) { newsRequest ⇒
          complete {
            val startTime = System.nanoTime
            Future.sequence(
              newsRequest.news.map(part ⇒ (worker ? part).mapTo[SearchResult])
            ).map(_.map {
              case Failure(ex) ⇒
                logger.error(ex, "Request failed")
                Nil
              case Success(response) ⇒
                response.results
            })
              .map(_.flatten)
              .map(countDomains)
              .map { results ⇒
                logger.warning(s"Request processed in ${(System.nanoTime - startTime) / 1000000} ms")
                AnalyzeNewsResponse(DateTime.now.toString(DateTimeFormat.fullDateTime()), results)
              }
          }
        }
      }
    }
  }

  private[this] def countDomains(list: List[GoogleSearchResult]): Map[String, Int] = {
    def parseDomain(url: String): String = {
      val regex = """^https?://(?:www\.)?([^/]+)(?:/.*$|$)""".r
      url match {
        case regex(s) ⇒ s
        case _ ⇒ ""
      }
    }

    val domains = list.map(result ⇒ parseDomain(result.unescapedUrl))
    domains.groupBy(w ⇒ w).mapValues(_.size)
  }

}