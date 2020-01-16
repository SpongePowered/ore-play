import Vue from 'vue'
import VueRouter from 'vue-router'
import ProjectDiscussion from "../pages/project/ProjectDiscussion";

Vue.use(VueRouter);

const Project = require('../pages/project/Project.vue').default;
const ProjectDocs = require('../pages/project/ProjectDocs.vue').default;
const ProjectVersions = require('../pages/project/ProjectVersions.vue').default;

const router = new VueRouter({
    base: window.PROJECT_OWNER + "/" + window.PROJECT_SLUG,
    mode: 'history',
    routes: [
        {
            path: '/',
            name: 'home',
            component: ProjectDocs,
            props: {
                page: 'Home'
            }
        },
        {
            path: '/pages/:page',
            name: 'pages',
            component: ProjectDocs,
            props: true
        },
        {
            path: '/versions',
            name: 'versions',
            component: ProjectVersions
        },
        {
            path: '/discuss',
            name: 'discussion',
            component: ProjectDiscussion
        }
    ]
});

new Vue({
    el: "#project",
    render: createElement => createElement(Project, {
        props: {
            pluginId: window.PROJECT_ID,
        }
    }),
    router
});
