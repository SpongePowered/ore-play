<template>
    <select form="form-continue" :style="hidden ? {display: 'none'} : ''">
        <option v-for="roleType in roles" :value="roleType.value">{{ roleType.title }}</option>
    </select>
</template>

<script>

    export default {
        props: {
            roleCategory: {
                type: String,
                required: true
            },
            hidden: Boolean
        },
        computed: {
            roles() {
                //return Role.values.filter(role => role.category == this.roleCategory && role.isAssignable).sortBy(_.permissions: Long).reverse
                return []
            }
        }
    }
</script>