const webpack = require('webpack');
const VueLoaderPlugin = require('vue-loader/lib/plugin');

module.exports = require('./scalajs.webpack.config');

const Path = require('path');
const rootDir = Path.resolve(__dirname, '../../../..');
module.exports.devServer = {
    contentBase: [
        Path.resolve(__dirname, 'dev'), // fastOptJS output
        Path.resolve(rootDir, 'assets') // project root containing index.html
    ],
    watchContentBase: true,
    hot: false,
    hotOnly: false, // only reload when build is successful
    inline: true // live reloading
};
module.exports.entry = Path.resolve(rootDir, 'assets', 'main.js');
module.exports.plugins = [
    // make sure to include the plugin!
    new VueLoaderPlugin()
];
module.exports.module.rules = [
    {
        test: /\.vue$/,
        loader: 'vue-loader'
    },
    // this will apply to both plain `.js` files
    // AND `<script>` blocks in `.vue` files
    {
        test: /\.js$/,
        loader: 'babel-loader'
    },
    // this will apply to both plain `.css` files
    // AND `<style>` blocks in `.vue` files
    {
        test: /\.css$/,
        use: [
            'vue-style-loader',
            'css-loader'
        ]
    }
];
module.exports.resolve = {
    alias: {
        'vue$': 'vue/dist/vue.esm.js'
    }
};
module.exports.output.libraryTarget = 'umd';
