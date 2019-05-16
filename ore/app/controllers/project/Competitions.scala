package controllers.project

import javax.inject.Inject

import scala.concurrent.ExecutionContext

import play.api.i18n.MessagesApi
import play.api.libs.Files
import play.api.libs.json.Json
import play.api.mvc.{Action, ActionBuilder, AnyContent, MultipartFormData}

import controllers.OreBaseController
import controllers.sugar.{Bakery, Requests}
import db.impl.query.CompetitionQueries
import form.OreForms
import form.project.competition.{CompetitionCreateForm, CompetitionSaveForm}
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.{DbRef, Model, ModelService}
import ore.models.competition.Competition
import ore.models.project.Project
import ore.permission.Permission
import ore.util.StringUtils
import ore.{OreConfig, OreEnv}
import security.spauth.{SingleSignOnConsumer, SpongeAuthApi}
import views.{html => views}

import cats.effect.IO
import cats.syntax.all._

/**
  * Handles competition based actions.
  */
class Competitions @Inject()(forms: OreForms)(
    implicit val ec: ExecutionContext,
    env: OreEnv,
    messagesApi: MessagesApi,
    config: OreConfig,
    service: ModelService[IO],
    bakery: Bakery,
    auth: SpongeAuthApi,
    sso: SingleSignOnConsumer
) extends OreBaseController {
  identity(messagesApi)

  private val self = routes.Competitions

  private def EditCompetitionsAction: ActionBuilder[Requests.AuthRequest, AnyContent] =
    Authenticated.andThen(PermissionAction(Permission.EditCompetition))

  /**
    * Shows the competition administrative panel.
    *
    * @return Competition manager
    */
  def showManager(): Action[AnyContent] = EditCompetitionsAction.asyncF { implicit request =>
    service
      .runDBIO(ModelView.raw(Competition).sortBy(_.createdAt).result)
      .map(all => Ok(views.projects.competitions.manage(all)))
  }

  /**
    * Shows the competition creator.
    *
    * @return Competition creator
    */
  def showCreator(): Action[AnyContent] = EditCompetitionsAction { implicit request =>
    Ok(views.projects.competitions.create())
  }

  /**
    * Creates a new competition.
    *
    * @return Redirect to manager or creator with errors.
    */
  def create(): Action[CompetitionCreateForm] =
    EditCompetitionsAction(parse.form(forms.CompetitionCreate, onErrors = FormError(self.showCreator()))).asyncF {
      implicit request =>
        service
          .runDBIO(
            ModelView.raw(Competition).filter(StringUtils.equalsIgnoreCase(_.name, request.body.name)).exists.result
          )
          .ifM(
            IO.pure(Redirect(self.showCreator()).withError("error.unique.competition.name")),
            service.insert(request.body.create(request.user)).as(Redirect(self.showManager()))
          )
    }

  /**
    * Saves the competition with the specified ID.
    *
    * @param id Competition ID
    * @return   Redirect to manager
    */
  def save(id: DbRef[Competition]): Action[CompetitionSaveForm] =
    EditCompetitionsAction
      .andThen(AuthedCompetitionAction(id))(parse.form(forms.CompetitionSave, onErrors = FormError(self.showManager())))
      .asyncEitherT { implicit request =>
        ModelView.now(Competition).get(id).toRight(notFound).semiflatMap { competition =>
          request.body.save(competition).as(Redirect(self.showManager()).withSuccess("success.saved.competition"))
        }
      }

  /**
    * Deletes the competition with the specified ID.
    *
    * @param id Competition ID
    * @return   Redirect to manager
    */
  def delete(id: DbRef[Competition]): Action[AnyContent] =
    EditCompetitionsAction.andThen(AuthedCompetitionAction(id)).asyncF {
      service
        .deleteWhere(Competition)(_.id === id)
        .as(Redirect(self.showManager()).withSuccess("success.deleted.competition"))
    }

  /**
    * Sets the specified competition's banner image.
    *
    * @param id Competition ID
    * @return   Json response
    */
  def setBanner(id: DbRef[Competition]): Action[MultipartFormData[Files.TemporaryFile]] =
    EditCompetitionsAction.andThen(AuthedCompetitionAction(id))(parse.multipartFormData) { implicit request =>
      request.body.file("banner") match {
        case None =>
          Ok(Json.obj("error" -> request.messages.apply("error.noFile")))
        case Some(file) =>
          this.competitions.saveBanner(request.competition, file.ref.path.toFile, file.filename)
          Ok(Json.obj("bannerUrl" -> self.showBanner(id).path()))
      }
    }

  /**
    * Displays the specified competition's banner image, if any, NotFound
    * otherwise.
    *
    * @param id Competition ID
    * @return   Banner image
    */
  def showBanner(id: DbRef[Competition]): Action[AnyContent] = CompetitionAction(id) { implicit request =>
    this.competitions.getBannerPath(request.competition).map(showImage).getOrElse(notFound)
  }

  /**
    * Displays the project entries in the specified competition.
    *
    * @param id Competition ID
    * @return   List of project entries
    */
  def showProjects(id: DbRef[Competition], page: Option[Int]): Action[AnyContent] = CompetitionAction(id).asyncF {
    implicit request =>
      val userProjectsF =
        request.currentUser.map(u => service.runDBIO(u.projects(ModelView.raw(Project)).result)).getOrElse(IO.pure(Nil))

      val projectModelsF = service.runDbCon(CompetitionQueries.getEntries(id).to[Vector])

      (userProjectsF, projectModelsF).parMapN {
        case (userProjects, competitionEntries) =>
          Ok(
            views.projects.competitions
              .projects(
                request.competition,
                competitionEntries,
                userProjects,
                page.getOrElse(1),
                config.ore.projects.initLoad
              )
          )
      }
  }

  /**
    * Submits a project to the specified competition.
    *
    * @param id Competition ID
    * @return   Redirect to project list
    */
  def submitProject(id: DbRef[Competition]): Action[DbRef[Project]] =
    AuthedCompetitionAction(id)(
      parse.form(forms.CompetitionSubmitProject, onErrors = FormError(self.showProjects(id, None)))
    ).asyncEitherT { implicit request =>
      val projectId = request.body
      request.user
        .projects(ModelView.now(Project))
        .get(projectId)
        .toRight(Redirect(self.showProjects(id, None)).withError("error.competition.submit.invalidProject"))
        .semiflatMap(project => project.settings.tupleLeft(project))
        .flatMap {
          case (project, projectSettings) =>
            this.competitions
              .submitProject(project, projectSettings, request.competition)
              .leftMap(error => Redirect(self.showProjects(id, None)).withErrors(error.toList))
        }
        .as(Redirect(self.showProjects(id, None)))
    }
}
