package ore.rest

import db.ModelService
import db.impl.access.ProjectBase
import javax.inject.Inject
import models.api.ProjectApiKey
import models.project._
import ore.OreConfig
import play.api.libs.json.Json.obj
import play.api.libs.json._
import security.pgp.PGPPublicKeyInfo

/**
  * Contains implicit JSON [[Writes]] for the Ore API.
  */
final class OreWrites @Inject()(implicit config: OreConfig, service: ModelService) {

  implicit val projects: ProjectBase = this.service.getModelBase(classOf[ProjectBase])

  implicit val projectApiKeyWrites: Writes[ProjectApiKey] = new Writes[ProjectApiKey] {
    def writes(key: ProjectApiKey): JsObject = obj(
      "id" -> key.id.value,
      "createdAt" -> key.createdAt.value,
      "keyType" -> obj("id" -> key.keyType.id, "name" -> key.keyType.name),
      "projectId" -> key.projectId,
      "value" -> key.value
    )
  }

  implicit val pageWrites: Writes[Page] = new Writes[Page] {
    def writes(page: Page): JsObject = obj(
      "id" -> page.id.value,
      "createdAt" -> page.createdAt.value.toString,
      "parentId" -> page.parentId,
      "name" -> page.name,
      "slug" -> page.slug
    )
  }

  implicit val channelWrites: Writes[Channel] = new Writes[Channel] {
    def writes(channel: Channel): JsObject = obj("name" -> channel.name, "color" -> channel.color.hex, "nonReviewed" -> channel.isNonReviewed)
  }

  /*
  implicit val memberWrites = new Writes[ProjectMember] {
    def writes(member: ProjectMember) = {
      obj(
        "userId"    ->  member.user.id,
        "name"      ->  member.username,
        "roles"     ->  JsArray(member.roles.map(r => JsString(r.roleType.title)).toSeq),
        "headRole"  ->  member.headRole.roleType.title
      )
    }
  }
  */

  implicit val tagWrites: Writes[Tag] = new Writes[Tag] {
    override def writes(tag: Tag): JsValue = {
      obj(
        "id" -> tag.id.value,
        "name" -> tag.name,
        "data" -> tag.data,
        "backgroundColor" -> tag.color.background,
        "foregroundColor" -> tag.color.foreground
      )
    }
  }

  implicit val tagColorWrites: Writes[TagColors.TagColor] = new Writes[TagColors.TagColor] {
    override def writes(tagColor: TagColors.TagColor): JsValue = {
      obj(
        "id" -> tagColor.id,
        "backgroundColor" -> tagColor.background,
        "foregroundColor" -> tagColor.foreground
      )
    }
  }

  /*
  implicit val versionWrites = new Writes[Version] {
    def writes(version: Version) = {
      val project = version.project
      val dependencies: List[JsObject] = version.dependencies.map { dependency =>
        obj("pluginId" -> dependency.pluginId, "version" -> dependency.version)
      }
      var returnObject = obj(
        "id"            ->  version.id.get,
        "createdAt"     ->  version.createdAt.get.toString,
        "name"          ->  version.versionString,
        "dependencies"  ->  dependencies,
        "pluginId"      ->  project.pluginId,
        "channel"       ->  toJson(project.channels.get(version.channelId).get),
        "fileSize"      ->  version.fileSize,
        "md5"           ->  version.hash,
        "staffApproved" ->  version.isReviewed,
        "href"          ->  ('/' + version.url),
        "tags"          ->  version.tags.map(toJson(_)),
        "downloads"     ->  version.downloadCount
      )
      val maybeAuthor = version.author
      if (maybeAuthor.isDefined) {
        returnObject = returnObject.+(("author", JsString(maybeAuthor.get.name)))
      }
      returnObject
    }
  }
  */

  /*
  implicit val projectWrites = new Writes[Project] {
    def writes(project: Project) = {
      val category = project.category
      obj(
        "pluginId"      ->  project.pluginId,
        "createdAt"     ->  project.createdAt.get.toString,
        "name"          ->  project.name,
        "owner"         ->  project.ownerName,
        "description"   ->  project.description,
        "href"          ->  ('/' + project.ownerName + '/' + project.slug),
        "members"       ->  project.memberships.members.filter(_.roles.exists(_.isAccepted)),
        "channels"      ->  toJson(project.channels.toSeq fut),
        "recommended"   ->  toJson(project.recommendedVersion),
        "category"      ->  obj("title" -> category.title, "icon" -> category.icon),
        "views"         ->  project.viewCount,
        "downloads"     ->  project.downloadCount,
        "stars"         ->  project.starCount
      )
    }
  }
  */


  /*
  implicit val userWrites = new Writes[User] {
    def writes(user: User) = {
      obj(
        "id"              ->  user.id,
        "createdAt"       ->  user.createdAt.get.toString,
        "username"        ->  user.username,
        "roles"           ->  user.globalRoles.map(_.title),
        "starred"         ->  user.starred().map(p => p.pluginId),
        "avatarUrl"       ->  user.avatarUrl,
        "projects"        ->  user.projects.all
      )
    }
  }
  */

  implicit val pgpPublicKeyInfoWrites: Writes[PGPPublicKeyInfo] = new Writes[PGPPublicKeyInfo] {
    def writes(key: PGPPublicKeyInfo): JsObject = {
      obj(
        "raw" -> key.raw,
        "userName" -> key.userName,
        "email" -> key.email,
        "id" -> key.id,
        "createdAt" -> key.createdAt.toString
      )
    }
  }

}
