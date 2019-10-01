package ore.models.project

import scala.collection.immutable

import doobie._
import doobie.implicits._
import enumeratum.values._

/**
  * Represents a strategy used to sort [[ore.models.project.Project]]s.
  */
sealed abstract class ProjectSortingStrategy(
    val value: Int,
    val title: String,
    val fragment: Fragment,
    val apiName: String
) extends IntEnumEntry {
  def id: Int = value
}

/**
  * Collection of sorting strategies used to sort the home page.
  */
object ProjectSortingStrategy extends IntEnum[ProjectSortingStrategy] {

  /** All sorting strategies. */
  val values: immutable.IndexedSeq[ProjectSortingStrategy] = findValues

  /** The default strategy. */
  val Default: RecentlyUpdated.type = RecentlyUpdated

  case object MostStars       extends ProjectSortingStrategy(0, "Most stars", fr"p.stars DESC, p.name ASC", "stars")
  case object MostDownloads   extends ProjectSortingStrategy(1, "Most downloads", fr"p.downloads DESC", "downloads")
  case object MostViews       extends ProjectSortingStrategy(2, "Most views", fr"p.views DESC", "views")
  case object Newest          extends ProjectSortingStrategy(3, "Newest", fr"p.created_at DESC", "newest")
  case object RecentlyUpdated extends ProjectSortingStrategy(4, "Recently updated", fr"p.last_updated DESC", "updated")
  case object OnlyRelevance
      extends ProjectSortingStrategy(5, "Only relevance", fr"p.last_updated DESC", "only_relevance")

  /**
    * Parses a string as a sorting strategy.
    */
  def fromApiName(str: String): Option[ProjectSortingStrategy] =
    values.find(_.apiName == str)
}
