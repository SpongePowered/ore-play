ThisBuild / turbo := true
ThisBuild / scalaVersion := Settings.scalaVer

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
    Deps.circeDerivation,
    Deps.circeParser,
    Deps.akkaHttp,
    Deps.akkaHttpCore,
    Deps.akkaStream,
    Deps.akkaTyped,
    Deps.akkaSerializationJackson,
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
      Deps.circeDerivation,
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
      Deps.zioCats,
      Deps.pureConfig
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
      Deps.circeDerivation,
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
    version in startWebpackDevServer := NPMDeps.webpackDevServer,
    version in webpack := NPMDeps.webpack,
    npmDependencies in Compile ++= Seq(
      NPMDeps.vue,
      NPMDeps.lodash,
      NPMDeps.queryString,
      NPMDeps.fontAwesome,
      NPMDeps.fontAwesomeSolid,
      NPMDeps.fontAwesomeRegular,
      NPMDeps.fontAwesomeBrands
    ),
    npmDevDependencies in Compile ++= Seq(
      NPMDeps.webpackMerge,
      NPMDeps.vueLoader,
      NPMDeps.vueTemplateCompiler,
      NPMDeps.cssLoader,
      NPMDeps.vueStyleLoader,
      NPMDeps.babelLoader,
      NPMDeps.babel,
      NPMDeps.babelPresetEnv,
      NPMDeps.webpackTerser,
      NPMDeps.miniCssExtractor,
      NPMDeps.optimizeCssAssets,
      NPMDeps.sassLoader,
      NPMDeps.postCssLoader,
      NPMDeps.autoprefixer,
      NPMDeps.nodeSass,
      NPMDeps.webpackCopy,
      NPMDeps.webpackBundleAnalyzer
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
      Deps.circeDerivation,
      Deps.circeParser,
      Deps.macwire,
      Deps.periscopeAkka,
      Deps.zioZmx
    ),
    libraryDependencies ++= Deps.flexmarkDeps,
    libraryDependencies ++= Seq(
      WebjarsDeps.jQuery,
      WebjarsDeps.fontAwesome,
      WebjarsDeps.filesize,
      WebjarsDeps.moment,
      WebjarsDeps.clipboard,
      WebjarsDeps.chartJs,
      WebjarsDeps.swaggerUI
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
