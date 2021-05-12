package auth

import pdi.jwt.JwtClaim
import play.api.mvc.{ Request, WrappedRequest }

// A custom request type to hold our JWT claims, we can pass these on to the
// handling action
case class UserRequest[A](jwt: JwtClaim, token: String, request: Request[A]) extends WrappedRequest[A](request)
