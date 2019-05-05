package models.querymodels
import ore.models.project.{TagColor, VersionTag}

case class ViewTag(name: String, data: String, color: TagColor)
object ViewTag {
  def fromVersionTag(tag: VersionTag): ViewTag = ViewTag(tag.name, tag.data, tag.color)
}