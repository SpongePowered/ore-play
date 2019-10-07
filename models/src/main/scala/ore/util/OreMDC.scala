package ore.util

import com.typesafe.scalalogging.CanLog

trait OreMDC {

  def logMessage(s: String): String

  def afterLog(): Unit
}
object OreMDC {
  case object NoMDC extends OreMDC {
    override def logMessage(s: String): String = s

    override def afterLog(): Unit = ()
  }

  implicit val canLog: CanLog[OreMDC] = new CanLog[OreMDC] {
    override def logMessage(originalMsg: String, a: OreMDC): String = a.logMessage(originalMsg)

    override def afterLog(a: OreMDC): Unit = a.afterLog()
  }
}
