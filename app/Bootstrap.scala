import java.security.Security

import db.ModelService
import db.impl.access.ProjectBase
import discourse.OreDiscourseApi
import javax.inject.{Inject, Singleton}
import ore.OreConfig
import ore.project.ProjectTask
import ore.user.UserSyncTask
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
  * Handles initialization logic for the application.
  */
trait Bootstrap {

  val modelService: ModelService
  val forums: OreDiscourseApi
  val config: OreConfig
  val userSync: UserSyncTask
  val projectTask: ProjectTask

  val Logger = play.api.Logger("Bootstrap")

  Logger.info("Initializing Ore...")
  val time: Long = System.currentTimeMillis()

  this.modelService.start()

  this.forums.projects = this.modelService.getModelBase(classOf[ProjectBase])
  this.forums.start()

  this.userSync.start()

  this.projectTask.start()

  if (this.config.security.get[Boolean]("requirePgp"))
    Security.addProvider(new BouncyCastleProvider)

  Logger.info(s"Ore Initialized (${System.currentTimeMillis() - time}ms).")

}

@Singleton
class BootstrapImpl @Inject()(override val modelService: ModelService,
                              override val forums: OreDiscourseApi,
                              override val config: OreConfig,
                              override val userSync: UserSyncTask,
                              override val projectTask: ProjectTask) extends Bootstrap
