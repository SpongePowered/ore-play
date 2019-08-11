<template>
    <ProjectList :owner="user"></ProjectList>
</template>

<script>
    import ProjectList from "./components/ProjectList";

    export default {
        components: {
            ProjectList
        },
        data: function() {
            return {
                user: window.location.toString().substring(window.location.toString().lastIndexOf("/") + 1, window.location.length)
            }
        }
    }
</script>
