package discourse
import com.typesafe.scalalogging.CanLog
import org.slf4j.MDC

case class DiscourseMDC(username: String, topicId: Option[Int], title: String)
object DiscourseMDC {
  implicit val canLogMDC: CanLog[DiscourseMDC] = new CanLog[DiscourseMDC] {
    override def logMessage(originalMsg: String, a: DiscourseMDC): String = {
      MDC.put("username", a.username)
      a.topicId.foreach(id => MDC.put("topicId", id.toString))
      MDC.put("title", a.title)
      originalMsg
    }

    override def afterLog(a: DiscourseMDC): Unit = {
      MDC.remove("username")
      MDC.remove("topicId")
      MDC.remove("title")
    }
  }
}
