package ore.rest

import javax.inject.Inject

import db.ModelService
import db.impl.access.ProjectBase
import models.api.ProjectApiKey
import models.project.{Channel, Page, Project, Version}
import models.user.User
import ore.OreConfig
import ore.project.ProjectMember
import play.api.libs.json.Json.{obj, toJson}
import play.api.libs.json._
import security.pgp.PGPPublicKeyInfo

/**
  * Contains implicit JSON [[Writes]] for the Ore API.
  */
final class OreWrites @Inject()(implicit config: OreConfig, service: ModelService) {

  implicit val projects: ProjectBase = this.service.getModelBase(classOf[ProjectBase])

  implicit val projectApiKeyWrites = new Writes[ProjectApiKey] {
    def writes(key: ProjectApiKey) = obj(
      "id" -> key.id.get,
      "createdAt" -> key.createdAt.get,
      "keyType" -> obj("id" -> key.keyType.id, "name" -> key.keyType.name),
      "projectId" -> key.projectId,
      "value" -> key.value
    )
  }

  implicit val pageWrites = new Writes[Page] {
    def writes(page: Page) = obj(
      "id" -> page.id.get,
      "createdAt" -> page.createdAt.get.toString,
      "parentId" -> page.parentId,
      "name" -> page.name,
      "slug" -> page.slug
    )
  }

  implicit val channelWrites = new Writes[Channel] {
    def writes(channel: Channel) = obj("name" -> channel.name, "color" -> channel.color.hex)
  }

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

  implicit val versionWrites = new Writes[Version] {
    def writes(version: Version) = {
      val project = version.project
      val dependencies: List[JsObject] = version.dependencies.map { dependency =>
        obj("pluginId" -> dependency.pluginId, "version" -> dependency.version)
      }
      obj(
        "id"            ->  version.id.get,
        "createdAt"     ->  version.createdAt.get.toString,
        "name"          ->  version.versionString,
        "dependencies"  ->  dependencies,
        "pluginId"      ->  project.pluginId,
        "channel"       ->  toJson(project.channels.get(version.channelId).get),
        "fileSize"      ->  version.fileSize,
        "staffApproved" ->  version.isReviewed,
        "href"          ->  ('/' + version.url)
      )
    }
  }

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
        "channels"      ->  toJson(project.channels.toSeq),
        "recommended"   ->  toJson(project.recommendedVersion),
        "category"      ->  obj("title" -> category.title, "icon" -> category.icon),
        "views"         ->  project.viewCount,
        "downloads"     ->  project.downloadCount,
        "stars"         ->  project.starCount
      )
    }
  }

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

  implicit val pgpPublicKeyInfoWrites = new Writes[PGPPublicKeyInfo] {
    def writes(key: PGPPublicKeyInfo) = {
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
