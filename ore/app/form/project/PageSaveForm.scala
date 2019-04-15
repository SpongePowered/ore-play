package form.project

import models.project.Page
import ore.db.DbRef

case class PageSaveForm(parentId: Option[DbRef[Page]], name: Option[String], content: Option[String])
