package form.project

import db.ObjectReference

case class PageSaveForm(parentId: Option[ObjectReference], name: Option[String], content: Option[String])
