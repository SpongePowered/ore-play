package form.project.competition

import java.time.LocalDateTime

import ore.db.Model
import ore.models.competition.Competition
import ore.models.user.User

import ore.util.StringUtils._
import ore.util.StringLocaleFormatterUtils._

case class CompetitionCreateForm(
    name: String,
    description: Option[String],
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    timeZoneId: String,
    isVotingEnabled: Boolean,
    isStaffVotingOnly: Boolean,
    shouldShowVoteCount: Boolean,
    isSpongeOnly: Boolean,
    isSourceRequired: Boolean,
    defaultVotes: Int,
    staffVotes: Int,
    allowedEntries: Int,
    maxEntryTotal: Int
) extends CompetitionData {

  def create(user: Model[User]): Competition = {
    Competition(
      userId = user.id.value,
      name = name.trim,
      description = description.flatMap(noneIfEmpty),
      startDate = localDateTime2Instant(startDate, timeZoneId),
      endDate = localDateTime2Instant(endDate, timeZoneId),
      timeZone = timeZoneId,
      isVotingEnabled = isVotingEnabled,
      isStaffVotingOnly = isStaffVotingOnly,
      shouldShowVoteCount = shouldShowVoteCount,
      isSpongeOnly = isSpongeOnly,
      isSourceRequired = isSourceRequired,
      defaultVotes = defaultVotes,
      staffVotes = staffVotes,
      allowedEntries = allowedEntries,
      maxEntryTotal = Some(maxEntryTotal).filter(_ != -1)
    )
  }
}
