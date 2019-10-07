const VueLoaderPlugin = require('vue-loader/lib/plugin');
const MiniCssExtractPlugin = require('mini-css-extract-plugin');
const CopyPlugin = require('copy-webpack-plugin');

const Path = require('path');
const rootDir = Path.resolve(__dirname, '../../../..');
const resourcesDir = Path.resolve(rootDir, 'src', 'main', 'resources');
const entryDir = Path.resolve(resourcesDir, 'assets', 'entries');
const modulesDir = Path.resolve(__dirname, 'node_modules');
const outputDir = Path.resolve(__dirname, 'build');

module.exports = {
    entry: {
        main: Path.resolve(resourcesDir, 'assets', 'scss', 'main.scss'),
        home: Path.resolve(entryDir, 'home.js'),
        'font-awesome': Path.resolve(entryDir, 'font-awesome.js'),
        'user-profile': Path.resolve(entryDir, 'user-profile.js'),
        'ore-client-fastopt': Path.resolve(entryDir, 'dummy.js'),
        'ore-client-opt': Path.resolve(entryDir, 'dummy.js')
    },
    output: {
        path: outputDir,
        filename: '[name].js',
        publicPath: '/dist/',
        libraryTarget: 'umd'
    },
    plugins: [
        new VueLoaderPlugin(),
        new MiniCssExtractPlugin(),
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
                options: {
                    presets: ['@babel/preset-env']
                }
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
                    name: 'vendors',
                    chunks: 'all',
                    priority: -10
                },
                commons: {
                    name: 'commons',
                    chunks: 'all',
                    priority: -20
                },
            }
        },
    }
};
