package ore

import outwatch.dom._
import outwatch.dom.dsl._
import monix.execution.Scheduler.Implicits.global

object Frontend {

  def main(args: Array[String]): Unit = {
    println("Hello world in console")
    //OutWatch.renderInto("#app", h1("Hello World")).unsafeRunSync()
  }
}
