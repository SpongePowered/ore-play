<template>
    <ul class="list-group project-list">
        <li v-for="project in projects" class="list-group-item project @entry.visibility.cssClass">
            <div class="container-fluid">
                <div class="row">
                    <div class="col-xs-12 col-sm-1">
                        <img class="user-avatar user-avatar-sm" :src="project.icon_url" :alt="project.name" />
                    </div>
                    <div class="col-xs-12 col-sm-11">
                        <div class="row">
                            <div class="col-sm-6">
                                <a class="title" href="@projectRoutes.show(entry.namespace.ownerName, entry.namespace.slug)">{{ project.name }}</a>
                            </div>
                            <div class="col-sm-6 hidden-xs">
                                <div class="info minor">
                                    <span class="stat recommended-version" title="Recommended version" v-if="project.recommended_version">
                                            <i class="far fa-gem"></i>
                                            <a href="@controllers.project.routes.Versions.show(
                                                entry.namespace.ownerName, entry.namespace.slug, recommendedVersion)">
                                                {{ project.recommended_version.version }}
                                            </a>
                                    </span>

                                    <span class="stat" title="Views"><i class="fas fa-eye"></i> {{ project.stats.views }}</span>
                                    <span class="stat" title="Download"><i class="fas fa-download"></i> {{ project.stats.downloads }}</span>
                                    <span class="stat" title="Stars"><i class="fas fa-star"></i> {{ project.stats.stars }}</span>

                                    <span class="stat" :title="categoryFromId(project.category).name">
                                            <i class="fas" :class="'fa-' + categoryFromId(project.category).icon"></i>
                                    </span>
                                </div>
                            </div>
                        </div>
                        <div class="row">
                            <div class="col-sm-7 description-column">
                                <div class="description">{{ project.description }}</div>
                            </div>
                            <div class="col-xs-12 col-sm-5 tags-line">
                                <Tag v-for="tag in filterTags(project.recommended_version.tags)" v-bind="tag" v-bind:key="tag.name"></Tag>
                            </div>
                        </div>
                    </div>
                </div>
            </div>
        </li>
    </ul>
</template>

<script>
    import Tag from "./Tag.vue"
    import { clearFromEmpty } from "./../utils"
    import {Category, Platform} from "../home";

    export default {
        components: {
            Tag
        },
        props: {
            q: String,
            categories: {
                type: Array
            },
            tags: Array,
            owner: String,
            sort: String,
            relevance: {
                type: Boolean,
                default: true
            },
            limit: Number,
            offset: Number
        },
        data () {
            return {
                projects: []
            }
        },
        mounted() {
            this.update();
        },
        methods: {
            update: function() {
                apiV2Request("projects", "GET", clearFromEmpty(this.$props)).then((response) => {
                    this.projects = response.result;
                });
            },
            categoryFromId: function (id) {
                return Category.fromId(id);
            },
            filterTags: function (tags) {
                return Platform.filterTags(tags);
            }
        }
    }
</script>

<style lang="scss">
</style>
