## What does this repo do?

It implements [this guide](https://auth0.com/blog/build-and-secure-a-scala-play-framework-api/),
using Auth0 for Oauth. Then, it rips out Auth0 from the example and replaces it with Keycloak as the auth provider.

It is a REST API built using Scala and Play Framework. 
It statically serves blog posts and comments from four endpoints. 
Two of the endpoints are secured using access tokens issued by auth0.com. The other two are secured using access tokens issued by a self-hosted Keycloak server. 
In order to successfully call the endpoints and retrieve data, the request must supply a valid JWT bearer token in the Authorization header. 
Otherwise, a 401 Unauthorized response is returned.

Here is the basic auth flow, copied from [this RFC](https://datatracker.ietf.org/doc/html/rfc8705):

     +--------+                                 +---------------+
     |        |                                 |               |
     |        |<--(A)-- Get an access token --->| Authorization |
     |        |                                 |     Server    |
     |        |                                 |               |
     |        |                                 +---------------+
     |        |                                         ^
     |        |                                         |
     |        |
     |        |                               (C)       |
     | Client |                           Validate the
     |        |                           access token  |
     |        |
     |        |                                         |
     |        |                                         v
     |        |                                 +---------------+
     |        |                                 |      (C)      |
     |        |                                 |               |
     |        |<--(B)-- Use the access token -->|   Protected   |
     |        |                                 |    Resource   |
     |        |                                 |               |
     +--------+                                 +---------------+

## Why does this repo exist?

This repo is a spike for "what's it like using Keycloak as the authorization server in oauth flow?"
It's also a spike for "what's it like using Auth0?"

Both implementations live side-by-side here. It's not how you would build an actual application, but it 
does well as an example/tutorial.

## Examples

If a user doesn't have access, we want to see:

```bash
~ curl -i localhost:9000/api/<auth-provider>/post/1
HTTP/1.1 401 Unauthorized
```

If a user has access, we want to see:

```bash
~ curl -H 'Authorization: Bearer <REDACTED>' localhost:9000/api/<auth-provider>/post/1
{"id":1,"content":"This is a blog post"}
```

This is possible because of the `ApiController`'s use of `AuthAction`: 

```scala
def getPost(postId: Int): Action[AnyContent] = authAction { implicit request =>
 dataRepository.getPost(postId) map { post =>
   // If the post was found, return a 200 with the post data as JSON
   Ok(Json.toJson(post))
 } getOrElse NotFound
}
```

The crux of `AuthAction` is its use of `AuthService` in the following way:

```scala
def validateJwt(token: String): Try[JwtClaim] = for {
 jwk <- getJwk(token) // Get the secret key for this token
 claims <- JwtJson.decode(token, jwk.getPublicKey, Seq(JwtAlgorithm.RS256)) // Decode the token using the secret key
 _ <- validateClaims(claims) // validate the data stored inside the token
} yield claims
```

In this way, we know if the JWT is valid :)

There are two endpoints, but two auth implementations, resulting in four total endpoints:

#### /api/auth0/post/:postId and /api/keycloak/post/:postId

Returns a single blog post with the specified ID. The static data provides two blog posts with IDs 1 and 2 respectively.

Example response:

```json
{
  "id": 1,
  "content": "This is a blog post"
}
```

#### /api/auth0/post/:postId/comments and /api/keycloak/post/:postId/comments

Return comments for the specified post. The static data provides comments for posts 1 and 2.

Example response:

```json
[
  {
    "id":1,
    "postId":1,
    "text":"This is an awesome blog post",
    "authorName":"Fantastic Mr Fox"
  },
  {
    "id":2,
    "postId":1,
    "text":"Thanks for the insights",
    "authorName":"Jane Doe"
  }
]
```

## Keycloak Quickstart
1) Start Keycloak
    ```bash
    # run with `-d` for background process
    docker run -d -p 8080:8080 -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin quay.io/keycloak/keycloak:12.0.4
    ```
2) Go to `http://localhost:8080/auth/admin/master/console/#/realms/master` and login using the above credentials
3) Create a new client in the "master" realm, named `terraform`
4) Update the client:
   - `Access Type` => `Confidential`
   - `Standard Flow Enabled` => `OFF`
   - `Direct Access Grants Enabled` => `OFF`
   - `Service Accounts Enabled` => `ON`
   - In "Service Account Roles", add `admin` to "Assigned Roles"
5) From the "Credentials" tab, copy the `client secret` and paste it into `main.tf` 
6) Setup the keycloak server with terraform
   ```bash
   # requires terraform > 0.13
   # install with `brew install terraform` or `brew upgrade terraform` 
   cd terraform
   terraform apply
   ```
7) Request a token using the `curl` command that terraform outputs
8) Run this sbt service `sbt run`
9) Try out your token
    ```bash
    curl -H 'Authorization: Bearer <PASTE YOUR TOKEN HERE>' localhost:9000/api/keycloak/post/1
    
    {"id":1,"content":"This is a blog post"}
    ```
10) Enjoy!

## Auth0 Quickstart

1) Run this sbt service with environment variables `AUTH0_DOMAIN=auth0-tutorial-11.us.auth0.com AUTH0_AUDIENCE=https://scala-api.example.com sbt run 9000`
2) Get a token from Auth0
   ```bash
   curl --request POST \
     --url https://auth0-tutorial-11.us.auth0.com/oauth/token \
     --header 'content-type: application/json' \
     --data '{"client_id":"<REDACTED>","client_secret":"<REDACTED>","audience":"https://scala-api.example.com","grant_type":"client_credentials"}'
   ```
3) Try out your token
   ```bash
   curl -H 'Authorization: Bearer <PASTE YOUR TOKEN HERE>' localhost:9000/api/auth0/post/1
       
   {"id":1,"content":"This is a blog post"}
   ```
4) Enjoy!

## Project Organization

Anyone can contribute to this repo.

[Scalafmt](https://scalameta.org/scalafmt/) is enforced.

Install scalafmt if necessary:
```bash
brew install coursier/formulas/coursier
coursier install scalafmt
scalafmt --version // should be 3.0.0-RC2
```
