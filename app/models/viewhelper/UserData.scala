package models.viewhelper

import controllers.routes
import models.user.{Organization, User}
import ore.permission.Permission


// TODO cache this! But keep in mind to invalidate caches when permission changes might occur or other stuff affecting the data in here

// TODO give this to templates with:

// TODO perms in user.toOrga: EditSettings
// TODO perms in user: ViewActivity - ReviewFlags

case class UserData(headerData: HeaderData,
                    user: User,
                    isOrga: Boolean,
                    projectCount: Int,
                    orgas: Seq[(Organization, User)],
                    userPerm: Map[Permission, Boolean],
                    orgaPerm: Map[Permission, Boolean]) {

  def global = headerData

  def hasUser = global.hasUser
  def currentUser = global.currentUser

  def isCurrent = currentUser.contains(user)

  def pgpFormCall = {
    user.pgpPubKey.map { _ =>
      routes.Users.verify(Some(routes.Users.deletePgpPublicKey(user.name, None, None).path()))
    } getOrElse {
      routes.Users.savePgpPublicKey(user.name)
    }
  }

  def pgpFormClass = user.pgpPubKey.map(_ => "pgp-delete").getOrElse("")

}
