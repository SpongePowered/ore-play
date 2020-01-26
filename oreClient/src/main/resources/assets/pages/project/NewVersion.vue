<template>
    <div>New version</div>
</template>

<script>

    export default {
        props: {
            project: {
                type: Object,
                required: true
            },
            permissions: {
                type: Array,
                required: true
            },
        }
    }
</script>