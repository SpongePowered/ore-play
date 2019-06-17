<template>
    <div>
        <div v-show="loading">
            <i class="fas fa-spinner fa-spin"></i>
            <span>Loading projects for you...</span>
        </div>
        <div v-show="!loading">
            <ul class="list-group project-list">
                <li v-for="project in projects" class="list-group-item project @entry.visibility.cssClass">
                    <div class="container-fluid">
                        <div class="row">
                            <div class="col-xs-12 col-sm-1">
                                <Icon extra-classes="user-avatar-sm" :src="project.icon_url" :name="project.name"></Icon>
                            </div>
                            <div class="col-xs-12 col-sm-11">
                                <div class="row">
                                    <div class="col-sm-6">
                                        <a class="title" :href="routes.Projects.show(project.namespace.owner, project.namespace.slug).absoluteURL()">
                                            {{ project.name }}
                                        </a>
                                    </div>
                                    <div class="col-sm-6 hidden-xs">
                                        <div class="info minor">
                                    <span class="stat recommended-version" title="Recommended version" v-if="project.recommended_version">
                                            <i class="far fa-gem"></i>
                                            <a :href="routes.Versions.show(project.namespace.owner, project.namespace.slug, project.recommended_version.version).absoluteURL()">
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
                                    <div class="col-xs-12 col-sm-5 tags-line" v-if="project.recommended_version">
                                        <Tag v-for="tag in filterTags(project.recommended_version.tags)"
                                             v-bind="tag" v-bind:key="project.name + '-' + tag.name"></Tag>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </div>
                </li>
            </ul>
            <Pagination :current="current" :total="total" @prev="previousPage" @next="nextPage" @jumpTo="jumpToPage($event)"></Pagination>
        </div>
    </div>
</template>

<script>
    import Tag from "./Tag.vue"
    import { clearFromEmpty } from "./../utils"
    import {Category, Platform} from "../home";
    import Pagination from "./Pagination.vue";
    import Icon from "./Icon.vue"

    export default {
        components: {
            Tag,
            Pagination,
            Icon
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
            limit: {
                type: Number,
                default: 25
            },
            offset: {
                type: Number,
                default: 0
            }
        },
        data () {
            return {
                projects: [],
                totalProjects: 0,
                loading: true
            }
        },
        computed: {
            current: function () {
                return Math.ceil(this.offset / this.limit) + 1;
            },
            total: function () {
                return Math.ceil(this.totalProjects / this.limit)
            },
            routes: function () {
                return jsRoutes.controllers.project;
            }
        },
        created() {
            this.update();
        },
        methods: {
            update: function() {
                apiV2Request("projects", "GET", clearFromEmpty(this.$props)).then((response) => {
                    this.projects = response.result;
                    this.totalProjects = response.pagination.count;
                    this.loading = false;
                    this.$emit('update:projectCount', this.totalProjects);
                });
            },
            categoryFromId: function (id) {
                return Category.fromId(id);
            },
            filterTags: function (tags) {
                return Platform.filterTags(tags);
            },
            previousPage: function() {
                this.$emit('prevPage')
            },
            nextPage: function () {
                this.$emit('nextPage')
            },
            jumpToPage: function (page) {
                this.$emit('jumpToPage', page)
            }
        }
    }
</script>

<style lang="scss">
    @import "./../scss/variables";

    .project-list {
        margin-bottom: 0;

        .row {
            display: flex;
            flex-wrap: nowrap;
        }

        @media (max-width: 767px) {
            .row {
                display: flex;
                flex-wrap: wrap;
            }
        }

        .project {
            padding: 10px 0;
            margin-bottom: 0.25rem;

            &:first-child {
                margin-top: 0.25rem;
            }
        }

        .title {
            font-size: 2rem;
            color: $sponge_grey;
            font-weight: bold;
        }

        .description-column {
            overflow: hidden;

            .description {
                white-space: nowrap;
                overflow: hidden;
                text-overflow: ellipsis;
            }
        }

        .tags-line {
            display: flex;
            justify-content: flex-end;

            @media (max-width: 480px) {
                justify-content: flex-start;
                margin-top: 0.5rem;
            }

            .tags {
                margin-right: 0.5rem;
            }

            :last-child {
                margin-right: 0;
            }

            .tag {
                margin: 0;
            }
        }

        .info {
            display: flex;
            justify-content: flex-end;

            span {
                margin-right: 1.5rem;

                &:last-child {
                    margin-right: 0;
                }

                &.recommended-version a {
                    font-weight: bold;
                    color: #636363;
                }
            }
        }
    }
</style>
