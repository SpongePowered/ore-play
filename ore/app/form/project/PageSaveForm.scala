package form.project

import ore.models.project.Page
import ore.db.DbRef

case class PageSaveForm(parentId: Option[DbRef[Page]], name: Option[String], content: Option[String])
