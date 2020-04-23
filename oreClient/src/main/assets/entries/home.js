import Vue from 'vue'
import VueRouter from 'vue-router'
import Vuex from 'vuex'
import {FontAwesomeIcon} from '@fortawesome/vue-fontawesome'

import TopPage from "../pages/TopPage";
import Home from "../pages/Home";
import ProjectDiscussion from "../pages/project/ProjectDiscussion";
import ProjectSettings from "../pages/project/ProjectSettings";
import ProjectDocs from "../pages/project/ProjectDocs";
import ProjectVersions from "../pages/project/ProjectVersions";
import Project from "../pages/project/Project";
import VersionPage from "../pages/project/VersionPage";
import NewVersion from "../pages/project/NewVersion";
import User from "../pages/user/User";

import topStore from "../stores/top"
import projectModule from "../stores/project"
import globalModule from "../stores/global"
import userModule from "../stores/user"
import UserProjects from "../pages/user/UserProjects";

Vue.use(VueRouter);
Vue.use(Vuex);
Vue.component('font-awesome-icon', FontAwesomeIcon);

const store = new Vuex.Store({
    ...topStore,
    modules: {
        project: projectModule,
        global: globalModule,
        user: userModule
    }
});

store.dispatch('global/loadUser');

const router = new VueRouter({
    base: '/',
    mode: 'history',
    routes: [
        {
            path: '/',
            name: 'home',
            component: Home
        },
        {
            path: '/:user',
            component: User,
            props: true,
            children: [
                {
                    path: '',
                    name: 'user_projects',
                    component: UserProjects
                }
            ]
        },
        {
            path: '/:owner/:slug/',
            name: 'project',
            component: Project,
            props: true,
            children: [
                {
                    path: '',
                    name: 'project_home',
                    component: ProjectDocs,
                    props: {
                        page: ['Home']
                    }
                },
                {
                    path: 'pages/:page+',
                    name: 'pages',
                    component: ProjectDocs,
                    props: true
                },
                {
                    path: 'versions',
                    name: 'versions',
                    component: ProjectVersions
                },
                {
                    path: 'versions/new',
                    name: 'new_version',
                    component: NewVersion
                },
                {
                    path: 'versions/:version',
                    name: 'version',
                    component: VersionPage,
                    props: true
                },
                {
                    path: 'discuss',
                    name: 'discussion',
                    component: ProjectDiscussion
                },
                {
                    path: 'settings',
                    name: 'settings',
                    component: ProjectSettings
                }
            ]
        }
    ]
});

const app = new Vue({
    el: '#home',
    render: createElement => createElement(TopPage),
    router,
    store
});
