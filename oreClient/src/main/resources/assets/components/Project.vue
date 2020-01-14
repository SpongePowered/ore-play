<template>
    <div>
        <project-header v-if="p" :p="p" :permissions="permissions" :page="page"></project-header>

        <project-home v-if="page === 'home' && p" :p="p" :permissions="permissions"></project-home>
        <project-home v-else-if="'page' === 'versions'" :p="p" :permissions="permissions"></project-home>
        <project-docs v-else-if="'page' === 'docs'" :p="p" :permissions="permissions"></project-docs>
        <project-home v-else-if="'page' === 'discussion'" :p="p" :permissions="permissions"></project-home>
        <project-home v-else-if="'page' === 'settings'" :p="p" :permissions="permissions"></project-home>
    </div>
</template>

<script>
    import ProjectHeader from "./ProjectHeader";
    import ProjectHome from "./ProjectHome";
    import ProjectDocs from "./ProjectDocs";
    import {API} from "../api";

    export default {
        components: {
            ProjectHeader,
            ProjectHome,
            ProjectDocs
        },
        data: function () {
            return {
                permissions: [],
                p: this.homeProject === undefined ? null : this.homeProject,
                maxPages: window.MAX_PAGES
            }
        },
        props: {
            pluginId: {
                type: String,
                required: true
            },
            page: {
                type: String,
                required: true
            },
            homeProject: Object
        },
        created() {
            API.request("projects/" + this.pluginId).then((response) => {
                this.p = response;
            });
            API.request("permissions", "GET", {'pluginId': this.pluginId}).then((response) => {
                this.permissions = response.permissions;
            })
        }
    }
</script>