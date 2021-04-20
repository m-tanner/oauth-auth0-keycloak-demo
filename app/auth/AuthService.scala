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

class AuthService @Inject()(config: Configuration) {
  implicit val clock: Clock = Clock.systemUTC

  val keycloakDeployment: KeycloakDeployment =
    KeycloakDeploymentBuilder.build(new ByteArrayInputStream(
      """
        |{
        |  "realm": "myrealm",
        |  "auth-server-url": "http://localhost:8080/auth",
        |  "ssl-required": "external",
        |  "resource": "myclient",
        |  "public-client": true,
        |  "confidential-port": 0
        |}
        |""".stripMargin.getBytes("UTF-8")
    ))

  def getVerifier(token: String): Future[RSATokenVerifier] =
    Future(RSATokenVerifier.create(token).realmUrl(keycloakDeployment.getRealmInfoUrl))(global)

  // A regex that defines the JWT pattern and allows us to
  // extract the header, claims and signature
  private val jwtRegex = """(.+?)\.(.+?)\.(.+?)""".r

  // Your Auth0 audience, read from configuration
  private def audience = "myclient"

  // The issuer of the token. For Auth0, this is just your Auth0
  // domain including the URI scheme and a trailing slash.
  private def issuer = s"http://localhost:8080/auth/realms/myrealm"

  // Validates a JWT and potentially returns the claims if the token was
  // successfully parsed and validated
  def validateJwt(token: String): Try[JwtClaim] = for {
    jwk <- getJwk(token)           // Get the secret key for this token
    claims <- JwtJson.decode(token, jwk.getPublicKey, Seq(JwtAlgorithm.RS256)) // Decode the token using the secret key
    _ <- validateClaims(claims)    // validate the data stored inside the token
  } yield claims

  private val splitToken = (jwt: String) => jwt match {
    case jwtRegex(header, body, sig) => Success((header, body, sig))
    case _ => Failure(new Exception("Token does not match the correct pattern"))
  }

  // As the header and claims data are base64-encoded, this function
  // decodes those elements
  private val decodeElements = (data: Try[(String, String, String)]) => data map {
    case (header, body, sig) =>
      (JwtBase64.decodeString(header), JwtBase64.decodeString(body), sig)
  }

  // Gets the JWK from the JWKS endpoint using the jwks-rsa library
  private val getJwk = (token: String) =>
    (splitToken andThen decodeElements) (token) flatMap {
      case (header, _, _) =>
        val jwtHeader = JwtJson.parseHeader(header)     // extract the header
        val jwkProvider = new UrlJwkProvider(new URL("http://localhost:8080/auth/realms/myrealm/protocol/openid-connect/certs"))

        // Use jwkProvider to load the JWKS data and return the JWK
        jwtHeader.keyId.map { k =>
          Try(jwkProvider.get(k))
        } getOrElse Failure(new Exception("Unable to retrieve kid"))
    }

  private val validateClaims = (claims: JwtClaim) =>
    if (claims.isValid(issuer, audience)(clock = clock)) {
      Success(claims)
    } else {
      Failure(new Exception("The JWT did not pass validation"))
    }

//  ➜  ~ curl --location --request POST 'http://localhost:8080/auth/realms/myrealm/protocol/openid-connect/token' \
//    --header 'Content-Type: application/x-www-form-urlencoded' \
//  --data-urlencode 'client_id=myclient' \
//  --data-urlencode 'client_secret=3fe1f2ea-8adf-44c6-80c0-22d954b64dab' \
//  --data-urlencode 'scope=email' \
//  --data-urlencode 'grant_type=client_credentials'
//  {"access_token":"eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJSQ3FnY21WRVEzYVc4eUdYOENHdEZwMGlpTjViNkltbWoyanpzNUJ4VF9VIn0.eyJleHAiOjE2MTg4Nzk4NDcsImlhdCI6MTYxODg3OTU0NywianRpIjoiM2FhZjU5ZWUtZjhmOS00OWU2LWFhMWEtMzNhYzhmYTkxZjAxIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4MDgwL2F1dGgvcmVhbG1zL215cmVhbG0iLCJhdWQiOiJhY2NvdW50Iiwic3ViIjoiN2NlMzA2MmEtMDZhMC00MjcyLWE2NzQtZDQ1MGRlZjEwZGU0IiwidHlwIjoiQmVhcmVyIiwiYXpwIjoibXljbGllbnQiLCJhY3IiOiIxIiwiYWxsb3dlZC1vcmlnaW5zIjpbImh0dHA6Ly9sb2NhbGhvc3Q6OTAwMCJdLCJyZWFsbV9hY2Nlc3MiOnsicm9sZXMiOlsib2ZmbGluZV9hY2Nlc3MiLCJ1bWFfYXV0aG9yaXphdGlvbiJdfSwicmVzb3VyY2VfYWNjZXNzIjp7Im15Y2xpZW50Ijp7InJvbGVzIjpbInVtYV9wcm90ZWN0aW9uIl19LCJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50IiwibWFuYWdlLWFjY291bnQtbGlua3MiLCJ2aWV3LXByb2ZpbGUiXX19LCJzY29wZSI6ImVtYWlsIHByb2ZpbGUiLCJlbWFpbF92ZXJpZmllZCI6ZmFsc2UsImNsaWVudEhvc3QiOiIxNzIuMTcuMC4xIiwiY2xpZW50SWQiOiJteWNsaWVudCIsInByZWZlcnJlZF91c2VybmFtZSI6InNlcnZpY2UtYWNjb3VudC1teWNsaWVudCIsImNsaWVudEFkZHJlc3MiOiIxNzIuMTcuMC4xIn0.Ns7ug6tsF_YZbhdth91MGgEs-7H6SJfGCzoDYQvHmJ2UKsjLjC_u8s8D-kPGeULDECV_s_eY1hxzyAgf8ILbQGQfme3LFp4DLRXtVh0IP5daPu5QKxdKl3h1k6HTNiPLxVA1JNoPsv1seEJ0a9SzP6Jtk2-GT4jmPIbHeE0VlY5pPYLL6Xkt3JPjP-K2LVe2DGM3w6Sw9VPh2O5KXA8wAoQsRxTKaKHcdebNonGmD1KQa3nqbua9cZ5Ubgj3jRh5t7za4cM8wuXDeTVwlm3xiUUjIUXufHv6ckFGIFbxQlheTHMKm4rvLpPmej_JT_yuNJTz0VhBffJjSsBXSkHNOg","expires_in":300,"refresh_expires_in":0,"token_type":"Bearer","not-before-policy":0,"scope":"email profile"}
}
