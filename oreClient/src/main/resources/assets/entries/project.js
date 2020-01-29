import Vue from 'vue'
import VueRouter from 'vue-router'

import ProjectDiscussion from "../pages/project/ProjectDiscussion";
import ProjectSettings from "../pages/project/ProjectSettings";
import ProjectDocs from "../pages/project/ProjectDocs";
import ProjectVersions from "../pages/project/ProjectVersions";
import Project from "../pages/project/Project";
import VersionPage from "../pages/project/VersionPage";
import NewVersion from "../pages/project/NewVersion";

Vue.use(VueRouter);

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
            path: '/versions/new',
            name: 'new_version',
            component: NewVersion
        },
        {
            path: '/versions/:version',
            name: 'version',
            component: VersionPage,
            props: true
        },
        {
            path: '/discuss',
            name: 'discussion',
            component: ProjectDiscussion
        },
        {
            path: '/settings',
            name: 'settings',
            component: ProjectSettings
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
