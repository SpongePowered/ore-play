package util

import play.api.data.Form
import play.api.data.Forms._

/**
  * Collection of forms used in this application.
  */
object Forms {

  /**
    * Submits a new Channel for a Project.
    */
  lazy val ChannelEdit = Form(tuple("channel-input" -> text, "channel-color-input" -> text))

  /**
    * Submits changes on a documentation page.
    */
  lazy val PageEdit = Form(tuple("name" -> text, "content" -> text))

  /**
    * Selects a Category for a project.
    */
  lazy val ProjectCategory = Form(single("category" -> text))

  /**
    * Submits a name change for a project.
    */
  lazy val ProjectRename = Form(single("name" -> text))

  /**
    * Submits a change to a Version's description.
    */
  lazy val VersionDescription = Form(single("description" -> text))

}
