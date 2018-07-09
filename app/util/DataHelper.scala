package util

import com.google.common.base.Preconditions.checkArgument

import db.{ModelService, ObjectId}
import db.access.ModelAccess
import db.impl.access.{ProjectBase, UserBase}
import discourse.OreDiscourseApi
import javax.inject.Inject

import models.project.{Channel, Project, ProjectSettings, Version}
import models.user.User
import ore.OreConfig
import ore.project.factory.ProjectFactory
import play.api.cache.SyncCacheApi
import scala.concurrent.ExecutionContext

/**
  * Utility class for performing some bulk actions on the application data.
  * Typically for testing.
  */
final class DataHelper @Inject()(config: OreConfig,
                                 statusZ: StatusZ,
                                 service: ModelService,
                                 factory: ProjectFactory,
                                 forums: OreDiscourseApi,
                                 cacheApi: SyncCacheApi) {

  implicit private val projects: ProjectBase = this.service.getModelBase(classOf[ProjectBase])
  private val channels: ModelAccess[Channel] = this.service.access[Channel](classOf[Channel])
  private val versions: ModelAccess[Version] = this.service.access[Version](classOf[Version])
  private val users: UserBase = this.service.getModelBase(classOf[UserBase])

  val Logger = play.api.Logger("DataHelper")

  /**
    * Resets the application to factory defaults.
    */
  def reset()(implicit ec: ExecutionContext): Unit = {
    if (sys.env.getOrElse(statusZ.SpongeEnv, "unknown") != "local") return
    Logger.info("Resetting Ore...")
    this.projects.all.map { projects =>
      Logger.info(s"Deleting ${projects.size} projects...")
      for (project <- projects) this.projects.delete(project)
    }
    this.users.size.map { size =>
      Logger.info(s"Deleting $size users...")
      this.users.removeAll()
    }
    Logger.info("Clearing disk...")
    FileUtils.deleteDirectory(this.factory.env.uploads)
    Logger.info("Done.")

  }

  /**
    * Fills the application with some dummy data.
    *
    * @param users Amount of users to create
    */
  def seed(users: Int, projects: Int, versions: Int, channels: Int)(implicit ec: ExecutionContext): Unit = {
    if (sys.env.getOrElse(statusZ.SpongeEnv, "unknown") != "local") return
    // Note: Dangerous as hell, handle with care
    Logger.info("---- Seeding Ore ----")

    checkArgument(channels <= Channel.Colors.size,
      "cannot make more channels than there are colors", "")
    this.factory.isPgpEnabled = false

    Logger.info("Resetting Ore")
    this.reset

    // Create some users.
    Logger.info("Seeding...")
    var projectNum = 0
    for (i <- 0 until users) {
      Logger.info(Math.ceil(i / users.asInstanceOf[Float] * 100).asInstanceOf[Int].toString + "%")
      this.users.add(User(id = ObjectId(i), _username = s"User-$i")).map { user =>
        // Create some projects
        for (j <- 0 until projects) {
          val pluginId = s"plugin$projectNum"
          this.projects.add(Project.Builder(this.service)
            .pluginId(pluginId)
            .ownerName(user.name)
            .ownerId(user.id.value)
            .name(s"Project$projectNum")
            .build()) map { project =>
            project.updateSettings(this.service.processor.process(ProjectSettings()))
            // Now create some additional versions for this project
            var versionNum = 0
            for (k <- 0 until channels) {
              this.channels.add(new Channel(s"channel$k", Channel.Colors(k), project.id.value)) map { channel =>
                for (l <- 0 until versions) {
                  this.versions.add(Version(
                    projectId = project.id.value,
                    versionString = versionNum.toString,
                    channelId = channel.id.value,
                    fileSize = 1,
                    hash = "none",
                    _authorId = i,
                    fileName = "none",
                    signatureFileName = "none")) map { version =>
                    if (l == 0)
                      project.setRecommendedVersion(version)
                    versionNum += 1
                  }
                }
              }
            }
            projectNum += 1

          }


        }
      }
    }

    Logger.info("---- Seed complete ----")

  }

  def migrate(): Unit = {
    if (sys.env.getOrElse(statusZ.SpongeEnv, "unknown") != "local") return
    Unit
  }

}
