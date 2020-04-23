<template>
    <div class="row user-header">
        <div class="header-body">
            <span class="user-badge">
                <icon v-if="user" :src="avatarUrl(user.name)" :name="user.name"
                      :extra-classes="'user-avatar-md' + (canEditOrgSettings ? 'organization-avatar' : '')"></icon>

                <template v-if="user && canEditOrgSettings">
                    <div class="edit-avatar" style="display: none;">
                        <a :href="routes.Organizations.updateAvatar(user.name).absoluteURL()"><i
                                class="fas fa-edit"></i> Edit avatar</a>
                    </div>

                    <prompt v-if="!headerData.readPrompts.includes(prompts.ChangeAvatar.id)"
                            :prompt="prompts.ChangeAvatar" idClass="popover-avatar"/>
                </template>

                <span class="user-title">
                    <h1 v-if="user" class="username">
                        {{ user.name }}

                        <template v-if="isCurrentUser && !orga">
                            <a class="user-settings" :href="config.security.api.url + '/accounts/settings'">
                                <i class="fas fa-cog" data-toggle="tooltip"
                                   data-placement="top" title="Settings"></i>
                            </a>

                            <a class="action-api" :href="routes.Users.editApiKeys(user.name).absoluteURL()">
                                <i class="fas fa-key" data-toggle="tooltip" data-placement="top" title="API Keys"></i>
                            </a>
                        </template>

                        <a v-if="permissions.includes('mod_notes_and_flags') || permissions.includes('reviewer')"
                           class="user-settings" :href="routes.Application.showActivities(user.name).absoluteURL()">
                            <i class="fas fa-calendar" data-toggle="tooltip"
                               data-placement="top" title="Activity"></i>
                        </a>

                        <a v-if="permissions.includes('edit_all_user_settings')" class="user-settings"
                           :href="routes.Application.userAdmin(user.name).absoluteURL()">
                            <i class="fas fa-wrench" data-toggle="tooltip"
                               data-placement="top" title="User Admin"></i>
                        </a>
                    </h1>

                    <div class="user-tag">
                        <i class="minor" v-if="user && user.tagline">{{ user.tagline }}</i>
                        <i v-else-if="isCurrentUser || canEditOrgSettings" class="minor">
                            Add a tagline
                        </i>

                        <a v-if="isCurrentUser || canEditOrgSettings" href="#" data-toggle="modal"
                           data-target="#modal-tagline">
                            <i class="fas fa-edit"></i>
                        </a>
                    </div>
                </span>
            </span>

            <!-- Roles -->
            <ul v-if="user" class="user-roles">
                <li v-for="role in user.roles" class="user-role channel"
                    :style="{'background-color': role.color}">
                    {{ role.title }}
                </li>
            </ul>

            <div v-if="user" class="user-info">
                <i class="minor">{{ user.project_count }}&nbsp;{{ user.project_count === 1 ? 'project' : 'projects' }}</i><br/>
                <i class="minor">
                    A member since {{ user.join_date ? prettifyDate(user.join_date) : prettifyDate(user.created_at) }}
                </i><br/>
                <a :href="'https://forums.spongepowered.org/users/' + user.name">
                    View on forums <i class="fas fa-external-link-alt"></i>
                </a>
            </div>

        </div>
    </div>
</template>

<script>
    import {mapState} from 'vuex'
    import {Role, Prompt as PromptEnum} from "../enums";
    import Icon from "./Icon";
    import Prompt from "./Prompt";
    import {avatarUrl} from "../utils";
    import config from "../config.json5";
    import {API} from "../api";

    export default {
        components: {Icon, Prompt},
        computed: {
            roles() {
                return Role
            },
            prompts() {
                return PromptEnum
            },
            routes: function () {
                return jsRoutes.controllers;
            },
            isCurrentUser() {
                return this.currentUser && this.user && this.currentUser.name === this.user.name;
            },
            canEditOrgSettings() {
                return this.orgaPermissions.includes('edit_organization_settings')
            },
            config() {
                return config;
            },
            ...mapState('global', ['currentUser', 'permissions', 'headerData']),
            ...mapState('user', ['user', 'orga', 'orgaPermissions']),
        },
        methods: {
            prettifyDate(rawDate) {
                return moment(rawDate).format("MMM DD[,] YYYY");
            },
            avatarUrl
        }
    }
</script>