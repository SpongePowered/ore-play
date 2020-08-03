import Vue from 'vue'
import isEqual from 'lodash/isEqual'
import { API } from '../api'

const state = {
  project: null,
  permissions: [],
  members: [],
  notFound: false,
}
const mutations = {
  updateProject(state, payload) {
    state.notFound = false
    state.project = payload.project
  },
  updatePermissions(state, payload) {
    state.permissions = payload.permissions
  },
  updateMembers(state, payload) {
    state.members = payload.members
  },
  setVisibility(state, payload) {
    Vue.set(state.project, 'visibility', payload.visibility)
  },
  clearProject(state) {
    state.project = null
    state.permissions = []
    state.members = []
  },
  toggleStarred(state) {
    if (state.project.user_actions.starred) {
      Vue.set(state.project.stats, 'stars', state.project.stats.stars - 1)
    } else {
      Vue.set(state.project.stats, 'stars', state.project.stats.stars + 1)
    }

    Vue.set(state.project.user_actions, 'starred', !state.project.user_actions.starred)
  },
  setWatching(state, payload) {
    const watching = payload.watching
    if (state.project.user_actions.watching !== watching) {
      if (watching) {
        Vue.set(state.project.stats, 'watchers', state.project.stats.watchers + 1)
      } else {
        Vue.set(state.project.stats, 'watchers', state.project.stats.watchers - 1)
      }
    }

    Vue.set(state.project.user_actions, 'watching', watching)
  },
  projectNotFound(state) {
    state.notFound = true
  },
}
const actions = {
  setActiveProjectFromFetched(context, project) {
    const projectsDiffer = !context.state.project || context.state.project.plugin_id !== project.plugin_id
    context.commit({
      type: 'updateProject',
      project,
    })

    if (projectsDiffer || !context.state.permissions.length) {
      API.request('permissions', 'GET', { pluginId: project.plugin_id }).then((response) => {
        context.commit({
          type: 'updatePermissions',
          permissions: response.permissions,
        })
      })
    }

    if (projectsDiffer || !context.state.members.length) {
      API.request('projects/' + project.plugin_id + '/members').then((response) => {
        context.commit({
          type: 'updateMembers',
          members: response,
        })
      })
    }
  },
  setActiveProject(context, project) {
    if (typeof project === 'string') {
      if (!context.state.project || context.state.project.plugin_id !== project) {
        API.request('projects/' + project).then((res) => context.dispatch('setActiveProjectFromFetched', res))
      }
    } else if (!context.state.project || !isEqual(context.state.project.namespace, project)) {
      API.request('projects?exact=true&owner=' + project.owner + '&q=' + project.slug).then((res) => {
        if (res.result.length) {
          context.dispatch('setActiveProjectFromFetched', res.result[0])
        } else {
          context.dispatch('projectNotFound')
        }
      })
    }
  },
}

export default {
  namespaced: true,
  state,
  mutations,
  actions,
}
