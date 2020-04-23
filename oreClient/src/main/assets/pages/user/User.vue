<template>
    <div>
        <user-header></user-header>
        <router-view></router-view>
    </div>
</template>

<script>
    import UserHeader from "../../components/UserHeader";

    export default {
        components: {
            UserHeader
        },
        props: {
            user: {
                type: String,
                required: true
            }
        },
        created() {
            this.distpatchUpdate();
        },
        watch: {
            $route() {
                this.distpatchUpdate();
            }
        },
        methods: {
            distpatchUpdate() {
                this.$store.dispatch('user/setActiveUser', this.user);
            }
        }
    }
</script>