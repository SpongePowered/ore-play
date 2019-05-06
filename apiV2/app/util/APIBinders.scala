package util

import play.api.mvc.QueryStringBindable

import ore.data.project.Category
import ore.models.project.ProjectSortingStrategy
import ore.permission.NamedPermission

object APIBinders {

  implicit val categoryQueryStringBindable: QueryStringBindable[Category] = new QueryStringBindable[Category] {
    override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, Category]] =
      params.get(key).flatMap(_.headOption).map { s =>
        Category.values.find(_.apiName == s).toRight(s"$s is not a valid category")
      }

    override def unbind(key: String, value: Category): String = s"$key=${value.apiName}"
  }

  implicit val namedPermissionQueryStringBindable: QueryStringBindable[NamedPermission] =
    new QueryStringBindable[NamedPermission] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, NamedPermission]] =
        params.get(key).flatMap(_.headOption).map { s =>
          NamedPermission.withNameOption(s).toRight(s"$s is not a valid permission")
        }

      override def unbind(key: String, value: NamedPermission): String = s"$key=${value.entryName}"
    }

  implicit val projectSortingStrategyQueryStringBindable: QueryStringBindable[ProjectSortingStrategy] =
    new QueryStringBindable[ProjectSortingStrategy] {
      override def bind(key: String, params: Map[String, Seq[String]]): Option[Either[String, ProjectSortingStrategy]] =
        params.get(key).flatMap(_.headOption).map { s =>
          ProjectSortingStrategy.values.find(_.apiName == s).toRight(s"$s is not a valid sorting strategy")
        }

      override def unbind(key: String, value: ProjectSortingStrategy): String = s"$key=${value.apiName}"
    }

}
