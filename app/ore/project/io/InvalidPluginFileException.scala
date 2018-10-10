package ore.project.io

/**
  * Exception thrown when an uploaded PluginFile is invalid.
  */
case class InvalidPluginFileException(private val message: String = null, private val cause: Throwable = null)
    extends Exception(message, cause)
