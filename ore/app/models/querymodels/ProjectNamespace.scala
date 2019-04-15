package models.querymodels

case class ProjectNamespace(ownerName: String, slug: String) {

  def namespace: String = ownerName + "/" + slug

  override def toString: String = namespace
}
