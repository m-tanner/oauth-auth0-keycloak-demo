package auth

import com.auth0.jwk.UrlJwkProvider
import org.keycloak.RSATokenVerifier
import org.keycloak.adapters.{KeycloakDeployment, KeycloakDeploymentBuilder}
import pdi.jwt.{JwtAlgorithm, JwtBase64, JwtClaim, JwtJson}
import play.api.Configuration

import java.io.ByteArrayInputStream
import java.net.URL
import java.time.Clock
import javax.inject.Inject
import scala.concurrent.ExecutionContext.global
import scala.concurrent.Future
import scala.util.{Failure, Success, Try}

/**
 * Based on https://auth0.com/blog/build-and-secure-a-scala-play-framework-api/
 * and https://scalac.io/blog/user-authentication-keycloak-2/
 *
 * Oauth intro: https://aaronparecki.com/oauth-2-simplified/
 * Oauth in depth intro: https://www.oauth.com/oauth2-servers/accessing-data/making-api-requests/
 *
 * Get started with Keycloak: https://www.keycloak.org/getting-started/getting-started-docker
 * Build an authorization service (this) with Keycloak: https://www.keycloak.org/docs/latest/authorization_services/index.html
 * What's audience? https://www.keycloak.org/docs/4.8/server_admin/#_audience
 * https://stackoverflow.com/questions/53543117/how-to-setup-public-key-for-verifying-jwt-tokens-from-keycloak
 *
 * You can even setup Keycloak with Terraform: https://registry.terraform.io/providers/mrparkers/keycloak/latest/docs but I haven't tried this yet
 */
class AuthService @Inject()(config: Configuration) {
  implicit val clock: Clock = Clock.systemUTC

  private val realm = config.get[String]("keycloak.realm")
  private val authServerUrl = config.get[String]("keycloak.auth-server-url")
  private val sslRequired = config.get[String]("keycloak.ssl-required")
  private val resource = config.get[String]("keycloak.resource")
  private val publicClient = config.get[Boolean]("keycloak.public-client")
  private val confidentialPort = config.get[Int]("keycloak.confidential-port")
  private val audience = config.get[String]("client-name")

  private val keycloakDeployment: KeycloakDeployment =
    KeycloakDeploymentBuilder.build(new ByteArrayInputStream(s"""
    |{
    |  "realm": "$realm",
    |  "auth-server-url": "$authServerUrl",
    |  "ssl-required": "$sslRequired",
    |  "resource": "$resource",
    |  "public-client": $publicClient,
    |  "confidential-port": $confidentialPort
    |}
    |"""
      .stripMargin
      .getBytes("UTF-8")
    ))

  // Validates a JWT and potentially returns the claims if the token was successfully parsed and validated
  def validateJwt(token: String): Try[JwtClaim] = for {
    jwk <- getJwk(token) // Get the secret key for this token
    claims <- JwtJson.decode(token, jwk.getPublicKey, Seq(JwtAlgorithm.RS256)) // Decode the token using the secret key
    _ <- validateClaims(claims) // validate the data stored inside the token
  } yield claims

  // Gets the JWK from the JWKS endpoint using the jwks-rsa library
  private def getJwk(token: String) =
    (splitToken andThen decodeElements) (token) flatMap {
      case (header, _, _) =>
        val jwtHeader = JwtJson.parseHeader(header)  // extract the header
        val jwkProvider = new UrlJwkProvider(new URL(keycloakDeployment.getJwksUrl))

        // Use jwkProvider to load the JWKS data and return the JWK
        jwtHeader.keyId.map { k =>
          Try(jwkProvider.get(k))
        } getOrElse Failure(new Exception("Unable to retrieve kid"))
    }

  // A regex that defines the JWT pattern and allows us to extract the header, claims and signature
  private val jwtRegex = """(.+?)\.(.+?)\.(.+?)""".r

  private val splitToken = (jwt: String) => jwt match {
    case jwtRegex(header, body, sig) => Success((header, body, sig))
    case _ => Failure(new Exception("Token does not match the correct pattern"))
  }

  // As the header and claims data are base64-encoded, this function decodes those elements
  private val decodeElements = (data: Try[(String, String, String)]) => data map {
    case (header, body, sig) =>
      (JwtBase64.decodeString(header), JwtBase64.decodeString(body), sig)
  }

  private def validateClaims(claims: JwtClaim) = {
    if (claims.isValid(keycloakDeployment.getRealmInfoUrl, audience)(clock = clock)) {
      Success(claims)
    } else {
      Failure(new Exception("The JWT did not pass validation"))
    }
  }
}
