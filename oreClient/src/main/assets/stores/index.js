import Vuex from "vuex";
import topStore from "./top";
import projectModule from "./project";
import globalModule from "./global";
import userModule from "./user";
import Vue from "vue";

Vue.use(Vuex);

export const store = new Vuex.Store({
    ...topStore,
    modules: {
        project: projectModule,
        global: globalModule,
        user: userModule
    }
});
