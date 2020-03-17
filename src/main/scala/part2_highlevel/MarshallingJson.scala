package part2_highlevel

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import part2_highlevel.GameAreaMap._
import spray.json._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._

case class Player(nickname: String, characterClass: String, level: Int)

trait PlayerJsonProtocol extends DefaultJsonProtocol {
    
    implicit val playerJson: RootJsonFormat[Player] = jsonFormat3(Player)
}

object GameAreaMap {
    case object OperationSuccess
    case object GetAllPlayers
    case class GetPlayer(nickname: String)
    case class GetPlayerByClass(characterClass: String)
    case class AddPlayer(player: Player)
    case class RemovePlayer(player: Player)
    
}

class GameAreaMap extends Actor with ActorLogging {
    
    // This class will check which players are active in the actual map
    var players: Map[String, Player] = Map() // [Nickname, Player]
    
    override def receive: Receive = {
        case GetAllPlayers =>
            log.info("Getting all players in this map")
            sender() ! players.values.toList
        
        case GetPlayer(nickname) =>
            log.info(s"Getting informations on $nickname")
            sender() ! players.get(nickname)
        
        case GetPlayerByClass(characterClass) =>
            log.info(s"Getting all players with class $characterClass")
            sender() ! players.values.toList.filter(_.characterClass == characterClass)
        
        case AddPlayer(player) =>
            log.info(s"Trying to add player ${player.nickname}")
            players += (player.nickname -> player)
            sender() ! OperationSuccess
        
        case RemovePlayer(player) =>
            log.warning(s"Removing player ${player.nickname}")
            players -= player.nickname
            sender() ! OperationSuccess
        
    }
    
}

object MarshallingJson extends App
                       with PlayerJsonProtocol
                       with SprayJsonSupport { // Marshall everything that can be converted to json automatically
    
    implicit val system: ActorSystem = ActorSystem("marshalling-json")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(3 seconds)
    
    val gameMap = system.actorOf(Props[GameAreaMap], "game-area-map")
    
    val playersList = List(
        Player("player1", "Warrior", 70),
        Player("player2", "Mage", 49),
        Player("player3", "Thief", 67)
    )
    
    playersList.foreach(player => gameMap ! AddPlayer(player))
    
    /*
        - GET /api/player : returns all the players in the map
        - GET /api/player/(nickname) : returns player with the given nickname
        - GET /api/player?nickname=X : ^ same
        - GET /api/player/class/(characterClass) : returns all players with given class
        - POST /api/player : adds player to the map
        - DELETE /api/player : removes player from the map
     */
    
    val gameMapServerRoute =
        pathPrefix("api" / "player") {
            get {
                path("class" / Segment) { characterClass =>
                    val playersFuture = (gameMap ? GetPlayerByClass(characterClass)).mapTo[List[Player]]
                    complete(playersFuture)
                    // It was automatically converted to JSON because of the PlayerJsonProtocol and the
                    // SprayJsonSupport trait
                } ~
                (path(Segment) | parameter("nickname")) { nickname =>
                    val playerFuture = (gameMap ? GetPlayer(nickname)).mapTo[Option[Player]]
                    complete(playerFuture)
                } ~
                pathEndOrSingleSlash {
                    val playersFuture = (gameMap ? GetAllPlayers).mapTo[List[Player]]
                    complete(playersFuture)
                }
            } ~
            post {
                entity(as[Player]) { player =>
                    // entity(as[Player]) gets the json payload and transforms it into a Player object
                    val postPlayerFuture = (gameMap ? AddPlayer(player)).map(_ => StatusCodes.OK)
                    complete(postPlayerFuture)
                }
                
            } ~
            delete {
                entity(as[Player]) { player =>
                    val deletePlayerFuture = (gameMap ? RemovePlayer(player)).map(_ => StatusCodes.OK)
                    complete(deletePlayerFuture)
                }
            }
        }
    
    Http().bindAndHandle(gameMapServerRoute, "localhost", 9999)
    
}
