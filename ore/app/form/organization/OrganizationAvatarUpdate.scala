package form.organization

import ore.models.organization.Organization

/**
  * Represents an action of updating an [[Organization]] avatar.
  *
  * @param method Update method ("by-file" or "by-url")
  * @param url    Avatar URL
  */
case class OrganizationAvatarUpdate(method: String, url: Option[String]) {

  /**
    * Returns true if this update was a file upload.
    */
  val isFileUpload: Boolean = this.method.equals("by-file")

}
