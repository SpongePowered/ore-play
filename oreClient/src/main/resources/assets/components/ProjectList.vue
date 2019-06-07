<template>
    <ul>
        <li v-for="project in projects">
            {{ project.name }}
        </li>
    </ul>
</template>

<script>
    import queryString from "query-string"

    export default {
        data () {
            return {
                projects: []
            }
        },
        mounted() {
            const params = queryString.parse(location.search, {arrayFormat: "comma"});

            apiV2Request("projects", "GET", params).then((response) => {
                this.projects = response.result;
            });
        }
    }
</script>

<style lang="scss">
</style>
