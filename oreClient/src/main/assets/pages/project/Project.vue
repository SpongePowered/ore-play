<template>
  <div>
    <project-header />
    <router-view />
  </div>
</template>

<script>
import ProjectHeader from './../../components/ProjectHeader'

export default {
  components: {
    ProjectHeader,
  },
  props: {
    pluginId: {
      type: String,
      default: null,
    },
    owner: {
      type: String,
      default: null,
    },
    slug: {
      type: String,
      default: null,
    },
    fetchedProject: {
      type: Object,
      required: false,
      default: null,
    },
  },
  watch: {
    $route: {
      handler() {
        this.distpatchUpdate()
      },
      immediate: true,
    },
  },
  methods: {
    distpatchUpdate() {
      if (
        this.fetchedProject &&
        this.fetchedProject.namespace.owner === this.owner &&
        this.fetchedProject.namespace.slug === this.slug
      ) {
        this.$store.dispatch('project/setActiveProjectFromFetched', this.fetchedProject)
      } else {
        const dispatchProject = this.pluginId ? this.pluginId : { owner: this.owner, slug: this.slug }
        this.$store.dispatch('project/setActiveProject', dispatchProject)
      }
    },
  },
}
</script>
