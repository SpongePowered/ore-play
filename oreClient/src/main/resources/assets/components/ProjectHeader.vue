<template>
    <div class="project-header-container">
        <div class="row" v-if="project.visibility !== 'public'">
            <div class="col-xs-12">
                <div class="alert alert-danger" role="alert" style="margin: 0.2em 0 0 0">
                    <span v-if="project.visibility === 'needsChanges'">
                        <a v-if="permissions.includes(permission.EditPage)" class="btn btn-success pull-right"
                           :href="fullSlug + '/manage/sendforapproval'">Send for approval</a>
                        <strong>@messages("visibility.notice." + project.visibility.nameKey)</strong>
                        {{ renderVisibilityChange("Unknown")}}
                    </span>
                    <span v-else-if="project.visibility === 'softDelete'">
                        @messages("visibility.notice." + project.visibility.nameKey, project.lastVisibilityChangeUser)
                        {{ renderVisibilityChange("") }}
                    </span>
                    <span v-else>
                        @messages("visibility.notice." + project.visibility.nameKey)
                    </span>


                </div>
            </div>
        </div>

        <div class="row">
            <div class="col-md-6">
                <div class="project-header">
                    <div class="project-path">
                        <a :href="routes.Users.showProjects(project.namespace.owner).absoluteURL()">{{ project.namespace.owner }}</a>
                        /
                        <a class="project-name" :href="routes.project.Projects.show(project.namespace.owner, project.namespace.slug).absoluteURL()">{{ project.name }}</a>
                    </div>
                    <div>
                        <i class="minor" :title="project.summary"> {{project.summary}} </i>
                    </div>
                </div>
            </div>
            <div class="col-md-6">
                <div v-if="!noButtons" class="pull-right project-controls">
                    <span v-if="reported" class="flag-msg">
                        <i class="fas fa-thumbs-up"></i>
                        Flag submitted for review
                    </span>

                    <template v-if="project.visibility !== 'softDelete'">
                        <template v-if="!isOwner">
                            <button class="btn btn-default btn-star">
                                <i :class="staredClasses"></i>
                                <span class="starred">{{ project.stats.stars }}</span>
                            </button>

                            <button class="btn btn-watch btn-default" :class="{watching: project.stats.watching}">
                                <template v-if="project.stats.watching">
                                    <i class="fas fa-eye-slash"></i>
                                    <span class="watch-status">Unwatch</span>
                                </template>
                                <template v-else>
                                    <i class="fas fa-eye"></i>
                                    <span class="watch-status">Watch</span>
                                </template>
                            </button>

                        </template>
                        <span v-else class="minor stars-static">
                            <i :class="staredClasses"></i>
                            {{ project.stats.stars }}
                        </span>

                        <button data-toggle="modal" data-target="#modal-flag" class="btn btn-default">
                            <i class="fas fa-flag"></i> @messages("project.flag")
                        </button>

                        <div class="modal fade" id="modal-flag" tabindex="-1" role="dialog"
                             aria-labelledby="label-flag">
                            <div class="modal-dialog" role="document">
                                <div class="modal-content">
                                    <div class="modal-header">
                                        <button type="button" class="close" data-dismiss="modal"
                                                aria-label="Close">
                                            <span aria-hidden="true">&times;</span>
                                        </button>
                                        <h4 class="modal-title" id="label-flag">Flag project</h4>
                                    </div>
                                    @form(action = Projects.flag(project.project.ownerName, project.project.slug)) {
                                    @CSRF.formField
                                    <div class="modal-body">
                                        <ul class="list-group list-flags">
                                            @for(i <- FlagReason.values.indices) {
                                            <li class="list-group-item">
                                                <span>@FlagReason.withValue(i).title</span>
                                                <span class="pull-right">
                                                        <input required type="radio"
                                                               value="@FlagReason.withValue(i).value"
                                                               name="flag-reason"/>
                                                        </span>
                                            </li>
                                            }
                                        </ul>
                                        <input class="form-control" name="comment" type="text"
                                               maxlength="255" required="required"
                                               placeholder="@messages('ph.comment')&hellip;"/>
                                    </div>
                                    <div class="modal-footer">
                                        <button type="button" class="btn btn-default" data-dismiss="modal">
                                            Close
                                        </button>
                                        <button type="submit" class="btn btn-primary">Flag</button>
                                    </div>
                                    }
                                </div>
                            </div>
                        </div>

                    </template>

                    <template v-if="permissions.includes(permission.ModNotesAndFlags) || permissions.includes(permission.ViewLogs)">
                        <button class="btn btn-alert dropdown-toggle" type="button" id="admin-actions"
                                data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">
                            Admin actions
                            <span class="caret"></span>
                        </button>
                        <ul class="dropdown-menu" aria-labelledby="admin-actions">
                            <li v-if="permissions.includes(permission.ModNotesAndFlags)">
                                <a :href="routes.project.Projects.showFlags(project.namespace.owner, project.namespace.slug).absoluteURL()">Flag history
                                    (@project.flagCount) </a>
                            </li>
                            <li v-if="permissions.includes(permission.ModNotesAndFlags)">
                                <a :href="routes.project.Projects.showNotes(project.namespace.owner, project.namespace.slug).absoluteURL()">Staff notes
                                    (@project.noteCount) </a>
                            </li>
                            <li v-if="permissions.includes(permission.ViewLogs)">
                                <a :href="routes.Application.showLog(null, null, project.plugin_id, null, null, null, null).absoluteURL()">User
                                    Action Logs</a>
                            </li>
                            <li>
                                <a :href="'https://forums.spongepowered.org/users/' + project.namespace.owner">Owner on forum <i
                                        class="fas fa-external-link-alt" aria-hidden="true"></i></a>
                            </li>
                        </ul>
                    </template>
                </div>
            </div>
        </div>

        <div class="row row-nav">
            <div class="col-md-12">
                <div class="navbar navbar-default project-navbar pull-left">
                    <div class="navbar-inner">
                        <ul class="nav navbar-nav">
                            <router-link :to="{name: 'home', params: {project, permissions}}" v-slot="{ href, navigate, isExactActive }">
                                <li :class="[isExactActive && 'active']">
                                    <a :href="href" @click="navigate"><i class="fas fa-book"></i> Docs</a>
                                </li>
                            </router-link>

                            <router-link :to="{name: 'versions'}" v-slot="{ href, navigate, isExactActive }">
                                <li :class="[isExactActive && 'active']">
                                    <a :href="href" @click="navigate"><i class="fas fa-download"></i> Versions</a>
                                </li>
                            </router-link>

                            <!-- TODO only show if topic -->
                            <router-link :to="{name: 'discussion'}" v-slot="{ href, navigate, isExactActive }">
                                <li :class="[isExactActive && 'active']">
                                    <a :href="href" @click="navigate"><i class="fas fa-users"></i> Discuss</a>
                                </li>
                            </router-link>

                            <router-link v-if="permissions.includes(permission.EditSubjectSettings)" :to="{name: 'settings'}" v-slot="{ href, navigate, isExactActive }">
                                <li :class="[isExactActive && 'active']">
                                    <a :href="href" @click="navigate"><i class="fas fas fa-cog"></i> Settings</a>
                                </li>
                            </router-link>

                            <li v-if="project.settings.homepage" id="homepage">
                                <a :title="project.settings.homepage" target="_blank" rel="noopener"
                                   :href="routes.Application.linkOut(project.settings.homepage).absoluteURL()">
                                    <i class="fas fa-home"></i> Homepage <i class="fas fa-external-link-alt"></i></a>
                            </li>

                            <li v-if="project.settings.issues" id="issues">
                                <a :title="project.settings.issues" target="_blank" rel="noopener"
                                   :href="routes.Application.linkOut(project.settings.issues).absoluteURL()">
                                    <i class="fas fa-bug"></i> Issues <i class="fas fa-external-link-alt"></i></a>
                            </li>

                            <li v-if="project.settings.sources" id="source">
                                <a :title="project.settings.sources" target="_blank" rel="noopener"
                                   :href="routes.Application.linkOut(project.settings.sources).absoluteURL()">
                                    <i class="fas fa-code"></i> Source <i class="fas fa-external-link-alt"></i>
                                </a>
                            </li>

                            <li v-if="project.settings.support" id="support">
                                <a :title="project.settings.support" target="_blank" rel="noopener"
                                   :href="routes.Application.linkOut(project.settings.support).absoluteURL()">
                                    <i class="fas fa-question-circle"></i> Support <i
                                        class="fas fa-external-link-alt"></i>
                                </a>
                            </li>
                        </ul>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
    import {Permission} from "../enums";

    export default {
        props: {
            noButtons: {
                type: Boolean,
                default: false
            },
            reported: {
                type: Boolean,
                default: false
            },
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
            fullSlug: function () {
                return project.owner_name + project.slug
            },
            routes: function () {
                return jsRoutes.controllers;
            },
            staredClasses: function () {
                return {
                    fas: this.project.user_actions.stared,
                    far: !this.project.user_actions.stared,
                    'fa-star': true
                }
            },
            isOwner: function () {
                //TODO
                return false;
            },
            permission() {
                return Permission;
            }
        },
        methods: {
            renderVisibilityChange: function (orElse) {
                //TODO
            },
        }
    }
</script>