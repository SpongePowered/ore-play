package db.impl.schema

import db.impl.OrePostgresDriver.api._
import db.impl.VersionTable
import db.{ModelFilter, ModelSchema, ModelService, ObjectReference}
import models.project.Version
import scala.concurrent.Future

/**
  * Version related queries.
  */
class VersionSchema(override val service: ModelService)
  extends ModelSchema[Version](service, classOf[Version], TableQuery[VersionTable]) {

  /**
    * Returns true if the specified hash is found in the specified
    * [[models.project.Project]]'s [[Version]]s.
    *
    * @param projectId  Project ID
    * @param hash       Version hash
    * @return           True if found
    */
  def hashExists(projectId: ObjectReference, hash: String): Future[Boolean] = service.DB.db.run(((for {
      model <- this.baseQuery
      if model.projectId === projectId
      if model.hash === hash
  } yield model.id).length > 0).result)

  /**
    * Returns a filter based on a Version's channel.
    *
    * @param channelIds Channel IDs to filter versions with
    * @return           Channel filter
    */
  def channelFilter(channelIds: Seq[ObjectReference]): ModelFilter[Version] = ModelFilter[Version](_.channelId inSetBind channelIds)

}
