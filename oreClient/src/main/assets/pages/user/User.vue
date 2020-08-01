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
    $route() {
      this.distpatchUpdate()
    },
  },
  created() {
    this.distpatchUpdate()
  },
  methods: {
    distpatchUpdate() {
      this.$store.dispatch('user/setActiveUser', this.user)
    },
  },
}
</script>
