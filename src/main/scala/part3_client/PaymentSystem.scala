package part3_client

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.util.Timeout
import part3_client.PaymentSystemDomain._
import spray.json._

import scala.concurrent.duration._


case class CreditCard(serialNumber: String, securityCode: String, account: String)
object PaymentSystemDomain {
    
    case class PaymentRequest(creditCard: CreditCard, receiverAccount: String, amount: Double)
    case object PaymentAccepted
    case object PaymentRejected
}

trait PaymentJsonProtocol extends DefaultJsonProtocol {
    
    implicit val creditCardFormat = jsonFormat3(CreditCard)
    implicit val paymentRequestFormat = jsonFormat3(PaymentRequest)
}

class PaymentValidator extends Actor with ActorLogging {
    
    override def receive: Receive = {
        case PaymentRequest(CreditCard(serialNumber, _, senderAccount), receiverAccount, amount) =>
            log.info(s"$senderAccount is trying to send $amount to $receiverAccount")
            if (serialNumber == "0000-0000-0000-0000") sender() ! PaymentRejected
            else sender() ! PaymentAccepted
    }
}

object PaymentSystem extends App
                     with PaymentJsonProtocol
                     with SprayJsonSupport {
    
    implicit val system = ActorSystem("payment-system")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher
    implicit val timeout = Timeout(3 seconds)
    
    val paymentValidator = system.actorOf(Props[PaymentValidator], "payment-validator")
    
    val paymentRoute =
        path("api" / "payments") {
            post {
                entity(as[PaymentRequest]) { paymentRequest =>
                    val validationResponse = (paymentValidator ? paymentRequest).map {
                        case PaymentRejected => StatusCodes.Forbidden
                        case PaymentAccepted => StatusCodes.OK
                        case _               => StatusCodes.BadRequest
                    }
                    
                    complete(validationResponse)
                }
            }
        }
    
    Http().bindAndHandle(paymentRoute, "localhost", 9999)
    
}
