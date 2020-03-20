package part3_client

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import part3_client.PaymentSystemDomain.PaymentRequest
import spray.json._

import scala.concurrent.Future
import scala.util.{Failure, Success}

object ConnectionLevel extends App
                       with PaymentJsonProtocol {
    
    implicit val system = ActorSystem("connection-level")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher
    
    val connectionFlow: Flow[HttpRequest, HttpResponse, Future[Http.OutgoingConnection]] = Http().outgoingConnection(
        "www.google.com")
    
    def oneOffRequest(request: HttpRequest) =
        Source.single(request).via(connectionFlow).runWith(Sink.head)
    
    oneOffRequest(HttpRequest()).onComplete {
        case Success(response) => println(s"Got a response: $response")
        case Failure(ex)       => println(s"Failed with $ex")
    }
    
    /*
        This is mostly used by Microservices because it needs to communicate with another servers.
        - You open a connection and send lots of requests through it.
        - Need to link to a Source of requests and a Sink to receive them
     */
    
    /**
      * Payment Service
      */
    
    val creditCards = List(
        CreditCard("4848-4848-4848-4848", "496", "ta-23-da-89"),
        CreditCard("5946-9567-4954-3150", "123", "es-34-kv-01"),
        CreditCard("0000-0000-0000-0000", "000", "gf-23-ku-10")
    )
    
    val paymentRequests = creditCards.map(cc => PaymentRequest(cc, "amazon-maybe", 20.97))
    val serverHttpRequests = paymentRequests.map { request =>
        HttpRequest(
            HttpMethods.POST,
            uri = Uri("/api/payments"),
            entity = HttpEntity(
                ContentTypes.`application/json`,
                request.toJson.prettyPrint
            )
        )
    }
    
    Source[HttpRequest](serverHttpRequests)
        .via(Http().outgoingConnection("localhost", 9999))
        .to(Sink.foreach[HttpResponse](println))
        .run()
    
}
