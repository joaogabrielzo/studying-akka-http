package part2_highlevel

import akka.actor.ActorSystem
import akka.event.LoggingAdapter
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer

object DirectivesBreakdown extends App {
    
    implicit val system = ActorSystem("directives-breakdown")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher
    
    import akka.http.scaladsl.server.Directives._
    
    /**
      * Type #1: Filtering Directives
      */
    val simpleHttpMethodRoute =
        post {
            complete {
                StatusCodes.Forbidden
            }
        }
    
    val simplePathRoute =
        path("about") {
            complete {
                HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    "Hello"
                )
            }
        }
    
    val complexPathRoute =
        path("api" / "myEndpoint") { // api/myEndpoint the WRITE way
            complete {
                StatusCodes.OK
            }
        }
    val dontConfuseWith =
        path("api/myEndpoint") { // This won't work because the / is gonna be a string within the words
            complete {
                StatusCodes.OK
            }
        }
    
    val pathEndRoute =
        pathEndOrSingleSlash { // = localhost:9999 or localhost:9999/
            complete {
                StatusCodes.OK
            }
        }
    
    /**
      * Type #2: Extraction Directives
      */
    // GET on /api/item/{id}
    
    val pathExtractionRoute =
    path("api" / "item" / IntNumber) { (itemId: Int) =>
        // the path now receives a function between that IntNumber and the result inside
        println(s"I've got the ID $itemId") // this will print on IDE console only
        complete { // It must always end on another route
            HttpEntity(
                ContentTypes.`text/html(UTF-8)`,
                s"This is the ID you got: \n$itemId"
            )
        }
    }
    
    val pathMultiExtract =
        path("api" / "order" / IntNumber / IntNumber) { (id, qtd) =>
            println(s"I've got TWO numbers: $id and $qtd")
            complete {
                StatusCodes.OK
            }
        }
    
    val queryParamExtraction =
    // /api/item?id=42
        path("api" / "item") {
            parameter("id".as[Int]) { (itemId) =>
                println(s"I've extracted the ID as $itemId")
                complete {
                    HttpEntity(
                        ContentTypes.`text/html(UTF-8)`,
                        s"Your first parameter is $itemId"
                    )
                }
            }
        }
    
    val extractRequestRoute: Route =
        path("controlEndpoint") {
            extractRequest { (httpRequest: HttpRequest) => // you can actually extract a lot of things
                extractLog { (log: LoggingAdapter) =>
                    log.info(s"I got a request as $httpRequest")
                    complete {
                        HttpResponse(
                            StatusCodes.OK,
                            entity = HttpEntity(
                                ContentTypes.`text/html(UTF-8)`,
                                "This is your response, take it"
                            )
                        )
                    }
                }
            }
        }
    
    /**
      * Type #3: Composite Directives
      */
    
    val simpleNestedRoute =
        path("api" / "item") {
            get {
                complete(StatusCodes.OK)
            }
        }
    
    // "path" and "get" are both filtering directives, so a composite directive with them is also a filtering directive
    val compactSimpleNestedRoute = (path("api" / "item") & get) {
        complete(StatusCodes.OK)
    }
    
    val compactExtractDirectiveRoute =
        (path("controlEndpoint") & extractRequest & extractLog) { (request, log) =>
            log.info(s"I got a request as $request")
            complete(StatusCodes.OK)
        }
    
    val repeatedRoute =
        path("about") {
            complete(StatusCodes.OK)
        } ~
        path("aboutUs") {
            complete(StatusCodes.OK)
        }
    
    // you can use the | to join paths which have the same logic
    val dryRoute =
        (path("about") | path("aboutUs")) {
            complete(StatusCodes.OK)
        }
    
    // blog.com/42 AND blog.com?postId=42
    val blogByIdRoute =
        path(IntNumber) { (blogPostId) =>
            // same server logic
            complete(StatusCodes.OK)
        }
    val blogByQueryParamRoute =
        parameter('postId.as[Int]) { (blogPostId) =>
            // same complex server logic
            complete(StatusCodes.OK)
        }
    
    val combinedBlogByIdRoute =
        (path(IntNumber) | parameter('postId.as[Int])) { (blogPostId) =>
            // original server logic
            complete(StatusCodes.OK)
        }
    
    /**
      * Type #4: "Actionable" Directives
      */
    
    val completeOkRoute = complete(StatusCodes.OK)
    
    val failedRoute =
        path("notSupported") {
            failWith(new RuntimeException("Unsupported!")) // complete with HTTP 500
        }
    
    val routeWithRejection =
        path("home") {
            reject
        } ~
        path("index") {
            completeOkRoute
        } // If a request comes into /home, it will reject and pass it to the next route
    
    val getOrPutPath =
        path("api" / "myEndpoint") {
            get {
                completeOkRoute
            } ~
            post {
                complete(StatusCodes.Forbidden)
            }
        }
    
    Http().bindAndHandle(getOrPutPath, "localhost", 9999)
    
}
