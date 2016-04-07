package controllers

import javax.inject.Inject

import auth.DiscourseSSO._
import controllers.routes.{Application => self}
import db.Storage
import models.auth.{FakeUser, User}
import models.project.Categories
import play.api.i18n.{I18nSupport, MessagesApi}
import play.api.mvc._
import views.{html => views}

import scala.util.{Failure, Success}

class Application @Inject()(override val messagesApi: MessagesApi) extends Controller with I18nSupport {

  val INITIAL_PROJECT_LOAD: Int = 50

  /**
    * Display the home page.
    *
    * @return Home page
    */
  def index(categories: Option[String]) = Action { implicit request =>
    categories match {
      case None => Storage.now(Storage.getProjects(limit = INITIAL_PROJECT_LOAD)) match {
        case Failure(thrown) => throw thrown
        case Success(projects) => Ok(views.index(projects, None))
      }
      case Some(csv) =>
        val categoryArray = Categories.fromString(csv)
        Storage.now(Storage.getProjects(categoryArray.map(_.id), INITIAL_PROJECT_LOAD)) match {
          case Failure(thrown) => throw thrown
          case Success(projects) =>
            // Don't pass "visible categories" if all categories are visible
            val allCategories = Categories.values.subsetOf(categoryArray.toSet)
            Ok(views.index(projects, if (allCategories) None else Some(categoryArray)))
        }
    }
  }

  /**
    * Redirect to forums for SSO authentication and then back here again.
    *
    * @param sso  Incoming payload from forums
    * @param sig  Incoming signature from forums
    * @return     Logged in home
    */
  def logIn(sso: Option[String], sig: Option[String]) = Action {
    if (FakeUser.ENABLED) {
      Storage.getOrCreateUser(FakeUser)
      Redirect(self.index(None)).withSession(Security.username -> FakeUser.username, "email" -> FakeUser.email)
    } else if (sso.isEmpty || sig.isEmpty) {
      Redirect(getRedirect)
    } else {
      val userData = authenticate(sso.get, sig.get)
      var user = new User(userData._1, userData._2, userData._3, userData._4)
      user = Storage.getOrCreateUser(user)
      Redirect(self.index(None)).withSession(Security.username -> user.username, "email" -> user.email)
    }
  }

  /**
    * Clears the current session.
    *
    * @return Home page
    */
  def logOut = Action { implicit request =>
    Redirect(self.index(None)).withNewSession
  }

}
