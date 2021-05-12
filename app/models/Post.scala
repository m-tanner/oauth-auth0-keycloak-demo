package models

import play.api.libs.json.{ Json, OFormat }

// Create our Post type as a standard case class
case class Post(id: Int, content: String)

object Post {
  // We're going to be serving this type as JSON, so specify a
  // default Json formatter for our Post type here
  implicit val format: OFormat[Post] = Json.format[Post]
}
