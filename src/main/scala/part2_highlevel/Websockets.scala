package part2_highlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util._

import scala.concurrent.duration._

object Websockets extends App {
    
    /*
        WebSocket is a computer communications protocol,
        providing full-duplex communication channels over a single TCP connection.
        
        It enables interaction between a client application and a server.
     */
    
    implicit val system = ActorSystem("websocket")
    implicit val materializer = ActorMaterializer()
    implicit val ec = system.dispatcher
    implicit val timeout = Timeout(3 seconds)
    
    // Message: Text Message x Binary Message
    val textMessage = TextMessage(Source.single("Hello from a text message"))
    val binaryMessage = BinaryMessage(Source.single(CompactByteString("Hello from a binary message")))
    
    val html =
        """
            |<html>
            |    <head>
            |        <script>
            |            var exampleSocket = new WebSocket("ws://localhost:9999/greeter");
            |            console.log("starting websocket");
            |
            |            exampleSocket.onmessage = function(event) {
            |                var newChild = document.createElement("div")
            |                newChild.innerText = event.data;
            |                document.getElementById("1").appendChild(newChild);
            |            };
            |
            |            exampleSocket.onopen = function(event) {
            |                exampleSocket.send("Socket seems to be open...");
            |            };
            |
            |            exampleSocket.send("socket says: hello, server")
            |
            |        </script>
            |    </head>
            |
            |    <body>
            |        Starting websocket...
            |        <div id="1">
            |
            |        </div>
            |    </body>
            |</html>
            |""".stripMargin
    
    def websocketFlow: Flow[Message, Message, _] = Flow[Message].map {
        case tm: TextMessage =>
            TextMessage(Source.single("Server says back:") ++ tm.textStream ++ Source.single("!"))
        
        case bm: BinaryMessage =>
            bm.dataStream.runWith(Sink.ignore)
            TextMessage(Source.single("Server received a binary message..."))
    }
    
    val webSocketRoute =
        (pathEndOrSingleSlash & get) {
            complete(
                HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    html
                )
            )
        } ~
        path("greeter") {
            handleWebSocketMessages(socialFlow)
        }
    
    Http().bindAndHandle(webSocketRoute, "localhost", 9999)
    
    case class SocialPost(owner: String, content: String)
    
    val socialFeed = Source(
        List(
            SocialPost("Person1", "This is a shitpost"),
            SocialPost("Person2", "This should be a meme"),
            SocialPost("Person3", "Politics post because I'm dumb")
        )
    )
    
    val socialMessages = socialFeed.throttle(1, 3 seconds)
                                   .map(post => TextMessage(s"${post.owner} said: ${post.content}"))
    
    val socialFlow: Flow[Message, Message, _] = Flow.fromSinkAndSource(
        Sink.foreach[Message](println),
        socialMessages
    )
    
}
