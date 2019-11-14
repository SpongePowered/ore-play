package models.querymodels
import ore.OreConfig
import ore.data.project.{Category, ProjectNamespace}
import ore.models.project.Visibility
import ore.models.project.io.ProjectFiles
import ore.models.user.User
import util.syntax._

import zio.ZIO
import zio.blocking.Blocking

case class ProjectListEntry(
    namespace: ProjectNamespace,
    visibility: Visibility,
    views: Long,
    downloads: Long,
    stars: Long,
    category: Category,
    description: Option[String],
    name: String,
    version: Option[String]
    //tags: List[ViewTag]
) {

  def withIcon(
      implicit projectFiles: ProjectFiles[ZIO[Blocking, Nothing, *]],
      config: OreConfig
  ): ZIO[Blocking, Nothing, ProjectListEntryWithIcon] = {
    val iconF = projectFiles.getIconPath(namespace.ownerName, name).map(_.isDefined).map {
      case true  => controllers.project.routes.Projects.showIcon(namespace.ownerName, namespace.slug).url
      case false => User.avatarUrl(namespace.ownerName)
    }

    iconF.map { icon =>
      ProjectListEntryWithIcon(
        namespace,
        visibility,
        views,
        downloads,
        stars,
        category,
        description,
        name,
        version,
        //tags,
        icon
      )
    }
  }
}
