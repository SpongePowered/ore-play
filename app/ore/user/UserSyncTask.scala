package ore.user

import akka.actor.ActorSystem
import db.ModelService
import db.impl.access.UserBase
import javax.inject.{Inject, Singleton}
import ore.OreConfig

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

/**
  * Task that is responsible for keeping Ore users synchronized with external
  * site data.
  *
  * @param models ModelService instance
  */
@Singleton
final class UserSyncTask @Inject()(models: ModelService, actorSystem: ActorSystem, config: OreConfig)(implicit ec: ExecutionContext) extends Runnable {

  val Logger = play.api.Logger("UserSync")
  val interval = this.config.users.get[Long]("syncRate").millis

  /**
    * Starts the task.
    */
  def start() = {
    this.actorSystem.scheduler.schedule(this.interval, this.interval, this)
    Logger.info(s"Initialized. First run in ${this.interval.toString}.")
  }

  /**
    * Synchronizes all users with external site data.
    */
  def run() = {
    this.models.getModelBase(classOf[UserBase]).all.map { users =>
      Logger.info(s"Synchronizing ${users.size} users with external site data...")
      Future.sequence(users.map { user =>
        user.pullForumData()
        user.pullSpongeData()
      }).map { _ =>
        Logger.info("Done")
      }
    }

  }

}
