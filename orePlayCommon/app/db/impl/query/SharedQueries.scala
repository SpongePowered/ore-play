package db.impl.query

import ore.db.DbRef
import ore.db.impl.query.DoobieOreProtocol
import ore.models.project.{Project, Webhook}

import doobie.implicits._

object SharedQueries extends DoobieOreProtocol {

  val refreshHomeView: doobie.Update0 = sql"REFRESH MATERIALIZED VIEW home_projects".update

  def watcherStartProject(id: DbRef[Project]): doobie.Query0[Long] =
    sql"""SELECT p.stars FROM project_stats p WHERE p.id = $id""".query[Long]

  def addWebhookJobs(
      projectId: DbRef[Project],
      projectOwner: String,
      projectSlug: String,
      webhookEvent: Webhook.WebhookEventType,
      data: String,
      discordData: String
  ): doobie.Update0 =
    sql"""|INSERT INTO jobs (created_at, last_updated, retry_at, last_error, last_error_descriptor, state, job_type,
          |                  job_properties)
          |SELECT now(),
          |       NULL,
          |       NULL,
          |       NULL,
          |       NULL,
          |       'not_started',
          |       'post_webhook',
          |       hstore(ARRAY [
          |           ['project_owner', $projectOwner],
          |           ['project_slug', $projectSlug],
          |           ['webhook_id', w.id::TEXT],
          |           ['webhook_secret', w.secret],
          |           ['webhook_type', $webhookEvent],
          |           ['webhook_callback', w.callback_url],
          |           ['webhook_data', CASE w.discord_formatted WHEN TRUE THEN $discordData ELSE $data END]])
          |    FROM project_callbacks w
          |    WHERE w.project_id = $projectId
          |      AND $webhookEvent = ANY (w.event_types)
          |      AND w.last_error IS NULL;""".stripMargin.update
}
