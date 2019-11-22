package ore

import ore.db.ModelService

import zio.UIO

trait Db {
  val service: ModelService[UIO]
}
