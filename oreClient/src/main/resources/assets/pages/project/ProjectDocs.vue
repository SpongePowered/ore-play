<template>
    <div class="row">
        <div class="col-md-9">
            <div class="row">
                <div class="col-md-12">
                    <editor :enabled="permissions && permissions.includes('edit_page')"
                            :raw="description"
                            subject="Page"
                            v-on:saved="savePage" />
                </div>
            </div>
        </div>

        <div class="col-md-3">

            <div class="stats minor">
                <p>Category: {{ parseCategory(project.category) }}</p>
                <p>Published on {{ parseDate(project.created_at) }}</p>
                <p>{{ formatStats(project.stats.views) }} views</p>
                <p>{{ formatStats(project.stats.downloads) }} total downloads</p>
                <p v-if="project.settings.license.name !== null">
                    <span>Licensed under </span>
                    <a target="_blank" rel="noopener" :href="project.settings.license.url">{{project.settings.license.name}}</a>
                </p>
            </div>

            <div class="panel panel-default">
                <div class="panel-heading">
                    <h3 class="panel-title">Promoted Versions</h3>
                </div>

                <ul class="list-group promoted-list">
                    <li v-for="version in project.promoted_versions" class="list-group-item row row-no-gutters" style="line-height: 2.4em;">
                        <div class="col-lg-8 col-12">
                            <router-link :to="{name: 'version', params: {project, permissions, 'version': version.version}}"
                                         v-slot="{ href, navigate }">
                                <a :href="href" @click="navigate">{{ version.version }}</a>
                            </router-link>
                        </div>
                        <div class="col-lg-4 col-12">
                            <a class="pull-right btn btn-primary" :href="routes.Versions.download(project.namespace.owner, project.namespace.slug, version.version, null).absoluteURL()">
                                <font-awesome-icon :icon="['fas', 'download']" /> Download
                            </a>
                        </div>
                    </li>
                </ul>
            </div>

            <div class="panel panel-default">
                <div class="panel-heading">
                    <h3 class="panel-title">Pages</h3>
                    <template v-if="permissions.includes('edit_page')">
                        <button class="new-page btn yellow btn-xs pull-right" data-toggle="modal"
                                data-target="#edit-page" title="New">
                            <font-awesome-icon :icon="['fas', 'plus']" />
                        </button>

                        <div class="modal fade" id="edit-page" tabindex="-1" role="dialog"
                             aria-labelledby="page-label">
                            <div class="modal-dialog" role="document">
                                <div class="modal-content">
                                    <div class="modal-header">
                                        <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                                            <span aria-hidden="true">&times;</span>
                                        </button>
                                        <h4 class="modal-title" id="page-label">
                                            <template v-if="newPage">Create a new page</template>
                                            <template v-else>Edit page</template>
                                        </h4>
                                        <h4 v-if="pagePutError" class="modal-title" id="page-label-error"
                                            style="display: none; color: red">
                                            Error updating page {{ pagePutError }}
                                        </h4>
                                    </div>
                                    <div class="modal-body input-group">
                                        <div class="setting">
                                            <div class="setting-description">
                                                <h4>Page name</h4>
                                                <p>Enter a title for your page.</p>
                                            </div>
                                            <div class="setting-content">
                                                <input v-model="requestPage.name" class="form-control" type="text"
                                                       id="page-name" name="page-name">
                                            </div>
                                            <div class="clearfix"></div>
                                        </div>
                                        <div class="setting">
                                            <div class="setting-description">
                                                <h4>Parent page</h4>
                                                <p>Select a parent page (optional)</p>
                                            </div>
                                            <div class="setting-content">
                                                <select v-model="requestPage.parent" class="form-control select-parent">
                                                    <option selected value="null">&lt;none&gt;</option>
                                                    <option v-for="page in pages" :value="page.slug.join('/')"
                                                            :data-slug="page.slug.join('/')">{{ page.name.join('/') }}
                                                    </option>
                                                </select>
                                            </div>
                                            <div class="clearfix"></div>
                                        </div>
                                        <div class="setting setting-no-border">
                                            <div class="setting-description">
                                                <h4>Navigational</h4>
                                                <p>Makes the page only useful for nagivation. <b>This will delete all content currently on the page.</b></p>
                                            </div>
                                            <div class="setting-content">
                                                <div class="form-check">
                                                    <input v-model="requestPage.navigational" class="form-check-input position-static" type="checkbox"
                                                           id="page-navigational" name="page-navigational" aria-label="Navigational">
                                                </div>
                                            </div>
                                            <div class="clearfix"></div>
                                        </div>
                                    </div>
                                    <div class="modal-footer">
                                        <button type="button" class="btn btn-default" data-dismiss="modal"
                                                @click="resetPutPage">Close
                                        </button>
                                        <button v-if="!newPage" type="button" class="btn btn-danger" @click="deletePage">Delete</button>
                                        <button type="button" class="btn btn-primary" @click="updateCreatePage">Continue</button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </template>
                </div>

                <page-list :pages="groupedPages" :project="project" :permissions="permissions"
                           :include-home="true" v-on:edit-page="startEditPage"></page-list>
            </div>


            <member-list :members="members" :permissions="permissions" role-category="project" :settings-route="{name: 'settings'}" />
        </div>
    </div>
</template>

