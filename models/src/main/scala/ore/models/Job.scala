package ore.models

import java.time.OffsetDateTime

import ore.db.{DbRef, ModelQuery}
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.JobTable
import ore.models.project.{Project, Version}

import enumeratum.values._
import slick.lifted.TableQuery

case class JobInfo(
    lastUpdated: Option[OffsetDateTime],
    retryAt: Option[OffsetDateTime],
    lastError: Option[String],
    lastErrorDescriptor: Option[String],
    state: Job.JobState,
    jobType: Job.JobType
)
object JobInfo {
  def newJob(tpe: Job.JobType): JobInfo = JobInfo(None, None, None, None, Job.JobState.NotStarted, tpe)
}

case class Job(
    info: JobInfo,
    jobProperties: Map[String, String]
) {

  def toTyped: Either[String, info.jobType.CaseClass] =
    info.jobType.toCaseClass(info, jobProperties)
}
object Job extends DefaultModelCompanion[Job, JobTable](TableQuery[JobTable]) {

  implicit val query: ModelQuery[Job] =
    ModelQuery.from(this)

  sealed abstract class JobState(val value: String) extends StringEnumEntry
  object JobState extends StringEnum[JobState] {
    override def values: IndexedSeq[JobState] = findValues

    case object NotStarted   extends JobState("not_started")
    case object Started      extends JobState("started")
    case object Done         extends JobState("done")
    case object FatalFailure extends JobState("fatal_failure")
  }

  sealed abstract class JobType(val value: String) extends StringEnumEntry {
    type CaseClass <: TypedJob

    def toCaseClass(
        info: JobInfo,
        properties: Map[String, String]
    ): Either[String, CaseClass]
  }
  object JobType extends StringEnum[JobType] {
    override def values: IndexedSeq[JobType] = findValues

    case object UpdateDiscourseProjectTopicType extends JobType("update_project_discourse_topic") {
      override type CaseClass = UpdateDiscourseProjectTopic

      def toCaseClass(
          info: JobInfo,
          properties: Map[String, String]
      ): Either[String, CaseClass] =
        properties
          .get("project_id")
          .toRight("No project id found")
          .flatMap(_.toLongOption.toRight("Project id is not a valid long"))
          .map(l => UpdateDiscourseProjectTopic(info, l))
    }

    case object UpdateDiscourseVersionPostType extends JobType("update_version_discourse_post") {
      override type CaseClass = UpdateDiscourseVersionPost

      def toCaseClass(
          info: JobInfo,
          properties: Map[String, String]
      ): Either[String, CaseClass] =
        properties
          .get("version_id")
          .toRight("No version id found")
          .flatMap(_.toLongOption.toRight("Version id is not a valid long"))
          .map(l => UpdateDiscourseVersionPost(info, l))
    }

    case object DeleteDiscourseTopicType extends JobType("delete_discourse_topic") {
      override type CaseClass = DeleteDiscourseTopic

      def toCaseClass(
          info: JobInfo,
          properties: Map[String, String]
      ): Either[String, CaseClass] =
        properties
          .get("topic_id")
          .toRight("No topic id found")
          .flatMap(_.toIntOption.toRight("Topic id is not a valid long"))
          .map(l => DeleteDiscourseTopic(info, l))
    }
  }

  sealed trait TypedJob {
    def info: JobInfo

    def toJob: Job
  }

  case class UpdateDiscourseProjectTopic(
      info: JobInfo,
      projectId: DbRef[Project]
  ) extends TypedJob {

    def toJob: Job =
      Job(info, Map("project_id" -> projectId.toString))
  }
  object UpdateDiscourseProjectTopic {
    def newJob(projectId: DbRef[Project]): UpdateDiscourseProjectTopic =
      UpdateDiscourseProjectTopic(JobInfo.newJob(JobType.UpdateDiscourseProjectTopicType), projectId)
  }

  case class UpdateDiscourseVersionPost(
      info: JobInfo,
      versionId: DbRef[Version]
  ) extends TypedJob {

    def toJob: Job =
      Job(info, Map("version_id" -> versionId.toString))
  }
  object UpdateDiscourseVersionPost {
    def newJob(versionId: DbRef[Version]): UpdateDiscourseVersionPost =
      UpdateDiscourseVersionPost(JobInfo.newJob(JobType.UpdateDiscourseVersionPostType), versionId)
  }

  case class DeleteDiscourseTopic(
      info: JobInfo,
      topicId: Int
  ) extends TypedJob {

    def toJob: Job =
      Job(info, Map("topic_id" -> topicId.toString))
  }
  object DeleteDiscourseTopic {
    def newJob(topicId: Int): DeleteDiscourseTopic =
      DeleteDiscourseTopic(JobInfo.newJob(JobType.DeleteDiscourseTopicType), topicId)
  }
}
