package exercises

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import exercises.PersonDB._
import spray.json._

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

case class Person(pin: Int, name: String)

trait PersonJsonProtocol extends DefaultJsonProtocol {
    
    implicit val personFormat: RootJsonFormat[Person] = jsonFormat2(Person)
}

object PersonDB {
    case object FetchAllPersons
    case class FetchPersonByPin(pin: Int)
    case class AddNewPerson(person: Person)
    case class PersonAdded(pin: Int)
}
class PersonDB extends Actor with ActorLogging {
    
    import PersonDB._
    
    var people = List(
        Person(1, "Alice"),
        Person(2, "Bob"),
        Person(3, "Charlie")
    )
    
    override def receive: Receive = {
        case FetchAllPersons         =>
            log.info("Searching for every person in town")
            sender() ! people
        case FetchPersonByPin(pinId) =>
            log.info(s"Searching for person with pin $pinId")
            sender() ! people.filter(_.pin == pinId)
        case AddNewPerson(person)    =>
            log.info("Adding person to database")
            people = people :+ person
            sender() ! PersonAdded(person.pin)
    }
}

object HighLevelExercise extends App with PersonJsonProtocol {
    
    implicit val system: ActorSystem = ActorSystem("high-level-exercise")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(3 seconds)
    
    /**
      * Exercise:
      * - GET /api/people: retrieves ALL the people registered
      * - GET /api/people/pin: retrieves the person with that pin
      * - GET /api/people?pin=X: retrieves the person with that pin
      * - POST /api/people: post a json payload denoting a Person
      *   - extract the HTTP request's payload (entity)
      *     - extract the request
      *     - process the entity's data
      */
    
    /**
      * Step 1: Add a JSON support for the Person case class
      * Step 2: Set up the server route early
      */
    
    val personDb = system.actorOf(Props[PersonDB], "person-database")
    
    def toHttpEntity(payload: String) = HttpEntity(ContentTypes.`application/json`, payload)
    
    val personServer =
        pathPrefix("api" / "person") {
            get {
                (path(IntNumber) | parameter('pin.as[Int])) { pinNumber =>
                    complete {
                        (personDb ? FetchPersonByPin(pinNumber))
                            .mapTo[List[Person]]
                            .map(_.toJson.prettyPrint)
                            .map(toHttpEntity)
                    }
                } ~
                pathEndOrSingleSlash {
                    complete(
                        (personDb ? FetchAllPersons)
                            .mapTo[List[Person]]
                            .map(_.toJson.prettyPrint)
                            .map(toHttpEntity)
                    )
                }
            } ~
            post {
                extractRequestEntity(entity => {
                    val entityFuture: Future[HttpEntity.Strict] = entity.toStrict(3 seconds)
                    val personFuture =
                        entityFuture.map(payload => payload.data.utf8String.parseJson.convertTo[Person])
                    
                    onComplete(personFuture) {
                        case Success(person) =>
                            (personDb ? AddNewPerson(person)).mapTo[PersonAdded]
                            complete(StatusCodes.OK)
                        case Failure(ex)     =>
                            failWith(ex)
                    }
                })
            }
        }
    
    Http().bindAndHandle(personServer, "localhost", 9999)
    
}
