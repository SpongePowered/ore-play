const webpack = require('webpack');
const VueLoaderPlugin = require('vue-loader/lib/plugin');

const Path = require('path');
const rootDir = Path.resolve(__dirname, '../../../..');

module.exports = {
    entry: {
        home: Path.resolve(rootDir, 'assets', 'main.js'),
        "ore-client-fastopt": Path.resolve(rootDir, 'assets', 'dummy.js'),
        "ore-client-opt": Path.resolve(rootDir, 'assets', 'dummy.js')
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
            Path.resolve(rootDir, 'assets')
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
