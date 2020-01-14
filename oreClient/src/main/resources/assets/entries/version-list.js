import Vue from 'vue'

const root = require('../pages/VersionList.vue').default;
const app = new Vue({
    el: '#version-list',
    render: createElement => createElement(root),
});
