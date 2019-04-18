package models.querymodels
import ore.models.project.Visibility
import ore.models.project.Category

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
