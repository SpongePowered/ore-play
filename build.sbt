name := "ore"

version := "1.5.10"

lazy val `ore` = (project in file(".")).enablePlugins(PlayScala)

scalaVersion := "2.11.7"

libraryDependencies ++= Seq(cache, ws, filters, specs2 % Test)

unmanagedResourceDirectories in Test <+=  baseDirectory (_ /"target/web/public/test")

resolvers += "scalaz-bintray" at "https://dl.bintray.com/scalaz/releases"

routesGenerator := InjectedRoutesGenerator

// Additional dependencies
resolvers ++= Seq(
  "sponge" at "https://repo.spongepowered.org/maven"
)

libraryDependencies ++= Seq(
  "org.spongepowered"     %   "play-discourse"          %   "1.1.0-SNAPSHOT",
  "org.spongepowered"     %   "plugin-meta"             %   "0.4.1",
  "com.typesafe.play"     %%  "play-slick"              %   "2.1.0",
  "com.typesafe.play"     %%  "play-slick-evolutions"   %   "2.1.0",
  "org.postgresql"        %   "postgresql"              %   "42.2.1",
  "com.github.tminglei"   %%  "slick-pg"                %   "0.15.5",
  "io.sentry"             %   "sentry-logback"          %   "1.7.0",
  "org.bouncycastle"      %   "bcprov-jdk15on"          %   "1.59",
  "org.bouncycastle"      %   "bcpkix-jdk15on"          %   "1.59",
  "org.bouncycastle"      %   "bcpg-jdk15on"            %   "1.59",
  "javax.mail"            %   "mail"                    %   "1.4.7",

  "com.vladsch.flexmark"  % "flexmark"                       %  "0.32.4",
  "com.vladsch.flexmark"  % "flexmark-ext-autolink"          %  "0.32.4",
  "com.vladsch.flexmark"  % "flexmark-ext-anchorlink"        %  "0.32.4",
  "com.vladsch.flexmark"  % "flexmark-ext-gfm-strikethrough" %  "0.32.4",
  "com.vladsch.flexmark"  % "flexmark-ext-gfm-tasklist"      %  "0.32.4",
  "com.vladsch.flexmark"  % "flexmark-ext-tables"            %  "0.32.4",
  "com.vladsch.flexmark"  % "flexmark-ext-typographic"       %  "0.32.4",
  "com.vladsch.flexmark"  % "flexmark-ext-wikilink"          %  "0.32.4",

  "org.webjars"       % "jquery"       % "2.2.4",
  "org.webjars"       % "font-awesome" % "4.7.0",
  "org.webjars.bower" % "filesize"     % "3.5.6",
  "org.webjars.bower" % "momentjs"     % "2.20.1"
)
