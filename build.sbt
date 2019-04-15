lazy val commonSettings = Seq(
  version := "1.8.2",
  scalaVersion := "2.12.8",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "utf-8",
    "-Ypartial-unification",
    "-explaintypes",
    "-feature",
    "-unchecked",
    "-Xcheckinit",
    //"-Xfatal-warnings",
    "-Xlint:adapted-args",
    "-Xlint:by-name-right-associative",
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
    "-Xlint:unsound-match",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-infer-any",
    "-Ywarn-numeric-widen",
    "-Ywarn-nullary-override",
    "-Ywarn-nullary-unit",
    "-Ywarn-unused:implicits",
    "-Ywarn-unused:locals",
    "-Ywarn-unused:patvars",
    "-Ywarn-unused:privates",
    "-Ywarn-value-discard",
    "-Yrangepos"
  ),
  addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.9"),
  addCompilerPlugin(("org.scalamacros" % "paradise" % "2.1.1").cross(CrossVersion.full)),
  addCompilerPlugin(scalafixSemanticdb("4.1.4")),
  // Disable generation of the API documentation for production builds
  sources in (Compile, doc) := Seq.empty,
  publishArtifact in (Compile, packageDoc) := false
)

lazy val playCommonSettings = Seq(
  routesImport ++= Seq(
    "db.DbRef",
    "models.admin._",
    "models.project._",
    "models.user._",
    "models.user.role._",
    "ore.user._"
  ).map(s => s"_root_.$s"),
  unmanagedResourceDirectories in Test += (baseDirectory.value / "target/web/public/test"),
  pipelineStages := Seq(digest, gzip)
)

lazy val doobieVersion    = "0.6.0"
lazy val flexmarkVersion  = "0.42.0"
lazy val playSlickVersion = "4.0.0"
lazy val slickPgVersion   = "0.17.1"
lazy val circeVersion     = "0.11.1"

lazy val `ore` = project
  .enablePlugins(PlayScala)
  .settings(
    commonSettings,
    playCommonSettings,
    name := "ore",
    resolvers ++= Seq(
      "sponge".at("https://repo.spongepowered.org/maven"),
      "scalaz-bintray".at("https://dl.bintray.com/scalaz/releases"),
      "Akka Snapshot Repository".at("http://repo.akka.io/snapshots/")
    ),
    libraryDependencies ++= Seq(caffeine, ws, guice),
    libraryDependencies ++= Seq(
      "org.spongepowered"          % "play-discourse"                 % "4.0.0",
      "org.spongepowered"          % "plugin-meta"                    % "0.4.1",
      "com.typesafe.play"          %% "play-slick"                    % playSlickVersion,
      "com.typesafe.play"          %% "play-slick-evolutions"         % playSlickVersion,
      "org.postgresql"             % "postgresql"                     % "42.2.5",
      "com.github.tminglei"        %% "slick-pg"                      % slickPgVersion,
      "com.github.tminglei"        %% "slick-pg_play-json"            % slickPgVersion,
      "com.typesafe.scala-logging" %% "scala-logging"                 % "3.9.2",
      "io.sentry"                  % "sentry-logback"                 % "1.7.21",
      "javax.mail"                 % "mail"                           % "1.4.7",
      "com.beachape"               %% "enumeratum"                    % "1.5.13",
      "com.beachape"               %% "enumeratum-slick"              % "1.5.15",
      "com.chuusai"                %% "shapeless"                     % "2.3.3",
      "org.typelevel"              %% "cats-core"                     % "1.6.0",
      "com.github.mpilquist"       %% "simulacrum"                    % "0.15.0",
      "org.tpolecat"               %% "doobie-core"                   % doobieVersion,
      "org.tpolecat"               %% "doobie-postgres"               % doobieVersion,
      "io.circe"                   %% "circe-core"                    % circeVersion,
      "io.circe"                   %% "circe-generic-extras"          % circeVersion,
      "io.circe"                   %% "circe-parser"                  % circeVersion,
      "com.vladsch.flexmark"       % "flexmark"                       % flexmarkVersion,
      "com.vladsch.flexmark"       % "flexmark-ext-autolink"          % flexmarkVersion,
      "com.vladsch.flexmark"       % "flexmark-ext-anchorlink"        % flexmarkVersion,
      "com.vladsch.flexmark"       % "flexmark-ext-gfm-strikethrough" % flexmarkVersion,
      "com.vladsch.flexmark"       % "flexmark-ext-gfm-tasklist"      % flexmarkVersion,
      "com.vladsch.flexmark"       % "flexmark-ext-tables"            % flexmarkVersion,
      "com.vladsch.flexmark"       % "flexmark-ext-typographic"       % flexmarkVersion,
      "com.vladsch.flexmark"       % "flexmark-ext-wikilink"          % flexmarkVersion,
      "org.webjars.npm"            % "jquery"                         % "2.2.4",
      "org.webjars"                % "font-awesome"                   % "5.7.2",
      "org.webjars.npm"            % "filesize"                       % "3.6.1",
      "org.webjars.npm"            % "moment"                         % "2.24.0",
      "org.webjars.npm"            % "clipboard"                      % "2.0.4",
      "org.webjars.npm"            % "chart.js"                       % "2.7.3"
    ),
    libraryDependencies ++= Seq(
      jdbc % Test,
      //specs2 % Test,
      "org.scalatestplus.play" %% "scalatestplus-play" % "4.0.1"       % Test,
      "org.tpolecat"           %% "doobie-scalatest"   % doobieVersion % Test
    )
  )

lazy val oreAll = project.in(file(".")).aggregate(ore)
