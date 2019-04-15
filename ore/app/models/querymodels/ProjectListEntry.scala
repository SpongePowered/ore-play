package models.querymodels
import models.project.Visibility
import ore.project.Category

case class ProjectListEntry(
    namespace: ProjectNamespace,
    visibility: Visibility,
    views: Long,
    downloads: Long,
    stars: Long,
    category: Category,
    description: Option[String],
    name: String,
    version: Option[String],
    tags: List[ViewTag]
)
