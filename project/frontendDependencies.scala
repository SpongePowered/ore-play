import sbt._

//noinspection TypeAnnotation
object NPMDeps {

  val vue                 = "vue"                   -> "2.6.11"
  val vueLoader           = "vue-loader"            -> "15.9.0"
  val vueTemplateCompiler = "vue-template-compiler" -> "2.6.11"
  val vueStyleLoader      = "vue-style-loader"      -> "4.1.2"

  val lodash      = "lodash"       -> "4.17.15"
  val queryString = "query-string" -> "6.11.1"

  val fontAwesome        = "@fortawesome/fontawesome-svg-core"   -> "1.2.27"
  val fontAwesomeSolid   = "@fortawesome/free-solid-svg-icons"   -> "5.12.1"
  val fontAwesomeRegular = "@fortawesome/free-regular-svg-icons" -> "5.12.1"
  val fontAwesomeBrands  = "@fortawesome/free-brands-svg-icons"  -> "5.12.1"

  val babel          = "@babel/core"       -> "7.9.0"
  val babelLoader    = "babel-loader"      -> "8.1.0"
  val babelPresetEnv = "@babel/preset-env" -> "7.9.5"

  val webpack               = "4.42.0"
  val webpackDevServer      = "3.10.3"
  val webpackMerge          = "webpack-merge" -> "4.2.2"
  val webpackTerser         = "terser-webpack-plugin" -> "2.3.5"
  val webpackCopy           = "copy-webpack-plugin" -> "5.1.1"
  val webpackBundleAnalyzer = "webpack-bundle-analyzer" -> "3.6.1"

  val cssLoader         = "css-loader"                         -> "3.4.2"
  val sassLoader        = "sass-loader"                        -> "8.0.2"
  val postCssLoader     = "postcss-loader"                     -> "3.0.0"
  val miniCssExtractor  = "mini-css-extract-plugin"            -> "0.9.0"
  val optimizeCssAssets = "optimize-css-assets-webpack-plugin" -> "5.0.3"
  val autoprefixer      = "autoprefixer"                       -> "9.7.4"
  val nodeSass          = "node-sass"                          -> "4.14.1"
}

object WebjarsDeps {

  val jQuery      = "org.webjars.npm" % "jquery"       % "2.2.4"
  val fontAwesome = "org.webjars"     % "font-awesome" % "5.12.0"
  val filesize    = "org.webjars.npm" % "filesize"     % "6.0.1"
  val moment      = "org.webjars.npm" % "moment"       % "2.24.0"
  val clipboard   = "org.webjars.npm" % "clipboard"    % "2.0.6"
  val chartJs     = "org.webjars.npm" % "chart.js"     % "2.9.3"
  val swaggerUI   = "org.webjars"     % "swagger-ui"   % "3.25.0"
}
