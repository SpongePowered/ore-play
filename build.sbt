name := "ore"
version := "1.6.18"

lazy val `ore` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.6"
scalacOptions ++= Seq(
  "-deprecation",
  "-encoding",
  "utf-8",
  "-Ypartial-unification",
  "-explaintypes",
  "-feature",
  "-unchecked",
  "-Xcheckinit",
  "-Xfatal-warnings",
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
  "-Ywarn-unused:params",
  "-Ywarn-unused:patvars",
  "-Ywarn-unused:privates",
  "-Ywarn-value-discard"
)
addCompilerPlugin("org.spire-math" %% "kind-projector" % "0.9.8")

routesGenerator := InjectedRoutesGenerator
routesImport ++= Seq(
  "db.DbRef",
  "models.admin._",
  "models.project._",
  "models.user._",
  "models.user.role._",
  "ore.user._"
).map(s => s"_root_.$s")

resolvers ++= Seq(
  "sponge".at("https://repo.spongepowered.org/maven"),
  "scalaz-bintray".at("https://dl.bintray.com/scalaz/releases"),
  "Akka Snapshot Repository".at("http://repo.akka.io/snapshots/")
)

lazy val doobieVersion = "0.5.3"

libraryDependencies ++= Seq(ehcache, ws, guice)

lazy val flexmarkVersion     = "0.34.52"
lazy val bouncycastleVersion = "1.60"
lazy val playSlickVersion    = "3.0.3"

libraryDependencies ++= Seq(
  "org.spongepowered"    % "play-discourse"                 % "3.0.2",
  "org.spongepowered"    % "plugin-meta"                    % "0.4.1",
  "com.typesafe.play"    %% "play-slick"                    % playSlickVersion,
  "com.typesafe.play"    %% "play-slick-evolutions"         % playSlickVersion,
  "org.postgresql"       % "postgresql"                     % "42.2.5",
  "com.github.tminglei"  %% "slick-pg"                      % "0.16.3",
  "io.sentry"            % "sentry-logback"                 % "1.7.12",
  "org.bouncycastle"     % "bcprov-jdk15on"                 % bouncycastleVersion,
  "org.bouncycastle"     % "bcpkix-jdk15on"                 % bouncycastleVersion,
  "org.bouncycastle"     % "bcpg-jdk15on"                   % bouncycastleVersion,
  "javax.mail"           % "mail"                           % "1.4.7",
  "com.beachape"         %% "enumeratum"                    % "1.5.13",
  "com.beachape"         %% "enumeratum-slick"              % "1.5.15",
  "com.chuusai"          %% "shapeless"                     % "2.3.3",
  "org.typelevel"        %% "cats-core"                     % "1.4.0",
  "org.tpolecat"         %% "doobie-core"                   % doobieVersion,
  "org.tpolecat"         %% "doobie-postgres"               % doobieVersion,
  "com.vladsch.flexmark" % "flexmark"                       % flexmarkVersion,
  "com.vladsch.flexmark" % "flexmark-ext-autolink"          % flexmarkVersion,
  "com.vladsch.flexmark" % "flexmark-ext-anchorlink"        % flexmarkVersion,
  "com.vladsch.flexmark" % "flexmark-ext-gfm-strikethrough" % flexmarkVersion,
  "com.vladsch.flexmark" % "flexmark-ext-gfm-tasklist"      % flexmarkVersion,
  "com.vladsch.flexmark" % "flexmark-ext-tables"            % flexmarkVersion,
  "com.vladsch.flexmark" % "flexmark-ext-typographic"       % flexmarkVersion,
  "com.vladsch.flexmark" % "flexmark-ext-wikilink"          % flexmarkVersion,
  "org.webjars.npm"      % "jquery"                         % "2.2.4",
  "org.webjars.npm"      % "font-awesome"                   % "4.7.0",
  "org.webjars.npm"      % "filesize"                       % "3.6.1",
  "org.webjars.npm"      % "moment"                         % "2.22.2",
  "org.webjars.npm"      % "clipboard"                      % "2.0.1",
  "org.webjars.npm"      % "chart.js"                       % "2.7.2"
)

libraryDependencies ++= Seq(
  jdbc % Test,
  //specs2 % Test,
  "org.scalatestplus.play" %% "scalatestplus-play" % "3.1.2"       % Test,
  "org.tpolecat"           %% "doobie-scalatest"   % doobieVersion % Test
)

unmanagedResourceDirectories in Test += (baseDirectory.value / "target/web/public/test")
pipelineStages := Seq(digest, gzip)

// Disable generation of the API documentation for production builds
sources in (Compile, doc) := Seq.empty
publishArtifact in (Compile, packageDoc) := false
