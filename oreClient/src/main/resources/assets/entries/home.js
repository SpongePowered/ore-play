import Vue from 'vue'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'

Vue.component('font-awesome-icon', FontAwesomeIcon);

const root = require('../pages/Home.vue').default;
const app = new Vue({
    el: '#home',
    render: createElement => createElement(root),
});
