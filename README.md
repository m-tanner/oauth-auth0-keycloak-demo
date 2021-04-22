1) Start Keycloak
    ```bash
    # run with `-d` for background process
    docker run -d -p 8080:8080 -e KEYCLOAK_USER=admin -e KEYCLOAK_PASSWORD=admin quay.io/keycloak/keycloak:12.0.4
    ```
2) Create a new client in the "master" realm, named `terraform`
3) Update the client:
   - `Access Type` => `Confidential`
   - `Standard Flow Enabled` => `OFF`
   - `Direct Access Grants Enabled` => `OFF`
   - `Service Accounts Enabled` => `ON`
   - In "Service Account Roles", add `admin` to "Assigned Roles"
4) From the "Credentials" tab, copy the `client secret` and paste it into `main.tf` 
5) Setup the keycloak server with terraform
   ```bash
   # requires terraform > 0.13
   # install with `brew install terraform` or `brew upgrade terraform` 
   cd terraform
   terraform apply
   ```
6) Request a token using the `curl` command that terraform outputs
7) Run this sbt service `sbt run`
8) Try out your token
    ```bash
    curl -H 'Authorization: Bearer <PASTE YOUR TOKEN HERE>' localhost:9000/api/post/1
    
    {"id":1,"content":"This is a blog post"}
    ```
9) Enjoy!