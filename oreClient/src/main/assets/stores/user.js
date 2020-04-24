import {API} from "../api";
import Vue from 'vue';

const state = {
    user: null,
    orga: null,
    membershipOrgs: [],
    membershipProjects: [],
    orgaPermissions: [],
    orgaMembers: []
}

const mutations = {
    setUser(state, payload) {
        state.user = payload.user;
    },
    setOrga(state, payload) {
        state.orga = payload.orga;
    },
    setOrgaPermissions(state, payload) {
        state.orgaPermissions = payload.orgaPermissions;
    },
    setMemberships(state, payload) {
        state.membershipOrgs = payload.memberships.filter(m => m.scope === 'organization');
        state.membershipProjects = payload.memberships.filter(m => m.scope === 'project');
    },
    clearUser(state) {
        state.user = null;
        state.orga = null;
        state.membershipOrgs = [];
        state.membershipProjects = [];
        state.orgaPermissions = [];
        state.orgaMembers = [];
    },
    updateMembers(state, payload) {
        state.orgaMembers = payload.members;
    },
    setTagline(state, payload) {
        state.user.tagline = payload.tagline;
    }
}

const actions = {
    setActiveUser(context, user) {
        if(!context.state.user || context.state.user.name !== user) {
            context.commit('clearUser')

            API.request('users/' + user).then(res => {
                context.commit({
                    type: 'setUser',
                    user: res
                })
            });

            API.request('users/' + user + '/memberships').then(res => {
                context.commit({
                    type: 'setMemberships',
                    memberships: res
                });
            });

            API.request('organizations/' + user).then(res => {
                context.commit({
                    type: 'setOrga',
                    orga: res
                });

                API.request('organizations/' + user + '/members').then(response => {
                    context.commit({
                        type: 'updateMembers',
                        members: response
                    });
                })

                API.request("permissions", {organization: user}).then(response => {
                    context.commit({
                        type: 'setOrgaPermissions',
                        orgaPermissions: response.permissions
                    })
                })
            }).catch(res => {
                if (res !== '404') {

                }
            });
        }
    }
}

export default {
    namespaced: true,
    state,
    mutations,
    actions
}
