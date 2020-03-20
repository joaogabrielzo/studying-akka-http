package part2_highlevel

import java.io.File

import akka.Done
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.{ActorMaterializer, IOResult}
import akka.stream.scaladsl.{FileIO, Sink, Source}
import akka.util.{ByteString, Timeout}

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.util.{Failure, Success}

object UploadFiles extends App {
    
    implicit val system: ActorSystem = ActorSystem("upload-files")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val ec: ExecutionContextExecutor = system.dispatcher
    implicit val timeout: Timeout = Timeout(3 seconds)
    
    val filesRoute =
        (pathEndOrSingleSlash & get) {
            complete(
                HttpEntity(
                    ContentTypes.`text/html(UTF-8)`,
                    """
                        |<html>
                        |  <body>
                        |    <form action="http://localhost:9999/upload" method="post">
                        |      <input type="file" name="zo">
                        |      <button type="submit">Upload</button>
                        |    </form>
                        |  </body>
                        |</html>
                        |""".stripMargin
                )
            )
        } ~
        (path("upload") & extractLog) { log =>
            // handle uploading
            // multipart / form-data
            entity(as[Multipart.FormData]) { formdata => // formdata will be handled as a source
                // handle file payload
                val partsSource: Source[Multipart.FormData.BodyPart, _] = formdata.parts
                // this is a source of all the chunks this file may contain
                
                val filePartsSink: Sink[Multipart.FormData.BodyPart, Future[Done]] =
                    Sink.foreach[Multipart.FormData.BodyPart] { bodyPart =>
                        if (bodyPart.name == "zo") {
                            /*
                                Creating a file
                             */
                            // Save the path
                            val filename = "src/name/resources/upload/" + bodyPart.filename.getOrElse(
                                "tempFile_" + System.currentTimeMillis())
                            // Create the file using "Java.io.file" package
                            val file: File = new File(filename)
                            
                            log.info(s"Write to file $filename")
                            
                            // Retrieve the binary representation of the payload
                            val fileContentsSource: Source[ByteString, _] = bodyPart.entity.dataBytes
                            // Stream the binary representation to the file via Akka-Stream
                            val fileContentsSink: Sink[ByteString, _] = FileIO.toPath(file.toPath)
                            
                            fileContentsSource.runWith(fileContentsSink)
                        }
                    }
                
                val writeOperationFuture = partsSource.runWith(filePartsSink)
                
                onComplete(writeOperationFuture) {
                    case Success(_)  => complete("File uploaded.")
                    case Failure(ex) => complete(s"File failed to upload: $ex")
                }
                
            }
        }
    
    Http().bindAndHandle(filesRoute, "localhost", 9999)
    
}
