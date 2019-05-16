package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.competition.CompetitionEntry
import ore.models.user.User

class CompetitionEntryUserVotesTable(tag: Tag)
    extends AssociativeTable[CompetitionEntry, User](tag, "project_competition_entry_votes") {

  def userId  = column[DbRef[User]]("user_id")
  def entryId = column[DbRef[CompetitionEntry]]("entry_id")

  override def * = (entryId, userId)

}
