name := "oauth_spike_2"
organization := "com.axon"
 
version := "1.0" 
      
lazy val `oauth_spike_2` = (project in file(".")).enablePlugins(PlayScala)

      
resolvers += "Akka Snapshot Repository" at "https://repo.akka.io/snapshots/"
resolvers += "Nexus" at "https://nexus.taservs.net/content/groups/public"

scalaVersion := "2.13.5"
val keycloakVersion = "4.0.0.Final"

libraryDependencies ++= Seq( jdbc , ehcache , ws , specs2 % Test , guice )
libraryDependencies += "org.scalatestplus.play" %% "scalatestplus-play" % "5.0.0" % Test
libraryDependencies ++= Seq(
  "com.github.jwt-scala"      %% "jwt-play"             % "7.1.3",
  "com.auth0"                 % "jwks-rsa"              % "0.6.1",
  "org.keycloak"              % "keycloak-core"         % keycloakVersion,
  "org.keycloak"              % "keycloak-adapter-core" % keycloakVersion,
  "org.jboss.logging"         % "jboss-logging"         % "3.3.0.Final",
  "org.apache.httpcomponents" % "httpclient"            % "4.5.1"
)

unmanagedResourceDirectories in Test <+=  baseDirectory ( _ /"target/web/public/test" )  

      