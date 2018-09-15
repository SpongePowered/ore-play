name := "ore"
version := "1.6.12"

lazy val `ore` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.6"
routesGenerator := InjectedRoutesGenerator
resolvers ++= Seq(
  "sponge" at "https://repo.spongepowered.org/maven",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
)

sources in (Compile, doc) := Seq.empty
publishArtifact in (Compile, packageDoc) := false

libraryDependencies ++= Seq( ehcache , ws , specs2 % Test , guice )
libraryDependencies ++= Seq(
  "org.spongepowered"     %   "play-discourse"          %   "3.0",
  "org.spongepowered"     %   "plugin-meta"             %   "0.4.1",
  "com.typesafe.play"     %%  "play-slick"              %   "3.0.3",
  "com.typesafe.play"     %%  "play-slick-evolutions"   %   "3.0.3",
  "org.postgresql"        %   "postgresql"              %   "42.2.5",
  "com.github.tminglei"   %%  "slick-pg"                %   "0.16.3",
  "io.sentry"             %   "sentry-logback"          %   "1.7.5",
  "org.bouncycastle"      %   "bcprov-jdk15on"          %   "1.60",
  "org.bouncycastle"      %   "bcpkix-jdk15on"          %   "1.60",
  "org.bouncycastle"      %   "bcpg-jdk15on"            %   "1.60",
  "javax.mail"            %   "mail"                    %   "1.4.7",
  "com.beachape"          %%  "enumeratum"              %   "1.5.13",
  "com.beachape"          %%  "enumeratum-slick"        %   "1.5.15",
  "com.chuusai"           %%  "shapeless"               %   "2.3.3",

  "com.vladsch.flexmark"  % "flexmark"                       %  "0.34.22",
  "com.vladsch.flexmark"  % "flexmark-ext-autolink"          %  "0.34.22",
  "com.vladsch.flexmark"  % "flexmark-ext-anchorlink"        %  "0.34.22",
  "com.vladsch.flexmark"  % "flexmark-ext-gfm-strikethrough" %  "0.34.22",
  "com.vladsch.flexmark"  % "flexmark-ext-gfm-tasklist"      %  "0.34.22",
  "com.vladsch.flexmark"  % "flexmark-ext-tables"            %  "0.34.22",
  "com.vladsch.flexmark"  % "flexmark-ext-typographic"       %  "0.34.22",
  "com.vladsch.flexmark"  % "flexmark-ext-wikilink"          %  "0.34.22",

  "org.webjars.npm"   % "jquery"       % "2.2.4",
  "org.webjars.npm"   % "font-awesome" % "4.7.0",
  "org.webjars.npm"   % "filesize"     % "3.6.1",
  "org.webjars.npm"   % "moment"       % "2.22.2",
  "org.webjars.npm"   % "clipboard"    % "2.0.1",
  "org.webjars.npm"   % "chart.js"     % "2.7.2"
)

unmanagedResourceDirectories in Test +=  (baseDirectory.value / "target/web/public/test")
