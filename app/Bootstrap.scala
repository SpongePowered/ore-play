import java.security.Security

import db.ModelService
import db.impl.access.ProjectBase
import discourse.OreDiscourseApi
import javax.inject.{Inject, Singleton}
import ore.OreConfig
import ore.project.ProjectTask
import org.bouncycastle.jce.provider.BouncyCastleProvider

/**
  * Handles initialization logic for the application.
  */
trait Bootstrap {

  val modelService: ModelService
  val forums: OreDiscourseApi
  val config: OreConfig
  val projectTask: ProjectTask

  val Logger = play.api.Logger("Bootstrap")

  Logger.info("Initializing Ore...")
  val time: Long = System.currentTimeMillis()

  this.modelService.start()

  this.forums.projects = ProjectBase.fromService(modelService)
  this.forums.start()

  this.projectTask.start()

  if (this.config.security.get[Boolean]("requirePgp"))
    Security.addProvider(new BouncyCastleProvider)

  Logger.info(s"Ore Initialized (${System.currentTimeMillis() - time}ms).")

}

@Singleton
class BootstrapImpl @Inject()(override val modelService: ModelService,
                              override val forums: OreDiscourseApi,
                              override val config: OreConfig,
                              override val projectTask: ProjectTask) extends Bootstrap
