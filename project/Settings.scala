import play.sbt.routes.RoutesKeys._

import com.typesafe.sbt.digest.Import._
import com.typesafe.sbt.gzip.Import._
import com.typesafe.sbt.web.Import._
import sbt.Keys._
import sbt._

object Settings {

  val scalaVer = "2.13.2"

  val commonSettings = Seq(
    version := "2.0.0-M2.1",
    scalaVersion := scalaVer,
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "utf-8",
      "-explaintypes",
      "-feature",
      "-unchecked",
      "-Xcheckinit",
      //"-Werror",
      "-Xlint:adapted-args",
      "-Xlint:constant",
      "-Xlint:delayedinit-select",
      "-Xlint:doc-detached",
      "-Xlint:inaccessible",
      "-Xlint:infer-any",
      "-Xlint:missing-interpolator",
      "-Xlint:nullary-override",
      "-Xlint:nullary-unit",
      "-Xlint:option-implicit",
      "-Xlint:package-object-classes",
      "-Xlint:poly-implicit-overload",
      "-Xlint:private-shadow",
      "-Xlint:stars-align",
      "-Xlint:type-parameter-shadow",
      "-Xlint:infer-any",
      "-Wdead-code",
      "-Wnumeric-widen",
      "-Wunused:params",
      "-Wunused:locals",
      "-Wunused:patvars",
      "-Wunused:privates",
      "-Wvalue-discard",
      "-Yrangepos",
      "-Ymacro-annotations",
      "-Ybackend-parallelism",
      "6"
    ),
    addCompilerPlugin(("org.typelevel" %% "kind-projector" % "0.11.0").cross(CrossVersion.full)),
    // Disable generation of the API documentation for production builds
    sources in (Compile, doc) := Seq.empty,
    publishArtifact in (Compile, packageDoc) := false
  )

  lazy val playCommonSettings = Seq(
    routesImport ++= Seq(
      "ore.db.DbRef",
      "ore.models.admin._",
      "ore.models.project._",
      "ore.models.user._",
      "ore.models.user.role._",
      "ore.permission.NamedPermission",
      "ore.data.project.Category"
    ).map(s => s"_root_.$s"),
    unmanagedResourceDirectories in Test += (baseDirectory.value / "target/web/public/test"),
    pipelineStages := Seq(digest, gzip)
  )
}
