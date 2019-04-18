package ore.models.admin

import scala.language.higherKinds

import ore.db.impl.DefaultModelCompanion
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{ProjectLogEntryTable, ProjectLogTable}
import ore.models.project.Project
import ore.db.access.{ModelView, QueryView}
import ore.db.{DbRef, Model, ModelQuery, ModelService}
import util.syntax._

import cats.effect.{Clock, IO}
import slick.lifted.TableQuery

/**
  * Represents a log for a [[ore.models.project.Project]].
  *
  * @param projectId  ID of project log is for
  */
case class ProjectLog(
    projectId: DbRef[Project]
)
object ProjectLog extends DefaultModelCompanion[ProjectLog, ProjectLogTable](TableQuery[ProjectLogTable]) {

  implicit val query: ModelQuery[ProjectLog] =
    ModelQuery.from(this)

  implicit class ProjectLogModelOps(private val self: Model[ProjectLog]) extends AnyVal {

    /**
      * Returns all entries in this log.
      *
      * @return Entries in log
      */
    def entries[V[_, _]: QueryView](
        view: V[ProjectLogEntryTable, Model[ProjectLogEntry]]
    ): V[ProjectLogEntryTable, Model[ProjectLogEntry]] =
      view.filterView(_.logId === self.id.value)

    /**
      * Adds a new entry with an "error" tag to the log.
      *
      * @param message  Message to log
      * @return         New entry
      */
    def err[F[_]](message: String)(implicit service: ModelService[F], clock: Clock[F]): F[Model[ProjectLogEntry]] = {
      val tag = "error"
      entries(ModelView.now(ProjectLogEntry))
        .find(e => e.message === message && e.tag === tag)
        .semiflatMap { entry =>
          service.update(entry)(
            _.copy(
              occurrences = entry.occurrences + 1,
              lastOccurrence = service.theTime
            )
          )
        }
        .getOrElseF {
          service.insert(
            ProjectLogEntry(self.id, tag, message, lastOccurrence = service.theTime)
          )
        }
    }
  }
}
