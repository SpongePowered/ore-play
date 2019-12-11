ThisBuild / turbo := true

//ThisBuild / semanticdbEnabled := true
Global / semanticdbVersion := "4.2.3"

lazy val db = project.settings(
  Settings.commonSettings,
  name := "ore-db",
  libraryDependencies ++= Seq(
    Deps.slick,
    Deps.doobie,
    Deps.catsTagless,
    Deps.shapeless
  )
)

lazy val externalCommon = project.settings(
  Settings.commonSettings,
  name := "ore-external",
  libraryDependencies ++= Seq(
    Deps.cats,
    Deps.catsEffect,
    Deps.catsTagless,
    Deps.circe,
    Deps.circeGeneric,
    Deps.circeParser,
    Deps.akkaHttp,
    Deps.akkaHttpCore,
    Deps.akkaStream,
    Deps.scalaLogging,
    Deps.simulacrum
  )
)

lazy val discourse = project
  .dependsOn(externalCommon)
  .settings(
    Settings.commonSettings,
    name := "ore-discourse"
  )

lazy val auth = project
  .dependsOn(externalCommon)
  .settings(
    Settings.commonSettings,
    name := "ore-auth"
  )

lazy val models = project
  .dependsOn(db)
  .settings(
    Settings.commonSettings,
    name := "ore-models",
    libraryDependencies ++= Seq(
      Deps.postgres,
      Deps.slickPg,
      Deps.slickPgCirce,
      Deps.doobiePostgres,
      Deps.doobiePostgresCirce,
      Deps.scalaLogging,
      Deps.enumeratum,
      Deps.enumeratumSlick,
      Deps.cats,
      Deps.simulacrum,
      Deps.circe,
      Deps.circeGeneric,
      Deps.circeParser
    )
  )

lazy val jobs = project
  .enablePlugins(UniversalPlugin, JavaAppPackaging, ExternalizedResourcesMappings)
  .dependsOn(models, discourse)
  .settings(
    Settings.commonSettings,
    name := "ore-jobs",
    libraryDependencies ++= Seq(
      Deps.zio,
      Deps.zioCats,
      Deps.slickHikariCp,
      Deps.scalaLogging,
      Deps.logback,
      Deps.sentry,
      Deps.pureConfig
    )
  )

lazy val orePlayCommon: Project = project
  .enablePlugins(PlayScala)
  .dependsOn(auth, models)
  .settings(
    Settings.commonSettings,
    Settings.playCommonSettings,
    name := "ore-play-common",
    resolvers += "sponge".at("https://repo.spongepowered.org/maven"),
    libraryDependencies ++= Seq(caffeine, ws),
    libraryDependencies ++= Seq(
      Deps.pluginMeta,
      Deps.slickPlay,
      Deps.zio,
      Deps.zioCats
    ),
    aggregateReverseRoutes := Seq(ore)
  )

lazy val apiV2 = project
  .enablePlugins(PlayScala)
  .dependsOn(orePlayCommon)
  .settings(
    Settings.commonSettings,
    Settings.playCommonSettings,
    name := "ore-apiv2",
    routesImport ++= Seq(
      "util.APIBinders._"
    ).map(s => s"_root_.$s"),
    libraryDependencies ++= Seq(
      Deps.scalaLogging,
      Deps.circe,
      Deps.circeGeneric,
      Deps.circeParser,
      Deps.scalaCache,
      Deps.scalaCacheCatsEffect
    ),
    libraryDependencies ++= Deps.playTestDeps
  )

