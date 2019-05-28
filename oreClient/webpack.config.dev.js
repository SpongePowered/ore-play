const webpack = require('webpack');
const VueLoaderPlugin = require('vue-loader/lib/plugin');

module.exports = require('./scalajs.webpack.config');

const Path = require('path');
const rootDir = Path.resolve(__dirname, '../../../..');
module.exports.entry = Path.resolve(rootDir, 'assets', 'main.js');
module.exports.plugins = [
    new VueLoaderPlugin()
];
module.exports.module.rules = [
    {
        test: /\.vue$/,
        loader: 'vue-loader'
    },
    {
        test: /\.js$/,
        loader: 'babel-loader'
    },
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
