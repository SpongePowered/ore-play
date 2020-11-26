import ore.db.ModelService
import ore.discourse.OreDiscourseApi

import ackcord.requests.Requests
import akka.actor.ActorSystem
import zio.{Has, Task, UIO}

package object ore {
  type Config    = Has[OreJobsConfig]
  type Db        = Has[ModelService[UIO]]
  type Discourse = Has[OreDiscourseApi[Task]]
  type Discord   = Has[Requests]
  type Actors    = Has[ActorSystem]

  type OreEnv = zio.ZEnv with Config with Db with Discourse with Discord with Actors
}
