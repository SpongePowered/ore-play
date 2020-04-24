const merge = require("webpack-merge");
const commonConfig = require("./webpack.config.common.js");
const HtmlWebpackPlugin = require('html-webpack-plugin');

module.exports = merge(commonConfig, {
    mode: "development",
    plugins: [
        new HtmlWebpackPlugin({
            title: 'Ore',
            favicon: 'src/main/assets/images/favicon.ico',
            template: 'index_template.html',
            meta: {
                viewport: 'width=device-width, initial-scale=1'
            }
        }),
    ]
});