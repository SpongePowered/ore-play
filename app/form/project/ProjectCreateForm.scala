package form.project
import db.DbRef
import models.user.User
import ore.project.Category

case class ProjectCreateForm(
    name: String,
    pluginId: String,
    category: Category,
    description: Option[String],
    ownerId: Option[DbRef[User]],
)
