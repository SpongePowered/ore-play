package ore.models.competition

import scala.language.higherKinds

import ore.db.access.ModelView
import ore.db.impl.DefaultModelCompanion
import ore.db.impl.schema.{CompetitionEntryTable, CompetitionEntryUserVotesTable}
import ore.db.{AssociationQuery, DbRef, Model, ModelQuery, ModelService}
import ore.models.project.{Project, ProjectOwned}
import ore.models.user.{User, UserOwned}

import cats.MonadError
import slick.lifted.TableQuery

/**
  * Represents a single entry in a [[Competition]].
  *
  * @param projectId      Project ID
  * @param userId         User owner ID
  * @param competitionId  Competition ID
  */
case class CompetitionEntry(
    projectId: DbRef[Project],
    userId: DbRef[User],
    competitionId: DbRef[Competition]
) {

  /**
    * Returns the [[Competition]] this entry belongs to.
    *
    * @return Competition entry belongs to
    */
  def competition[F[_]](implicit service: ModelService[F], F: MonadError[F, Throwable]): F[Model[Competition]] =
    ModelView
      .now(Competition)
      .get(competitionId)
      .getOrElseF(
        F.raiseError[Model[Competition]](new IllegalStateException("Found competition entry without competition"))
      )
}
object CompetitionEntry
    extends DefaultModelCompanion[CompetitionEntry, CompetitionEntryTable](TableQuery[CompetitionEntryTable]) {

  implicit val query: ModelQuery[CompetitionEntry] =
    ModelQuery.from(this)

  implicit val assocEntryVotesQuery: AssociationQuery[CompetitionEntryUserVotesTable, CompetitionEntry, User] =
    AssociationQuery.from[CompetitionEntryUserVotesTable, CompetitionEntry, User](
      TableQuery[CompetitionEntryUserVotesTable]
    )(_.entryId, _.userId)

  implicit val competitionEntryIsUserOwned: UserOwned[CompetitionEntry]       = _.userId
  implicit val competitionEntryIsProjectOwned: ProjectOwned[CompetitionEntry] = _.projectId
}
