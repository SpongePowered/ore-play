name := "ore"
version := "1.5.23"

lazy val `ore` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.12.5"
routesGenerator := InjectedRoutesGenerator
resolvers ++= Seq(
  "sponge" at "https://repo.spongepowered.org/maven",
  "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases",
  "Akka Snapshot Repository" at "http://repo.akka.io/snapshots/"
)

libraryDependencies ++= Seq( ehcache , ws , specs2 % Test , guice )
libraryDependencies ++= Seq(
  "org.spongepowered"     %   "play-discourse"          %   "2.0.0-SNAPSHOT",
  "org.spongepowered"     %   "plugin-meta"             %   "0.4.1",
  "com.typesafe.play"     %%  "play-slick"              %   "3.0.3",
  "com.typesafe.play"     %%  "play-slick-evolutions"   %   "3.0.3",
  "org.postgresql"        %   "postgresql"              %   "42.2.2",
  "com.github.tminglei"   %%  "slick-pg"                %   "0.16.2",
  "io.sentry"             %   "sentry-logback"          %   "1.7.5",
  "org.bouncycastle"      %   "bcprov-jdk15on"          %   "1.59",
  "org.bouncycastle"      %   "bcpkix-jdk15on"          %   "1.59",
  "org.bouncycastle"      %   "bcpg-jdk15on"            %   "1.59",
  "javax.mail"            %   "mail"                    %   "1.4.7",
  "com.beachape"          %%  "enumeratum"              %   "1.5.13",

  "com.vladsch.flexmark"  % "flexmark"                       %  "0.34.0",
  "com.vladsch.flexmark"  % "flexmark-ext-autolink"          %  "0.34.0",
  "com.vladsch.flexmark"  % "flexmark-ext-anchorlink"        %  "0.34.0",
  "com.vladsch.flexmark"  % "flexmark-ext-gfm-strikethrough" %  "0.34.0",
  "com.vladsch.flexmark"  % "flexmark-ext-gfm-tasklist"      %  "0.34.0",
  "com.vladsch.flexmark"  % "flexmark-ext-tables"            %  "0.34.0",
  "com.vladsch.flexmark"  % "flexmark-ext-typographic"       %  "0.34.0",
  "com.vladsch.flexmark"  % "flexmark-ext-wikilink"          %  "0.34.0",

  "org.webjars"       % "jquery"       % "2.2.4",
  "org.webjars"       % "font-awesome" % "4.7.0",
  "org.webjars.bower" % "filesize"     % "3.5.6",
  "org.webjars.bower" % "momentjs"     % "2.22.2"
)

unmanagedResourceDirectories in Test +=  (baseDirectory.value / "target/web/public/test")
