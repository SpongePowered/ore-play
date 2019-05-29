const Path = require("path");

module.exports = {
    plugins: [
        require(Path.resolve(__dirname, "target/scala-2.12/scalajs-bundler/main/node_modules/autoprefixer"))
    ]
};
