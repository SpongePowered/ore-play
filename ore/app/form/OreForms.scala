package form

import java.net.{MalformedURLException, URL}
import javax.inject.Inject

import scala.util.Try

import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.data.{FieldMapping, Form, FormError, Mapping}

import controllers.sugar.Requests.ProjectRequest
import ore.db.impl.OrePostgresDriver.api._
import form.organization.{OrganizationAvatarUpdate, OrganizationMembersUpdate, OrganizationRoleSetBuilder}
import form.project._
import ore.models.project.{Channel, Page}
import ore.models.user.role.ProjectUserRole
import ore.OreConfig
import ore.db.access.ModelView
import ore.db.{DbRef, Model, ModelService}
import ore.models.api.ProjectApiKey
import ore.models.organization.Organization
import ore.data.project.Category
import ore.models.project.factory.ProjectFactory
import util.syntax._

import cats.data.OptionT
import zio.UIO

/**
  * Collection of forms used in this application.
  */
//noinspection ConvertibleToMethodValue
class OreForms @Inject()(
    implicit config: OreConfig,
    factory: ProjectFactory,
    service: ModelService[UIO],
    runtime: zio.Runtime[Any]
) {

  val url: Mapping[String] = text.verifying("error.url.invalid", text => {
    if (text.isEmpty)
      true
    else {
      try {
        new URL(text)
        true
      } catch {
        case _: MalformedURLException =>
          false
      }
    }
  })

  /**
    * Submits a member to be removed from a Project.
    */
  lazy val ProjectMemberRemove = Form(single("username" -> nonEmptyText))

  /**
    * Submits changes to a [[ore.models.project.Project]]'s
    * [[ProjectUserRole]]s.
    */
  lazy val ProjectMemberRoles = Form(
    mapping(
      "users" -> list(longNumber),
      "roles" -> list(text)
    )(ProjectRoleSetBuilder.apply)(ProjectRoleSetBuilder.unapply)
  )

  /**
    * Submits a flag on a project for further review.
    */
  lazy val ProjectFlag = Form(
    mapping("flag-reason" -> number, "comment" -> nonEmptyText)(FlagForm.apply)(FlagForm.unapply)
  )

  /**
    * This is a Constraint checker for the ownerId that will search the list allowedIds to see if the number is in it.
    * @param allowedIds number that are allowed as ownerId
    * @return Constraint
    */
  def ownerIdInList[A](allowedIds: Seq[DbRef[A]]): Constraint[Option[DbRef[A]]] =
    Constraint("constraints.check") { ownerId =>
      val errors =
        if (ownerId.isDefined && !allowedIds.contains(ownerId.get)) Seq(ValidationError("error.plugin"))
        else Nil
      if (errors.isEmpty) Valid
      else Invalid(errors)
    }

  val category: FieldMapping[Category] = of[Category](new Formatter[Category] {
    override def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Category] =
      data
        .get(key)
        .flatMap(s => Category.values.find(_.title == s))
        .toRight(Seq(FormError(key, "error.project.categoryNotFound", Nil)))

    override def unbind(key: String, value: Category): Map[String, String] = Map(key -> value.title)
  })

  def projectCreate(organisationUserCanUploadTo: Seq[DbRef[Organization]]) = Form(
    mapping(
      "name"        -> text,
      "pluginId"    -> text,
      "category"    -> category,
      "description" -> optional(text),
      "owner"       -> optional(longNumber).verifying(ownerIdInList(organisationUserCanUploadTo))
    )(ProjectCreateForm.apply)(ProjectCreateForm.unapply)
  )

  /**
    * Submits settings changes for a Project.
    */
  def ProjectSave(organisationUserCanUploadTo: Seq[DbRef[Organization]]) =
    Form(
      mapping(
        "category"     -> text,
        "homepage"     -> url,
        "issues"       -> url,
        "source"       -> url,
        "support"      -> url,
        "license-name" -> text,
        "license-url"  -> url,
        "description"  -> text,
        "users"        -> list(longNumber),
        "roles"        -> list(text),
        "userUps"      -> list(text),
        "roleUps"      -> list(text),
        "update-icon"  -> boolean,
        "owner"        -> optional(longNumber).verifying(ownerIdInList(organisationUserCanUploadTo)),
        "forum-sync"   -> boolean,
        "keywords"     -> text,
      )(ProjectSettingsForm.apply)(ProjectSettingsForm.unapply)
    )

  /**
    * Submits a name change for a project.
    */
  lazy val ProjectRename = Form(single("name" -> text))

  /**
    * Submits a post reply for a project discussion.
    */
  lazy val ProjectReply = Form(
    mapping(
      "content" -> text(minLength = Page.minLength, maxLength = Page.maxLength),
      "poster"  -> optional(nonEmptyText)
    )(DiscussionReplyForm.apply)(DiscussionReplyForm.unapply)
  )

  /**
    * Submits a list of organization members to be invited.
    */
  lazy val OrganizationCreate = Form(
    mapping(
      "name"  -> nonEmptyText,
      "users" -> list(longNumber),
      "roles" -> list(text)
    )(OrganizationRoleSetBuilder.apply)(OrganizationRoleSetBuilder.unapply)
  )

  /**
    * Submits an avatar update for an [[Organization]].
    */
  lazy val OrganizationUpdateAvatar = Form(
    mapping(
      "avatar-method" -> nonEmptyText,
      "avatar-url"    -> optional(url)
    )(OrganizationAvatarUpdate.apply)(OrganizationAvatarUpdate.unapply)
  )

  /**
    * Submits an organization member for removal.
    */
  lazy val OrganizationMemberRemove = Form(single("username" -> nonEmptyText))

  /**
    * Submits a list of members to be added or updated.
    */
  lazy val OrganizationUpdateMembers = Form(
    mapping(
      "users"   -> list(longNumber),
      "roles"   -> list(text),
      "userUps" -> list(text),
      "roleUps" -> list(text)
    )(OrganizationMembersUpdate.apply)(OrganizationMembersUpdate.unapply)
  )

  /**
    * Submits a new Channel for a Project.
    */
  lazy val ChannelEdit = Form(
    mapping(
      "channel-input" -> text.verifying(
        "Invalid channel name.",
        config.isValidChannelName(_)
      ),
      "channel-color-input" -> text.verifying(
        "Invalid channel color.",
        c => Channel.Colors.exists(_.hex.equalsIgnoreCase(c))
      ),
      "non-reviewed" -> default(boolean, false)
    )(ChannelData.apply)(ChannelData.unapply)
  )

  /**
    * Submits changes on a documentation page.
    */
  lazy val PageEdit = Form(
    mapping(
      "parent-id" -> optional(longNumber),
      "name"      -> optional(text),
      "content" -> optional(
        text(
          maxLength = Page.maxLengthPage
        )
      )
    )(PageSaveForm.apply)(PageSaveForm.unapply).verifying(
      "error.maxLength",
      pageSaveForm => {
        val isHome   = pageSaveForm.parentId.isEmpty && pageSaveForm.name.contains(Page.homeName)
        val pageSize = pageSaveForm.content.getOrElse("").length
        if (isHome)
          pageSize <= Page.maxLength
        else
          pageSize <= Page.maxLengthPage
      }
    )
  )

  /**
    * Submits a tagline change for a User.
    */
  lazy val UserTagline = Form(single("tagline" -> text))

  /**
    * Submits a new Version.
    */
  lazy val VersionCreate = Form(
    mapping(
      "unstable"      -> boolean,
      "recommended"   -> boolean,
      "channel-input" -> text.verifying("Invalid channel name.", config.isValidChannelName(_)),
      "channel-color-input" -> text
        .verifying("Invalid channel color.", c => Channel.Colors.exists(_.hex.equalsIgnoreCase(c))),
      "non-reviewed" -> default(boolean, false),
      "content"      -> optional(text),
      "forum-post"   -> boolean
    )(VersionData.apply)(VersionData.unapply)
  )

  /**
    * Submits a change to a Version's description.
    */
  lazy val VersionDescription = Form(single("content" -> text))

  def required(key: String): Seq[FormError] = Seq(FormError(key, "error.required", Nil))

  def projectApiKey: FieldMapping[OptionT[UIO, Model[ProjectApiKey]]] =
    of[OptionT[UIO, Model[ProjectApiKey]]](new Formatter[OptionT[UIO, Model[ProjectApiKey]]] {
      def bind(key: String, data: Map[String, String]): Either[Seq[FormError], OptionT[UIO, Model[ProjectApiKey]]] =
        data
          .get(key)
          .flatMap(id => Try(id.toLong).toOption)
          .map(ModelView.now(ProjectApiKey).get(_))
          .toRight(required(key))

      def unbind(key: String, value: OptionT[UIO, Model[ProjectApiKey]]): Map[String, String] =
        runtime.unsafeRun(value.value).map(_.id.toString).map(key -> _).toMap
    })

  def ProjectApiKeyRevoke = Form(single("id" -> projectApiKey))

  def channel(implicit request: ProjectRequest[_]): FieldMapping[OptionT[UIO, Model[Channel]]] =
    of[OptionT[UIO, Model[Channel]]](new Formatter[OptionT[UIO, Model[Channel]]] {
      def bind(key: String, data: Map[String, String]): Either[Seq[FormError], OptionT[UIO, Model[Channel]]] =
        data
          .get(key)
          .map(channelOptF(_))
          .toRight(Seq(FormError(key, "api.deploy.channelNotFound", Nil)))

      def unbind(key: String, value: OptionT[UIO, Model[Channel]]): Map[String, String] =
        runtime.unsafeRun(value.value).map(key -> _.name.toLowerCase).toMap
    })

  def channelOptF(c: String)(implicit request: ProjectRequest[_]): OptionT[UIO, Model[Channel]] =
    request.data.project.channels(ModelView.now(Channel)).find(_.name.toLowerCase === c.toLowerCase)

  def VersionDeploy(implicit request: ProjectRequest[_]) =
    Form(
      mapping(
        "apiKey"      -> nonEmptyText,
        "channel"     -> channel,
        "recommended" -> default(boolean, true),
        "forumPost"   -> default(boolean, request.data.settings.forumSync),
        "changelog"   -> optional(text(minLength = Page.minLength, maxLength = Page.maxLength))
      )(VersionDeployForm.apply)(VersionDeployForm.unapply)
    )

  lazy val ReviewDescription = Form(single("content" -> text))

  lazy val UserAdminUpdate = Form(
    tuple(
      "thing"  -> text,
      "action" -> text,
      "data"   -> text
    )
  )

  lazy val NoteDescription = Form(single("content" -> text))

  lazy val NeedsChanges = Form(single("comment" -> text))

  lazy val SyncSso = Form(
    tuple(
      "sso"     -> nonEmptyText,
      "sig"     -> nonEmptyText,
      "api_key" -> nonEmptyText
    )
  )
}
