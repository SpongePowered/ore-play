package ore.discourse

import zio.Task

trait Discourse {
  val discourse: OreDiscourseApi[Task]
}
