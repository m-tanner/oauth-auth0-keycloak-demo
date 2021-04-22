terraform {
  required_version = "~>0.15.0"

  required_providers {
    keycloak = {
      source  = "mrparkers/keycloak"
      version = "3.0.0"
    }
  }
}

provider "keycloak" {
  client_id = "terraform"
  # the secret must be manually pasted here after spinning up keycloak in docker per the README
  # this is only okay because this spike is a local demo
  client_secret = "27ef39a2-b922-4d8e-84d9-f7d16bd13311"
  url           = "http://localhost:8080"
}

resource "keycloak_realm" "realm" {
  realm        = "my-realm-for-oauth2-spike-2"
  ssl_required = "external"
}

locals {
  uuid = uuid()
}

resource "keycloak_openid_client" "client" {
  realm_id      = keycloak_realm.realm.id
  client_id     = "my-client-for-oauth2-spike-2"
  client_secret = local.uuid

  enabled = true

  access_type              = "CONFIDENTIAL"
  service_accounts_enabled = true
}

resource "keycloak_openid_client_scope" "client_scope" {
  realm_id = keycloak_realm.realm.id
  name     = "${keycloak_openid_client.client.client_id}-scope"
}

resource "keycloak_openid_audience_protocol_mapper" "audience_mapper" {
  realm_id  = keycloak_realm.realm.id
  client_id = keycloak_openid_client.client.id
  name      = "audience for ${keycloak_openid_client.client.client_id}"

  included_custom_audience = "oauth2-spike-2" # the name of this api client, the only client we allow
}

resource "keycloak_openid_client_optional_scopes" "client_optional_scopes" {
  realm_id  = keycloak_realm.realm.id
  client_id = keycloak_openid_client.client.id

  optional_scopes = [
    keycloak_openid_client_scope.client_scope.name
  ]
}

output "curl_command_for_token" {
  value = <<-EOT
          curl --location --request POST 'http://localhost:8080/auth/realms/${keycloak_realm.realm.realm}/protocol/openid-connect/token' \
               --header 'Content-Type: application/x-www-form-urlencoded' \
               --data 'client_id=${keycloak_openid_client.client.client_id}' \
               --data 'client_secret=${local.uuid}' \
               --data 'scope=${keycloak_openid_client_scope.client_scope.name}' \
               --data 'grant_type=client_credentials'
          EOT
}
