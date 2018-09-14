package util

object JavaUtils {

  def autoClose[A <: AutoCloseable, B](closeable: A)(fun: A ⇒ B)(exHandler: Exception => B): B = {
    try {
      fun(closeable)
    } catch {
      case e: Exception => exHandler.apply(e)
    } finally {
      closeable.close()
    }
  }

}
