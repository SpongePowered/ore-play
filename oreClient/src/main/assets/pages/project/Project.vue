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
            if (!this.pluginId && (!this.owner || !this.slug)) {
                throw "Neither pluginId or namespace for project defined"
            }

            if (!this.fetchedProject) {
                let dispatchProject = this.pluginId ? this.pluginId : {owner: this.owner, slug: this.slug}
                this.$store.dispatch('project/setActiveProject', dispatchProject);
            }
            else {
                this.$store.dispatch('project/setActiveProjectFromFetched', this.fetchedProject);
            }
        }
    }
</script>