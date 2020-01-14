import Vue from 'vue'

const root = require('../pages/Project.vue').default;
const app = new Vue({
    el: '#project',
    render: createElement => createElement(root, {
        props: {
            pluginId: window.PROJECT_ID,
            page: 'home'
        }
    }),
});
