package form.project

import ore.data.project.Category
import ore.db.DbRef
import ore.models.project.factory.ProjectTemplate
import ore.models.user.User

case class ProjectCreateForm(
    name: String,
    pluginId: String,
    category: Category,
    description: Option[String],
    ownerId: Option[DbRef[User]]
) {

  def asTemplate: ProjectTemplate = ProjectTemplate(name, pluginId, category, description)
}
