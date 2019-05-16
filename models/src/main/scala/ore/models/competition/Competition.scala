package ore.models.competition

import scala.language.higherKinds

import java.time.Instant

import scala.concurrent.duration._

import ore.db.access.QueryView
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.common.{Describable, Named}
import ore.db.impl.schema.{CompetitionEntryTable, CompetitionTable}
import ore.db.{DbRef, Model, ModelQuery}
import ore.models.user.{User, UserOwned}
import ore.syntax._

import slick.lifted.TableQuery

/**
  * Represents a [[ore.models.project.Project]] competition.
  *
  * @param userId              Owner ID
  * @param name                Competition name
  * @param description         Competition description
  * @param startDate           Date when the competition begins
  * @param endDate             Date when the competition ends
  * @param timeZone            Time zone of competition
  * @param isVotingEnabled     True if project voting is enabled
  * @param isStaffVotingOnly   True if only staff members can vote
  * @param shouldShowVoteCount True if the vote count should be displayed
  * @param isSpongeOnly        True if only Sponge plugins are permitted in the competition
  * @param isSourceRequired    True if source-code is required for entry to the competition
  * @param defaultVotes        Default amount of votes a user has
  * @param staffVotes          The amount of votes staff-members have
  * @param allowedEntries      The amount of entries a user may submit
  * @param maxEntryTotal       The total amount of projects allowed in the competition
  */
case class Competition(
    userId: DbRef[User],
    name: String,
    description: Option[String],
    startDate: Instant,
    endDate: Instant,
    timeZone: String,
    isVotingEnabled: Boolean = true,
    isStaffVotingOnly: Boolean = false,
    shouldShowVoteCount: Boolean = true,
    isSpongeOnly: Boolean = false,
    isSourceRequired: Boolean = false,
    defaultVotes: Int = 1,
    staffVotes: Int = 1,
    allowedEntries: Int = 1,
    maxEntryTotal: Option[Int] = None
) extends Named
    with Describable {

  /**
    * Returns the amount of time remaining in the competition.
    *
    * @return Time remaining in competition
    */
  def timeRemaining: FiniteDuration = (this.endDate.toEpochMilli - Instant.now().toEpochMilli).millis
}
object Competition extends DefaultModelCompanion[Competition, CompetitionTable](TableQuery[CompetitionTable]) {

  implicit val query: ModelQuery[Competition] =
    ModelQuery.from(this)

  implicit val competitionIsUserOwned: UserOwned[Competition] = _.userId

  implicit class CompetitionModelOps(private val self: Model[Competition]) extends AnyVal {

    /**
      * Returns this competition's [[CompetitionEntry]]s.
      *
      * @return Competition entries
      */
    def entries[V[_, _]: QueryView](
        view: V[CompetitionEntryTable, Model[CompetitionEntry]]
    ): V[CompetitionEntryTable, Model[CompetitionEntry]] =
      view.filterView(_.competitionId === self.id.value)
  }
}
