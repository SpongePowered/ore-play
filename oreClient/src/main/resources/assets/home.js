import Vue from 'vue'
import Vuex from 'vuex'

Vue.use(Vuex);

export const HomeStore = new Vuex.Store({
    strict: true,
    state: {
        query: null,
        categories: null,
        tags: null,
        owner: null,
        sort: null,
        relevance: null,
        limit: null,
        offset: null
    },
    computed: {
        notNull () {
            let data = {};
            Object.assign(data, this.$store.state);
            Object.keys(data).forEach(key => data[key] === null && delete data[key]);

            return data;
        }
    },
    mutations: {
        query(state, query) {
            this.state.query = query;
        }
    }
});

const root = require('./Home.vue').default;
const app = new Vue({
    el: '#app',
    render: createElement => createElement(root),
});
