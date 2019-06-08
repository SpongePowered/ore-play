package util.uiowrappers

import javax.inject.Inject

import play.api.mvc.Result

import controllers.sugar.Requests
import ore.StatTracker
import ore.db.Model
import ore.models.project.Version

import scalaz.zio.{Task, UIO}

class UIOStatTracker @Inject()(underlying: StatTracker[Task]) extends StatTracker[UIO] {
  override def projectViewed(result: UIO[Result])(implicit request: Requests.ProjectRequest[_]): UIO[Result] =
    underlying.projectViewed(result).orDie

  override def versionDownloaded(version: Model[Version])(result: UIO[Result])(
      implicit request: Requests.ProjectRequest[_]
  ): UIO[Result] = underlying.versionDownloaded(version)(result).orDie
}
