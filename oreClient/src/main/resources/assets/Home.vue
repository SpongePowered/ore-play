<template>
    <div>
        <input placeholder="Query" v-model="query" />
        <project-list v-bind="parameters" ref="list"></project-list>
    </div>
</template>

<script>
    import ProjectList from "./components/ProjectList.vue"
    import { HomeStore } from "./home"
    import debounce from "lodash/debounce"
    import queryString from "query-string"

    export default {
        components: {
            ProjectList
        },
        data: function () {
            return {
                query: this.q
            }
        },
        watch: {
            query: function () {
                this.deboundedQuery();
            }
        },
        computed: {
            parameters: function () {
                return HomeStore.getters.notNull;
            }
        },
        methods: {
            updateQuery: function () {
                HomeStore.commit('mutate', {
                    property: 'q',
                    with: this.query.trim() === "" ? null : this.query
                });
                this.$refs.list.update();
                window.history.pushState(null, null, "?" + queryString.stringify(HomeStore.getters.notNull))
            }
        },
        created() {
            this.deboundedQuery = debounce(this.updateQuery, 500);
        }
    }
</script>

<style lang="scss">
</style>
