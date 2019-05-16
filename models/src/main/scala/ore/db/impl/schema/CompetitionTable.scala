package ore.db.impl.schema

import java.time.Instant

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.table.common.{DescriptionColumn, NameColumn}
import ore.models.competition.Competition
import ore.models.user.User

class CompetitionTable(tag: Tag)
    extends ModelTable[Competition](tag, "project_competitions")
    with NameColumn[Competition]
    with DescriptionColumn[Competition] {

  def userId              = column[DbRef[User]]("user_id")
  def startDate           = column[Instant]("start_date")
  def endDate             = column[Instant]("end_date")
  def timeZone            = column[String]("time_zone")
  def isVotingEnabled     = column[Boolean]("is_voting_enabled")
  def isStaffVotingOnly   = column[Boolean]("is_staff_voting_only")
  def shouldShowVoteCount = column[Boolean]("should_show_vote_count")
  def isSpongeOnly        = column[Boolean]("is_sponge_only")
  def isSourceRequired    = column[Boolean]("is_source_required")
  def defaultVotes        = column[Int]("default_votes")
  def staffVotes          = column[Int]("staff_votes")
  def allowedEntries      = column[Int]("allowed_entries")
  def maxEntryTotal       = column[Int]("max_entry_total")

  override def * =
    (
      id.?,
      createdAt.?,
      (
        userId,
        name,
        description.?,
        startDate,
        endDate,
        timeZone,
        isVotingEnabled,
        isStaffVotingOnly,
        shouldShowVoteCount,
        isSpongeOnly,
        isSourceRequired,
        defaultVotes,
        staffVotes,
        allowedEntries,
        maxEntryTotal.?
      )
    ) <> (mkApply((Competition.apply _).tupled), mkUnapply(Competition.unapply))
}
