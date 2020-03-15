package part1_lowlevel

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import part1_lowlevel.GuitarDB.{CreateGuitar, FindAllGuitars, GuitarCreated}
import spray.json._

import scala.concurrent.Future
import scala.concurrent.duration._

case class Guitar(make: String, model: String)

object GuitarDB {
    case class CreateGuitar(guitar: Guitar)
    case class GuitarCreated(id: Int)
    case class FindGuitar(id: Int)
    case object FindAllGuitars
}

class GuitarDB extends Actor with ActorLogging {
    
    import GuitarDB._
    
    var guitars: Map[Int, Guitar] = Map()
    var currentGuitarId = 0
    
    override def receive: Receive = {
        case FindAllGuitars       =>
            log.info("Searching for all guitars")
            sender() ! guitars.values.toList
        case FindGuitar(id)       =>
            log.info("Searching guitar by id")
            sender() ! guitars.get(id)
        case CreateGuitar(guitar) =>
            log.info(s"Adding ${guitar.model} with ID $currentGuitarId")
            guitars = guitars + (currentGuitarId -> guitar)
            sender() ! GuitarCreated(currentGuitarId)
            currentGuitarId += 1
    }
}

trait GuitarStoreJsonProtocol extends DefaultJsonProtocol {
    
    implicit val guitarFormat = jsonFormat2(Guitar) // convert Guitars into JSON. jsonFormat2 because it has 2 params
}

object LowLevelRest extends App with GuitarStoreJsonProtocol {
    
    implicit val system = ActorSystem("low-level-rest")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher
    implicit val timeout = Timeout(3 seconds)
    
    /*
        GET on localhost:8080/api/guitar => ALL the guitars in store
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
            |  "model": "Stratocaster"
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
    
    val requestHandler: HttpRequest => Future[HttpResponse] = {
        case HttpRequest(HttpMethods.GET, Uri.Path("/api/guitar"), _, _, _)                    =>
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
