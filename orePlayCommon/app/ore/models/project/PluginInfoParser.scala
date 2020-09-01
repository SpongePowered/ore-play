package ore.models.project

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, IOException}
import java.util.jar.JarInputStream

import scala.annotation.nowarn
import scala.jdk.CollectionConverters._
import scala.util.Try

import ore.OreConfig
import ore.OreConfig.Ore.Loader
import ore.OreConfig.Ore.Loader._

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.instances.list._
import cats.syntax.all._
import _root_.io.circe._
import _root_.io.circe.syntax._
import shapeless.tag
import shapeless.tag.@@

object PluginInfoParser {

  case class Dependency(
      identifier: String,
      lowestVersion: Option[String],
      rawVersion: Option[String],
      required: Boolean
  )
  case class PartialEntry(
      name: Option[String],
      identifier: String,
      version: Option[String],
      dependencies: Seq[Dependency]
  )
  case class Entry(
      name: String,
      identifier: String,
      version: String,
      dependencies: Seq[Dependency],
      mixin: Boolean
  )

  def processJar(jar: JarInputStream)(implicit config: OreConfig): (List[String], List[Entry]) = {
    val filesOfInterest =
      config.ore.loaders.flatMap(loader => loader.filename.map(loader -> _).toList).groupMap(_._2)(_._1)

    val res = Iterator
      .continually(jar.getNextJarEntry)
      .takeWhile(_ != null) // scalafix:ok
      .filter(entry => filesOfInterest.contains(entry.getName))
      .flatMap { entry =>
        val bytes = Iterator.continually(jar.read()).takeWhile(_ != -1).map(_.toByte).toArray
        filesOfInterest(entry.getName).map(processLoader(bytes, _))
      }
      .toList

    //Need to handle the manifest seperately
    val metaInfRes = filesOfInterest.get("META-INF/MANIFEST.MF").toList.flatMap { manifestLoaders =>
      val baos = new ByteArrayOutputStream()
      jar.getManifest.write(baos)
      val bytes = baos.toByteArray
      manifestLoaders.map(processLoader(bytes, _))
    }

    val (userErrors, entries) = (res ::: metaInfRes).map {
      case Validated.Valid((errors, entries)) => (errors, entries)
      case Validated.Invalid(errors)          => (errors.map(_.show).toList, Nil)
    }.unzip

    (userErrors.flatten, entries.flatten)
  }

  def isMixin(jar: JarInputStream): Boolean =
    jar.getManifest.getMainAttributes.asScala.contains("MixinConfigs")

  def processLoader(bytes: Array[Byte], loader: Loader): ValidatedNel[Error, (Seq[String], Seq[Entry])] = {
    lazy val strContent = new String(bytes, "UTF-8")

    val json: Either[ParsingFailure, Json] = loader.dataType match {
      case DataType.JSON => parser.parse(strContent)
      case DataType.YAML => ???
      case DataType.Manifest =>
        Try {
          val attributes = new java.util.jar.Manifest(new ByteArrayInputStream(bytes)).getMainAttributes
          Right(Json.obj(attributes.asScala.map(t => t._1.toString := t._2.toString).toSeq: _*))
        }.recover {
          case e: IOException => Left(ParsingFailure("Could not parse jar manifest", e))
        }.get
      case DataType.TOML => ???
    }

    Validated.fromEither(json).leftMap(NonEmptyList.one).andThen(json => parseLoaderData(json.hcursor, loader))
  }

