import java.net.InetAddress
import java.sql.Timestamp
import javax.sql.DataSource

import scala.reflect.runtime.universe.TypeTag

import play.api.db.Databases
import play.api.db.evolutions.Evolutions
import play.api.i18n.Lang

import db.{ObjId, DbRef, ObjectTimestamp}
import models.admin._
import models.api.ProjectApiKey
import models.project._
import models.statistic.{ProjectView, VersionDownload}
import models.user._
import models.user.role.{OrganizationRole, ProjectRole}
import ore.Color
import ore.permission.role.RoleType
import ore.project.io.DownloadType
import ore.project.{Category, FlagReason}
import ore.rest.ProjectApiKeyType
import ore.user.Prompt
import ore.user.notification.NotificationType

import cats.data.{NonEmptyList => NEL}
import cats.effect.IO
import com.github.tminglei.slickpg.InetString
import doobie._
import doobie.implicits._
import doobie.postgres.implicits._
import doobie.scalatest.IOChecker
import enumeratum.values.{ValueEnum, ValueEnumEntry}
import org.junit.runner._
import org.scalatest.junit.JUnitRunner
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

@RunWith(classOf[JUnitRunner])
class SchemaSpec extends FunSuite with Matchers with IOChecker with BeforeAndAfterAll {

  lazy val database = Databases(
    "org.postgresql.Driver",
    sys.env.getOrElse("ORE_TESTDB_JDBC", "jdbc:postgresql://localhost/ore_test"),
    config = Map(
      "username" -> sys.env.getOrElse("DB_USERNAME", "ore"),
      "password" -> sys.env.getOrElse("DB_PASSWORD", "")
    )
  )

  lazy val transactor: Transactor.Aux[IO, DataSource] =
    Transactor.fromDataSource[IO](database.dataSource)

  implicit val objectIdMeta: Meta[ObjId]                  = Meta[DbRef].xmap(ObjId.apply, _.value)
  implicit val objectTimestampMeta: Meta[ObjectTimestamp] = Meta[Timestamp].xmap(ObjectTimestamp.apply, _.value)

  def enumeratumMeta[V: TypeTag, E <: ValueEnumEntry[V]: TypeTag](
      enum: ValueEnum[V, E]
  )(implicit meta: Meta[V]): Meta[E] =
    meta.xmap[E](enum.withValue, _.value)

  implicit val colorMeta: Meta[Color]                             = enumeratumMeta(Color)
  implicit val tagColorMeta: Meta[TagColor]                       = enumeratumMeta(TagColor)
  implicit val roleTypeMeta: Meta[RoleType]                       = enumeratumMeta(RoleType)
  implicit val categoryMeta: Meta[Category]                       = enumeratumMeta(Category)
  implicit val flagReasonMeta: Meta[FlagReason]                   = enumeratumMeta(FlagReason)
  implicit val notificationTypeMeta: Meta[NotificationType]       = enumeratumMeta(NotificationType)
  implicit val promptMeta: Meta[Prompt]                           = enumeratumMeta(Prompt)
  implicit val downloadTypeMeta: Meta[DownloadType]               = enumeratumMeta(DownloadType)
  implicit val pojectApiKeyTypeMeta: Meta[ProjectApiKeyType]      = enumeratumMeta(ProjectApiKeyType)
  implicit val visibilityMeta: Meta[Visibility]                   = enumeratumMeta(Visibility)
  implicit val loggedActionMeta: Meta[LoggedAction]               = enumeratumMeta(LoggedAction)
  implicit val loggedActionContextMeta: Meta[LoggedActionContext] = enumeratumMeta(LoggedActionContext)

  implicit val langMeta: Meta[Lang] = Meta[String].xmap(Lang.apply, _.toLocale.toLanguageTag)
  implicit val inetStringMeta: Meta[InetString] =
    Meta[InetAddress].xmap(address => InetString(address.toString), str => InetAddress.getByName(str.value))

  implicit val promptArrayMeta: Meta[List[Prompt]] =
    Meta[List[Int]].xmap(_.map(Prompt.withValue), _.map(_.value))
  implicit val roleTypeArrayMeta: Meta[List[RoleType]] =
    Meta[List[String]].xmap(_.map(RoleType.withValue), _.map(_.value))

  implicit def unsafeNelMeta[A](implicit listMeta: Meta[List[A]], typeTag: TypeTag[NEL[A]]): Meta[NEL[A]] =
    listMeta.xmap(NEL.fromListUnsafe, _.toList)

  override def beforeAll(): Unit = Evolutions.applyEvolutions(database)

  test("Project") {
    check(sql"""|SELECT id, created_at, plugin_id, owner_name, owner_id, name, slug, recommended_version_id,
                |category, description, stars, views, downloads, topic_id, post_id, is_topic_dirty, visibility,
                |last_updated, notes FROM projects""".stripMargin.query[Project])
  }

  test("Project settings") {
    check(sql"""|SELECT id, created_at, project_id, homepage, issues, source, license_name, license_url,
                |forum_sync FROM project_settings""".stripMargin.query[ProjectSettings])
  }

  test("Project watchers") {
    check(sql"""SELECT project_id, user_id FROM project_watchers""".query[(DbRef, DbRef)])
  }

  test("Project views") {
    check(sql"""|SELECT id, created_at, project_id, address, cookie,
                |user_id from project_views""".stripMargin.query[ProjectView])
  }

  test("Project stars") {
    check(sql"""SELECT user_id, project_id FROM project_stars""".query[(DbRef, DbRef)])
  }

  test("Project log") {
    check(sql"""SELECT id, created_at, project_id FROM project_logs""".query[ProjectLog])
  }

