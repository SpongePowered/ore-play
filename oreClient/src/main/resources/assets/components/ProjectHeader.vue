<template>
    <div class="project-header-container">
        <div class="row" v-if="project.visibility !== 'public'">
            <div class="col-xs-12">
                <div class="alert alert-danger" role="alert" style="margin: 0.2em 0 0 0">
                    <span v-if="project.visibility === 'new'">
                        This project is new, and will not be shown to others until a version has been uploaded. If a version is not uploaded over a longer time the project will be deleted.
                    </span>
                    <span v-else-if="project.visibility === 'needsChanges'">
                        <a v-if="permissions.includes(permission.EditPage)" class="btn btn-success pull-right"
                           :href="fullSlug + '/manage/sendforapproval'">Send for approval</a>
                        <strong>This project requires changes:</strong>
                        {{ renderVisibilityChange("Unknown")}}
                    </span>
                    <span v-else-if="project.visibility === 'needsApproval'">
                        You have sent the project for review
                    </span>
                    <span v-else-if="project.visibility === 'softDelete'">
                        Project deleted by {{ project.lastVisibilityChangeUser }}
                        {{ renderVisibilityChange("") }}
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
                        <router-link :to="{name: 'home', params: {project, permissions}}" v-slot="{ href, navigate, isExactActive }">
                            <a class="project-name" :href="href" @click="navigate">{{ project.name }}</a>
                        </router-link>
                    </div>
                    <div>
                        <i class="minor" :title="project.summary"> {{project.summary}} </i>
                    </div>
                </div>
            </div>
            <div class="col-md-6">
                <div v-if="!noButtons" class="pull-right project-controls">
                    <span v-if="reported" class="flag-msg">
                        <font-awesome-icon :icon="['fas', 'thumbs-up']" />
                        Flag submitted for review
                    </span>

                    <template v-if="project.visibility !== 'softDelete'">
                        <template v-if="!isOwner">
                            <button class="btn btn-default btn-star">
                                <font-awesome-icon :icon="staredIcon" />
                                <span class="starred">{{ project.stats.stars }}</span>
                            </button>

                            <button class="btn btn-watch btn-default" :class="{watching: project.stats.watching}">
                                <template v-if="project.stats.watching">
                                    <font-awesome-icon :icon="['fas', 'eye-slash']" />
                                    <span class="watch-status">Unwatch</span>
                                </template>
                                <template v-else>
                                    <font-awesome-icon :icon="['fas', 'eye']" />
                                    <span class="watch-status">Watch</span>
                                </template>
                            </button>

                        </template>
                        <span v-else class="minor stars-static">
                            <font-awesome-icon :icon="staredIcon" />
                            {{ project.stats.stars }}
                        </span>

                        <button data-toggle="modal" data-target="#modal-flag" class="btn btn-default">
                            <font-awesome-icon :icon="['fas', 'flag']" /> Flag
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
                                    <form :action="routes.project.Projects.flag(project.namespace.owner, project.namespace.slug).absoluteURL()" method="post">
                                        <CSRFField></CSRFField>
                                        <div class="modal-body">
                                            <ul class="list-group list-flags">
                                                <li v-for="reason in flagReason.values" class="list-group-item">
                                                    <span>{{ reason.title }}</span>
                                                    <span class="pull-right">
                                                        <input required type="radio"
                                                               :value="reason.value"
                                                               name="flag-reason"/>
                                                        </span>
                                                </li>
                                            </ul>
                                            <input class="form-control" name="comment" type="text"
                                                   maxlength="255" required="required"
                                                   placeholder="Comment&hellip;"/>
                                        </div>
                                        <div class="modal-footer">
                                            <button type="button" class="btn btn-default" data-dismiss="modal">
                                                Close
                                            </button>
                                            <button type="submit" class="btn btn-primary">Flag</button>
                                        </div>
                                    </form>
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
                                <a :href="routes.project.Projects.showFlags(project.namespace.owner, project.namespace.slug).absoluteURL()">
                                    Flag history ({{ project.flagCount }})
                                </a>
                            </li>
                            <li v-if="permissions.includes(permission.ModNotesAndFlags)">
                                <a :href="routes.project.Projects.showNotes(project.namespace.owner, project.namespace.slug).absoluteURL()">
                                    Staff notes ({{ project.noteCount }})
                                </a>
                            </li>
                            <li v-if="permissions.includes(permission.ViewLogs)">
                                <a :href="routes.Application.showLog(null, null, project.plugin_id, null, null, null, null).absoluteURL()">
                                    User Action Logs
                                </a>
                            </li>
                            <li>
                                <a :href="'https://forums.spongepowered.org/users/' + project.namespace.owner">
                                    Owner on forum <font-awesome-icon :icon="['fas', 'external-link-alt']" aria-hidden="true" />
                                </a>
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
                                    <a :href="href" @click="navigate"><font-awesome-icon :icon="['fas', 'book']" /> Docs</a>
                                </li>
                            </router-link>

                            <router-link :to="{name: 'versions'}" v-slot="{ href, navigate, isActive }">
                                <li :class="[isActive && 'active']">
                                    <a :href="href" @click="navigate"><font-awesome-icon :icon="['fas', 'download']" /> Versions</a>
                                </li>
                            </router-link>

                            <!-- TODO only show if topic -->
                            <router-link :to="{name: 'discussion'}" v-slot="{ href, navigate, isActive }">
                                <li :class="[isActive && 'active']">
                                    <a :href="href" @click="navigate"><font-awesome-icon :icon="['fas', 'users']" /> Discuss</a>
                                </li>
                            </router-link>

                            <router-link v-if="permissions.includes(permission.EditSubjectSettings)" :to="{name: 'settings'}" v-slot="{ href, navigate, isActive }">
                                <li :class="[isActive && 'active']">
                                    <a :href="href" @click="navigate"><font-awesome-icon :icon="['fas', 'cog']" /> Settings</a>
                                </li>
                            </router-link>

                            <li v-if="project.settings.homepage" id="homepage">
                                <a :title="project.settings.homepage" target="_blank" rel="noopener"
                                   :href="routes.Application.linkOut(project.settings.homepage).absoluteURL()">
                                    <font-awesome-icon :icon="['fas', 'home']" /> Homepage <font-awesome-icon :icon="['fas', 'external-link-alt']" /></a>
                            </li>

                            <li v-if="project.settings.issues" id="issues">
                                <a :title="project.settings.issues" target="_blank" rel="noopener"
                                   :href="routes.Application.linkOut(project.settings.issues).absoluteURL()">
                                    <font-awesome-icon :icon="['fas', 'bug']" /> Issues <font-awesome-icon :icon="['fas', 'external-link-alt']" /></a>
                            </li>

                            <li v-if="project.settings.sources" id="source">
                                <a :title="project.settings.sources" target="_blank" rel="noopener"
                                   :href="routes.Application.linkOut(project.settings.sources).absoluteURL()">
                                    <font-awesome-icon :icon="['fas', 'code']" /> Source <font-awesome-icon :icon="['fas', 'external-link-alt']" />
                                </a>
                            </li>

                            <li v-if="project.settings.support" id="support">
                                <a :title="project.settings.support" target="_blank" rel="noopener"
                                   :href="routes.Application.linkOut(project.settings.support).absoluteURL()">
                                    <font-awesome-icon :icon="['fas', 'question-circle']" /> Support <font-awesome-icon :icon="['fas', 'external-link-alt']" />
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
    import {Permission, FlagReason} from "../enums";
    import CSRFField from "./CSRFField";

    export default {
        components: {
            CSRFField
        },
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
            },
            currentUser: Object
        },
        computed: {
            fullSlug: function () {
                return project.namespace.owner + '/' + project.slug
            },
            routes: function () {
                return jsRoutes.controllers;
            },
            staredIcon: function () {
                return [
                    this.project.user_actions.stared ? 'fas' : 'far',
                    'star'
                ]
            },
            isOwner: function () {
                return this.currentUser && this.project.namespace.owner === this.currentUser.name;
            },
            permission() {
                return Permission;
            },
            flagReason() {
                return FlagReason
            }
        },
        methods: {
            renderVisibilityChange: function (orElse) {
                //TODO
            },
        }
    }
</script>