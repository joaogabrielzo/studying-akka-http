package part2_highlevel

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.util.Timeout
import pdi.jwt._
import spray.json._

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration._
import scala.util.{Failure, Success}


object SecurityDomain extends DefaultJsonProtocol {
    case class LoginRequest(username: String, password: String)
    
    implicit val loginRequestFormat: RootJsonFormat[LoginRequest] = jsonFormat2(LoginRequest)
}

object JWT extends App with SprayJsonSupport {
    
    implicit val system: ActorSystem = ActorSystem("jwt")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(3 seconds)
    
    import SecurityDomain._
    
    val passwordDatabase: Map[String, String] = Map(
        "zo" -> "zo",
        "admin" -> "admin"
    )
    
    val algorithm = JwtAlgorithm.HS256
    val secretKey = "zoSecret"
    
    def checkPassword(username: String, password: String): Boolean = {
        passwordDatabase.contains(username) && passwordDatabase(username) == password
    }
    
    def createToken(username: String, expirationTimeDays: Int): String = {
        val claims = JwtClaim(
            expiration = Some(System.currentTimeMillis() / 1000 + TimeUnit.DAYS.toSeconds(expirationTimeDays)),
            issuedAt = Some(System.currentTimeMillis() / 1000),
            issuer = Some("Zó")
        )
        
        JwtSprayJson.encode(claims, secretKey, algorithm) // JWT String
    }
    
    def isTokenExpired(token: String): Boolean =
        JwtSprayJson.decode(token, secretKey, Seq(algorithm)) match {
            case Success(claims) => claims.expiration.getOrElse(0L) < System.currentTimeMillis() / 1000
            case Failure(_)      => true
        }
    
    def isTokenValid(token: String): Boolean =
        JwtSprayJson.isValid(token, secretKey, Seq(algorithm))
    
    val loginRoute =
        post {
            entity(as[LoginRequest]) {
                case LoginRequest(username, password) if checkPassword(username, password) =>
                    val token = createToken(username, 1)
                    respondWithHeader(RawHeader("Acces-Token", token)) {
                        complete(StatusCodes.OK)
                    }
                case _                                                                     => complete(
                    StatusCodes.Unauthorized)
                
            }
        }
    
    val authenticatedRoute =
        (path("secureEndpoint") & get) {
            optionalHeaderValueByName("Authorization") {
                case Some(token) =>
                    if (isTokenValid(token)) {
                        if (isTokenExpired(token)) {
                            complete(HttpResponse(StatusCodes.Unauthorized, entity = "Token expired."))
                        } else {
                            complete("User accessed an authorized endpoint.")
                        }
                    } else {
                        complete(HttpResponse(StatusCodes.Unauthorized, entity = "Token is invalid."))
                    }
                case _           =>
                    complete(HttpResponse(StatusCodes.Unauthorized, entity = "No token provided."))
                
            }
        }
    
    val route = loginRoute ~ authenticatedRoute
    
    Http().bindAndHandle(route, "localhost", 9999)
    
}
