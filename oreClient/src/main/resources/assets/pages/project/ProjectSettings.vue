<template>
    <div>
        <div class="row">
            <div class="col-md-8">

                <!-- Main settings -->
                <div class="panel panel-default panel-settings">
                    <div class="panel-heading">
                        <h3 class="panel-title pull-left">Settings</h3>
                        <template v-if="permissions.includes('see_hidden')">
                            <btn-hide :namespace="project.namespace" :project-visibility="project.visibility"></btn-hide>
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
                        <div class="setting">
                            <div class="setting-description">
                                <h4>Category</h4>
                                <p>
                                    <label for="category-setting">
                                        Categorize your project into one of {{ categories.values.length }} categories.
                                        Appropriately categorizing your project makes it easier for people to find.
                                    </label>
                                </p>
                            </div>
                            <div class="setting-content">
                                <select id="category-setting" class="form-control" v-model="category">
                                    <option v-for="categoryIt in categories.values" :value="categoryIt.id">
                                        {{ categoryIt.name }}
                                    </option>
                                </select>
                            </div>
                            <div class="clearfix"></div>
                        </div>

                        <div class="setting">
                            <div class="setting-description">
                                <h4>Keywords <i>(optional)</i></h4>
                                <p>
                                    <label for="keyword-setting">
                                        These are special words that will return your project when people add them to their searches. Max 5.
                                    </label>
                                </p>
                            </div>
                            <input id="keyword-setting"
                                   v-model="keywords"
                                   type="text"
                                   class="form-control"
                                   placeholder="sponge server plugins mods">
                            <div class="clearfix"></div>
                        </div>

                        <div class="setting">
                            <div class="setting-description">
                                <h4>Homepage <i>(optional)</i></h4>
                                <p>
                                    <label for="homepage-setting">
                                        Having a custom homepage for your project helps you look more proper, official,
                                        and gives you another place to gather information about your project.
                                    </label>
                                </p>
                            </div>
                            <input id="homepage-setting"
                                   v-model="homepage"
                                   type="url"
                                   class="form-control"
                                   placeholder="https://spongepowered.org">
                            <div class="clearfix"></div>
                        </div>

                        <div class="setting">
                            <div class="setting-description">
                                <h4>Issue tracker <i>(optional)</i></h4>
                                <p>
                                    <label for="issues-setting">
                                        Providing an issue tracker helps your users get support more easily and provides
                                        you with an easy way to track bugs.
                                    </label>
                                </p>
                            </div>
                            <input id="issues-setting"
                                   v-model="issues"
                                   type="url"
                                   class="form-control"
                                   placeholder="https://github.com/SpongePowered/SpongeAPI/issues">
                            <div class="clearfix"></div>
                        </div>

                        <div class="setting">
                            <div class="setting-description">
                                <h4>Source code <i>(optional)</i></h4>
                                <p><label for="sources-setting">Support the community of developers by making your project open source!</label></p>
                            </div>
                            <input id="sources-setting"
                                   v-model="sources"
                                   type="url"
                                   class="form-control"
                                   placeholder="https://github.com/SpongePowered/SpongeAPI">
                        </div>

                        <div class="setting">
                            <div class="setting-description">
                                <h4>External support <i>(optional)</i></h4>
                                <p>
                                    <label for="support-setting">
                                        An external place where you can offer support to your users. Could be a forum,
                                        a Discord server, or somewhere else.
                                    </label>
                                </p>
                            </div>
                            <input id="support-setting"
                                   v-model="support"
                                   type="url"
                                   class="form-control"
                                   placeholder="https://discord.gg/sponge">
                            <div class="clearfix"></div>
                        </div>

                        <div class="setting">
                            <div class="setting-description">
                                <h4>License <i>(optional)</i></h4>
                                <p>What can people do (and not do) with your project?</p>
                            </div>
                            <div class="input-group pull-left">
                                <div class="input-group-btn">
                                    <button type="button" class="btn btn-default btn-license dropdown-toggle" data-toggle="dropdown"
                                            aria-haspopup="true" aria-expanded="false">
                                        <span class="license">{{ licenseName }}</span>
                                        <span class="caret"></span>
                                    </button>
                                    <input type="text" class="form-control" style="display: none;" v-model="licenseName" />
                                    <ul class="dropdown-menu dropdown-license">
                                        <li><a>MIT</a></li>
                                        <li><a>Apache 2.0</a></li>
                                        <li><a>GNU General Public License (GPL)</a></li>
                                        <li><a>GNU Lesser General Public License (LGPL)</a></li>
                                        <li role="separator" class="divider"></li>
                                        <li><a class="license-custom">Custom&hellip;</a></li>
                                    </ul>
                                </div>
                                <input type="text" class="form-control"
                                       placeholder="https://github.com/SpongePowered/SpongeAPI/LICENSE.md" v-model="licenseUrl">
                            </div>
                            <div class="clearfix"></div>
                        </div>

                        <div class="setting">
                            <div class="setting-description">
                                <h4>Create posts on the forums</h4>
                                <p>Sets if events like a new release should automatically create a post on the forums</p>
                            </div>
                            <div class="setting-content">
                                <label>
                                    <input v-model="forumSync" type="checkbox">
                                    Make forum posts
                                </label>
                            </div>
                            <div class="clearfix"></div>
                        </div>

                        <!-- Summary -->
                        <div class="setting" :set="maxLength = config.ore.projects.maxDescLen">
                            <div class="setting-description">
                                <h4>Summary</h4>
                                <p><label for="summary-setting">A short summary of your project (max {{ maxLength }}).</label></p>
                            </div>
                            <input id="summary-setting" class="form-control" type="text" :maxlength="maxLength" v-model="summary">
                            <div class="clearfix"></div>
                        </div>

                        <button name="save" class="btn btn-success btn-spinner" data-icon="fa-check">
                            <i class="fas fa-check"></i> Save changes
                            {{ dataToSend }}
                        </button>
                    </div>
                </div>

                <div class="panel panel-default panel-settings">
                    <div clasS="panel-heading">
                        <h3 class="panel-title pull-left">Danger area</h3>
                    </div>

                    <div class="panel-body">
                        <!-- Project icon -->
                        <div class="setting setting-icon">
                            <form id="form-icon" enctype="multipart/form-data">
                                <CSRFField></CSRFField>
                                <div class="setting-description">
                                    <h4>Icon</h4>

                                    <icon :username="project.namespace.owner"
                                          :src="avatarUrl(project.namespace.owner)"
                                          :imgSrc="iconUrl"
                                          class="user-avatar-md"></icon>

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

                        <div v-if="permissions.includes('edit_api_keys')" class="setting">
                            <div class="setting-description">
                                <h4>Deployment key</h4>
                                <p>
                                    Generate a unique deployment key to enable build deployment from Gradle
                                    <a href="#"><i class="fas fa-question-circle"></i></a>
                                </p>
                                <input class="form-control input-key" type="text" :value="deployKey" readonly/>
                            </div>
                            <div class="setting-content">
                                <template v-if="deployKey !== null">
                                    <button class="btn btn-danger btn-block btn-key-revoke" :data-key-id="deployKey">
                                        <span class="spinner" style="display: none;"><i class="fas fa-spinner fa-spin"></i></span>
                                        <span class="text">Revoke key</span>
                                    </button>
                                </template>
                                <template v-else>
                                    <button class="btn btn-info btn-block btn-key-gen">
                                        <span class="spinner" style="display: none;"><i class="fas fa-spinner fa-spin"></i></span>
                                        <span class="text">Generate key</span>
                                    </button>
                                </template>
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
                    </div>
                </div>
            </div>

            <!-- Side panel -->
            <div class="col-md-4">
                <router-link :to="{name: 'settings'}" v-slot="{ href, navigate }">
                    <!-- TODO: Pass in navigate to member-list -->
                    <member-list
                            :editable="true"
                            :members="members"
                            :permissions="permissions"
                            :remove-call="routes.project.Projects.removeMember(project.namespace.owner, project.namespace.slug).absoluteURL()"
                            :settings-call="href"
                            role-category="project"></member-list>
                </router-link>
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
    import BtnHide from "../../components/BtnHide";
    import CSRFField from "../../components/CSRFField";
    import MemberList from "../../components/MemberList";
    import Icon from "../../components/Icon";
    import {avatarUrl as avatarUrlUtils, clearFromDefaults} from "../../utils"
    import {Category} from "../../enums";

    export default {
        components: {
            BtnHide,
            MemberList,
            CSRFField,
            Icon
        },
        data() {
            return {
                category: this.project.category,
                keywords: '', //TODO
                homepage: this.project.settings.homepage,
                issues: this.project.settings.issues,
                sources: this.project.settings.sources,
                support: this.project.settings.support,
                licenseName: this.project.settings.license.name,
                licenseUrl: this.project.settings.license.url,
                forumSync: this.project.settings.forum_sync,
                summary: this.project.summary,
                iconUrl: this.project.icon_url,
                deployKey: null //TODO
            }
        },
        props: {
            permissions: {
                type: Array,
                required: true
            },
            project: {
                type: Object,
                required: true
            },
            members: {
                type: Array,
                required: true
            }
        },
        computed: {
            routes() {
                return jsRoutes.controllers
            },
            config() {
                return {
                    ore: {
                        projects: {
                            //TODO: Get this from someplace better
                            maxDescLen: 120,
                            maxNameLen: 25
                        }
                    }
                }
            },
            categories() {
                return Category
            },
            keywordArr() {
                return this.keywords.split(' ')
            },
            dataToSend() {
                let base = clearFromDefaults({category: this.category, summary: this.summary}, this.project);
                base.settings = clearFromDefaults({
                    keywords: this.keywordArr,
                    homepage: this.homepage,
                    issues: this.issues,
                    support: this.support,
                    forum_sync: this.forumSync
                }, this.project.settings);
                base.settings.license = clearFromDefaults({name: this.licenseName, url: this.licenseUrl}, this.project.settings.license);

                return base
            }
        },
        methods: {
            avatarUrl(name) {
                return avatarUrlUtils(name)
            }
        }
    }
</script>
