<template>
    <ul>
        <li v-for="project in projects">
            {{ project.name }}
        </li>
    </ul>
</template>

<script>
    export default {
        props: {
            q: String,
            categories: {
                type: Array
            },
            tags: Array,
            owner: String,
            sort: String,
            relevance: {
                type: Boolean,
                default: true
            },
            limit: Number,
            offset: Number
        },
        data () {
            return {
                projects: []
            }
        },
        mounted() {
            this.update();
        },
        methods: {
            update: function() {
                apiV2Request("projects", "GET", this.$props).then((response) => {
                    this.projects = response.result;
                });
            },
        }
    }
</script>

<style lang="scss">
</style>
