package ore.models.project.factory

import ore.data.project.Category

case class ProjectTemplate(
    name: String,
    pluginId: String,
    category: Category,
    description: Option[String]
)
