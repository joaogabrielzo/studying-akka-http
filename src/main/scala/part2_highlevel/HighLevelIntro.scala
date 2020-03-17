package part2_highlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

object HighLevelIntro extends App {
    
    implicit val system = ActorSystem("high-level-intro")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher
    
    // directives
    
    import akka.http.scaladsl.server.Directives._
    /*
        Directives are building blocks of high level Akka HTTP server logic
     */
    
    val simpleRoute: Route = // RequestContext => Future[RouteResult]
        path("home") { // directive. It will only filter the requests on /home
            complete(StatusCodes.OK) // directive. Decides what will happen if it passes through the path
        }
    
    val pathGetRoute: Route =
        path("home") {
            get {
                complete(StatusCodes.OK)
            }
        }
    
    // chaining directives with ~
    val chainedRoute: Route =
        path("myEndpoint") {
            get {
                complete(StatusCodes.OK)
            } ~ // IMPORTANT
            post {
                complete(StatusCodes.Forbidden)
            }
        } ~
        path("home") {
            get {
                complete(HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    """
                        |<html>
                        |<body>
                        |<h1>Hello etc</h1>
                        |</body>
                        |</html>
                        |""".stripMargin
                ))
            }
        }
    
    Http().bindAndHandle(chainedRoute, "localhost", 9999)
    
}
