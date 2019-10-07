package form.project

import ore.db.DbRef
import ore.models.project.Page

case class PageSaveForm(parentId: Option[DbRef[Page]], name: Option[String], content: Option[String])
