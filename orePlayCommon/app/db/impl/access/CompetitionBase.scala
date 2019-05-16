package db.impl.access

import scala.language.higherKinds

import java.io.File
import java.nio.file.Files._
import java.nio.file.{Path, Paths, StandardCopyOption}
import java.time.Instant

import scala.collection.JavaConverters._

import ore.OreConfig
import ore.db.access.{ModelView, QueryView}
import ore.db.{Model, ModelService}
import ore.db.impl.OrePostgresDriver.api._
import ore.db.impl.schema.{CompetitionTable, VersionTagTable}
import ore.models.competition.{Competition, CompetitionEntry}
import ore.models.project.{Project, ProjectSettings, Version}
import util.syntax._

import cats.data.{EitherT, NonEmptyList}
import cats.effect.{ContextShift, IO}
import cats.syntax.all._

/**
  * Handles competition based database actions.
  *
  * @param service ModelService instance
  */
class CompetitionBase(implicit val service: ModelService[IO]) {

  private def uploadsDir(implicit config: OreConfig): Path = Paths.get(config.app.uploadsDir)

  /**
    * Returns [[QueryView]] to all active competitions.
    *
    * @return Access to active competitions
    */
  def active[V[_, _]: QueryView](
      view: V[CompetitionTable, Model[Competition]]
  ): V[CompetitionTable, Model[Competition]] = {
    val now = Instant.now()
    view.filterView(competition => competition.startDate <= now && competition.endDate > now)
  }

  /**
    * Saves the specified file as the specified competition's banner.
    *
    * @param competition  Competition to set banner for
    * @param file         Banner file
    * @param fileName     Banner file name
    */
  def saveBanner(competition: Model[Competition], file: File, fileName: String)(implicit config: OreConfig): Path = {
    val path = getBannerDir(competition).resolve(fileName)
    if (notExists(path.getParent))
      createDirectories(path.getParent)
    list(path.getParent).iterator().asScala.foreach(delete)
    copy(file.toPath, path, StandardCopyOption.REPLACE_EXISTING)
  }

  /**
    * Returns the directory that contains the specified competition's banner.
    *
    * @param competition  Competition
    * @return             Banner directory
    */
  def getBannerDir(competition: Model[Competition])(implicit config: OreConfig): Path =
    this.uploadsDir.resolve("competitions").resolve(competition.id.value.toString)

  /**
    * Returns the path to the specified competition's banner, if any.
    *
    * @param competition  Competition
    * @return             Banner path, if any, none otherwise
    */
  def getBannerPath(competition: Model[Competition])(implicit config: OreConfig): Option[Path] = {
    val dir = getBannerDir(competition)
    if (exists(dir))
      Option(list(dir).findAny().orElse(null))
    else
      None
  }

  /**
    * Submits the specified Project to the specified Competition.
    *
    * @param project      Project to submit
    * @param comp         Competition to submit project to
    * @return             Error string if any, none otherwise
    */
  def submitProject(project: Model[Project], projectSettings: Model[ProjectSettings], comp: Model[Competition])(
      implicit cs: ContextShift[IO]
  ): EitherT[IO, NonEmptyList[String], Model[CompetitionEntry]] = {
    val entries                = comp.entries(ModelView.now(CompetitionEntry))
    val projectAlreadyEnteredF = entries.exists(_.projectId === project.id.value)
    val projectLimitReachedF   = entries.count(_.userId === project.ownerId).map(_ >= comp.allowedEntries)
    val competitionCapacityReachedF =
      comp.maxEntryTotal.fold(IO.pure(false))(capacity => entries.size.map(_ >= capacity))
    val deadlinePassed = comp.timeRemaining.toSeconds <= 0

    val onlySpongePluginsF =
      if (comp.isSpongeOnly)
        project
          .recommendedVersion(ModelView.later(Version))
          .map { rvQuery =>
            service
              .runDBIO(
                rvQuery
                  .join(TableQuery[VersionTagTable])
                  .on(_.id === _.versionId)
                  .filter(_._2.name.startsWith("Sponge"))
                  .exists
                  .result
              )
              .map(!_)
          }
          .getOrElse(IO.pure(true))
      else IO.pure(false)
    val onlyVisibleSource = comp.isSourceRequired && projectSettings.source.isEmpty

    EitherT
      .right[NonEmptyList[String]](
        (
          projectAlreadyEnteredF,
          projectLimitReachedF,
          competitionCapacityReachedF,
          onlySpongePluginsF,
        ).parTupled
      )
      .flatMap {
        case (
            projectAlreadyEntered,
            projectLimitReached,
            competitionCapacityReached,
            onlySpongePlugins,
            ) =>
          val errors = Seq(
            projectAlreadyEntered      -> "error.project.competition.alreadySubmitted",
            projectLimitReached        -> "error.project.competition.entryLimit",
            competitionCapacityReached -> "error.project.competition.capacity",
            deadlinePassed             -> "error.project.competition.over",
            onlySpongePlugins          -> "error.project.competition.spongeOnly",
            onlyVisibleSource          -> "error.project.competition.sourceOnly"
          )

          val applicableErrors = errors.collect {
            case (pred, msg) if pred => msg
          }

          applicableErrors.toList.toNel.fold(
            EitherT.right[NonEmptyList[String]](
              service.insert(
                CompetitionEntry(
                  projectId = project.id.value,
                  userId = project.ownerId,
                  competitionId = comp.id.value
                )
              )
            )
          )(errs => EitherT.leftT(errs))
      }
  }
}
object CompetitionBase {
  def apply()(implicit competitionBase: CompetitionBase): CompetitionBase = competitionBase

  implicit def fromService(implicit service: ModelService[IO]): CompetitionBase = new CompetitionBase()
}
