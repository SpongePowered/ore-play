<template>
    <div>
        <div class="example">{{ msg }}</div>
        <project-list></project-list>
    </div>
</template>

<script>
    import ProjectList from "./components/ProjectList.vue"

    export default {
        components: {
            ProjectList
        },
        data () {
            return {
                msg: 'Hello world!'
            }
        },
    }
</script>

<style lang="scss">
    .example {
        color: red;
        display: flex;
        background: linear-gradient(45deg, blue, red);
    }
</style>