  test("Project log entry") {
    check(sql"""|SELECT id, created_at, log_id, tag, message, occurrences,
                |last_occurrence FROM project_log_entries""".stripMargin.query[ProjectLogEntry])
  }

  test("Page") {
    check(sql"""|SELECT id, created_at, project_id, parent_id, name, slug,
                |is_deletable, contents FROM project_pages""".stripMargin.query[Page])
  }

  test("Channel") {
    check(sql"""|SELECT id, created_at, project_id, name, color,
                |is_non_reviewed FROM project_channels""".stripMargin.query[Channel])
  }

  test("Tag") {
    check(sql"""SELECT id, version_ids, name, data, color FROM project_tags""".query[Tag])
  }

  test("Version") {
    check(sql"""|SELECT id, created_at, project_id, version_string, dependencies, assets, channel_id, file_size, hash,
                |author_id, description, downloads, is_reviewed, reviewer_id, approved_at, tags, visibility, file_name,
                |signature_file_name, is_non_reviewed FROM project_versions
       """.stripMargin.query[Version])
  }

  test("DownloadWarning") {
    check(sql"""|SELECT id, created_at, expiration, token, version_id, address, is_confirmed, download_id
                |FROM project_version_download_warnings""".stripMargin.query[DownloadWarning])
  }

  test("UnsafeDownload") {
    check(sql"""|SELECT id, created_at, user_id, address, download_type
                |FROM project_version_unsafe_downloads""".stripMargin.query[UnsafeDownload])
  }

  test("VersionDownloads") {
    check(sql"""|SELECT id, created_at, version_id, address, cookie, user_id
                |FROM project_version_downloads""".stripMargin.query[VersionDownload])
  }

  test("User") {
    check(
      sql"""|SELECT id, created_at, full_name, name, email, tagline, global_roles, join_date, read_prompts, pgp_pub_key,
            |last_pgp_pub_key_update, is_locked, language FROM users""".stripMargin.query[User]
    )
  }

  test("Session") {
    check(sql"""SELECT id, created_at, expiration, username, token FROM user_sessions""".query[Session])
  }

  test("SignOn") {
    check(sql"""SELECT id, created_at, nonce, is_completed FROM user_sign_ons""".query[SignOn])
  }

  test("Organization") {
    check(sql"""SELECT id, created_at, name, user_id FROM organizations""".query[Organization])
  }

  test("OrganizationMember") {
    check(sql"""SELECT user_id, organization_id FROM organization_members""".query[(DbRef, DbRef)])
  }

  test("OrganizationRole") {
    check(sql"""|SELECT id, created_at, user_id, organization_id, role_type,
                |is_accepted FROM user_organization_roles""".stripMargin.query[OrganizationRole])
  }

  test("ProjectRole") {
    check(sql"""|SELECT id, created_at, user_id, project_id, role_type,
                |is_accepted FROM user_project_roles""".stripMargin.query[ProjectRole])
  }

  test("ProjectMember") {
    check(
      sql"""SELECT project_id, user_id FROM project_members"""
        .query[(DbRef, DbRef)]
    )
  }

  test("Notifiation") {
    check(sql"""|SELECT id, created_at, user_id, origin_id, notification_type, message_args, action,
                |read FROM notifications""".stripMargin.query[Notification])
  }

  test("Flag") {
    check(sql"""|SELECT id, created_at, project_id, user_id, reason, comment, is_resolved, resolved_at, resolved_by
                |FROM project_flags""".stripMargin.query[Flag])
  }

  test("ProjectApiKey") {
    check(sql"""SELECT id, created_at, project_id, key_type, value FROM project_api_keys""".query[ProjectApiKey])
  }

  test("Review") {
    check(
      sql"""SELECT id, created_at, version_id, user_id, ended_at, comment FROM project_version_reviews""".query[Review]
    )
  }

  test("ProjectVisibilityChange") {
    check(sql"""|SELECT id, created_at, created_by, project_id, comment, resolved_at, resolved_by, visibility
                |FROM project_visibility_changes""".stripMargin.query[ProjectVisibilityChange])
  }

  test("LoggedAction") {
    check(
      sql"""|SELECT id, created_at, user_id, address, action, action_context, action_context_id, new_state, old_state
            |FROM logged_actions""".stripMargin.query[LoggedActionModel]
    )
  }

  test("VersionVisibilityChange") {
    check(sql"""|SELECT id, created_at, created_by, version_id, comment, resolved_at, resolved_by, visibility
                |FROM project_version_visibility_changes""".stripMargin.query[VersionVisibilityChange])
  }

  test("LoggedActionView") {
    /* We can't check this one as all columns in views are nullable
    check(
      sql"""|SELECT id, created_at, user_id, address, action, action_context, action_context_id, new_state, old_state,
            |u_id, u_name, p_id, p_plugin_id, p_slug, p_owner_name, pv_id, pv_version_string, pp_id, pp_slug, s_id,
            |s_name, filter_project, filter_version, filter_page, filter_subject, filter_action
            |FROM v_logged_actions""".stripMargin.query[LoggedActionViewModel])
     */
    check(sql"""SELECT p_id, p_plugin_id, p_slug, p_owner_name FROM v_logged_actions""".query[LoggedProject])
    check(sql"""SELECT pv_id, pv_version_string FROM v_logged_actions""".query[LoggedProjectVersion])
    check(sql"""SELECT pp_id, pp_slug FROM v_logged_actions""".query[LoggedProjectPage])
    check(sql"""SELECT s_id, s_name FROM v_logged_actions""".query[LoggedSubject])
  }

  override def afterAll(): Unit = database.shutdown()
}
