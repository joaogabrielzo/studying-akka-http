package part2_highlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.ExceptionHandler
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

object HandlingExceptions extends App {
    
    implicit val system: ActorSystem = ActorSystem("handling-exceptions")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(3 seconds)
    
    val simpleRoute =
        path("api" / "people") {
            get {
                throw new RuntimeException("took too long")
            } ~
            post {
                parameter('id) { id =>
                    if (id.length > 2)
                        throw new NoSuchElementException(s"Couldn't find $id in database")
                    else complete(StatusCodes.OK)
                }
            }
        }
    
    implicit val customExceptionHandler: ExceptionHandler = ExceptionHandler {
        case e: RuntimeException         =>
            complete(StatusCodes.NotFound, e.getMessage)
        case e: IllegalArgumentException =>
            complete(StatusCodes.BadRequest, e.getMessage)
    }
    
    Http().bindAndHandle(simpleRoute, "localhost", 9999)
    
}
