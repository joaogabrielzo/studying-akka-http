package part3_client

import java.util.UUID

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import part3_client.PaymentSystemDomain._
import spray.json._

import scala.util.{Failure, Success}

object HostLevel extends App with PaymentJsonProtocol {
    
    implicit val system = ActorSystem("host-level")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher
    
    /*
        The Host Level API gives us the freedom from managing individual connections
        and the ability to attach data to requests (aside from payloads)
        
        Recommended for high volume and low latency requests
     */
    
    val poolFlow = Http().cachedHostConnectionPool[Int]("www.google.com")
    
    Source(1 to 10).map(i => (HttpRequest(), i))
                   .via(poolFlow)
                   .map {
                       case (Success(response), value) =>
                           response.discardEntityBytes()
                           // If we don't do this, it will block the connection which the response wants get through
                           s"Request $value has received $response"
                       case (Failure(ex), value)       =>
                           s"Request $value has failed: $ex"
                   }
                   .runWith(Sink.foreach[String](println))
    
    val creditCards = List(
        CreditCard("4848-4848-4848-4848", "496", "ta-23-da-89"),
        CreditCard("5946-9567-4954-3150", "123", "es-34-kv-01"),
        CreditCard("0000-0000-0000-0000", "000", "gf-23-ku-10")
    )
    
    val paymentRequests = creditCards.map(cc => PaymentRequest(cc, "amazon-maybe", 20.97))
    val serverHttpRequests = paymentRequests.map { request =>
        (
            HttpRequest(
                HttpMethods.POST,
                uri = Uri("/api/payments"),
                entity = HttpEntity(
                    ContentTypes.`application/json`,
                    request.toJson.prettyPrint
                )
            ),
            UUID.randomUUID().toString
        )
    }
    
    Source(serverHttpRequests)
        .via(Http().cachedHostConnectionPool[String]("localhost", 9999))
        .runForeach { // (Try[HttpResponse], String)
            case (Success(response), orderId) =>
                println(s"The order ID $orderId was successful and returned $response")
            // You can actually do something with the data attached to the response like
            // dispatch it, send a notification to the costumer, etc
            case (Failure(ex), orderId) =>
                println(s"The order ID $orderId could no be completed: $ex")
        }
    
}
