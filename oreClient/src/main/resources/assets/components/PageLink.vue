<template>
    <li class="list-group-item">
        <a v-if="page.children" :class="expandedChildren ? 'page-collapse' : 'page-expand'"
           @click="expandedChildren = !expandedChildren">
            <!-- FIXME This isn't working -->
            <i v-show="expandedChildren" class="far fa-minus-square"></i>
            <i v-show="!expandedChildren" class="far fa-plus-square"></i>
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
            <a href="#" @click="$emit('edit-page', page)"><i style="padding-left:5px" class="fas fa-edit"></i></a>
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