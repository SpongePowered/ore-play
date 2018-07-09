package form

import java.net.{MalformedURLException, URL}

import controllers.sugar.Requests.ProjectRequest
import db.ModelService
import db.impl.OrePostgresDriver.api._
import form.organization.{OrganizationAvatarUpdate, OrganizationMembersUpdate, OrganizationRoleSetBuilder}
import form.project._
import javax.inject.Inject
import models.api.ProjectApiKey
import models.project.{Channel, Page}
import models.project.Page._
import models.user.role.ProjectRole
import ore.OreConfig
import ore.project.factory.ProjectFactory
import ore.rest.ProjectApiKeyTypes
import ore.rest.ProjectApiKeyTypes.ProjectApiKeyType
import play.api.data.Forms._
import play.api.data.format.Formatter
import play.api.data.validation.{Constraint, Invalid, Valid, ValidationError}
import play.api.data.{FieldMapping, Form, FormError, Mapping}

import scala.concurrent.ExecutionContext
import scala.util.Try

/**
  * Collection of forms used in this application.
  */
//noinspection ConvertibleToMethodValue
class OreForms @Inject()(implicit config: OreConfig, factory: ProjectFactory, service: ModelService) {

  val url: Mapping[String] = text verifying("error.url.invalid", text => {
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
    * Submits changes to a [[models.project.Project]]'s
    * [[ProjectRole]]s.
    */
  lazy val ProjectMemberRoles = Form(mapping(
    "users" -> list(number),
    "roles" -> list(text)
  )(ProjectRoleSetBuilder.apply)(ProjectRoleSetBuilder.unapply))

  /**
    * Submits a flag on a project for further review.
    */
  lazy val ProjectFlag = Form(mapping(
    "flag-reason" -> number,
    "comment" -> nonEmptyText)
  (FlagForm.apply)(FlagForm.unapply))


  /**
    * This is a Constraint checker for the ownerId that will search the list allowedIds to see if the number is in it.
    * @param allowedIds number that are allowed as ownerId
    * @return Constraint
    */
  def ownerIdInList(allowedIds: Seq[Int]): Constraint[Option[Int]] = Constraint("constraints.check")({
    ownerId =>
      var errors: Seq[ValidationError] = Seq()
      if (ownerId.isDefined) {
        if (!allowedIds.contains(ownerId.get)) {
          errors = Seq(ValidationError("error.plugin"))
        }
      }
      if (errors.isEmpty) {
        Valid
      } else {
        Invalid(errors)
      }
  })

  /**
    * Submits settings changes for a Project.
    */
  def ProjectSave(organisationUserCanUploadTo: Seq[Int]) = Form(mapping(
    "category" -> text,
    "issues" -> url,
    "source" -> url,
    "license-name" -> text,
    "license-url" -> url,
    "description" -> text,
    "users" -> list(number),
    "roles" -> list(text),
    "userUps" -> list(text),
    "roleUps" -> list(text),
    "update-icon" -> boolean,
    "owner" -> optional(number).verifying(ownerIdInList(organisationUserCanUploadTo)),
    "forum-sync" -> boolean
  )(ProjectSettingsForm.apply)(ProjectSettingsForm.unapply))

  /**
    * Submits a name change for a project.
    */
  lazy val ProjectRename = Form(single("name" -> text))

  /**
    * Submits a post reply for a project discussion.
    */
  lazy val ProjectReply = Form(mapping(
    "content" -> text(minLength = MinLength, maxLength = MaxLength),
    "poster" -> optional(nonEmptyText)
  )(DiscussionReplyForm.apply)(DiscussionReplyForm.unapply))

  /**
    * Submits a list of organization members to be invited.
    */
  lazy val OrganizationCreate = Form(mapping(
    "name" -> nonEmptyText,
    "users" -> list(number),
    "roles" -> list(text)
  )(OrganizationRoleSetBuilder.apply)(OrganizationRoleSetBuilder.unapply))

  /**
    * Submits an avatar update for an [[models.user.Organization]].
    */
  lazy val OrganizationUpdateAvatar = Form(mapping(
    "avatar-method" -> nonEmptyText,
    "avatar-url" -> optional(url)
  )(OrganizationAvatarUpdate.apply)(OrganizationAvatarUpdate.unapply))

  /**
    * Submits an organization member for removal.
    */
  lazy val OrganizationMemberRemove = Form(single("username" -> nonEmptyText))

  /**
    * Submits a list of members to be added or updated.
    */
  lazy val OrganizationUpdateMembers = Form(mapping(
    "users" -> list(number),
    "roles" -> list(text),
    "userUps" -> list(text),
    "roleUps" -> list(text)
  )(OrganizationMembersUpdate.apply)(OrganizationMembersUpdate.unapply))

  /**
    * Submits a new Channel for a Project.
    */
  lazy val ChannelEdit = Form(mapping(
    "channel-input" -> text.verifying(
      "Invalid channel name.", config.isValidChannelName(_)
    ),

    "channel-color-input" -> text.verifying(
      "Invalid channel color.", c => Channel.Colors.exists(_.hex.equalsIgnoreCase(c))
    ),
    "non-reviewed" -> default(boolean, false)
  )(ChannelData.apply)(ChannelData.unapply))

  /**
    * Submits changes on a documentation page.
    */
  lazy val PageEdit = Form(mapping(
    "parent-id" -> optional(number),
    "name" -> optional(text),
    "content" -> optional(text(
      maxLength = MaxLengthPage
    )))(PageSaveForm.apply)(PageSaveForm.unapply) verifying("error.maxLength", pageSaveForm => {
      val isHome = pageSaveForm.parentId.isEmpty && pageSaveForm.name.contains(HomeName)
      val pageSize = pageSaveForm.content.getOrElse("").length
      if (isHome)
        pageSize <= MaxLength
      else
        pageSize <= MaxLengthPage
    })
  )

  /**
    * Submits a tagline change for a User.
    */
  lazy val UserTagline = Form(single("tagline" -> text))

  /**
    * Submits a PGP public key update.
    */
  lazy val UserPgpPubKey = Form(mapping(
    "pgp-pub-key" -> text
  )(PGPPublicKeySubmission.apply)(PGPPublicKeySubmission.unapply) verifying("error.invalidKey", _.validateKey()))

  /**
    * Submits a new Version.
    */
  lazy val VersionCreate = Form(mapping(
    "unstable" -> boolean,
    "recommended" -> boolean,
    "channel-input" -> text.verifying(
      "Invalid channel name.", config.isValidChannelName(_)),
    "channel-color-input" -> text.verifying(
      "Invalid channel color.", c => Channel.Colors.exists(_.hex.equalsIgnoreCase(c))),
    "non-reviewed" -> default(boolean, false),
    "content" -> optional(text),
    "forum-post" -> boolean
  )(VersionData.apply)(VersionData.unapply))

  /**
    * Submits a change to a Version's description.
    */
  lazy val VersionDescription = Form(single("content" -> text))

  val projectApiKeyType: FieldMapping[ProjectApiKeyType] = of[ProjectApiKeyType](new Formatter[ProjectApiKeyType] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], ProjectApiKeyType] =
      data.get(key)
        .flatMap(id => Try(id.toInt).toOption.map(ProjectApiKeyTypes(_).asInstanceOf[ProjectApiKeyType]))
        .toRight(Seq(FormError(key, "error.required", Nil)))
    def unbind(key: String, value: ProjectApiKeyType): Map[String, String] = Map(key -> value.id.toString)
  })

  lazy val ProjectApiKeyCreate = Form(single("key-type" -> projectApiKeyType))

  def required(key: String) = Seq(FormError(key, "error.required", Nil))

  def projectApiKey(implicit ec: ExecutionContext): FieldMapping[ProjectApiKey] = of[ProjectApiKey](new Formatter[ProjectApiKey] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], ProjectApiKey] = {
      data.get(key).
        flatMap(id => Try(id.toInt).toOption.flatMap(evilAwaitpProjectApiKey(_)))
        .toRight(required(key))
    }

    def unbind(key: String, value: ProjectApiKey): Map[String, String] = Map(key -> value.id.value.toString)
  })

  def evilAwaitpProjectApiKey(key: Int)(implicit ec: ExecutionContext): Option[ProjectApiKey] = {
    val projectApiKeys = this.service.access[ProjectApiKey](classOf[ProjectApiKey])
    // TODO remvove await
    this.service.await(projectApiKeys.get(key).value).getOrElse(None)
  }

  def ProjectApiKeyRevoke(implicit ec: ExecutionContext) = Form(single("id" -> projectApiKey))

  def channel(implicit request: ProjectRequest[_], ec: ExecutionContext): FieldMapping[Channel] = of[Channel](new Formatter[Channel] {
    def bind(key: String, data: Map[String, String]): Either[Seq[FormError], Channel] = {
      data.get(key)
        .flatMap(evilAwaitChannel(_))
        .toRight(Seq(FormError(key, "api.deploy.channelNotFound", Nil)))
    }

    def unbind(key: String, value: Channel) = Map(key -> value.name.toLowerCase)
  })

  def evilAwaitChannel(c: String)(implicit request: ProjectRequest[_], ec: ExecutionContext): Option[Channel] = {
    val value = request.data.project.channels.find(_.name.toLowerCase === c.toLowerCase)
    // TODO remvove await
    this.service.await(value.value).getOrElse(None)
  }

  def VersionDeploy(implicit request: ProjectRequest[_], ec: ExecutionContext) = Form(mapping(
    "apiKey" -> nonEmptyText,
    "channel" -> channel,
    "recommended" -> default(boolean, true),
    "forumPost" -> default(boolean, request.data.settings.forumSync),
    "changelog" -> optional(text(minLength = Page.MinLength, maxLength = Page.MaxLength)))
  (VersionDeployForm.apply)(VersionDeployForm.unapply))

  lazy val ReviewDescription = Form(single("content" -> text))

  lazy val UserAdminUpdate = Form(tuple(
      "thing" -> text,
      "action" -> text,
      "data" -> text
  ))

  lazy val NoteDescription = Form(single("content" -> text))

  lazy val NeedsChanges = Form(single("comment" -> text))

  lazy val SyncSso = Form(tuple(
    "sso" -> nonEmptyText,
    "sig" -> nonEmptyText,
    "api_key" -> nonEmptyText
  ))
}
