const webpack = require('webpack');
const VueLoaderPlugin = require('vue-loader/lib/plugin');

const Path = require('path');
const rootDir = Path.resolve(__dirname, '../../../..');
const resourcesDir = Path.resolve(rootDir, 'src', 'main', 'resources');

module.exports = {
    entry: {
        home: Path.resolve(resourcesDir, 'assets', 'main.js'),
        "ore-client-fastopt": Path.resolve(resourcesDir, 'assets', 'dummy.js'),
        "ore-client-opt": Path.resolve(resourcesDir, 'assets', 'dummy.js')
    },
    output: {
        path: __dirname,
        filename: '[name]-library.js',
        publicPath: '/dist/',
        libraryTarget: 'umd'
    },
    devServer: {
        contentBase: [
            Path.resolve(__dirname, 'dev'),
            Path.resolve(resourcesDir, 'assets')
        ],
        watchContentBase: true,
        hot: false,
        hotOnly: false,
        inline: true
    },
    plugins: [
        new VueLoaderPlugin()
    ],
    module: {
        rules: [
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
        ]
    },
    resolve: {
        alias: {
            'vue$': 'vue/dist/vue.esm.js'
        },
        modules: [
            Path.resolve(__dirname, 'node_modules')
        ]
    }
};
