package ore.models.project.io

import play.api.libs.Files.TemporaryFile
import play.api.mvc.{AnyContent, Request}

/**
  * Represents data submitted when a new plugin / signature pair is being
  * uploaded.
  *
  * @param pluginFile         Plugin file
  * @param pluginFileName     Plugin file name
  */
case class PluginUpload(
    pluginFile: TemporaryFile,
    pluginFileName: String
)

object PluginUpload {

  val PLUGIN_FILE = "pluginFile"

  /**
    * Attempts to parse upload data from the specified request.
    *
    * @param request  Request to parse data from
    * @return         Upload data instance
    */
  def bindFromRequest()(implicit request: Request[AnyContent]): Option[PluginUpload] = {
    request.body.asMultipartFormData.flatMap { formData =>
      val pluginPart     = formData.file(PLUGIN_FILE)
      val pluginFile     = pluginPart.map(_.ref)
      val pluginFileName = pluginPart.map(_.filename)
      if (pluginFile.isEmpty)
        None
      else
        Some(PluginUpload(pluginFile.get, pluginFileName.get))
    }
  }

}
