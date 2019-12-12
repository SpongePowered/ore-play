import sbt._

//noinspection TypeAnnotation
object NPMDeps {

  val vue                 = "vue"                   -> "2.6.10"
  val vueLoader           = "vue-loader"            -> "15.7.2"
  val vueTemplateCompiler = "vue-template-compiler" -> "2.6.10"
  val vueStyleLoader      = "vue-style-loader"      -> "4.1.2"

  val lodash      = "lodash"       -> "4.17.15"
  val queryString = "query-string" -> "6.9.0"

  val fontAwesome        = "@fortawesome/fontawesome-svg-core"   -> "1.2.26"
  val fontAwesomeSolid   = "@fortawesome/free-solid-svg-icons"   -> "5.12.0"
  val fontAwesomeRegular = "@fortawesome/free-regular-svg-icons" -> "5.12.0"
  val fontAwesomeBrands  = "@fortawesome/free-brands-svg-icons"  -> "5.12.0"

  val babel          = "@babel/core"       -> "7.7.5"
  val babelLoader    = "babel-loader"      -> "8.0.6"
  val babelPresetEnv = "@babel/preset-env" -> "7.7.6"

  val webpack               = "4.41.2"
  val webpackDevServer      = "3.9.0"
  val webpackMerge          = "webpack-merge" -> "4.2.2"
  val webpackTerser         = "terser-webpack-plugin" -> "2.2.3"
  val webpackCopy           = "copy-webpack-plugin" -> "5.1.0"
  val webpackBundleAnalyzer = "webpack-bundle-analyzer" -> "3.6.0"

  val cssLoader         = "css-loader"                         -> "3.3.0"
  val sassLoader        = "sass-loader"                        -> "8.0.0"
  val postCssLoader     = "postcss-loader"                     -> "3.0.0"
  val miniCssExtractor  = "mini-css-extract-plugin"            -> "0.8.0"
  val optimizeCssAssets = "optimize-css-assets-webpack-plugin" -> "5.0.3"
  val autoprefixer      = "autoprefixer"                       -> "9.7.3"
  val nodeSass          = "node-sass"                          -> "4.13.0"
}

object WebjarsDeps {

  val jQuery      = "org.webjars.npm" % "jquery"       % "2.2.4"
  val fontAwesome = "org.webjars"     % "font-awesome" % "5.11.2"
  val filesize    = "org.webjars.npm" % "filesize"     % "6.0.1"
  val moment      = "org.webjars.npm" % "moment"       % "2.24.0"
  val clipboard   = "org.webjars.npm" % "clipboard"    % "2.0.4"
  val chartJs     = "org.webjars.npm" % "chart.js"     % "2.9.3"
  val swaggerUI   = "org.webjars"     % "swagger-ui"   % "3.24.3"
}
