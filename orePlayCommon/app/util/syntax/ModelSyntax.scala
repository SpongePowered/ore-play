package util.syntax

import scala.language.{higherKinds, implicitConversions}

import java.nio.file.Path

import play.api.i18n.Lang
import play.api.mvc.RequestHeader
import play.twirl.api.Html

import ore.OreConfig
import ore.auth.AuthUser
import ore.db.access.ModelView
import ore.db.impl.OrePostgresDriver.api._
import ore.db.{DbRef, Model, ModelService, ObjId}
import ore.markdown.MarkdownRenderer
import ore.models.organization.Organization
import ore.models.project.io.ProjectFiles
import ore.models.project.{Page, Project}
import ore.models.user.User
import ore.permission.role.Role

import cats.data.OptionT
import cats.syntax.all._
import cats.{Functor, Monad}

trait ModelSyntax {

  implicit def userSyntax(u: User): ModelSyntax.UserSyntax                = new ModelSyntax.UserSyntax(u)
  implicit def userModelRawSyntax(u: Model[User]): ModelSyntax.UserSyntax = new ModelSyntax.UserSyntax(u)
  implicit def userObjSyntax(u: User.type): ModelSyntax.UserObjSyntax     = new ModelSyntax.UserObjSyntax(u)
  implicit def pageSyntax(p: Page): ModelSyntax.PageSyntax                = new ModelSyntax.PageSyntax(p)
  implicit def pageModelRawSyntax(p: Model[Page]): ModelSyntax.PageSyntax = new ModelSyntax.PageSyntax(p)
  implicit def pageObjSyntax(p: Page.type): ModelSyntax.PageObjSyntax     = new ModelSyntax.PageObjSyntax(p)
  implicit def projectSyntax(p: Project): ModelSyntax.ProjectSyntax       = new ModelSyntax.ProjectSyntax(p)
  implicit def projectModelSyntax(p: Model[Project]): ModelSyntax.ProjectModelSyntax =
    new ModelSyntax.ProjectModelSyntax(p)
  implicit def orgSyntax(o: Organization): ModelSyntax.OrganizationSyntax = new ModelSyntax.OrganizationSyntax(o)
  implicit def orgModelRawSyntax(o: Model[Organization]): ModelSyntax.OrganizationSyntax =
    new ModelSyntax.OrganizationSyntax(o)
  implicit def authUserSyntax(u: AuthUser): ModelSyntax.AuthUserSyntax = new ModelSyntax.AuthUserSyntax(u)
}
object ModelSyntax extends ModelSyntax {

  class UserSyntax(private val u: User) extends AnyVal {

    def avatarUrl(implicit config: OreConfig): String = User.avatarUrl(u.name)

    /**
      * Returns this user's current language, or the default language if none
      * was configured.
      */
    implicit def langOrDefault: Lang = u.lang.fold(Lang.defaultLang)(Lang.apply)
  }

  class UserObjSyntax(private val u: User.type) extends AnyVal {

    def avatarUrl(name: String)(implicit config: OreConfig): String =
      if (name == "Spongie") config.sponge.logo
      else config.auth.api.avatarUrl.format(name)
  }

  class PageSyntax(private val p: Page) extends AnyVal {

    /**
      * Returns the HTML representation of this Page.
      *
      * @return HTML representation
      */
    def html(project: Option[Project])(implicit renderer: MarkdownRenderer): Html = {
      val settings = MarkdownRenderer.RenderSettings(
        linkEscapeChars = Some(" +<>"),
        linkPrefix = project.map(p => s"/${p.ownerName}/${p.slug}/pages/")
      )
      renderer.render(p.contents, settings)
    }

    /**
      * Returns true if this is the home page.
      *
      * @return True if home page
      */
    def isHome(implicit config: OreConfig): Boolean = p.name.equals(Page.homeName) && p.parentId.isEmpty
  }

  class PageObjSyntax(private val p: Page.type) extends AnyVal {

    /**
      * The name of each Project's homepage.
      */
    def homeName(implicit config: OreConfig): String = config.ore.pages.homeName