  def parseLoaderData(data: HCursor, loader: Loader): ValidatedNel[DecodingFailure, (Seq[String], Seq[Entry])] = {
    def parseField(
        cursor: ACursor,
        field: Field,
        name: Option[String] = None,
        identifier: Option[String] = None,
        version: Option[String] = None
    ): Seq[Decoder.Result[ACursor]] @@ NonDeterministic =
      tag[NonDeterministic](
        field.map { path =>
          path.foldLeft(Right(if (path.head == "$") data else cursor): Decoder.Result[ACursor])((c, s) =>
            c.flatMap {
              cursor =>
                @nowarn
                val fieldToGoDown = s match {
                  case "$identifier" =>
                    identifier.toRight(DecodingFailure("Identifier unknown at this location", cursor.history))
                  case "$name" =>
                    name.toRight(DecodingFailure("Name unknown at this location", cursor.history))
                  case "$version" =>
                    version.toRight(DecodingFailure("Version unknown at this location", cursor.history))
                  case _ => Right(s)
                }

                fieldToGoDown.map(cursor.downField)
            }
          )
        }
      )

    def getFieldOptional(
        cursor: ACursor,
        field: Field,
        name: Option[String] = None,
        identifier: Option[String] = None,
        version: Option[String] = None
    ): Seq[ACursor] @@ NonDeterministic =
      tag[NonDeterministic](parseField(cursor, field, name, identifier, version).flatMap(_.toOption))

    def getFieldRequired(
        cursor: ACursor,
        field: Field
    ): ValidatedNel[DecodingFailure, NonEmptyList[ACursor] @@ NonDeterministic] = {
      val parsedFields   = parseField(cursor, field)
      val ignoringErrors = parsedFields.flatMap(_.toOption)

      if (ignoringErrors.isEmpty && field.nonEmpty)
        Validated.invalid(NonEmptyList.fromListUnsafe(parsedFields.flatMap(_.swap.toOption).toList))
      else
        Validated.validNel(tag[NonDeterministic](NonEmptyList.fromListUnsafe(ignoringErrors.toList)))
    }

    def loadEntry(json: JsonObject): Option[PartialEntry] = {
      val cursor = Json.fromJsonObject(json).hcursor

      def getEntryField[A: Decoder](
          cursor: ACursor,
          field: Field,
          refName: => Option[String] = name,
          refIdentifier: => Option[String] = identifier,
          refVersion: => Option[String] = version
      ): Option[A] =
        getFieldOptional(cursor, field, refName, refIdentifier, refVersion).view
          .map(_.as[A])
          .flatMap(_.toOption)
          .headOption

      lazy val name       = getEntryField[String](cursor, loader.nameField, refName = None)
      lazy val identifier = getEntryField[String](cursor, loader.identifierField, refIdentifier = None)
      lazy val version    = getEntryField[String](cursor, loader.identifierField, refVersion = None)

      val dependencies = for {
        depBlock    <- loader.dependencyTypes
        arrayCursor <- getFieldOptional(cursor, depBlock.field)
        depSyntax     = depBlock.dependencySyntax
        versionSyntax = depBlock.versionSyntax
        arrayObj <- arrayCursor.values.toList.flatten
        cursor = arrayObj.hcursor
        depTuple <- depSyntax match {
          case DependencyBlock.DependencySyntax.AsObject(identifierField, versionField, requiredField, optionalField) =>
            val identifierFieldTyped = tag[NonDeterministic](identifierField.toList)

            val identifier = getEntryField[String](cursor, identifierFieldTyped)
            val version    = getEntryField[String](cursor, versionField)
            val required   = getEntryField[Boolean](cursor, requiredField)
            val optional   = getEntryField[Boolean](cursor, optionalField)

            identifier.map { id =>
              (
                id,
                version,
                required.orElse(optional.map(!_)).getOrElse(depBlock.defaultIsRequired)
              )
            }.toList

          case DependencyBlock.DependencySyntax.AtSeparated =>
            cursor.as[String].map(_.split("@", 2)).map(a => (a(0), a.lift(1))).toOption.toList.map {
              case (id, version) =>
                (id, version, depBlock.defaultIsRequired)
            }
        }
      } yield {
        val lowestDepVersion = depTuple._2.map { version =>
          versionSyntax match {
            case DependencyBlock.VersionSyntax.Maven => ???
          }
        }

        Dependency(depTuple._1, lowestDepVersion, depTuple._2, depTuple._3)
      }

      identifier.map(id => PartialEntry(name, id, version, dependencies))
    }

    val entryCursors = loader.entryLocation match {
      case OreConfig.Ore.Loader.EntryLocation.Root         => Validated.validNel(NonEmptyList.one(data))
      case OreConfig.Ore.Loader.EntryLocation.Field(field) => getFieldRequired(data, field)
    }

    entryCursors
      .andThen { cursors =>
        val entryObjs = tag[NonDeterministic](
          if (loader.hasMultipleEntries) cursors.map(_.as[Seq[JsonObject]])
          else cursors.map(_.as[JsonObject].map(Seq(_)))
        )

        val ignoringErrors = entryObjs.toList.flatMap(_.toOption)
        if (ignoringErrors.isEmpty)
          Validated.invalid(NonEmptyList.fromListUnsafe(entryObjs.toList.flatMap(_.swap.toOption)))
        else
          Validated.validNel(tag[NonDeterministic](NonEmptyList.fromListUnsafe(ignoringErrors)))
      }
      .map { entryChoices =>
        val results = entryChoices.toList.flatMap(_.flatMap(loadEntry))
        val userErrorsAndEntries =
          results
            .groupMapReduce(_.identifier)(identity)((p1, p2) =>
              PartialEntry(
                p1.name.orElse(p2.name),
                p1.identifier,
                p1.version.orElse(p2.version),
                p1.dependencies ++ p2.dependencies
              )
            )
            .values
            .map {
              case PartialEntry(Some(name), identifier, Some(version), dependencies) =>
                Right(Entry(name, identifier, version, dependencies, mixin = false))
              case PartialEntry(None, identifier, _, _) => Left(s"No name found for entry with identifier $identifier")
              case PartialEntry(_, identifier, None, _) =>
                Left(s"No version found for entry with identifier $identifier")
            }
            .toSeq

        userErrorsAndEntries.toList.partitionEither(identity)
      }
  }
}