lazy val oreClient = project
  .enablePlugins(ScalaJSBundlerPlugin)
  .settings(
    Settings.commonSettings,
    name := "ore-client",
    useYarn := true,
    scalaJSUseMainModuleInitializer := false,
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.CommonJSModule) },
    webpackConfigFile in fastOptJS := Some(baseDirectory.value / "webpack.config.dev.js"),
    webpackConfigFile in fullOptJS := Some(baseDirectory.value / "webpack.config.prod.js"),
    webpackMonitoredDirectories += baseDirectory.value / "assets",
    includeFilter in webpackMonitoredFiles := "*.vue" || "*.js",
    webpackBundlingMode in fastOptJS := BundlingMode.LibraryOnly(),
    webpackBundlingMode in fullOptJS := BundlingMode.LibraryOnly(),
    version in startWebpackDevServer := "3.7.2",
    version in webpack := "4.36.1",
    npmDependencies in Compile ++= Seq(
      "vue"                                 -> "2.6.10",
      "lodash"                              -> "4.17.15",
      "query-string"                        -> "6.8.1",
      "@fortawesome/fontawesome-svg-core"   -> "1.2.19",
      "@fortawesome/free-solid-svg-icons"   -> "5.9.0",
      "@fortawesome/free-regular-svg-icons" -> "5.9.0",
      "@fortawesome/free-brands-svg-icons"  -> "5.9.0"
    ),
    npmDevDependencies in Compile ++= Seq(
      "webpack-merge"                      -> "4.2.1",
      "vue-loader"                         -> "15.7.1",
      "vue-template-compiler"              -> "2.6.10",
      "css-loader"                         -> "3.1.0",
      "vue-style-loader"                   -> "4.1.2",
      "babel-loader"                       -> "8.0.6",
      "@babel/core"                        -> "7.5.5",
      "@babel/preset-env"                  -> "7.5.5",
      "terser-webpack-plugin"              -> "1.3.0",
      "mini-css-extract-plugin"            -> "0.8.0",
      "optimize-css-assets-webpack-plugin" -> "5.0.3",
      "sass-loader"                        -> "7.1.0",
      "postcss-loader"                     -> "3.0.0",
      "autoprefixer"                       -> "9.6.1",
      "node-sass"                          -> "4.12.0",
      "copy-webpack-plugin"                -> "5.0.3",
      "webpack-bundle-analyzer"            -> "3.4.1"
    )
  )

lazy val ore = project
  .enablePlugins(PlayScala, SwaggerPlugin, WebScalaJSBundlerPlugin)
  .dependsOn(orePlayCommon, apiV2)
  .settings(
    Settings.commonSettings,
    Settings.playCommonSettings,
    name := "ore",
    libraryDependencies ++= Seq(
      Deps.slickPlayEvolutions,
      Deps.scalaLogging,
      Deps.sentry,
      Deps.javaxMail,
      Deps.circe,
      Deps.circeGeneric,
      Deps.circeParser,
      Deps.macwire
    ),
    libraryDependencies ++= Deps.flexmarkDeps,
    libraryDependencies ++= Seq(
      "org.webjars.npm" % "jquery"       % "2.2.4",
      "org.webjars"     % "font-awesome" % "5.10.1",
      "org.webjars.npm" % "filesize"     % "3.6.1",
      "org.webjars.npm" % "moment"       % "2.24.0",
      "org.webjars.npm" % "clipboard"    % "2.0.4",
      "org.webjars.npm" % "chart.js"     % "2.8.0",
      "org.webjars"     % "swagger-ui"   % "3.23.8"
    ),
    libraryDependencies ++= Deps.playTestDeps,
    swaggerRoutesFile := "apiv2.routes",
    swaggerDomainNameSpaces := Seq(
      "models.protocols.APIV2",
      "controllers.apiv2.ApiV2Controller"
    ),
    swaggerNamingStrategy := "snake_case",
    swaggerAPIVersion := "2.0",
    swaggerV3 := true,
    PlayKeys.playMonitoredFiles += baseDirectory.value / "swagger.yml",
    PlayKeys.playMonitoredFiles += baseDirectory.value / "swagger-custom-mappings.yml",
    scalaJSProjects := Seq(oreClient),
    pipelineStages in Assets += scalaJSPipeline,
    WebKeys.exportedMappings in Assets := Seq(),
    PlayKeys.playMonitoredFiles += (oreClient / baseDirectory).value / "assets"
  )

lazy val oreAll =
  project
    .in(file("."))
    .aggregate(db, externalCommon, discourse, auth, models, orePlayCommon, apiV2, ore, oreClient, jobs)
