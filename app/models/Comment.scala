package models

import play.api.libs.json.{ Json, OFormat }

// Represents a comment on a blog post
case class Comment(id: Int, postId: Int, text: String, authorName: String)

object Comment {
  // Use a default JSON formatter for the Comment type
  implicit val format: OFormat[Comment] = Json.format[Comment]
}
