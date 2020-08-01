<template>
  <div>
    <user-header />
    <router-view />
  </div>
</template>

<script>
import UserHeader from '../../components/UserHeader'

export default {
  components: {
    UserHeader,
  },
  props: {
    user: {
      type: String,
      required: true,
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
      this.$store.dispatch('user/setActiveUser', this.user)
    },
  },
}
</script>
