<template>
    <div>
        <div v-if="editable && permissions.includes('manage_subject_members')">
            <role-select id="select-role" class="pull-right" :hidden="true" :role-category="roleCategory"></role-select>

            <ul style="display: none">
                <li id="row-user" class="list-group-item">
                    <input type="hidden"/>
                    <icon :src="avatarUrl('Spongie')" class="user-avatar-xs"></icon>
                    <a class="username"></a>
                    <i class="fas fa-times user-cancel"></i>
                    <role-select class="pull-right" :role-category="roleCategory"></role-select>
                </li>
            </ul>

            <div class="modal fade" id="modal-user-delete" tabindex="-1" role="dialog"
                 aria-labelledby="label-user-delete">
                <div class="modal-dialog" role="document">
                    <div class="modal-content">
                        <button type="button" class="close" data-dismiss="modal"
                                aria-label="Close">
                            <span aria-hidden="true">&times;</span>
                        </button>
                        <h4 class="modal-title" id="label-user-delete">Remove member</h4>
                    </div>
                    <div class="modal-body">Are you sure you want to remove this user?</div>
                    <div class="modal-footer">
                        <form :action="removeCall" method="post" class="form-inline">
                            <CSRFField></CSRFField>
                            <input type="hidden" name="username"/>
                            <button type="button" class="btn btn-default" data-dismiss="modal">
                                Close
                            </button>
                            <button type="submit" class="btn btn-danger">Remove</button>
                        </form>
                    </div>
                </div>
            </div>
        </div>

        <div class="alert alert-danger member-error" style="display: none">
            <span>error</span>
        </div>

        <div class="panel panel-default">
            <div class="panel-heading">
                <h3 class="pull-left panel-title">Members</h3>

                <div v-if="permissions.includes('manage_subject_members')" class="pull-right">
                    <a v-if="!editable" :href="settingsCall"
                       class="btn yellow btn-xs">
                        <i class="fas fa-pencil-alt"></i>
                    </a>

                    <form v-if="saveCall !== null" :action="saveCall" method="post" id="save">
                        <CSRFField></CSRFField>
                        <button class="btn-members-save btn btn-default btn-panel btn-xs" data-toggle="tooltip"
                                data-placement="top" data-title="Save Users" style="display: none;">
                            <i class="fas fa-paper-plane"></i>
                        </button>
                    </form>
                </div>
            </div>

            <ul class="list-members list-group">
                <!-- Member list -->
                <template v-for="member in members" class="list-group-item">
                    <li v-for="role in member.roles">
                        <icon :name="member.user" :src="avatarUrl(member.user)" ,
                                     class="user-avatar-xs"></icon>
                        <a class="username" :href="routes.Users.showProjects(member.user)">
                            {{ member.user }}
                        </a>
                        <p style="display: none;" class="role-id">{{ role.name }}</p>
                        <template
                                v-if="editable && permissions.includes('manage_subject_members') && role.permissions.includes('manage_subject_members')">
                            <a href="#">
                                <i style="padding-left:5px" class="fas fa-trash" data-toggle="modal"
                                   data-target="#modal-user-delete"></i>
                            </a>
                            <a href="#"><i style="padding-left:5px" class="fas fa-edit"></i></a>
                        </template>

                        <span class="minor pull-right">
                        <template v-if="role.is_accepted">{{ role.title }}</template>
                        <span v-else class="minor">(Invited as {{ role.title }})</span>
                    </span>
                    </li>
                </template>

                <!-- User search -->
                <li v-if="permissions.includes('manage_subject_members') && editable" class="list-group-item">
                    <user-search></user-search>
                </li>

            </ul>
        </div>
    </div>
</template>

<script>
    import {avatarUrl} from "../utils";
    import CSRFField from "./CSRFField";
    import Icon from "./Icon"
    import UserSearch from "./UserSearch"
    import RoleSelect from "./RoleSelect"

    export default {
        components: {
            CSRFField,
            Icon,
            UserSearch,
            RoleSelect
        },
        props: {
            roleCategory: {
                type: String,
                required: true
            },
            members: Array,
            permissions: Array,
            editable: {
                type: Boolean,
                default: false
            },
            removeCall: String,
            settingsCall: String,
            saveCall: String
        },
        computed: {
            routes: function () {
                return jsRoutes.controllers;
            }
        },
        methods: {
            avatarUrl
        }
    }
</script>