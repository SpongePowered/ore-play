<template>
    <div class="version-list">
        <div class="row text-center">
            <div class="col-xs-12">
                <router-link :to="{name: 'new_version', params: {project, permissions}}" v-slot="{ href, navigate }">
                    <a v-if="canUpload" class="btn yellow" :href="href" @click="navigate">Upload a New Version</a>
                </router-link>
            </div>
        </div>
        <div v-show="loading">
            <font-awesome-icon :icon="['fas', 'spinner']" spin></font-awesome-icon>
            <span>Loading versions for you...</span>
        </div>
        <div v-show="!loading">
            <div class="list-group">
                <template v-for="version in versions">
                    <router-link :to="{name: 'version', params: {project, permissions, 'version': version.name}}" v-slot="{ href, navigate }">
                        <a :href="href" @click="navigate" class="list-group-item" :class="[classForVisibility(version.visibility)]">
                            <div class="container-fluid">
                                <div class="row">
                                    <div class="col-xs-6 col-sm-3">
                                        <div class="row">
                                            <div class="col-xs-12">
                                                <span class="text-bold">{{ version.name }}</span>
                                            </div>
                                            <div class="col-xs-12" :set="stability = stabilities.fromId(version.tags.stability)">
                                                <span class="channel" v-bind:style="{ background: stability.color }">{{ stability.title }}</span>
                                            </div>
                                            <div v-if="version.tags.release_type" class="col-xs-12" :set="releaseType = releaseTypes.fromId(version.tags.release_type)">
                                                <span class="channel" v-bind:style="{ background: releaseType.color }">{{ releaseType.title }}</span>
                                            </div>
                                        </div>
                                    </div>
                                    <div class="col-xs-6 col-sm-3">
                                        <Tag v-if="version.tags.mixin" name="Mixin" :color="{background: '#FFA500', foreground: '#333333'}"></Tag>

                                        <Tag v-for="platform in version.tags.platforms" :set="platformObj = platformById(platform.platform)"
                                             :key="platform.platform + ':' + platform.platform_version"
                                             :name="platformObj.shortName"
                                             :data="platform.platform_version"
                                             :color="platformObj.color"></Tag>
                                    </div>
                                    <div class="col-xs-3 hidden-xs">
                                        <div class="row">
                                            <div class="col-xs-12">
                                                <font-awesome-icon :icon="['fas', 'calendar']" fixed-width></font-awesome-icon>
                                                {{ formatDate(version.created_at) }}
                                            </div>
                                            <div class="col-xs-12">
                                                <font-awesome-icon :icon="['far', 'file']" fixed-width></font-awesome-icon>
                                                {{ formatSize(version.file_info.size_bytes) }}
                                            </div>
                                        </div>
                                    </div>
                                    <div class="col-xs-3 hidden-xs">
                                        <div class="row">
                                            <div class="col-xs-12">
                                                <font-awesome-icon :icon="['fas', 'user-tag']" fixed-width></font-awesome-icon>
                                                {{ version.author }}
                                            </div>
                                            <div class="col-xs-12">
                                                <font-awesome-icon :icon="['fas', 'download']" fixed-width></font-awesome-icon>
                                                {{ version.stats.downloads }} Downloads
                                            </div>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </a>
                    </router-link>
                </template>
            </div>

            <Pagination :current="current" :total="total" @prev="page--" @next="page++" @jumpTo="page = $event"></Pagination>
        </div>
    </div>
</template>

<script>
    import Tag from "../../components/Tag";
    import Pagination from "../../components/Pagination";
    import {Visibility, Stability, ReleaseType, Platform} from "../../enums";

    export default {
        components: {
            Tag,
            Pagination
        },
        props: {
            permissions: Array,
            project: {
                type: Object,
                required: true
            }
        },
        data() {
            return {
                page: 1,
                limit: 10,
                versions: [],
                totalVersions: 0,
                loading: true
            }
        },
        created() {
            this.update();
            this.$watch(vm => vm.page, () => {
                this.update();
                window.scrollTo(0,0);
            });
        },
        methods: {
            update() {
                apiV2Request("projects/" + this.project.plugin_id + "/versions", "GET", { limit: this.limit, offset: this.offset}).then((response) => {
                    this.versions = response.result;
                    this.totalVersions = response.pagination.count;
                    this.loading = false;
                });
            },
            formatSize(size) {
                return window.filesize(size);
            },
            formatDate(date) {
                return window.moment(date).format("MMM D, YYYY")
            },
            classForVisibility(visibility) {
                return Visibility.fromName(visibility).class;
            },
            platformById(id) {
                return Platform.fromId(id)
            }
        },
        computed: {
            canUpload() {
                return this.permissions.includes('create_version')
            },
            routes() {
                return jsRoutes.controllers.project;
            },
            offset() {
                return (this.page - 1) * this.limit
            },
            current() {
                return Math.ceil(this.offset / this.limit) + 1;
            },
            total() {
                return Math.ceil(this.totalVersions / this.limit)
            },
            stabilities() {
                return Stability
            },
            releaseTypes() {
                return ReleaseType
            }
        }
    }
</script>

<style lang="scss">
    .version-list {
        .list-group > .list-group-item > .container-fluid > .row {
            display: flex;
            align-items: center;
        }
        .btn {
            margin-bottom: 1rem;
        }
    }
</style>