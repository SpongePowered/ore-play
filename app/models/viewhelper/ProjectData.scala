package models.viewhelper

import play.twirl.api.Html

import db.{Model, ModelService}
import db.access.ModelView
import db.impl.OrePostgresDriver.api._
import db.impl.schema.{ProjectRoleTable, UserTable}
import models.admin.{ProjectLogEntry, ProjectVisibilityChange}
import models.project._
import models.user.User
import models.user.role.ProjectUserRole
import ore.OreConfig
import ore.markdown.MarkdownRenderer
import ore.permission.role.RoleCategory
import util.syntax._

import cats.data.OptionT
import cats.effect.{ContextShift, IO}
import cats.syntax.all._
import slick.lifted.TableQuery

/**
  * Holds ProjetData that is the same for all users
  */
case class ProjectData(
    joinable: Model[Project],
    projectOwner: Model[User],
    publicVersions: Int, // project.versions.count(_.visibility === VisibilityTypes.Public)
    settings: Model[ProjectSettings],
    members: Seq[(Model[ProjectUserRole], Model[User])],
    projectLogSize: Int,
    flags: Seq[(Model[Flag], String, Option[String])], // (Flag, user.name, resolvedBy)
    noteCount: Int, // getNotes.size
    lastVisibilityChange: Option[ProjectVisibilityChange],
    lastVisibilityChangeUser: String, // users.get(project.lastVisibilityChange.get.createdBy.get).map(_.username).getOrElse("Unknown")
    recommendedVersion: Option[Model[Version]]
) extends JoinableData[ProjectUserRole, Project] {

  def flagCount: Int = flags.size

  def project: Model[Project] = joinable

  def visibility: Visibility = project.visibility

  def fullSlug = s"""/${project.ownerName}/${project.slug}"""

  def renderVisibilityChange(implicit renderer: MarkdownRenderer): Option[Html] =
    lastVisibilityChange.map(_.renderComment)

  def roleCategory: RoleCategory = RoleCategory.Project
}

object ProjectData {

  def cacheKey(project: Model[Project]): String = "project" + project.id

  def of[A](project: Model[Project])(
      implicit service: ModelService,
      cs: ContextShift[IO]
  ): IO[ProjectData] = {
    val flagsWithNames = project
      .flags(ModelView.later(Flag))
      .query
      .join(TableQuery[UserTable])
      .on(_.userId === _.id)
      .joinLeft(TableQuery[UserTable])
      .on(_._1.resolvedBy === _.id)
      .map {
        case ((flag, user), resolver) =>
          (flag, user.name, resolver.map(_.name))
      }

    val lastVisibilityChangeQ = project.lastVisibilityChange(ModelView.later(ProjectVisibilityChange))
    val lastVisibilityChangeUserWithUser =
      lastVisibilityChangeQ.joinLeft(TableQuery[UserTable]).on((t1, t2) => t1.createdBy === t2.id).map {
        case (lastVisibilityChange, changer) =>
          lastVisibilityChange -> changer.map(_.name)
      }

    (
      project.settings,
      project.owner.user,
      project.versions(ModelView.now(Version)).count(_.visibility === (Visibility.Public: Visibility)),
      members(project),
      project.logger.flatMap(log => log.entries(ModelView.now(ProjectLogEntry)).size),
      service.runDBIO(flagsWithNames.result),
      service.runDBIO(lastVisibilityChangeUserWithUser.result.headOption),
      project.recommendedVersion(ModelView.now(Version)).getOrElse(OptionT.none[IO, Model[Version]]).value
    ).parMapN {
      case (
          settings,
          projectOwner,
          versions,
          members,
          logSize,
          flagData,
          lastVisibilityChangeInfo,
          recommendedVersion
          ) =>
        val noteCount = project.decodeNotes.size

        new ProjectData(
          project,
          projectOwner,
          versions,
          settings,
          members.sortBy(_._1.role.permissions: Long).reverse, //This is stupid, but works
          logSize,
          flagData,
          noteCount,
          lastVisibilityChangeInfo.map(_._1),
          lastVisibilityChangeInfo.flatMap(_._2).getOrElse("Unknown"),
          recommendedVersion
        )
    }
  }

  def members(
      project: Model[Project]
  )(implicit service: ModelService): IO[Seq[(Model[ProjectUserRole], Model[User])]] = {
    val query = for {
      r <- TableQuery[ProjectRoleTable] if r.projectId === project.id.value
      u <- TableQuery[UserTable] if r.userId === u.id
    } yield (r, u)

    service.runDBIO(query.result)
  }
}