<script>

    import {API} from "../../api";
    import Editor from "../../components/Editor";
    import MemberList from "../../components/MemberList";
    import {Category} from "../../enums";
    import PageList from "../../components/PageList";
    import _ from 'lodash'

    export default {
        components: {
            PageList,
            Editor,
            MemberList,
        },
        data() {
            return {
                description: "",
                pages: [],
                requestPage: {},
                pagePutError: null
            }
        },
        props: {
            project: {
                type: Object,
                required: true
            },
            permissions: {
                type: Array,
                required: true
            },
            page: {
                type: [Array, String],
                required: true
            },
            members: {
                type: Array,
                required: true
            }
        },
        computed: {
            routes: function () {
                return jsRoutes.controllers.project;
            },
            groupedPages() {
                let nonHome = this.pages.filter(p => p.slug.length !== 1 || p.slug[0] !== 'Home');
                let acc = {};

                for (let page of nonHome) {
                    let obj = acc;

                    for (let i = 0; i < page.slug.length - 1; i++) {
                        let k = page.slug[i];
                        if (typeof obj[k] === 'undefined') {
                            obj[k] = {}
                        }

                        if (typeof obj[k].children === 'undefined') {
                            obj[k].children = {}
                        }

                        obj = obj[k].children;
                    }

                    let key = page.slug[page.slug.length - 1];
                    if (typeof obj[key] === 'undefined') {
                        obj[key] = {}
                    }

                    obj[key].slug = page.slug;
                    obj[key].name = page.name;
                    obj[key].navigational = page.navigational;
                }

                return acc
            },
            newPage() {
                return typeof this.requestPage.existing === 'undefined'
            },
            splitPage() {
                return Array.isArray(this.page) ? this.page : this.page.split('/')
            },
            joinedPage() {
                return Array.isArray(this.page) ? this.page.join('/') : this.page
            },
            currentPage() {
                return this.pages.filter(p => _.isEqual(p.slug, this.splitPage))[0]
            }
        },
        created() {
            this.updatePage(true);
        },
        watch: {
            $route() {
                this.updatePage(false)
            }
        },
        methods: {
            updatePage(fetchPages) {
                API.request('projects/' + this.project.plugin_id + '/_pages/' + this.joinedPage).then((response) => {
                    if(response.content === null) {
                        this.description = ""
                    }
                    else {
                        this.description = response.content;
                    }
                }).catch((error) => {
                    this.description = "";

                    if (error === 404) {
                        //TODO
                    } else {

                    }
                });

                if (fetchPages) {
                    API.request('projects/' + this.project.plugin_id + '/_pages').then(pageList => {
                        this.pages = pageList.pages;
                    })
                }
            },
            parseDate(rawDate) {
                return moment(rawDate).format("MMM DD[,] YYYY");
            },
            parseCategory(category) {
                return Category.fromId(category).name;
            },
            resetPutPage() {
                this.requestPage = {};
            },
            updateCreatePage() {
                let page = this.requestPage;
                if(page.parent === 'null') {
                    page.parent = null;
                }

                let content = null;
                if(!page.navigational) {
                    content = page.content ? page.content : 'Welcome to your new page'
                }
                let action;
                if(this.newPage) {
                    let pageSlug = page.parent ? page.parent + '/' + page.name : page.name;
                    action = API.request('projects/' + this.project.plugin_id + '/_pages/' + pageSlug, 'PUT', {
                        'name': page.name,
                        'content': content
                    });
                }
                else {
                    let pageSlug = page.oldParent ? page.oldParent + '/' + page.oldName : page.oldName;
                    action = API.request('projects/' + this.project.plugin_id + '/_pages/' + pageSlug, 'PATCH', {
                        'name': page.name,
                        'content': content,
                        'parent': page.parent,
                    });
                }

                action.then(res => {
                    $('#edit-page').modal('toggle');
                    this.resetPutPage();
                    this.updatePage(true)
                }).catch(err => {
                    //TODO: Better error handling here

                    console.error(err);
                    this.pagePutError = err;
                });
            },
            savePage(newContent) {
                API.request('projects/' + this.project.plugin_id + '/_pages/' + this.joinedPage, 'PUT', {
                    'name': this.currentPage.name[this.currentPage.name.length - 1],
                    'content': newContent
                }).then(res => {
                    this.description = newContent
                }).catch(err => {
                    //TODO: Handle error here
                });
            },
            deletePage() {
                let page = this.requestPage;
                let pageSlug = page.parent ? page.parent + '/' + page.name : page.name;
                //TODO

                API.request('projects/' + this.project.plugin_id + '/_pages/' + pageSlug, 'DELETE').then(res => {
                    $('#edit-page').modal('toggle');
                    this.resetPutPage();

                    if (pageSlug === this.joinedPage) {
                        this.$router.push({'name': 'home', params: {'project': this.project, 'permissions': this.permissions}})
                    }
                    else {
                        this.updatePage(true);
                    }
                }).catch(err => {
                    //TODO: Better error handling here

                    console.error(err);
                    this.pagePutError = err;
                });
            },
            startEditPage(page) {
                this.$set(this.requestPage, 'existing', true);
                this.$set(this.requestPage, 'oldName', page.name[page.name.length - 1]);
                this.$set(this.requestPage, 'name', this.requestPage.oldName);
                this.$set(this.requestPage, 'oldParent', page.slug.length === 1 ? null : page.slug.slice(0, -1).join('/'));
                this.$set(this.requestPage, 'parent', this.requestPage.oldParent);
                this.$set(this.requestPage, 'navigational', page.navigational);

                API.request('projects/' + this.project.plugin_id + '/_pages/' + page.slug.join('/')).then((response) => {
                    this.$set(this.requestPage, 'content', response.content);
                    $('#edit-page').modal('toggle');
                }).catch((error) => {
                    if (error === 404) {
                        this.$set(this.requestPage, 'existing', undefined);
                        $('#edit-page').modal('toggle');
                    } else {
                        //TODO
                    }
                });
            },
            formatStats(number) {
                return numberWithCommas(number);
            }
        }
    }
</script>
