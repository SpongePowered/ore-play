<template>
    <div class="project-header-container">
        <div class="row" v-if="p.visibility !== 'public'">
            <div class="col-xs-12">
                <div class="alert alert-danger" role="alert" style="margin: 0.2em 0 0 0">
                    <span v-if="p.visibility === 'needsChanges'">
                        <a v-if="permissions.includes('edit_page')" class="btn btn-success pull-right"
                           :href="fullSlug + '/manage/sendforapproval'">Send for approval</a>
                        <strong>@messages("visibility.notice." + p.visibility.nameKey)</strong>
                        {{ renderVisibilityChange("Unknown")}}
                    </span>
                    <span v-else-if="p.visibility === 'softDelete'">
                        @messages("visibility.notice." + p.visibility.nameKey, p.lastVisibilityChangeUser)
                        {{ renderVisibilityChange("") }}
                    </span>
                    <span v-else>
                        @messages("visibility.notice." + p.visibility.nameKey)
                    </span>


                </div>
            </div>
        </div>

        <div class="row">
            <div class="col-md-6">
                <div class="project-header">
                    <div class="project-path">
                        <a :href="routes.Users.showProjects(p.owner_name)">@p.owner_name</a>
                        /
                        <a class="project-name" :href="routes.Projects.show(p.owner_name, p.slug)">@p.name</a>
                    </div>
                    <div>
                        <i class="minor" :title="p.description"> {{p.description}} </i>
                    </div>
                </div>
            </div>
            <div class="col-md-6">
                <div v-if="!noButtons" class="pull-right project-controls">
                    <span v-if="reported" class="flag-msg">
                        <i class="fas fa-thumbs-up"></i>
                        Flag submitted for review
                    </span>

                    <template v-if="p.visibility !== 'softDelete'">
                        <template v-if="isOwner">
                            <button class="btn btn-default btn-star">
                                <i :class="staredClasses"></i>
                                <span class="starred">{{ p.stats.stars }}</span>
                            </button>

                            <button class="btn btn-watch btn-default" :class="{watching: p.stats.watching}">
                                <template v-if="p.stats.watching">
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
                            {{ p.stats.stars }}
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
                                    @form(action = Projects.flag(p.project.ownerName, p.project.slug)) {
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

                    <template v-if="permissions.includes('mod_notes_and_flags') || permissions.includes('view_logs')">
                        <button class="btn btn-alert dropdown-toggle" type="button" id="admin-actions"
                                data-toggle="dropdown" aria-haspopup="true" aria-expanded="true">
                            Admin actions
                            <span class="caret"></span>
                        </button>
                        <ul class="dropdown-menu" aria-labelledby="admin-actions">
                            <li v-if="permissions.includes('mod_notes_and_flags')">
                                <a href="@Projects.showFlags(p.project.ownerName, p.project.slug)">Flag history
                                    (@p.flagCount) </a>
                            </li>
                            <li v-if="permissions.includes('mod_notes_and_flags')">
                                <a href="@Projects.showNotes(p.project.ownerName, p.project.slug)">Staff notes
                                    (@p.noteCount) </a>
                            </li>
                            <li v-if="permissions.includes('view_logs')">
                                <a href="@appRoutes.showLog(None, None, Some(p.project.pluginId), None, None, None, None)">User
                                    Action Logs</a>
                            </li>
                            <li>
                                <a href="https://forums.spongepowered.org/users/@p.project.ownerName">Owner on forum <i
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
                            <!-- Tabs -->
                            <li id="docs" class="">
                                <a href="@Projects.show(p.project.ownerName, p.project.slug)">
                                    <i class="fas fa-book"></i> @messages("project.docs")</a>
                            </li>

                            <li id="versions" class="">
                                <a href="@Versions.showList(p.project.ownerName, p.project.slug)">
                                    <i class="fas fa-download"></i> @messages("project.versions")
                                </a>
                            </li>

                            <li v-if="p.discourse.topic_id !== null" id="discussion" class="">
                                <a href="@Projects.showDiscussion(p.project.ownerName, p.project.slug)">
                                    <i class="fas fa-users"></i> @messages("project.discuss")
                                </a>
                            </li>

                            <!-- Show manager if permitted -->
                            <li v-if="permissions.includes('edit_project_settings')" id="settings" class="">
                                <a href="@Projects.showSettings(p.project.ownerName, p.project.slug)">
                                    <i class="fas fa-cog"></i> @messages("project.settings")
                                </a>
                            </li>

                            <li v-if="p.settings.homepage !== null" id="homepage">
                                <a title="@homepage" target="_blank" rel="noopener"
                                   href="@routes.Application.linkOut(homepage)">
                                    <i class="fas fa-home"></i> Homepage <i class="fas fa-external-link-alt"></i></a>
                            </li>

                            <li v-if="p.settings.issues !== null" id="issues">
                                <a title="@issues" target="_blank" rel="noopener"
                                   href="@routes.Application.linkOut(issues)">
                                    <i class="fas fa-bug"></i> Issues <i class="fas fa-external-link-alt"></i></a>
                            </li>

                            <li v-if="p.settings.source !== null" id="source">
                                <a title="@source" target="_blank" rel="noopener"
                                   href="@routes.Application.linkOut(source)">
                                    <i class="fas fa-code"></i> Source <i class="fas fa-external-link-alt"></i>
                                </a>
                            </li>

                            <li v-if="p.settings.support !== null" id="support">
                                <a title="@support" target="_blank" rel="noopener"
                                   href="@routes.Application.linkOut(support)">
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

    <slot></slot>

</template>

<script>
    import {API} from "../api";

    export default {
        data: function () {
            return {
                permissions: [],
                p: {}
            }
        },
        props: {
            noButtons: {
                type: Boolean,
                default: false
            },
            pluginId: {
                type: String,
                required: true
            },
            reported: {
                type: Boolean,
                default: false
            }
        },
        computed: {
            fullSlug: function () {
                return p.owner_name + p.slug
            },
            routes: function () {
                return jsRoutes.controllers.project;
            },
            staredClasses: function () {
                return {
                    fas: p.user_actions.stared,
                    far: !p.user_actions.stared,
                    'fa-star': true
                }
            },
            isOwner: function () {
                //TODO
            }
        },
        created() {
            API.request("permissions", "GET", {pluginId: this.pluginId}).then((response) => {
                this.permissions = response.permissions;
            });
            API.request('projects/' + this.pluginId).then((response) => {
                this.project = response
            })
        },
        methods: {
            renderVisibilityChange: function (orElse) {
                //TODO
            }
        }
    }
</script>