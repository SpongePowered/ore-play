<template>
    <div v-if="currentProject" >
        <project-header :project="currentProject" :permissions="permissions" :current-user="currentUser" />

        <router-view :project="currentProject" :permissions="permissions" />
    </div>
</template>

<script>
    import ProjectHeader from "./../../components/ProjectHeader";
    import {API} from "../../api";

    export default {
        components: {
            ProjectHeader,
        },
        data() {
            return {
                fetchedProject: null,
                permissions: [],
                currentUser: null //TODO
            }
        },
        props: {
            pluginId: {
                type: String,
                required: true
            },
            project: {
                type: Object,
                required: false,
                default: null
            }
        },
        computed: {
          currentProject() {
              return this.fetchedProject || this.project;
          }
        },
        created() {
            if(!this.project) {
                API.request("projects/" + this.pluginId).then((response) => {
                    this.fetchedProject = response;
                });
            }
            API.request("permissions", "GET", {'pluginId': this.pluginId}).then((response) => {
                this.permissions = response.permissions;
            })
        }
    }
</script>