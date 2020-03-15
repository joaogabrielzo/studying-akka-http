package part1_lowlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.IncomingConnection
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object LowLevelAPI extends App {
    
    implicit val system = ActorSystem("low-level-api")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher
    
    val serverSource = Http().bind("localhost", 8000)
    val connectionSink = Sink.foreach[IncomingConnection] { conn =>
        println(s"Accepted connection from ${conn.remoteAddress}")
    }
    
    val serverBindingFuture = serverSource.runWith(connectionSink)
    
    serverBindingFuture.onComplete {
        case Success(_)         => println("Server binding success")
        case Failure(exception) => println(s"Server binding failed: $exception")
    }
    
    /*
        Method 1: synchronously serve HTTP response
     */
    
    val requestHandler: HttpRequest => HttpResponse = {
        case HttpRequest(HttpMethods.GET, _, _, _, _) =>
            HttpResponse(
                StatusCodes.OK,
                entity = HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    """
                        |<html>
                        |<body>
                        |Hello from Akka HTTP.
                        |</body>
                        |</html>
                        |""".stripMargin
                )
            )
        case request: HttpRequest                     =>
            request.discardEntityBytes()
            HttpResponse(
                StatusCodes.NotFound,
                entity = HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    """
                        |<html>
                        |<body>
                        |Resource not found.
                        |</body>
                        |</html>
                        |""".stripMargin
                )
            )
    }
    
    val httpSyncConnectionHandler = Sink.foreach[IncomingConnection] { conn =>
        conn.handleWithSyncHandler(requestHandler)
    }

//    Http().bind("localhost", 8080).runWith(httpSyncConnectionHandler)
//    Http().bindAndHandleSync(requestHandler, "localhost", 8080)
    // Both do the same thing
    
    /*
        Method 2: serve asynchronously HTTP response
     */
    
    val asyncRequestHandler: HttpRequest => Future[HttpResponse] = {
        case HttpRequest(HttpMethods.GET, Uri.Path("/home"), _, _, _) => // (method, URI, HTTP headers, content,
            // protocol(HTTP1.1/2.0)
            Future(HttpResponse(
                StatusCodes.OK, // HTTP 200
                entity = HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    """
                        |<html>
                        |<body>
                        |Hello from Akka HTTP.
                        |</body>
                        |</html>
                        |""".stripMargin
                )
            ))
        case request: HttpRequest                                     =>
            request.discardEntityBytes()
            Future(HttpResponse(
                StatusCodes.NotFound,
                entity = HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    """
                        |<html>
                        |<body>
                        |Resource not found.
                        |</body>
                        |</html>
                        |""".stripMargin
                )
            ))
    }

//    Http().bindAndHandleAsync(asyncRequestHandler, "localhost", 8081)
    
    /*
        Method 3: serve HTTP response asynchronously via Akka Stream
     */
    
    val streamRequestHandler: Flow[HttpRequest, HttpResponse, _] = Flow[HttpRequest].map {
        case HttpRequest(HttpMethods.GET, _, _, _, _) =>
            HttpResponse(
                StatusCodes.OK,
                entity = HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    """
                        |<html>
                        |<body>
                        |Hello from Akka HTTP.
                        |</body>
                        |</html>
                        |""".stripMargin
                )
            )
        case request: HttpRequest                     =>
            request.discardEntityBytes()
            HttpResponse(
                StatusCodes.NotFound,
                entity = HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    """
                        |<html>
                        |<body>
                        |Resource not found.
                        |</body>
                        |</html>
                        |""".stripMargin
                )
            )
    }
    
    Http().bindAndHandle(streamRequestHandler, "localhost", 8082)
    
}
