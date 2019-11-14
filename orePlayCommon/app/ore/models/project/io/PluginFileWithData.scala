package ore.models.project.io

import java.nio.file.{Files, Path}

import ore.data.Platform
import ore.data.project.Dependency
import ore.db.{DbRef, Model}
import ore.models.project.{Project, Version, VersionTag}
import ore.models.user.User
import ore.util.StringUtils

import cats.data.{Validated, ValidatedNel}
import cats.instances.list._
import cats.instances.tuple._
import cats.syntax.all._
import cats.effect.Sync

class PluginFileWithData(val path: Path, val user: Model[User], val data: PluginFileData) {

  def delete[F[_]](implicit F: Sync[F]): F[Unit] = F.delay(Files.delete(path))

  /**
    * Returns an MD5 hash of this PluginFile.
    *
    * @return MD5 hash
    */
  lazy val md5: String = StringUtils.md5ToHex(Files.readAllBytes(this.path))

  lazy val fileSize: Long = Files.size(path)

  lazy val fileName: String = path.getFileName.toString

  lazy val dependencyIds: Seq[String] = data.dependencies.map {
    case Dependency(pluginId, Some(version)) => s"$pluginId:$version"
    case Dependency(pluginId, None)          => pluginId
  }

  lazy val versionString: String = StringUtils.slugify(data.version.get)

  def tagsForVersion(
      id: DbRef[Version],
      userTags: Map[String, Seq[String]]
  ): ValidatedNel[String, (List[String], List[VersionTag])] = {
    val userVersionTags: ValidatedNel[String, (List[String], List[VersionTag])] =
      userTags.toList
        .map(t => VersionTag.userTagsToReal(t._1, t._2, id))
        .combineAll

    (Platform.ghostTags(id, data.dependencies).combine(data.tags(id)), userVersionTags)
      .mapN {
        case ((autoWarnings, autoTags), (userWarnings, allUserCustomTags)) =>
          //Technically we might emit warnings for stuff we don't use, but it also
          //means we can emit warnings for platform tags and user tags in one
          val allWarnings = autoWarnings ++ userWarnings

          val autoTagsByName       = autoTags.groupBy(_.name)
          val userCustomTagsByName = allUserCustomTags.groupBy(_.name)

          val (userOverrideTags, userCustomVersionTags) =
            userCustomTagsByName.partition(t => autoTagsByName.contains(t._1))

          val autoTagsWithOverrides: List[VersionTag] = autoTagsByName.view.flatMap {
            case (name, tags) => userOverrideTags.getOrElse(name, tags)
          }.toList

          val platformAndUserTags = autoTagsWithOverrides ++ userCustomVersionTags.flatMap(_._2)

          val tagsWithStability =
            if (!platformAndUserTags.exists(_.name == VersionTag.StabilityTag.name))
              platformAndUserTags :+ VersionTag.StabilityTag.createTag(
                VersionTag.StabilityTag.StabilityValues.Stable,
                id
              )
            else platformAndUserTags

          val hasNoPlatform = tagsWithStability.forall(t => Platform.values.forall(p => p.name != t.name))

          if (hasNoPlatform) {
            Validated.invalidNel("tags.errors.missingPlatform")
          } else {
            Validated.validNel((allWarnings, tagsWithStability))
          }
      }
      .andThen(identity)
  }

  def warnings: Seq[String] = ???

  def asVersion(projectId: DbRef[Project], description: Option[String], createForumPost: Boolean): Version = Version(
    projectId = projectId,
    versionString = versionString,
    dependencyIds = dependencyIds.toList,
    fileSize = fileSize,
    hash = md5,
    authorId = Some(user.id),
    description = description,
    fileName = fileName,
    createForumPost = createForumPost
  )
}
