<template>
    <project-page :plugin-id="window.PLUGIN_ID" page="Home"></project-page>
</template>
<script>
    import ProjectPage from "./components/ProjectPage";

    export default {
        components: {ProjectPage}
    }
</script>