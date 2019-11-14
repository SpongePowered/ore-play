package models.querymodels

import ore.data.project.{Category, ProjectNamespace}
import ore.models.project.Visibility

case class ProjectListEntryWithIcon(
    namespace: ProjectNamespace,
    visibility: Visibility,
    views: Long,
    downloads: Long,
    stars: Long,
    category: Category,
    description: Option[String],
    name: String,
    version: Option[String],
    //tags: List[ViewTag],
    icon: String
)
