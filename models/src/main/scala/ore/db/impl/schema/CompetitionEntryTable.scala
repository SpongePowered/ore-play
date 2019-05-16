package ore.db.impl.schema

import ore.db.DbRef
import ore.db.impl.OrePostgresDriver.api._
import ore.models.competition.{Competition, CompetitionEntry}
import ore.models.project.Project
import ore.models.user.User

class CompetitionEntryTable(tag: Tag) extends ModelTable[CompetitionEntry](tag, "project_competition_entries") {

  def projectId     = column[DbRef[Project]]("project_id")
  def userId        = column[DbRef[User]]("user_id")
  def competitionId = column[DbRef[Competition]]("competition_id")

  override def * =
    (id.?, createdAt.?, (projectId, userId, competitionId)) <> (mkApply((CompetitionEntry.apply _).tupled), mkUnapply(
      CompetitionEntry.unapply
    ))

}
