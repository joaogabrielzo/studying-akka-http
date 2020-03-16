package part1_lowlevel

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import part1_lowlevel.GuitarDB._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}

case class Guitar(make: String, model: String, qtd: Int = 0)

object GuitarDB {
    case class CreateGuitar(guitar: Guitar)
    case class GuitarCreated(id: Int)
    case class FindGuitar(id: Int)
    case object FindAllGuitars
    case class AddQuantity(id: Int, qtd: Int)
    case class FindAllGuitarsInStock(inStock: Boolean)
}

class GuitarDB extends Actor with ActorLogging {
    
    import GuitarDB._
    
    var guitars: Map[Int, Guitar] = Map()
    var currentGuitarId = 0
    
    override def receive: Receive = {
        case FindAllGuitars                 =>
            log.info("Searching for all guitars")
            sender() ! guitars.values.toList
        case FindGuitar(id)                 =>
            log.info("Searching guitar by id")
            sender() ! guitars.get(id)
        case CreateGuitar(guitar)           =>
            log.info(s"Adding guitar ${guitar.model} with ID $currentGuitarId and quantity ${guitar.qtd}")
            guitars = guitars + (currentGuitarId -> guitar)
            sender() ! GuitarCreated(currentGuitarId)
            currentGuitarId += 1
        case AddQuantity(id, qtd)           =>
            log.info(s"Trying to add $qtd guitars in id $id")
            val guitar: Option[Guitar] = guitars.get(id)
            val newGuitar: Option[Guitar] = guitar.map {
                case Guitar(make, model, quantity) => Guitar(make, model, quantity + qtd)
            }
            
            newGuitar.foreach(guitar => guitars = guitars + (id -> guitar))
            sender() ! newGuitar
        case FindAllGuitarsInStock(inStock) =>
            log.info(s"Searching for all guitars ${if (inStock) "in" else "out of"} stock")
            if (inStock)
                sender() ! guitars.values.filter(_.qtd > 0)
            else
                sender() ! guitars.values.filter(_.qtd == 0)
    }
}

trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
    
    implicit val guitarFormat: RootJsonFormat[Guitar] = jsonFormat3(Guitar)
    // converts Guitars into JSON. jsonFormat2 because it has 2 params
}

object LowLevelRest extends App with GuitarStoreJsonProtocol {
    
    implicit val system: ActorSystem = ActorSystem("low-level-rest")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(3 seconds)
    
    /*
        GET on localhost:8080/api/guitar => ALL the guitars in store
        GET on localhost:8080/api/guitar?id=X => fetches the guitar associated with id X
        POST on localhost:8080/api/guitar => insert guitar into store
     */
    
    // JSON -> marshalling (serialize the data to a format which the api can understand)
    
    val simpleGuitar = Guitar("Fender", "Stratocaster")
    println(simpleGuitar.toJson.prettyPrint) // the .toJson method is available via DefaultJsonProtocol trait
    
    // unmarshalling
    val simpleGuitarJson =
        """
            |{
            |  "make": "Fender",
            |  "model": "Stratocaster",
            |  "qtd": 3
            |}
            |""".stripMargin
    println(simpleGuitarJson.parseJson.convertTo[Guitar])
    
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
    
    /*
        Server code
     */
    def getGuitar(query: Query): Future[HttpResponse] = {
        
        val guitarId: Option[Int] = query.get("id").map(_.toInt)
        // because the query parameter acts like a Map, the .get method will return a Option[String] by default
        guitarId match {
            case None          => Future(HttpResponse(StatusCodes.NotFound))
            case Some(id: Int) =>
                val guitarFuture: Future[Option[Guitar]] = (guitarDB ? FindGuitar(id)).mapTo[Option[Guitar]]
                guitarFuture.map { // because it returns a Option, we got to Pattern Match it
                    case None         => HttpResponse(StatusCodes.NotFound)
                    case Some(guitar) =>
                        HttpResponse(
                            entity = HttpEntity(
                                ContentTypes.`application/json`,
                                guitar.toJson.prettyPrint
                            )
                        )
                }
            
        }
    }
    
    val requestHandler: HttpRequest => Future[HttpResponse] = {
        case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar"), _, _, _)            =>
//            query parameters handling code:
            val query = uri.query() // query object <=> Map[String(key), String(value)]
            if (query.isEmpty) {
                val guitarsFuture = (guitarDB ? FindAllGuitars).mapTo[List[Guitar]]
                guitarsFuture.map { guitars =>
                    HttpResponse(
                        StatusCodes.OK,
                        entity = HttpEntity(
                            ContentTypes.`application/json`,
                            guitars.toJson.prettyPrint
                        )
                    )
                }
            } else {
                // fetch guitar associated with id
                // localhost:9999/api/guitar?id=42
                getGuitar(query)
            }
        case HttpRequest(HttpMethods.POST, uri@Uri.Path("/api/guitar/inventory"), _, _, _) =>
            val query = uri.query()
            val guitarId = query.get("id").map(_.toInt)
            val guitarQtd = query.get("qtd").map(_.toInt)
            
            val validGuitarResponseFuture: Option[Future[HttpResponse]] = for {
                id <- guitarId
                qtd <- guitarQtd
            } yield {
                val newGuitarFuture: Future[Option[Guitar]] = (guitarDB ? AddQuantity(id, qtd)).mapTo[Option[Guitar]]
                newGuitarFuture.map(_ => HttpResponse(StatusCodes.OK))
            }
            
            validGuitarResponseFuture.getOrElse(Future(HttpResponse(StatusCodes.BadRequest)))
        // If it contains a value, will return it. Else, it's gonna return a BadRequest.
        
        case HttpRequest(HttpMethods.GET, uri@Uri.Path("/api/guitar/inventory"), headers, entity, protocol) =>
            val query = uri.query()
            val stockBool: Option[Boolean] = query.get("inStock").map(_.toBoolean)
            
            stockBool match {
                case Some(inStock) =>
                    val guitarsInStockFuture = (guitarDB ? FindAllGuitarsInStock(inStock)).mapTo[List[Guitar]]
                    // as the Ask returns a list, we got to map the result and marshal it into JSONs
                    guitarsInStockFuture.map { guitars =>
                        HttpResponse(
                            entity = HttpEntity(
                                ContentTypes.`application/json`,
                                guitars.toJson.prettyPrint
                            )
                        )
                    }
                case None          => Future(HttpResponse(StatusCodes.BadRequest))
            }
        
        
        case HttpRequest(HttpMethods.POST, Uri.Path("/api/guitar"), headers, entity, protocol) =>
//            entities are a Source[ByteString]
            val strictEntityFuture = entity.toStrict(3 seconds)
            // It will try to bring all the content on 'entity' into memory, in 3 seconds. Returns a Future.
            strictEntityFuture.flatMap { strictEntity =>
                val guitarJsonString = strictEntity.data.utf8String // Return a JSON String
                val guitar = guitarJsonString.parseJson.convertTo[Guitar] // return a Guitar instance
                
                val guitarCreatedFuture = (guitarDB ? CreateGuitar(guitar)).mapTo[GuitarCreated]
                guitarCreatedFuture.map(_ => HttpResponse(StatusCodes.OK))
            }
        
        case request: HttpRequest =>
            request.discardEntityBytes()
            Future {
                HttpResponse(StatusCodes.NotFound)
            }
    }
    
    Http().bindAndHandleAsync(requestHandler, "localhost", 9999)
    
}
