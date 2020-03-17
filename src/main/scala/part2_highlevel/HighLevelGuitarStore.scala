package part2_highlevel

import akka.actor.{ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import spray.json._
import part1_lowlevel.{Guitar, GuitarDB, GuitarStoreJsonProtocol}
import part1_lowlevel.GuitarDB._

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration._

object HighLevelGuitarStore extends App with GuitarStoreJsonProtocol {
    
    implicit val system: ActorSystem = ActorSystem("high-level-rest")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(3 seconds)
    
    /*
        GET /api/guitar fetches ALL guitars in the store
        GET /api/guitar?id=X fetches guitar with id X
        GET /api/guitar/X fetches guitar with id X
        GET /api/guitar/inventory?inStock=true
     */
    
    /*
        Setup
     */
    
    val guitarDB = system.actorOf(Props[GuitarDB], "guitar-db")
    val guitarsList = List(
        Guitar("Fender", "Stratocaster"),
        Guitar("Gibson", "LesPaul"),
        Guitar("Martin", "LX1")
    )
    
    guitarsList.foreach(guitar => guitarDB ! CreateGuitar(guitar))
    
    val guitarServerRoute =
        path("api" / "guitar") {
            // the more specific route goes first.
            parameter("id".as[Int]) { guitarId =>
                get {
                    val guitarFuture: Future[Option[Guitar]] =
                        (guitarDB ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
                    val entityFuture = guitarFuture.map { guitarOption =>
                        HttpEntity(
                            ContentTypes.`application/json`,
                            guitarOption.toJson.prettyPrint
                        )
                    }
                    complete(entityFuture)
                }
            }
        } ~
        get {
            val guitarsFuture = (guitarDB ? FindAllGuitars).mapTo[List[Guitar]]
            val entityFuture: Future[HttpEntity.Strict] = guitarsFuture.map { guitars =>
                HttpEntity(
                    ContentTypes.`application/json`,
                    guitars.toJson.prettyPrint
                )
            }
            complete(entityFuture)
        } ~
        path("api" / "guitar" / IntNumber) { guitarId =>
            get {
                val guitarFuture: Future[Option[Guitar]] =
                    (guitarDB ? FindGuitar(guitarId)).mapTo[Option[Guitar]]
                val entityFuture = guitarFuture.map { guitarOption =>
                    HttpEntity(
                        ContentTypes.`application/json`,
                        guitarOption.toJson.prettyPrint
                    )
                }
                complete(entityFuture)
            }
        } ~
        path("api" / "guitar" / "inventory") {
            get {
                parameter("inStock".as[Boolean]) { inStock =>
                    val guitarFuture = (guitarDB ? FindAllGuitarsInStock(inStock)).mapTo[Option[List[Guitar]]]
                    val entityFuture: Future[HttpEntity.Strict] = guitarFuture.map { guitars =>
                        HttpEntity(
                            ContentTypes.`application/json`,
                            guitars.toJson.prettyPrint
                        )
                    }
                    complete(entityFuture)
                }
            }
        }
    
    def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)
    
    val simplifiedGuitarServerRoute =
        (pathPrefix("api" / "guitar") & get) {
            path("inventory") {
                parameter("inStock".as[Boolean]) { inStock =>
                    complete(
                        (guitarDB ? FindAllGuitarsInStock(inStock)) // Ask the actor for the guitars
                            .mapTo[List[Guitar]] // transform the response into an Option List of Guitars
                            .map(_.toJson.prettyPrint) // transform the guitars into Json (strings)
                            .map(toHttpEntity) // send each Json as an HttpEntity
                    )
                }
            } ~
            (path(IntNumber) | parameter("id".as[Int])) { guitarId =>
                complete(
                    (guitarDB ? FindGuitar(guitarId))
                        .mapTo[Option[Guitar]]
                        .map(_.toJson.prettyPrint)
                        .map(toHttpEntity)
                )
            } ~
            pathEndOrSingleSlash {
                complete(
                    (guitarDB ? FindAllGuitars)
                        .mapTo[List[Guitar]]
                        .map(_.toJson.prettyPrint)
                        .map(toHttpEntity)
                )
            }
        }
    
    Http().bindAndHandle(simplifiedGuitarServerRoute, "localhost", 9999)
    
}
