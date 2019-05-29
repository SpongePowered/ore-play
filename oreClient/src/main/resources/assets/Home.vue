<template>
    <div class="example">{{ msg }}</div>
</template>

<script>
    export default {
        data () {
            return {
                msg: 'Hello world!'
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
