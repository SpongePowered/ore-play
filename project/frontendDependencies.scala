import sbt._

//noinspection TypeAnnotation
object NPMDeps {

  val vue                 = "vue"                   -> "2.6.10"
  val vueLoader           = "vue-loader"            -> "15.7.1"
  val vueTemplateCompiler = "vue-template-compiler" -> "2.6.10"
  val vueStyleLoader      = "vue-style-loader"      -> "4.1.2"

  val lodash      = "lodash"       -> "4.17.15"
  val queryString = "query-string" -> "6.8.1"

  val fontAwesome        = "@fortawesome/fontawesome-svg-core"   -> "1.2.19"
  val fontAwesomeSolid   = "@fortawesome/free-solid-svg-icons"   -> "5.9.0"
  val fontAwesomeRegular = "@fortawesome/free-regular-svg-icons" -> "5.9.0"
  val fontAwesomeBrands  = "@fortawesome/free-brands-svg-icons"  -> "5.9.0"

  val babel          = "@babel/core"       -> "7.5.5"
  val babelLoader    = "babel-loader"      -> "8.0.6"
  val babelPresetEnv = "@babel/preset-env" -> "7.5.5"

  val webpackMerge          = "webpack-merge"           -> "4.2.1"
  val webpackTerser         = "terser-webpack-plugin"   -> "1.3.0"
  val webpackCopy           = "copy-webpack-plugin"     -> "5.0.3"
  val webpackBundleAnalyzer = "webpack-bundle-analyzer" -> "3.4.1"

  val cssLoader         = "css-loader"                         -> "3.1.0"
  val sassLoader        = "sass-loader"                        -> "7.1.0"
  val postCssLoader     = "postcss-loader"                     -> "3.0.0"
  val miniCssExtractor  = "mini-css-extract-plugin"            -> "0.8.0"
  val optimizeCssAssets = "optimize-css-assets-webpack-plugin" -> "5.0.3"
  val autoprefixer      = "autoprefixer"                       -> "9.6.1"
  val nodeSass          = "node-sass"                          -> "4.12.0"
}

object WebjarsDeps {

  val jQuery      = "org.webjars.npm" % "jquery"       % "2.2.4"
  val fontAwesome = "org.webjars"     % "font-awesome" % "5.10.1"
  val filesize    = "org.webjars.npm" % "filesize"     % "3.6.1"
  val moment      = "org.webjars.npm" % "moment"       % "2.24.0"
  val clipboard   = "org.webjars.npm" % "clipboard"    % "2.0.4"
  val chartJs     = "org.webjars.npm" % "chart.js"     % "2.8.0"
  val swaggerUI   = "org.webjars"     % "swagger-ui"   % "3.23.8"
}
