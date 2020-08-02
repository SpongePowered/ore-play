package db.impl

import java.nio.charset.StandardCharsets
import java.nio.file.Files

import scala.jdk.CollectionConverters._

import play.api.Environment
import play.api.db.evolutions.{Evolution, Evolutions, EvolutionsReader}
import play.api.libs.Collections

//Some stuff taken from ResourceEvolutionsReader
class OreEvolutionsReader(environment: Environment) extends EvolutionsReader {

  private val namePattern = """(\d+)(?:_.+)?\.sql""".r

  override def evolutions(db: String): collection.Seq[Evolution] = {
    val upsMarker   = """^(#|--).*!Ups.*$""".r
    val downsMarker = """^(#|--).*!Downs.*$""".r

    val UPS     = "UPS"
    val DOWNS   = "DOWNS"
    val UNKNOWN = "UNKNOWN"

    val mapUpsAndDowns: PartialFunction[String, String] = {
      case upsMarker(_)   => UPS
      case downsMarker(_) => DOWNS
      case _              => UNKNOWN
    }

    val isMarker: PartialFunction[String, Boolean] = {
      case upsMarker(_)   => true
      case downsMarker(_) => true
      case _              => false
    }

    val folder = environment.getFile(Evolutions.directoryName(db))
    val files = folder
      .listFiles(_.getName.endsWith(".sql"))
      .toSeq
      .flatMap { file =>
        file.getName match {
          case namePattern(revision) => Some((revision.toInt, file))
          case _                     => None
        }
      }
      .sortBy(_._1)

    require(files.toMap.sizeIs == files.length, "Found more than one evolution with the same revision")

    files.map {
      case (revision, file) =>
        val script = Files.readAllLines(file.toPath, StandardCharsets.UTF_8).asScala

        val parsed = Collections
          .unfoldLeft(("", script.toList.map(_.trim))) {
            case (_, Nil) => None
            case (context, lines) =>
              val (some, next) = lines.span(l => !isMarker(l))
              Some(
                (
                  next.headOption.map(c => (mapUpsAndDowns(c), next.tail)).getOrElse("" -> Nil),
                  context -> some.mkString("\n")
                )
              )
          }
          .reverse
          .drop(1)
          .groupBy(i => i._1)
          .view
          .mapValues(_.map(_._2).mkString("\n").trim)
          .toMap

        Evolution(revision, parsed.getOrElse(UPS, ""), parsed.getOrElse(DOWNS, ""))
    }
  }
}
