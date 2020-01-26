<template>
    <div v-if="this.versionObj">
        <!-- Version header -->
        <div class="row">
            <div class="col-md-12 version-header">
                <!-- Title -->
                <div class="clearfix">
                    <h1 class="pull-left">{{ versionObj.name }}</h1>
                    <!-- <span class="channel channel-head" style="background-color: @v.c.color.hex;">@v.c.name</span> -->
                </div>

                <!-- User info -->
                <p class="user date pull-left">
                    <a :href="routes.Users.showProjects(project.namespace.owner)">
                        <strong>{{ project.namespace.owner }}</strong>
                    </a>
                    released this version on {{ prettifyDate(versionObj.created_at) }}
                </p>

                <!-- Buttons -->

                <div class="pull-right version-actions">
                    <div class="version-icons">
                        <div>
                            <template v-if="isReviewStateChecked">
                                <i v-if="permissions.includes('reviewer') && versionObj.review_state.approved_by && versionObj.review_state.approved_at" class="minor">
                                    <strong> {{ versionObj.review_state.approved_by }} </strong> approved this version on <strong> {{ prettifyDate(versionObj.review_state.approved_at) }} </strong>
                                </i>
                                <i data-toggle="tooltip"
                                   data-placement="left"
                                   :title="versionObj.review_state = 'partially_reviewed' ? 'Partially Approved' : 'Approved'"
                                   class="far fa-lg fa-check-circle"></i>
                            </template>
                        </div>
                    </div>

                    <div class="version-buttons pull-right">
                        <div><span class="date">{{ humanFileSize(versionObj.file_info.size_bytes) }}</span></div>

                        <div>
                            <a v-if="permissions.includes('reviewer')" :href="routes.Reviews.showReviews(project.namespace.owner, project.namespace.slug, versionObj.name)" :class="{btn: true, 'btn-info': isReviewStateChecked, 'btn-success': !isReviewStateChecked}">
                                <template v-if="isReviewStateChecked">Review logs</template>
                                <i v-else class="fas fa-play"></i> Start review
                            </a>

                            <a v-if="versionObj.visibility === 'softDelete'" class="btn btn-danger" disabled data-toggle="tooltip" data-placement="top"
                               title="This version has already been deleted">
                                <i class="fas fa-trash"></i> Delete
                            </a>
                            <template v-else-if="permissions.includes('delete_version')">
                                <a v-if="publicVersions === 1" class="btn btn-danger" disabled data-toggle="tooltip" data-placement="top"
                                   title="Every project must have at least one version">
                                    <i class="fas fa-trash"></i> Delete
                                </a>
                                <button v-else type="button" class="btn btn-danger" data-toggle="modal" data-target="#modal-delete">
                                    <i class="fas fa-trash"></i> Delete
                                </button>
                            </template>

                            <div class="btn-group btn-download">
                                <a :href="routes.project.Versions.download(project.namespace.owner, project.namespace.slug, versionObj.name, null)"
                                   title="Download the latest recommended version" data-toggle="tooltip"
                                data-placement="bottom" class="btn btn-primary">
                                <i class="fas fa-download"></i> Download
                                </a>
                                <button type="button" class="btn btn-primary dropdown-toggle" data-toggle="dropdown" aria-haspopup="true" aria-expanded="false">
                                    <span class="caret"></span>
                                    <span class="sr-only">Toggle Dropdown</span>
                                </button>
                                <ul class="dropdown-menu dropdown-menu-right">
                                    <li><a :href="routes.project.Versions.download(project.namespace.owner, project.namespace.slug, versionObj.name, null)">Download</a></li>
                                    <li><a href="#" class="copy-url" :data-clipboard-text="config.app.baseUrl + routes.project.Versions.download(project.namespace.owner, project.namespace.slug, versionObj.name, null)">Copy URL</a></li>
                                </ul>
                            </div>

                            <div v-if="permissions.includes('view_logs')" class="dropdown dropdown-menu-right" style="display: inline-block">
                                <button class="btn btn-alert dropdown-toggle" type="button" id="admin-version-actions" data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">
                                    Admin actions
                                    <span class="caret"></span>
                                </button>
                                <ul class="dropdown-menu" aria-labelledby="admin-version-actions">
                                    <li><a :href="routes.Application.showLog(null, null, null, versionObj.name, null, null, null)">User Action Logs</a></li>
                                    <template v-if="permissions.includes('reviewer')">
                                        <li v-if="versionObj.visibility = 'softDelete'"><a href="#" data-toggle="modal" data-target="#modal-restore">Undo delete</a></li>
                                        <li v-if="permissions.includes('hard_delete_version') && (publicVersions > 1 || versionObj.visibility === 'softDelete')"><a href="#" data-toggle="modal" data-target="#modal-harddelete" style="color: darkred">Hard delete</a></li>
                                    </template>
                                </ul>
                            </div>

                        </div>
                    </div>
                </div>
            </div>
        </div>


        <!-- Description -->
        <div class="row version-description">
            <div id="description" class="col-md-8">
                <div class="row">
                    <div v-if="!isReviewStateChecked" class="col-md-12">
                        <div class="alert-review alert alert-info" role="alert">
                            <i class="fas fa-info-circle"></i>
                            This version has not been reviewed by our moderation staff and may not be safe for download.
                        </div>
                    </div>
                    <div class="col-md-12">
                        <editor :save-call="routes.project.Versions.saveDescription(project.namespace.owner, project.namespace.slug, versionObj.name)"
                                :enabled="permissions.includes('edit_page')"
                                :raw="versionDescription ? versionDescription : ''"
                                subject="Version">
                        </editor>
                    </div>
                </div>
            </div>


            <!-- Dependencies -->
            <div v-if="dependencyObs" class="col-md-4">
                <div class="panel panel-default">
                    <div class="panel-heading">
                        <h3 class="panel-title">Dependencies</h3>
                    </div>
                    <ul class="list-group">

                        <li v-for="platform in platforms.getPlatforms(dependencyObs.map(d => d.pluginId))" class="list-group-item">
                            <a :href="platform.url">
                                <strong>{{ platform.name }}</strong>
                            </a>
                            <p class="version-string" :set="depVersion = dependencyObs.filter(d => d.pluginId === platform.id)[0].version" v-if="depVersion">
                                {{ depVersion }}
                            </p>
                        </li>


                        <li class="list-group-item" v-for="depend in dependencyObs.filter(d => !platforms.isPlatformTag(d.pluginId))">
                            <!-- TODO: Use vue link -->
                            <a v-if="depend.project" :href="routes.project.Projects.show(project.namespace.owner, project.namespace.slug)">
                                <strong>{{ project.name }}</strong>
                            </a>
                            <div v-else class="minor">
                                {{ depend.pluginId }}
                                <i class="fas fa-question-circle"
                                   title="This plugin is not available for download on Ore"
                                data-toggle="tooltip" data-placement="right"></i>
                            </div>
                            <p class="version-string" v-if="depend.version">{{ depend.version }}</p>
                        </li>
                    </ul>
                </div>
            </div>
            <p v-else class="minor text-center"><i>This release has no dependencies</i></p>
        </div>

        <div v-if="permissions.includes('delete_version') && publicVersions !== 1" class="modal fade" id="modal-delete" tabindex="-1" role="dialog" aria-labelledby="label-delete">
            <div class="modal-dialog" role="document">
                <div class="modal-content">
                    <div class="modal-header">
                        <button type="button" class="close" data-dismiss="modal" aria-label="Cancel">
                            <span aria-hidden="true">&times;</span>
                        </button>
                        <h4 class="modal-title" id="label-delete">Delete version</h4>
                    </div>
                    <form method="post" :action="routes.project.Versions.softDelete(project.namespace.owner, project.namespace.slug, versionObj.name)">
                        <div class="modal-body">
                            Are you sure you want to delete this version? This action cannot be undone. Please explain why you want to delete it.
                            <textarea name="comment" class="textarea-delete-comment form-control" rows="3"></textarea>
                        </div>
                        <div class="modal-footer">
                            <div class="form-inline">
                                <CSRFField></CSRFField>
                                <button type="button" class="btn btn-default" data-dismiss="modal">
                                    Close
                                </button>
                                <input type="submit" name="delete" value="Delete" class="btn btn-danger">
                            </div>
                        </div>

                    </form>
                </div>
            </div>
        </div>

        <div v-if="permissions.includes('reviewer') && versionObj.visibility === 'softDelete'" class="modal fade" id="modal-restore" tabindex="-1" role="dialog" aria-labelledby="label-delete">
            <div class="modal-dialog" role="document">
                <div class="modal-content">
                    <div class="modal-header">
                        <button type="button" class="close" data-dismiss="modal" aria-label="Cancel">
                            <span aria-hidden="true">&times;</span>
                        </button>
                        <h4 class="modal-title" id="label-delete">Restore deleted</h4>
                    </div>
                    <form method="post" :action="routes.project.Versions.restore(project.namespace.owner, project.namespace.slug, versionObj.name)">
                        <div class="modal-body">
                            <textarea name="comment" class="textarea-delete-comment form-control" rows="3"></textarea>
                        </div>
                        <div class="modal-footer">
                            <div class="form-inline">
                                <CSRFField></CSRFField>
                                <button type="button" class="btn btn-default" data-dismiss="modal">
                                    Close
                                </button>
                                <input type="submit" name="delete" value="Restore deleted" class="btn btn-success">
                            </div>
                        </div>
                    </form>
                </div>
            </div>
        </div>

        <div v-if="permissions.includes('reviewer') && permissions.includes('hard_delete_version')" class="modal fade" id="modal-harddelete" tabindex="-1" role="dialog" aria-labelledby="label-delete">
            <div class="modal-dialog" role="document">
                <div class="modal-content">
                    <div class="modal-header">
                        <button type="button" class="close" data-dismiss="modal" aria-label="Cancel">
                            <span aria-hidden="true">&times;</span>
                        </button>
                        <h4 class="modal-title" id="label-delete">Hard delete</h4>
                    </div>
                    <form method="post" :action="routes.project.Versions.delete(project.namespace.owner, project.namespace.slug, versionObj.name)">
                        <div class="modal-body">
                            <textarea name="comment" class="textarea-delete-comment form-control" rows="3"></textarea>
                        </div>
                        <div class="modal-footer">
                            <div class="form-inline">
                                <CSRFField></CSRFField>
                                <button type="button" class="btn btn-default" data-dismiss="modal">
                                    Close
                                </button>
                                <input type="submit" name="delete" value="Hard delete" class="btn btn-danger">
                            </div>
                        </div>
                    </form>
                </div>
            </div>
        </div>
    </div>
    <div v-else>

    </div>

</template>

<script>

    import Editor from "../../components/Editor";
    import {API} from "../../api";
    import CSRFField from "../../components/CSRFField";

    export default {
        components: {
            CSRFField,
            Editor,
        },
        data() {
            return {
                versionObj: null,
                dependencyObs: []
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
            version: {
                type: String,
                required: true
            }
        },
        computed: {
            routes: function () {
                return jsRoutes.controllers;
            },
            isReviewStateChecked() {
                return this.versionObj.review_state === 'partially_reviewed' || this.versionObj.review_state === 'reviewed'
            }
        },
        created() {
            this.updateVersion();
        },
        watch: {
            '$route': 'updateVersion'
        },
        method: {
            updateVersion() {
                API.request('projects/' + this.project.plugin_id + '/versions/' + this.version).then(v => {
                    this.versionObj = v;

                    //TODO: Handle dependencies
                    v.dependencies

                }).catch((error) => {
                    this.versionObj = null;

                    if(error === 404) {
                        //TODO
                    } else {

                    }
                })
            }
        }
    }
</script>