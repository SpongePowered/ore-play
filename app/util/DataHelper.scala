package util

import javax.inject.Inject

import com.google.common.base.Preconditions.checkArgument
import db.ModelService
import db.access.ModelAccess
import db.impl.access.{ProjectBase, UserBase}
import discourse.OreDiscourseApi
import models.project.{Channel, Project, ProjectSettings, Version}
import models.user.User
import ore.OreConfig
import ore.project.factory.ProjectFactory
import play.api.cache.SyncCacheApi

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
  def reset(): Unit = {
    if (sys.env.getOrElse(statusZ.SpongeEnv, "unknown") != "local") return
    Logger.info("Resetting Ore...")
    val projects = this.projects.all
    Logger.info(s"Deleting ${projects.size} projects...")
    for (project <- this.projects.all) this.projects.delete(project)
    Logger.info(s"Deleting ${this.users.size} users...")
    this.users.removeAll()
    Logger.info("Clearing disk...")
    FileUtils.deleteDirectory(this.factory.env.uploads)
    Logger.info("Done.")

  }

  /**
    * Fills the application with some dummy data.
    *
    * @param users Amount of users to create
    */
  def seed(users: Int, projects: Int, versions: Int, channels: Int): Unit = {
    if (sys.env.getOrElse(statusZ.SpongeEnv, "unknown") != "local") return
    // Note: Dangerous as hell, handle with care
    Logger.info("---- Seeding Ore ----")

    checkArgument(channels <= Channel.Colors.size,
      "cannot make more channels than there are colors", "")
    this.factory.isPgpEnabled = false

    Logger.info("Resetting Ore")
    this.reset()

    // Create some users.
    Logger.info("Seeding...")
    var projectNum = 0
    for (i <- 0 until users) {
      Logger.info(Math.ceil(i / users.asInstanceOf[Float] * 100).asInstanceOf[Int].toString + "%")
      val user = this.users.add(User(id = Some(i), _username = s"User-$i"))
      // Create some projects
      for (j <- 0 until projects) {
        val pluginId = s"plugin$projectNum"
        val project = this.projects.add(Project.Builder(this.service)
          .pluginId(pluginId)
          .ownerName(user.name)
          .ownerId(user.id.get)
          .name(s"Project$projectNum")
          .build())
        project.settings = this.service.processor.process(ProjectSettings())
        // Now create some additional versions for this project
        var versionNum = 0
        for (k <- 0 until channels) {
          val channel = this.channels.add(new Channel(s"channel$k", Channel.Colors(k), project.id.get))
          for (l <- 0 until versions) {
            val version = this.versions.add(Version(
              projectId = project.id.get,
              versionString = versionNum.toString,
              channelId = channel.id.get,
              fileSize = 1,
              hash = "none",
              _authorId = i,
              fileName = "none",
              signatureFileName = "none"))
            if (l == 0)
              project.recommendedVersion = version
            versionNum += 1
          }
        }
        projectNum += 1
      }
    }

    Logger.info("---- Seed complete ----")

  }

  def migrate(): Unit = {
    if (sys.env.getOrElse(statusZ.SpongeEnv, "unknown") != "local") return
    Unit
  }

}
