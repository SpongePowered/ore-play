const Vue = require('vue');
const root = require('./Test.vue').default;
const app = new Vue({
    el: '#app',
    render: createElement => createElement(root),
});
