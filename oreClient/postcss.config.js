const Path = require("path");

// This is very stupid, yes.
function getPath() {
    const arr = __dirname.split("/");
    if(arr[arr.length - 1] === "main" && arr[arr.length -2] === "scalajs-bundler") {
        return Path.resolve(__dirname, "node_modules/autoprefixer");
    } else {
        return Path.resolve(__dirname, "target/scala-2.12/scalajs-bundler/main/node_modules/autoprefixer");
    }
}

module.exports = {
    plugins: [
        require(getPath())
    ]
};
