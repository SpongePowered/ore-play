<template>
  <ProjectList
    :owner="user"
    :offset="(page - 1) * limit"
    :limit="limit"
    :use-vue-router="false"
    @prevPage="page--"
    @nextPage="page++"
    @jumpToPage="page = $event"
  />
</template>

<script>
import ProjectList from './../components/ProjectList'

export default {
  components: {
    ProjectList,
  },
  props: {
    user: {
      type: String,
      required: true,
    },
  },
  data() {
    return {
      page: 1,
      limit: 5,
    }
  },
}
</script>
