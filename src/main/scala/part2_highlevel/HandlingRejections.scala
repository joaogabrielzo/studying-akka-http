package part2_highlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{MethodRejection, MissingQueryParamRejection, Rejection, RejectionHandler}
import akka.stream.ActorMaterializer
import akka.util.Timeout

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

object HandlingRejections extends App {
    
    implicit val system: ActorSystem = ActorSystem("marshalling-json")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(3 seconds)
    
    /*
        Rejections
        - If a request doesn't match a filter directive, it's rejected
        - rejected = pass the request to another branch in the routing tree
        - a rejection is NOT a failure
     */
    
    val simpleRoute =
        path("api" / "endpoint") {
            get {
                complete(StatusCodes.OK)
            }
            parameter("id") { _ =>
                complete(StatusCodes.OK)
            }
        }
    
    // Rejection handlers
    val badRequestHandler: RejectionHandler = { rejections: Seq[Rejection] =>
        println(s"Found a bad request: $rejections")
        Some(complete(StatusCodes.BadRequest))
    }
    
    // The RejectionHandler returns a Option, so we got to return a Some(complete))
    
    val forbiddenHandler: RejectionHandler = { rejections: Seq[Rejection] =>
        println(s"Found a forbidden request: $rejections")
        Some(complete(StatusCodes.Forbidden))
        // The RejectionHandler returns a Option, so we got to return a Some(complete))
    }
    
    val simpleRouteWithHandlers =
        handleRejections(badRequestHandler) { // every rejection will be handled by this handler
            // define server logic inside
            path("api" / "endpoint") {
                get {
                    complete(StatusCodes.OK)
                } ~
                post {
                    handleRejections(forbiddenHandler) { // every post on this will be handled by this handler
                        parameter("param") { param =>
                            complete(StatusCodes.OK)
                        }
                    }
                }
            }
        }
    
    /*
        if you don't wanna write a handler for each operation, can use de default method
        - RejectionHandler.default
     */
    
    implicit val customRejectionHandler = RejectionHandler.newBuilder()
                                                          .handle {
                                                              case m: MissingQueryParamRejection =>
                                                                  println(s"Got a param rejection: $m")
                                                                  complete("Rejected parameter!")
                                                          }
                                                          .handle {
                                                              case m: MethodRejection =>
                                                                  println(s"Got a method rejection: $m")
                                                                  complete("Rejected method!")
                                                          }
                                                          .result()
    
    // Because the Rejection Handler above is implicit, the bindAndHandle method will AUTOMATICALLY added
    Http().bindAndHandle(simpleRoute, "localhost", 9999)
    
}
