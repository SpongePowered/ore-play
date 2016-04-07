package plugin

import java.nio.file.{Files, Path}

import com.google.common.base.Preconditions._
import models.project.Project
import org.pegdown.Extensions._
import org.pegdown.{Extensions, PegDownProcessor}
import util.Dirs._

/**
  * Handles management of project documentation pages.
  */
object Pages {

  val FILE_EXTENSION:     String  =   ".md"
  val HOME_PAGE:          String  =   "Home"
  val DEFAULT_HOME_PAGE:  Path    =   MD_DIR.resolve(HOME_PAGE + FILE_EXTENSION)
  val DEFAULT_NEW_PAGE:   Path    =   MD_DIR.resolve("default" + FILE_EXTENSION)
  val FILE_ENCODING:      String  =   "UTF-8"
  val MAX_PAGES:          Int     =   10

  val MD: PegDownProcessor = new PegDownProcessor(ALL & ~ANCHORLINKS)

  /**
    * Represents a documentation Page for a project.
    *
    * @param owner        Project owner
    * @param projectName  Project name
    * @param name         Page name
    */
  case class Page(private val owner: String, private val projectName: String, private var name: String) {

    private val docsDir = getDocsDir(owner, projectName)

    /**
      * Returns the Project owner for this Page.
      *
      * @return Project owner
      */
    def getOwner: String = this.owner

    /**
      * Returns the name of the Project that this Page belongs to.
      *
      * @return Name of Project this Page belongs to
      */
    def getProjectName: String = this.projectName

    /**
      * Returns the name of this Page.
      *
      * @return Name of page
      */
    def getName: String = this.name

    /**
      * Returns the Path to this Page's markdown contents.
      *
      * @return Path to markdown contents
      */
    def getPath: Path = this.docsDir.resolve(this.name + FILE_EXTENSION)

    /**
      * Updates this documentation page to the specified new name and
      * new content.
      *
      * @param newName    New page name
      * @param newContent Page content
      * @return           New page Path
      */
    def update(newName: String, newContent: String) = {
      Files.deleteIfExists(getPath)
      this.name = newName
      Files.createFile(getPath)
      Files.write(getPath, newContent.getBytes(FILE_ENCODING))
    }

    /**
      * Sets the markdown content of this page.
      *
      * @param newContent Content to set
      */
    def setContent(newContent: String): Unit = {
      Files.deleteIfExists(getPath)
      Files.createFile(getPath)
      Files.write(getPath, newContent.getBytes(FILE_ENCODING))
    }

    /**
      * Returns this page's markdown contents.
      *
      * @return Page contents
      */
    def getContents: String = {
      new String(Files.readAllBytes(getPath))
    }

    /**
      * Converts this Page's markdown contents to HTML.
      *
      * @return HTML representation of markdown contents
      */
    def toHtml: String = MD.markdownToHtml(getContents)

  }

  /**
    * Returns the specified project's documentation directory.
    *
    * @param owner        Owner name
    * @param projectName  Project name
    * @return             Documentation directory
    */
  def getDocsDir(owner: String, projectName: String): Path = {
    DOCS_DIR.resolve(owner).resolve(projectName)
  }

  /**
    * Returns or creates, if not exists, the page with the specified owner,
    * project name, and page name.
    *
    * @param project      Project page is being added to
    * @param name         Page name
    * @return             Existing or new page
    */
  def getOrCreate(project: Project, name: String): Page = {
    val page = Page(project.owner, project.getName, name)
    val path = page.getPath
    if (notExists(project.owner, project.getName, name)) {
      checkArgument(getAll(project.owner, project.getName).length < MAX_PAGES, "no more pages allowed", "")
      Files.createDirectories(path.getParent)
      Files.createFile(path)
      page.setContent(fillTemplate(page.getName))
    }
    page
  }

  /**
    * Returns the page with the specified owner, project name, and page name,
    * or None if the page does not exist.
    *
    * @param owner        Project owner
    * @param projectName  Project name
    * @param name         Page name
    * @return             Page if exists, None otherwise
    */
  def get(owner: String, projectName: String, name: String): Option[Page] = {
    val page = Page(owner, projectName, name)
    if (notExists(owner, projectName, name)) {
      None
    } else {
      Some(page)
    }
  }

  /**
    * Returns the home page for the specified Project.
    *
    * @param owner        Project owner
    * @param projectName  Project name
    * @return             Home page
    */
  def getHome(owner: String, projectName: String): Page = get(owner, projectName, HOME_PAGE).get

  /**
    * Returns all of the Pages for the specified Project.
    *
    * @param owner        Project owner
    * @param projectName  Project name
    * @return             All pages for project
    */
  def getAll(owner: String, projectName: String): Array[Page] = {
    for (file <- getDocsDir(owner, projectName).toFile.listFiles)
      yield Page(owner, projectName, file.getName.substring(0, file.getName.lastIndexOf('.')))
  }

  /**
    * Returns true if the specified page for the specified project exists,
    * false otherwise.
    *
    * @param owner        Project owner
    * @param projectName  Project name
    * @param page         Page name
    * @return             True if exists, false otherwise
    */
  def exists(owner: String, projectName: String, page: String): Boolean = {
    Files.exists(getDocsDir(owner, projectName).resolve(page + FILE_EXTENSION))
  }

  /**
    * Returns true if the specified page for the specified project does not
    * exists.
    *
    * @param owner        Project owner
    * @param projectName  Project name
    * @param page         Page name
    * @return             True if does not exists, false otherwise
    */
  def notExists(owner: String, projectName: String, page: String): Boolean = {
    Files.notExists(getDocsDir(owner, projectName).resolve(page + FILE_EXTENSION))
  }

  /**
    * Deletes the specified page for the specified project if it exists.
    *
    * @param owner        Project owner
    * @param projectName  Project name
    * @param page         Page name
    * @return             True if a Page was deleted, false if it didn't exists
    */
  def delete(owner: String, projectName: String, page: String) = {
    checkArgument(!page.equals(HOME_PAGE), "cannot delete homepage", "")
    Files.deleteIfExists(getDocsDir(owner, projectName).resolve(page + FILE_EXTENSION))
  }

  protected[plugin] def createHomePage(owner: String, projectName: String): Page = {
    val path = getDocsDir(owner, projectName).resolve(DEFAULT_HOME_PAGE.getFileName)
    if (Files.notExists(path.getParent)) {
      Files.createDirectories(path.getParent)
    }
    Files.copy(DEFAULT_HOME_PAGE, path)
    get(owner, projectName, HOME_PAGE).get
  }

  private def fillTemplate(title: String): String = {
    new String(Files.readAllBytes(CONF_DIR.resolve("markdown/default.md"))).format(title)
  }

}
