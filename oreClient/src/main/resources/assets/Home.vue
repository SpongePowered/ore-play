<template>
    <div>
        <button v-on:click="click">Here</button>
        <project-list v-bind="parameters"></project-list>
    </div>
</template>

<script>
    import ProjectList from "./components/ProjectList.vue"
    import { HomeStore } from "./home"

    export default {
        components: {
            ProjectList
        },
        data () {
            return {
                msg: 'Hello world!'
            }
        },
        computed: {
            parameters: function () {
                return HomeStore.getters.notNull;
            }
        },
        methods: {
            click: function () {
                HomeStore.commit('query', 'Nucleus')
            }
        }
    }
</script>

<style lang="scss">
    .example {
        color: red;
        display: flex;
        background: linear-gradient(45deg, blue, red);
    }
</style>
