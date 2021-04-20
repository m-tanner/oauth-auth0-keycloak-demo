package controllers

import auth.AuthAction
import play.api.libs.json.Json
import play.api.mvc.{AbstractController, Action, AnyContent, ControllerComponents}
import repositories.DataRepository

import javax.inject.{Inject, Singleton}

@Singleton
class ApiController @Inject()(cc: ControllerComponents,
                              dataRepository: DataRepository,
                              authAction: AuthAction)
  extends AbstractController(cc) {

  def health: Action[AnyContent] = Action { implicit request => Ok }

  def getPost(postId: Int): Action[AnyContent] = authAction { implicit request =>
    dataRepository.getPost(postId) map { post =>
      // If the post was found, return a 200 with the post data as JSON
      Ok(Json.toJson(post))
    } getOrElse NotFound
  }

  def getComments(postId: Int): Action[AnyContent] = authAction { implicit request =>
    // Simply return 200 OK with the comment data as JSON.
    Ok(Json.toJson(dataRepository.getComments(postId)))
  }
}
