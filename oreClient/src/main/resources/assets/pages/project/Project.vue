<template>
    <div v-if="currentProject">
        <project-header :project="currentProject" :permissions="permissions" :members="members" :current-user="currentUser"
                        v-on:set-watching="updateWatching" v-on:toggle-starred="toggleStarred"/>

        <router-view :project="currentProject" :permissions="permissions" :members="members"/>
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
                members: [],
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
            if (!this.project) {
                API.request("projects/" + this.pluginId).then((response) => {
                    this.fetchedProject = response;
                });
            }
            API.request("permissions", "GET", {'pluginId': this.pluginId}).then((response) => {
                this.permissions = response.permissions;
            });

            API.request("projects/" + this.pluginId + "/members").then((response) => {
                this.members = response
            });

            if (API.hasUser()) {
               API.request("users/@me").then((res) => {
                   this.currentUser = res;
               })
            }
        },
        methods: {
            toggleStarred() {
                if (this.fetchedProject) {
                    let newStarred = !this.fetchedProject.user_actions.starred;
                    if(newStarred) {
                        this.fetchedProject.stats.stars += 1;
                    }
                    else {
                        this.fetchedProject.stats.stars -= 1;
                    }

                    this.fetchedProject.user_actions.starred = newStarred;
                }
                else {
                    this.$emit('toggle-starred')
                }
            },
            updateWatching(newWatching) {
                if (this.fetchedProject) {
                    if(newWatching) {
                        this.fetchedProject.stats.watchers += 1;
                    }
                    else {
                        this.fetchedProject.stats.watchers -= 1;
                    }

                    this.fetchedProject.user_actions.watching = newWatching;
                }
                else {
                    this.$emit('update-watching', newWatching)
                }
            }
        }
    }
</script>