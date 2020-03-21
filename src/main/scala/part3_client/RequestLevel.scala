package part3_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import part3_client.PaymentSystemDomain.PaymentRequest
import spray.json._

import scala.util.{Failure, Success}

object RequestLevel extends App with PaymentJsonProtocol {
    
    implicit val system = ActorSystem("request-level")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher
    
    val responseFuture = Http().singleRequest(HttpRequest(uri = "http://www.google.com.br"))
    responseFuture.onComplete {
        case Success(value)     =>
            value.discardEntityBytes()
            println(s"Success: $value")
        case Failure(exception) => println(s"Failure: $exception")
    }
    
    val creditCards = List(
        CreditCard("4848-4848-4848-4848", "496", "ta-23-da-89"),
        CreditCard("5946-9567-4954-3150", "123", "es-34-kv-01"),
        CreditCard("0000-0000-0000-0000", "000", "gf-23-ku-10")
    )
    
    val paymentRequests = creditCards.map(cc => PaymentRequest(cc, "amazon-maybe", 20.97))
    val serverHttpRequests = paymentRequests.map { request =>
        HttpRequest(
            HttpMethods.POST,
            uri = "http://localhost:9999/api/payments",
            entity = HttpEntity(
                ContentTypes.`application/json`,
                request.toJson.prettyPrint
            )
        )
    }
    
    Source(serverHttpRequests).mapAsyncUnordered(10)(request => Http().singleRequest(request))
                              .runForeach(println)
    
}
