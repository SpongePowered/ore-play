package form.organization

/**
  * Represents an action of updating an [[models.user.Organization]] avatar.
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
