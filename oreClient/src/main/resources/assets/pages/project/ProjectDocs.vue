<template>
    <div class="row">
        <div class="col-md-9">
            <div class="row">
                <div class="col-md-12">
                    <editor :enabled="permissions && permissions.includes('edit_page')" :raw="description"
                            subject="Page"/>
                </div>
            </div>
        </div>

        <div class="col-md-3">

            <div class="stats minor">
                <p>Category: {{ parseCategory(project.category) }}</p>
                <p>Published on {{ parseDate(project.created_at) }}</p>
                <p>{{ project.stats.views }} views</p>
                <p>{{ project.stats.stars }} <a
                        :href="routes.Projects.showStargazers(project.namespace.owner, project.namespace.slug, null).absoluteURL()">stars</a>
                </p>
                <p>{{ project.stats.watchers }} <a
                        :href="routes.Projects.showWatchers(project.namespace.owner, project.namespace.slug, null).absoluteURL()">watchers</a>
                </p>
                <p>{{ project.stats.downloads }} total downloads</p>
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
                    <li v-for="version in project.promoted_versions" class="list-group-item">
                        <router-link :to="{name: 'version', params: {project, permissions, 'version': version.version}}"
                                     v-slot="{ href, navigate }">
                            <a :href="href" @click="navigate">{{ version.version }}</a>
                        </router-link>
                    </li>
                </ul>
            </div>

            <div class="panel panel-default">
                <div class="panel-heading">
                    <h3 class="panel-title">Pages</h3>
                    <template v-if="permissions.includes('edit_page')">
                        <button class="new-page btn yellow btn-xs pull-right" data-toggle="modal"
                                data-target="#new-page" title="New">
                            <i class="fas fa-plus"></i>
                        </button>

                        <div class="modal fade" id="new-page" tabindex="-1" role="dialog"
                             aria-labelledby="new-page-label">
                            <div class="modal-dialog" role="document">
                                <div class="modal-content">
                                    <div class="modal-header">
                                        <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                                            <span aria-hidden="true">&times;</span>
                                        </button>
                                        <h4 class="modal-title" id="new-page-label">Create a new page</h4>
                                        <h4 v-if="pagePutError" class="modal-title" id="new-page-label-error"
                                            style="display: none; color: red">
                                            Error creating page {{ pagePutError }}
                                        </h4>
                                    </div>
                                    <div class="modal-body input-group">
                                        <div class="setting">
                                            <div class="setting-description">
                                                <h4>Page name</h4>
                                                <p>Enter a title for your new page.</p>
                                            </div>
                                            <div class="setting-content">
                                                <input v-model="newPageName" class="form-control" type="text"
                                                       id="page-name" name="page-name">
                                            </div>
                                            <div class="clearfix"></div>
                                        </div>
                                        <div class="setting setting-no-border">
                                            <div class="setting-description">
                                                <h4>Parent page</h4>
                                                <p>Select a parent page (optional)</p>
                                            </div>
                                            <div class="setting-content">
                                                <select v-model="newPageParent" class="form-control select-parent">
                                                    <option selected value="null">&lt;none&gt;</option>
                                                    <option v-for="page in pages" :value="page.slug.join('/')"
                                                            :data-slug="page.slug.join('/')">{{ page.name.join('/') }}
                                                    </option>
                                                </select>
                                            </div>
                                            <div class="clearfix"></div>
                                        </div>
                                    </div>
                                    <div class="modal-footer">
                                        <button type="button" class="btn btn-default" data-dismiss="modal"
                                                @click="resetNewPage">Close
                                        </button>
                                        <button type="button" class="btn btn-primary" @click="createNewPage">Continue
                                        </button>
                                    </div>
                                </div>
                            </div>
                        </div>
                    </template>
                </div>

                <page-list :pages="groupedPages" :project="project" :permissions="permissions"
                           :include-home="true"></page-list>
            </div>


            <member-list :members="members" :permissions="permissions" role-category="project"/>
        </div>
    </div>
</template>

<script>

    import {API} from "../../api";
    import Editor from "../../components/Editor";
    import MemberList from "../../components/MemberList";
    import {Category} from "../../enums";
    import PageList from "../../components/PageList";

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
                newPageName: "",
                newPageParent: null,
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
                type: String,
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
                }

                return acc
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
                API.request('projects/' + this.project.plugin_id + '/_pages/' + this.page).then((response) => {
                    this.description = response.content;
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
            resetNewPage() {
                this.newPageName = "";
                this.newPageParent = null
            },
            createNewPage() {
                let newPageSlug = this.newPageParent ? this.newPageParent + '/' + this.newPageName : this.newPageName;

                API.request('projects/' + this.project.plugin_id + '/_pages/' + newPageSlug, 'PUT', {
                    'name': this.newPageName,
                    'content': 'Welcome to your new page'
                }).then(res => {
                    $('#new-page'). modal('toggle');
                    this.resetNewPage();
                    this.updatePage(true)
                }).catch(err => {
                    //TODO: Better error handling here

                    console.error(err);
                    this.pagePutError = err;
                });
            }
        }
    }
</script>
