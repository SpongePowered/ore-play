const VueLoaderPlugin = require('vue-loader/lib/plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const CopyPlugin = require('copy-webpack-plugin');

const Path = require('path');
const rootDir = Path.resolve(__dirname, '../../../..');
const resourcesDir = Path.resolve(rootDir, 'src', 'main', 'resources');
const modulesDir = Path.resolve(__dirname, 'node_modules');
const outputDir = Path.resolve(__dirname, 'vue');

module.exports = {
    entry: {
        home: Path.resolve(resourcesDir, 'assets', 'home.js'),
        'font-awesome': Path.resolve(resourcesDir, 'assets', 'font-awesome.js'),
        'ore-client-fastopt': Path.resolve(resourcesDir, 'assets', 'dummy.js'),
        'ore-client-opt': Path.resolve(resourcesDir, 'assets', 'dummy.js')
    },
    output: {
        path: outputDir,
        filename: '[name].js',
        publicPath: '/dist/',
        libraryTarget: 'umd'
    },
    plugins: [
        new VueLoaderPlugin(),
        new MiniCssExtractPlugin({
            filename: '[name].css',
            chunkFilename: '[id].css',
        }),
        new CopyPlugin([
            {
                from: Path.resolve(modulesDir, '@fortawesome', 'fontawesome-svg-core', 'styles.css'),
                to: Path.resolve(outputDir, 'font-awesome.css')
            }
        ]),
    ],
    module: {
        rules: [
            {
                test: /\.vue$/,
                loader: 'vue-loader'
            },
            {
                test: /\.js$/,
                loader: 'babel-loader',
                include: resourcesDir,
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
        extensions: ['.js', '.vue', '.css'],
        alias: {
            'vue$': 'vue/dist/vue.esm.js'
        },
        modules: [
            modulesDir
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
