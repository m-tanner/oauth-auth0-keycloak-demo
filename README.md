1) Start Keycloak
    ```bash
    # run with `-d` for background process
    docker run -d -p 8080:8080 -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin quay.io/keycloak/keycloak:12.0.4
    ```
2) Nav to http://localhost:8080/auth/ and open the admin console
3) Add a realm. I called my example `spike`.
4) Add a client. `oauth2-spike-2` (this must match `client-name`)
5) Set "Access Type" to "Confidential". Set "Authorization Enabled" to "ON". Set Valid Redirect URIs to `http://localhost:9000/*`.
6) Create a client scopes.
7) Create a mapper for the client scope.
   - Name: audience for oauth2-spike-2
   - Mapper Type: Audience
   - Included Client Audience: oauth2-spike-2
   - Add to access token: ON 
7) Go back to your client. Add the newly created client scope in the "Client Scopes" tab.
8) Request a token
    ```bash
    curl --location --request POST 'http://localhost:8080/auth/realms/spike/protocol/openid-connect/token' \
         --header 'Content-Type: application/x-www-form-urlencoded' \
         --data 'client_id=oauth2-spike-2' \
         --data 'client_secret=6172a6c7-828e-4857-b009-cb3936bdeb72' \
         --data 'scope=oauth2-spike-2' \
         --data 'grant_type=client_credentials'
    
    {"access_token":"REDACTED","expires_in":300,"refresh_expires_in":0,"token_type":"Bearer","not-before-policy":0,"scope":"profile email oauth2-spike-2"}
    ```
9) Run this sbt service `sbt run`
10) Try out your token
    ```bash
    curl -H 'Authorization: Bearer REDACTED' localhost:9000/api/post/1
    
    {"id":1,"content":"This is a blog post"}
    ```
11) Enjoy!