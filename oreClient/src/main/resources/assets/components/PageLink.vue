<template>
    <li class="list-group-item">
        <router-link
                :to="{name: 'pages', params: {project, permissions, 'page': page.slug.join('/')}}"
                v-slot="{ href, navigate }">
            <div>
                <a v-if="page.children" :class="expandedChildren ? 'page-collapse' : 'page-expand'"
                   @click="expandedChildren = !expandedChildren">
                    <!-- FIXME This isn't working -->
                    <i v-show="expandedChildren" class="far fa-minus-square"></i>
                    <i v-show="!expandedChildren" class="far fa-plus-square"></i>
                </a>
                <a :href="href" @click="navigate">{{ page.name[page.name.length - 1] }}</a>

                <page-list v-if="page.children && expandedChildren" :pages="page.children" :project="project"
                           :permissions="permissions"></page-list>
            </div>
        </router-link>
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