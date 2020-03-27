<template>
    <li class="list-group-item">
        <a v-if="page.children" :class="expandedChildren ? 'page-collapse' : 'page-expand'"
           @click="expandedChildren = !expandedChildren">
            <font-awesome-icon :icon="['far', expandedChildren ? 'minus-square' : 'plus-square']" />
        </a>
        <router-link
                v-if="!page.navigational"
                :to="{name: 'pages', params: {project, permissions, 'page': page.slug}}"
                v-slot="{ href, navigate }">
            <a :href="href" @click="navigate">{{ page.name[page.name.length - 1] }}</a>
        </router-link>
        <span v-else>
            {{ page.name[page.name.length - 1] }}
        </span>

        <div v-if="permissions.includes('edit_page')" class="pull-right">
            <a href="#" @click="$emit('edit-page', page)"><font-awesome-icon style="padding-left:5px" :icon="['fas', 'edit']" /></a>
        </div>

        <page-list v-if="page.children && expandedChildren" :pages="page.children" :project="project"
                   :permissions="permissions" v-on:edit-page="event => $emit('edit-page', event)"></page-list>

    </li>
</template>

<script>
    export default {
        components: {
            PageList: () => import('./PageList'),
        },
        data() {
            return {
                expandedChildren: false
            }
        },
        props: {
            page: {
                type: Object,
                required: true
            },
            project: {
                type: Object,
                required: true
            },
            permissions: {
                type: Array,
                required: true
            }
        }
    }
</script>