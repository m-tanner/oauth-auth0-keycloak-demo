package auth

import play.api.http.HeaderNames
import play.api.mvc._

import javax.inject.Inject
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.{ Failure, Success }

// Our custom action implementation
class Auth0Action @Inject() (bodyParser: BodyParsers.Default, auth0Service: Auth0Service)(implicit
  ec: ExecutionContext
) extends ActionBuilder[UserRequest, AnyContent] {

  override def parser: BodyParser[AnyContent]               = bodyParser
  override protected def executionContext: ExecutionContext = ec

  // A regex for parsing the Authorization header value
  private val headerTokenRegex = """Bearer (.+?)""".r

  // Called when a request is invoked. We should validate the bearer token here
  // and allow the request to proceed if it is valid.
  override def invokeBlock[A](
    request: Request[A],
    block: UserRequest[A] => Future[Result]
  ): Future[Result] = extractBearerToken(request) map { token =>
    auth0Service.validateJwt(token) match {
      case Success(claim) =>
        block(UserRequest(claim, token, request)) // token was valid - proceed!
      case Failure(t) =>
        Future.successful(Results.Unauthorized(t.getMessage)) // token was invalid - return 401
    }
  } getOrElse Future.successful(Results.Unauthorized) // no token was sent - return 401

  // Helper for extracting the token value
  private def extractBearerToken[A](request: Request[A]): Option[String] =
    request.headers.get(HeaderNames.AUTHORIZATION) collect { case headerTokenRegex(token) =>
      token
    }
}
