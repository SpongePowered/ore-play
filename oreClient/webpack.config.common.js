const webpack = require('webpack');
const VueLoaderPlugin = require('vue-loader/lib/plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');

const Path = require('path');
const rootDir = Path.resolve(__dirname, '../../../..');
const resourcesDir = Path.resolve(rootDir, 'src', 'main', 'resources');

module.exports = {
    entry: {
        home: Path.resolve(resourcesDir, 'assets', 'home.js'),
        "ore-client-fastopt": Path.resolve(resourcesDir, 'assets', 'dummy.js'),
        "ore-client-opt": Path.resolve(resourcesDir, 'assets', 'dummy.js')
    },
    output: {
        path: Path.resolve(__dirname, "vue"),
        filename: '[name].js',
        publicPath: '/dist/',
        libraryTarget: 'umd'
    },
    plugins: [
        new VueLoaderPlugin(),
        new MiniCssExtractPlugin({
            filename: '[name].css',
            chunkFilename: '[id].css',
        })
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
                use: [MiniCssExtractPlugin.loader, 'css-loader', 'postcss-loader'],
            },
            {
                test: /\.scss$/,
                use: [MiniCssExtractPlugin.loader, 'css-loader', 'postcss-loader', 'sass-loader'],
            },
        ]
    },
    resolve: {
        extensions: ['*', '.js', '.vue'],
        alias: {
            'vue$': 'vue/dist/vue.esm.js'
        },
        modules: [
            Path.resolve(__dirname, 'node_modules')
        ]
    },
    optimization: {
        splitChunks: {
            cacheGroups: {
                vendors: {
                    test: /[\\/]node_modules[\\/]/,
                    name: 'commons',
                    chunks: 'all'
                },
            }
        },
    }
};
