package part2_highlevel

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport
import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import spray.json._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.ActorMaterializer
import org.scalatest.{Matchers, WordSpec}

case class Book(id: Int, author: String, title: String)

trait BookJsonProtocol extends DefaultJsonProtocol {
    
    implicit val bookFormat: RootJsonFormat[Book] = jsonFormat3(Book)
}

class RouteDSLSpec extends WordSpec
                   with Matchers
                   with ScalatestRouteTest
                   with BookJsonProtocol {
    
    import RouteDSLSpec._
    
    "A digital library backend" should {
        "return all the books in the library" in {
            // send an HTTP request and inspect the response
            
            // like Akka-Streams, using the ~> to build a flow
            // This will make a GET request to the libraryRoute and check the result
            Get("/api/book") ~> libraryRoute ~> check {
                // assertions
                status shouldBe StatusCodes.OK // The request must be successful
                entityAs[List[Book]] shouldBe books
                // The HTTP Response converted into a list of Book must be equal to the books variable list
            }
        }
        "return a book by it's id through a query parameter" in {
            Get("/api/book?id=2") ~> libraryRoute ~> check {
                status shouldBe StatusCodes.OK
                responseAs[Option[Book]] shouldBe Some(Book(2, "J.R.R. Tolkien", "The Lord of the Rings"))
            }
        }
        "return a book by it's id through the endpoint" in {
            Get("/api/book/2") ~> libraryRoute ~> check {
                status shouldBe StatusCodes.OK
                response.entity.contentType shouldBe ContentTypes.`application/json`
                responseAs[Option[Book]] shouldBe Some(Book(2, "J.R.R. Tolkien", "The Lord of the Rings"))
            }
        }
        "add a new book to the Books variable" in {
            val newBookPayload = Book(5, "Karl Marx", "The Communist Manifesto")
            Post("/api/book", newBookPayload) ~> libraryRoute ~> check {
                status shouldBe StatusCodes.OK
                books should contain(newBookPayload)
            }
        }
        "return all the books by an author through the endpoint" in {
            Get("/api/book/author/Ernest%20Hemingway") ~> libraryRoute ~> check {
                status shouldBe StatusCodes.OK
                
                entityAs[List[Book]] shouldBe books.filter(_.author == "Ernest Hemingway")
            }
        }
    }
}

object RouteDSLSpec extends BookJsonProtocol
                    with SprayJsonSupport {
    
    // code under test
    var books = List(
        Book(1, "Harper Lee", "To Kill a Mockingbird"),
        Book(2, "J.R.R. Tolkien", "The Lord of the Rings"),
        Book(3, "G.R.R. Martin", "A Song of Ice and Fire"),
        Book(4, "Ernest Hemingway", "The Sun Also Rises")
    )
    
    /*
        GET /api/book - returns all books
        GET /api/book/X & api/book?id=X - returns book with id X
        POST /api/book - adds new book
     */
    
    val libraryRoute =
        pathPrefix("api" / "book") {
            (path("author" / Segment) & get) { author =>
                val booksWithAuthor = books.filter(_.author == author)
                complete(booksWithAuthor)
            } ~
            get {
                (path(IntNumber) | parameter("id".as[Int])) { bookId =>
                    val bookWithId = books.find(_.id == bookId)
                    complete(bookWithId)
                } ~
                pathEndOrSingleSlash {
                    complete(books)
                }
            } ~
            post {
                entity(as[Book]) { book =>
                    books = books :+ book
                    complete(StatusCodes.OK)
                } ~
                complete(StatusCodes.BadRequest)
            }
        }
    
}
