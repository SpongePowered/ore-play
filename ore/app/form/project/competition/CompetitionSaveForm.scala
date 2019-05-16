package form.project.competition

import scala.language.higherKinds

import java.time.LocalDateTime

import ore.db.{Model, ModelService}
import ore.models.competition.Competition
import ore.util.StringLocaleFormatterUtils._

case class CompetitionSaveForm(
    startDate: LocalDateTime,
    endDate: LocalDateTime,
    timeZoneId: String,
    isVotingEnabled: Boolean,
    isStaffVotingOnly: Boolean,
    shouldShowVoteCount: Boolean,
    isSourceRequired: Boolean,
    defaultVotes: Int,
    staffVotes: Int,
    allowedEntries: Int,
    maxEntryTotal: Int
) extends CompetitionData {

  def save[F[_]](competition: Model[Competition])(implicit service: ModelService[F]): F[Model[Competition]] =
    service.update(competition)(
      _.copy(
        startDate = localDateTime2Instant(startDate, timeZoneId),
        endDate = localDateTime2Instant(endDate, timeZoneId),
        isVotingEnabled = isVotingEnabled,
        isStaffVotingOnly = isStaffVotingOnly,
        shouldShowVoteCount = shouldShowVoteCount,
        isSourceRequired = isSourceRequired,
        defaultVotes = defaultVotes,
        staffVotes = staffVotes,
        allowedEntries = allowedEntries,
        maxEntryTotal = Some(maxEntryTotal).filter(_ != -1)
      )
    )
}
