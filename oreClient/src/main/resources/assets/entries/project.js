import Vue from 'vue'

const root = require('../components/Project.vue').default;
const app = new Vue({
    el: '#project',
    render: createElement => createElement(root),
});
