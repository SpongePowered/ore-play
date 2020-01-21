<template>
    <div>
        <div class="row">
            <div class="col-md-8">

                <!-- Main settings -->
                <div class="panel panel-default panel-settings">
                    <div class="panel-heading">
                        <h3 class="panel-title pull-left">Settings</h3>
                        <template v-if="permissions.includes('see_hidden')">
                            <btn-hide :project="project"></btn-hide>
                            <div class="modal fade" id="modal-visibility-comment" tabindex="-1" role="dialog"
                                 aria-labelledby="modal-visibility-comment">
                                <div class="modal-dialog" role="document">
                                    <div class="modal-content">
                                        <div class="modal-header">
                                            <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                                                <span aria-hidden="true">&times;</span>
                                            </button>
                                            <h4 class="modal-title" style="color:black;">Comment</h4>
                                        </div>
                                        <div class="modal-body">
                                            <textarea class="textarea-visibility-comment form-control" rows="3"></textarea>
                                        </div>
                                        <div class="modal-footer">
                                            <button class="btn btn-default" data-dismiss="modal">Close</button>
                                            <button class="btn btn-visibility-comment-submit btn-primary"><i
                                                    class="fas fa-pencil-alt"></i> Submit
                                            </button>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </template>
                    </div>

                    <div class="panel-body">
                        <input-settings form="save" :project="project"></input-settings>

                        <!-- Summary -->
                        <div class="setting" :set="maxLength = config.ore.projects.maxDescLen">
                            <div class="setting-description">
                                <h4>Description</h4>
                                <p>A short summary of your project (max {{ maxLength }}).</p>
                            </div>
                            <input v-if="project.summary" form="save" class="form-control" type="text" :maxlength="maxLength" :value="project.summary">
                            <input v-else form="save" class="form-control" type="text" :maxlength="maxLength" placeholder="No description given.">
                            <div class="clearfix"></div>
                        </div>

                        <!-- Project icon -->
                        <div class="setting setting-icon">
                            <form id="form-icon" enctype="multipart/form-data">
                                <CSRFField></CSRFField>
                                <div class="setting-description">
                                    <h4>Icon</h4>

                                    <user-avatar :username="project.namespace.owner"
                                                 :avatarUrl="avatarUrl(project.namespace.owner)"
                                                 :imgSrc="iconUrl"
                                                 class="user-avatar-md"></user-avatar>

                                    <input class="form-control-static" type="file" id="icon" name="icon"/>
                                </div>
                                <div class="setting-content">
                                    <div class="icon-description">
                                        <p>Upload an image representative of your project.</p>
                                        <div class="btn-group pull-right">
                                            <button class="btn btn-default btn-reset">Reset</button>
                                            <button class="btn btn-info btn-upload pull-right" disabled>
                                                <i class="fas fa-upload"></i> Upload
                                            </button>
                                        </div>
                                    </div>
                                </div>
                                <div class="clearfix"></div>
                            </form>
                        </div>

                        <div v-if="permissions.inclues('edit_api_keys')" class="setting">
                            <div class="setting-description">
                                <h4>Deployment key</h4>
                                <p>
                                    Generate a unique deployment key to enable build deployment from Gradle
                                    <a href="#"><i class="fas fa-question-circle"></i></a>
                                </p>
                                @deploymentKey.map { key =>
                                <input class="form-control input-key" type="text" value="@key.value" readonly/>
                                }.getOrElse {
                                <input class="form-control input-key" type="text" value="" readonly/>
                                }
                            </div>
                            <div class="setting-content">
                                @deploymentKey.map { key =>
                                <button class="btn btn-danger btn-block btn-key-revoke" data-key-id="@key.id">
                                <span class="spinner" style="display: none;"><i
                                        class="fas fa-spinner fa-spin"></i></span>
                                    <span class="text">Revoke key</span>
                                </button>
                                }.getOrElse {
                                <button class="btn btn-info btn-block btn-key-gen">
                                <span class="spinner" style="display: none;"><i
                                        class="fas fa-spinner fa-spin"></i></span>
                                    <span class="text">Generate key</span>
                                </button>
                                }
                            </div>
                            <div class="clearfix"></div>
                        </div>

                        <!-- Rename -->
                        <div class="setting">
                            <div class="setting-description">
                                <h4 class="danger">Rename</h4>
                                <p>Rename project</p>
                            </div>
                            <div class="setting-content">
                                <input form="rename" class="form-control" type="text"
                                       :value="project.name"
                                       :maxlength="config.ore.projects.maxNameLen">
                                <button id="btn-rename" data-toggle="modal" data-target="#modal-rename"
                                        class="btn btn-warning" disabled>
                                    Rename
                                </button>
                            </div>
                            <div class="clearfix"></div>
                        </div>

                        <!-- Delete -->
                        <div v-if="permissions.includes('delete_project')" class="setting">
                            <div class="setting-description">
                                <h4 class="danger">Delete</h4>
                                <p>Once you delete a project, it cannot be recovered.</p>
                            </div>
                            <div class="setting-content">
                                <button class="btn btn-delete btn-danger" data-toggle="modal"
                                        data-target="#modal-delete">
                                    Delete
                                </button>
                            </div>
                            <div class="clearfix"></div>
                        </div>

                        <div v-if="permissions.includes('hard_delete_project')" class="setting striped">
                            <div class="setting-description">
                                <h4 class="danger">Hard Delete</h4>
                                <p>Once you delete a project, it cannot be recovered.</p>
                            </div>
                            <div class="setting-content">
                                <button class="btn btn-delete btn-danger btn-visibility-change"
                                        :data-project="project.namespace.owner + '/' + project.namespace.slug" data-level="-99"
                                        data-modal="true">
                                    <strong>Hard Delete</strong>
                                </button>
                            </div>
                            <div class="clearfix"></div>
                        </div>

                        <button name="save" class="btn btn-success btn-spinner" data-icon="fa-check">
                            <i class="fas fa-check"></i> Save changes
                        </button>
                    </div>
                </div>
            </div>

            <!-- Side panel -->
            <div class="col-md-4">
                <member-list
                        :editable="true"
                        :members="members"
                        :permissions="permissions"
                        :remove-call="routes.project.Projects.removeMember(project.namespace.owner, project.namespace.slug)"
                        :settings-call="routes.project.Projects.showSettings(project.namespace.owner, project.namespace.slug)"></member-list>
            </div>
        </div>

        <div class="modal fade" id="modal-rename" tabindex="-1" role="dialog" aria-labelledby="label-rename">
            <div class="modal-dialog" role="document">
                <div class="modal-content">
                    <div class="modal-header">
                        <button type="button" class="close" data-dismiss="modal" aria-label="Cancel">
                        <span aria-hidden="true">&times;</span>
                        </button>
                        <h4 class="modal-title" id="label-rename">Rename project</h4>
                    </div>
                    <div class="modal-body">
                        Changing your projects name can have undesired consequences. We will not setup any redirects.
                    </div>
                    <div class="modal-footer">
                        <div class="form-inline">
                            <button type="button" class="btn btn-default" data-dismiss="modal">
                                Close
                            </button>
                            <button name="rename" class="btn btn-warning">Rename</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>

        <div class="modal fade" id="modal-delete" tabindex="-1" role="dialog" aria-labelledby="label-delete">
            <div class="modal-dialog" role="document">
                <div class="modal-content">
                    <div class="modal-header">
                        <button type="button" class="close" data-dismiss="modal" aria-label="Cancel">
                            <span aria-hidden="true">&times;</span>
                        </button>
                        <h4 class="modal-title" id="label-delete">Delete project</h4>
                    </div>
                    <div class="modal-body">
                        Are you sure you want to delete your Project? This action cannot be undone. Please explain why you want to delete it.
                        <br>
                        <textarea name="comment" class="textarea-delete-comment form-control" rows="3"></textarea>
                        <br>
                        <div class="alert alert-warning">
                            WARNING: You or anybody else will not be able to use the plugin ID "{0}" in the future if you continue. If you are deleting your project to recreate it, please do not delete your project and contact the Ore staff for help.
                        </div>
                    </div>
                    <div class="modal-footer">
                        <div class="form-inline">
                            <button type="button" class="btn btn-default" data-dismiss="modal">
                                Close
                            </button>
                            <button name="delete" class="btn btn-danger">Delete</button>
                        </div>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
    import CSRFField from "../../components/CSRFField";
    import MemberList from "../../components/MemberList";
    import InputSettings from "./InputSettings";
    export default {
        components: {
            MemberList,
            CSRFField,
            InputSettings
        },
        props: {
            permissions: {
                type: Array,
                required: true
            },
            project: {
                type: Object,
                required: true
            }
        },
        computed: {
            routes() {
                return jsRoutes.controllers
            }
        }
    }
</script>
