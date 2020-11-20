import ore.db.ModelService
import ore.discourse.OreDiscourseApi

import zio.{Has, Task, UIO}

package object ore {
  type Config    = Has[OreJobsConfig]
  type Db        = Has[ModelService[UIO]]
  type Discourse = Has[OreDiscourseApi]

  type OreEnv = zio.ZEnv with Config with Db with Discourse
}
