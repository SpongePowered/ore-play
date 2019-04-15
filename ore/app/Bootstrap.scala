import javax.inject.{Inject, Singleton}

import scala.concurrent.ExecutionContext

import db.impl.DbUpdateTask
import discourse.OreDiscourseApi
import ore.OreConfig
import ore.db.ModelService
import ore.project.ProjectTask
import ore.user.UserTask

import com.typesafe.scalalogging

/**
  * Handles initialization logic for the application.
  */
abstract class Bootstrap(
    service: ModelService,
    forums: OreDiscourseApi,
    config: OreConfig,
    projectTask: ProjectTask,
    userTask: UserTask,
    dbUpdateTask: DbUpdateTask,
    ec: ExecutionContext
) {

  private val Logger = scalalogging.Logger("Bootstrap")

  Logger.info("Initializing Ore...")
  val time: Long = System.currentTimeMillis()

  this.forums.start(
    ec,
    service,
    config
  )

  this.projectTask.start()
  this.dbUpdateTask.start()
  this.userTask.start()

  Logger.info(s"Ore Initialized (${System.currentTimeMillis() - time}ms).")

}

@Singleton
class BootstrapImpl @Inject()(
    modelService: ModelService,
    forums: OreDiscourseApi,
    config: OreConfig,
    projectTask: ProjectTask,
    userTask: UserTask,
    dbUpdateTask: DbUpdateTask,
    ec: ExecutionContext
) extends Bootstrap(modelService, forums, config, projectTask, userTask, dbUpdateTask, ec)
