package exercises

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Location
import akka.stream.ActorMaterializer

import scala.concurrent.Future

object FirstHttpServer extends App {
    
    /**
      * Create own HTTP server on 8388, which replies
      * - with a welcome message on the landing page
      * - with a proper HTML on /about
      * - 404 otherwise
      */
    
    implicit val system = ActorSystem("first-http-server")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher
    
    val requestHandler: HttpRequest => Future[HttpResponse] = {
        case HttpRequest(HttpMethods.GET, Uri.Path("/"), _, _, _)      =>
            Future(HttpResponse(
                StatusCodes.OK,
                entity = HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    """
                        |<html>
                        |<body>
                        |Welcome to the front page, etc
                        |</body>
                        |</html>
                        |""".stripMargin
                )
            ))
        case HttpRequest(HttpMethods.GET, Uri.Path("/about"), _, _, _) =>
            Future(HttpResponse(
                StatusCodes.OK,
                entity = HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    """
                        |<html>
                        |   <body>
                        |       This is the first server I make by myself.
                        |       There might be better ways to do it but I still don't know.
                        |   </body>
                        |</html>
                        |""".stripMargin
                )
            ))
        // path /search redirects to some other part of the website/webapp/microservice
        case HttpRequest(HttpMethods.GET, Uri.Path("/search"), _, _, _) =>
            Future(HttpResponse(
                StatusCodes.Found, // HTTP 302
                headers = List(Location("http://google.com"))
            ))
        case request: HttpRequest                                       =>
            request.discardEntityBytes()
            Future(HttpResponse(
                StatusCodes.NotFound,
                entity = HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    """
                        |<html>
                        |   <body>
                        |       Sorry, not found.
                        |   </body>
                        |</body>
                        |""".stripMargin
                )
            ))
    }
    
    val bindingFuture = Http().bindAndHandleAsync(requestHandler, "localhost", 8388)
    
    // shutdown the server:
    bindingFuture
        .flatMap(_.unbind())
        .onComplete(_ => system.terminate())
    
}
