package form.project

import db.DbRef
import models.project.Page

case class PageSaveForm(parentId: Option[DbRef[Page]], name: Option[String], content: Option[String])
