package ore.db.impl.schema

import java.time.OffsetDateTime

import ore.db.impl.OrePostgresDriver.api._
import ore.models.{Job, JobInfo}

class JobTable(tag: Tag) extends ModelTable[Job](tag, "jobs") {

  def lastUpdated         = column[OffsetDateTime]("last_updated")
  def retryAt             = column[OffsetDateTime]("retry_at")
  def lastError           = column[String]("last_error")
  def lastErrorDescriptor = column[String]("last_error_descriptor")
  def state               = column[Job.JobState]("state")
  def jobType             = column[Job.JobType]("job_type")
  def jobProperties       = column[Map[String, String]]("job_properties")

  def info =
    (lastUpdated.?, retryAt.?, lastError.?, lastErrorDescriptor.?, state, jobType)
      .<>((JobInfo.apply _).tupled, JobInfo.unapply)

  def * =
    (id.?, createdAt.?, (info, jobProperties)).<>(mkApply((Job.apply _).tupled), mkUnapply(Job.unapply))
}
