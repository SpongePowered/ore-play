import Vue from 'vue'
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome'

Vue.component('font-awesome-icon', FontAwesomeIcon);

const root = require('../pages/UserProfile.vue').default;
const app = new Vue({
    el: '#user-profile',
    render: createElement => createElement(root),
});
