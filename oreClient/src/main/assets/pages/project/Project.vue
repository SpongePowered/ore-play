<template>
    <div>
        <project-header/>
        <router-view/>
    </div>
</template>

<script>
    import ProjectHeader from "./../../components/ProjectHeader";

    export default {
        components: {
            ProjectHeader,
        },
        props: {
            pluginId: String,
            owner: String,
            slug: String,
            fetchedProject: {
                type: Object,
                required: false,
                default: null
            }
        },
        created() {
            this.distpatchUpdate();
        },
        watch: {
            $route() {
                this.distpatchUpdate();
            }
        },
        methods: {
            distpatchUpdate() {

                if(this.fetchedProject && this.fetchedProject.namespace.owner === this.owner && this.fetchedProject.namespace.slug === this.slug) {
                    this.$store.dispatch('project/setActiveProjectFromFetched', this.fetchedProject);
                }
                else {
                    let dispatchProject = this.pluginId ? this.pluginId : {owner: this.owner, slug: this.slug}
                    this.$store.dispatch('project/setActiveProject', dispatchProject);
                }
            }
        }
    }
</script>