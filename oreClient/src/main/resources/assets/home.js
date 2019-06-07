import Vue from 'vue'
import Vuex from 'vuex'

Vue.use(Vuex);

export const HomeStore = new Vuex.Store({
    strict: true,
    state: {
        q: null,
        categories: null,
        tags: null,
        owner: null,
        sort: null,
        relevance: null,
        limit: null,
        offset: null
    },
    getters: {
        notNull(state) {
            return Object.entries(state)
                .filter(([key, value]) => value != null)
                .reduce((acc, [key, value]) => ({...acc, [key]: value}), {})
        }
    },
    mutations: {
        mutate(state, payload) {
            state[payload.property] = payload.with;
        }
    }
});

const root = require('./Home.vue').default;
const app = new Vue({
    el: '#app',
    render: createElement => createElement(root),
});
