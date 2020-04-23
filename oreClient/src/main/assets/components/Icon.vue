<template>
    <router-link v-if="name" :to="{name: 'user_projects', params: {user: name}}">
        <img class="user-avatar" :class="extraClasses" :src="src" :alt="name" />
    </router-link>
    <a v-else :href="linkUrl">
        <img class="user-avatar" :class="extraClasses" :src="src" :alt="name" />
    </a>
</template>

<script>
    export default {
        props: {
            src: {
                type: String,
                required: true
            },
            href: String,
            extraClasses: String,
            name: String
        },
        computed: {
            linkUrl: function () {
                if(this.href != null) {
                    return this.href;
                } else {
                    return "#";
                }
            }
        }
    }
</script>