    /**
      * The template body for the Home page.
      */
    def homeMessage(implicit config: OreConfig): String = config.ore.pages.homeMessage

    /**
      * The minimum amount of characters a page may have.
      */
    def minLength(implicit config: OreConfig): Int = config.ore.pages.minLen

    /**
      * The maximum amount of characters the home page may have.
      */
    def maxLength(implicit config: OreConfig): Int = config.ore.pages.maxLen

    /**
      * The maximum amount of characters a page may have.
      */
    def maxLengthPage(implicit config: OreConfig): Int = config.ore.pages.pageMaxLen
  }

  //TODO: Remove these from views
  class ProjectSyntax(private val p: Project) extends AnyVal {

    def iconUrlOrPath[F[_]](
        implicit projectFiles: ProjectFiles[F],
        F: Functor[F],
        config: OreConfig
    ): F[Either[String, Path]] =
      projectFiles.getIconPath(p).map(_.toRight(User.avatarUrl(p.ownerName)))

    def hasIcon[F[_]](implicit projectFiles: ProjectFiles[F], F: Functor[F]): F[Boolean] =
      projectFiles.getIconPath(p).map(_.isDefined)

    def iconUrl[F[_]](
        implicit projectFiles: ProjectFiles[F],
        F: Functor[F],
        header: RequestHeader,
        config: OreConfig
    ): F[String] =
      iconUrlOrPath.map(
        _.swap.getOrElse(controllers.project.routes.Projects.showIcon(p.ownerName, p.slug).absoluteURL())
      )
  }

  class ProjectModelSyntax(private val p: Model[Project]) extends AnyVal {

    private def getOrInsert[F[_]](name: String, parentId: Option[DbRef[Page]])(
        page: Page
    )(implicit service: ModelService[F], F: Monad[F]): F[Model[Page]] = {
      def like =
        ModelView.now(Page).find { page =>
          page.projectId === p.id.value && page.name.toLowerCase === name.toLowerCase && parentId.fold(
            page.parentId.isEmpty
          )(parentId => (page.parentId === parentId).getOrElse(false: Rep[Boolean]))
        }

      like.value.flatMap {
        case Some(u) => F.pure(u)
        case None    => service.insert(page)
      }
    }

    /**
      * Returns this Project's home page.
      *
      * @return Project home page
      */
    def homePage[F[_]](implicit service: ModelService[F], F: Monad[F], config: OreConfig): F[Model[Page]] = {
      val page = Page(p.id, Page.homeName, Page.template(p.name, Page.homeMessage), isDeletable = false, None)
      getOrInsert(Page.homeName, None)(page)
    }

    /**
      * Returns the specified Page or creates it if it doesn't exist.
      *
      * @param name   Page name
      * @return       Page with name or new name if it doesn't exist
      */
    def getOrCreatePage[F[_]](
        name: String,
        parentId: Option[DbRef[Page]],
        content: Option[String] = None
    )(implicit config: OreConfig, service: ModelService[F], F: Monad[F]): F[Model[Page]] = {
      val c = content match {
        case None => Page.template(name, Page.homeMessage)
        case Some(text) =>
          require(text.length <= Page.maxLengthPage, "contents too long")
          text
      }
      val page = Page(p.id, name, c, isDeletable = true, parentId)
      getOrInsert(name, parentId)(page)
    }
  }

  class OrganizationSyntax(private val o: Organization) extends AnyVal {

    /**
      * Returns this Organization as a [[User]].
      *
      * @return This Organization as a User
      */
    def toUser[F[_]](implicit service: ModelService[F]): OptionT[F, Model[User]] =
      ModelView.now(User).get(o.userId)
  }

  class AuthUserSyntax(private val u: AuthUser) extends AnyVal {
    def newGlobalRoles: Option[List[Role]] = u.addGroups.map { groups =>
      if (groups.trim.isEmpty) Nil
      else groups.split(",").flatMap(Role.withValueOpt).toList
    }

    def toUser: User = User(
      id = ObjId(u.id),
      fullName = None,
      name = u.username,
      email = Some(u.email),
      lang = u.lang,
      tagline = None,
      joinDate = None,
      readPrompts = Nil
    )
  }
}
