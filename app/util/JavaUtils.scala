package util

object JavaUtils {

  def autoClose[A <: AutoCloseable](closeable: A)(fun: (A) ⇒ Unit)(exHandler: Exception => Unit): Unit = {
    try {
      fun(closeable)
    } catch {
      case e: Exception => exHandler.apply(e)
    } finally {
      closeable.close()
    }
  }

}
