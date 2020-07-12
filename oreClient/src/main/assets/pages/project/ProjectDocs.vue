<template>
  <div class="row">
    <div class="col-md-9">
      <div class="row">
        <div class="col-md-12">
          <editor :enabled="permissions.includes('edit_page')" :raw="description" subject="Page" @saved="savePage" />
        </div>
      </div>
    </div>

    <div class="col-md-3">
      <div v-if="project" class="stats minor">
        <p>Category: {{ parseCategory(project.category) }}</p>
        <p>Published on {{ parseDate(project.created_at) }}</p>
        <p>{{ formatStats(project.stats.views) }} views</p>
        <p>{{ formatStats(project.stats.downloads) }} total downloads</p>
        <p v-if="project.settings.license.name !== null">
          <span>Licensed under </span>
          <a target="_blank" rel="noopener" :href="project.settings.license.url">{{ project.settings.license.name }}</a>
        </p>
      </div>

      <div class="panel panel-default">
        <div class="panel-heading">
          <h3 class="panel-title">
            Promoted Versions
          </h3>
        </div>

        <ul v-if="project" class="list-group promoted-list">
          <li
            v-for="version in project.promoted_versions"
            :key="version.version"
            class="list-group-item row row-no-gutters"
            style="line-height: 2.4em;"
          >
            <div class="col-lg-8 col-12">
              <router-link
                v-slot="{ href, navigate }"
                :to="{ name: 'version', params: { project, permissions, version: version.version } }"
              >
                <a :href="href" @click="navigate">{{ version.version }}</a>
              </router-link>
            </div>
            <div class="col-lg-4 col-12">
              <a
                class="pull-right btn btn-primary"
                :href="
                  routes.Versions.download(
                    project.namespace.owner,
                    project.namespace.slug,
                    version.version,
                    null
                  ).absoluteURL()
                "
              >
                <FontAwesomeIcon :icon="['fas', 'download']" /> Download
              </a>
            </div>
          </li>
        </ul>
      </div>

      <div class="panel panel-default">
        <div class="panel-heading">
          <h3 class="panel-title">
            Pages
          </h3>
          <template v-if="permissions.includes('edit_page')">
            <button
              class="new-page btn yellow btn-xs pull-right"
              data-toggle="modal"
              data-target="#edit-page"
              title="New"
            >
              <FontAwesomeIcon :icon="['fas', 'plus']" />
            </button>

            <div id="edit-page" class="modal fade" tabindex="-1" role="dialog" aria-labelledby="page-label">
              <div class="modal-dialog" role="document">
                <div class="modal-content">
                  <div class="modal-header">
                    <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                      <span aria-hidden="true">&times;</span>
                    </button>
                    <h4 id="page-label" class="modal-title">
                      <template v-if="newPage">
                        Create a new page
                      </template>
                      <template v-else>
                        Edit page
                      </template>
                    </h4>
                    <h4
                      v-if="pagePutError"
                      id="page-label-error"
                      class="modal-title"
                      style="display: none; color: red;"
                    >
                      Error updating page {{ pagePutError }}
                    </h4>
                  </div>
                  <div class="modal-body input-group">
                    <div class="setting">
                      <div class="setting-description">
                        <h4>Page name</h4>
                        <p>Enter a title for your page.</p>
                      </div>
                      <div class="setting-content">
                        <input
                          id="page-name"
                          v-model="requestPage.name"
                          class="form-control"
                          type="text"
                          name="page-name"
                        />
                      </div>
                      <div class="clearfix" />
                    </div>
                    <div class="setting">
                      <div class="setting-description">
                        <h4>Parent page</h4>
                        <p>Select a parent page (optional)</p>
                      </div>
                      <div class="setting-content">
                        <select v-model="requestPage.parent" class="form-control select-parent">
                          <option selected value="null">
                            &lt;none&gt;
                          </option>
                          <option
                            v-for="(forPage, index) in pages"
                            :key="index"
                            :value="forPage.slug.join('/')"
                            :data-slug="forPage.slug.join('/')"
                          >
                            {{ forPage.name.join('/') }}
                          </option>
                        </select>
                      </div>
                      <div class="clearfix" />
                    </div>
                    <div class="setting setting-no-border">
                      <div class="setting-description">
                        <h4>Navigational</h4>
                        <p>
                          Makes the page only useful for nagivation.
                          <b>This will delete all content currently on the page.</b>
                        </p>
                      </div>
                      <div class="setting-content">
                        <div class="form-check">
                          <input
                            id="page-navigational"
                            v-model="requestPage.navigational"
                            class="form-check-input position-static"
                            type="checkbox"
                            name="page-navigational"
                            aria-label="Navigational"
                          />
                        </div>
                      </div>
                      <div class="clearfix" />
                    </div>
                  </div>
                  <div class="modal-footer">
                    <button type="button" class="btn btn-default" data-dismiss="modal" @click="resetPutPage">
                      Close
                    </button>
                    <button v-if="!newPage" type="button" class="btn btn-danger" @click="deletePage">
                      Delete
                    </button>
                    <button
                      :disabled="!requestPage.name || requestPage.name.includes('/')"
                      type="button"
                      class="btn btn-primary"
                      @click="updateCreatePage"
                    >
                      Continue
                    </button>
                  </div>
                </div>
              </div>
            </div>
          </template>
        </div>

        <page-list :pages="groupedPages" :include-home="true" @edit-page="startEditPage" />
      </div>

      <member-list
        :permissions="permissions"
        :members="members"
        role-category="project"
        :settings-route="{ name: 'settings' }"
      />
    </div>
  </div>
</template>

<script>
import isEqual from 'lodash/isEqual'
import NProgress from 'nprogress'
import { mapState } from 'vuex'
import moment from 'moment'
import { API } from '../../api'
import Editor from '../../components/Editor'
import MemberList from '../../components/MemberList'
import { Category } from '../../enums'
import PageList from '../../components/PageList'
import { numberWithCommas } from '../../utils'

export default {
  components: {
    PageList,
    Editor,
    MemberList,
  },
  props: {
    page: {
      type: [Array, String],
      required: true,
    },
  },
  data() {
    return {
      description: '',
      pages: [],
      requestPage: {},
      pagePutError: null,
    }
  },
  computed: {
    routes() {
      return jsRoutes.controllers.project
    },
    groupedPages() {
      const nonHome = this.pages.filter((p) => p.slug.length !== 1 || p.slug[0] !== 'Home')
      const acc = {}

      for (const page of nonHome) {
        let obj = acc

        for (let i = 0; i < page.slug.length - 1; i++) {
          const k = page.slug[i]
          if (typeof obj[k] === 'undefined') {
            obj[k] = {}
          }

          if (typeof obj[k].children === 'undefined') {
            obj[k].children = {}
          }

          obj = obj[k].children
        }

        const key = page.slug[page.slug.length - 1]
        if (typeof obj[key] === 'undefined') {
          obj[key] = {}
        }

        obj[key].slug = page.slug
        obj[key].name = page.name
        obj[key].navigational = page.navigational
      }

      return acc
    },
    newPage() {
      return typeof this.requestPage.existing === 'undefined'
    },
    splitPage() {
      return Array.isArray(this.page) ? this.page : this.page.split('/')
    },
    joinedPage() {
      return Array.isArray(this.page) ? this.page.join('/') : this.page
    },
    currentPage() {
      return this.pages.filter((p) => isEqual(p.slug, this.splitPage))[0]
    },
    ...mapState('project', ['project', 'permissions', 'members']),
  },
  watch: {
    $route() {
      this.updatePage(false)
    },
    project(val, oldVal) {
      if (!oldVal || val.plugin_id !== oldVal.plugin_id) {
        this.updatePage(true)
      }
    },
  },
  created() {
    if (this.project) {
      this.updatePage(true)
    }
  },
  methods: {
    updatePage(fetchPages) {
      NProgress.start()
      API.request('projects/' + this.project.plugin_id + '/_pages/' + this.joinedPage)
        .then((response) => {
          if (response.content === null) {
            this.description = ''
          } else {
            this.description = response.content
          }
          NProgress.done()
        })
        .catch((error) => {
          this.description = ''

          if (error === 404) {
            // TODO
          } else {
          }
        })

      if (fetchPages) {
        API.request('projects/' + this.project.plugin_id + '/_pages').then((pageList) => {
          this.pages = pageList.pages
        })
      }
    },
    parseDate(rawDate) {
      return moment(rawDate).format('MMM DD[,] YYYY')
    },
    parseCategory(category) {
      return Category.fromId(category).name
    },
    resetPutPage() {
      this.requestPage = {}
    },
    updateCreatePage() {
      const page = this.requestPage

      if (page.name.includes('/')) {
        return
      }

      if (page.parent === 'null') {
        page.parent = null
      }

      let content = null
      if (!page.navigational) {
        content = page.content ? page.content : 'Welcome to your new page'
      }
      let action
      if (this.newPage) {
        const pageSlug = page.parent ? page.parent + '/' + page.name : page.name
        action = API.request('projects/' + this.project.plugin_id + '/_pages/' + pageSlug, 'PUT', {
          name: page.name,
          content,
        })
      } else {
        const pageSlug = page.oldParent ? page.oldParent + '/' + page.oldName : page.oldName
        action = API.request('projects/' + this.project.plugin_id + '/_pages/' + pageSlug, 'PATCH', {
          name: page.name,
          content,
          parent: page.parent,
        })
      }

      action
        .then((res) => {
          $('#edit-page').modal('toggle')
          this.resetPutPage()
          this.updatePage(true)
        })
        .catch((err) => {
          // TODO: Better error handling here

          this.pagePutError = err
        })
    },
    savePage(newContent) {
      API.request('projects/' + this.project.plugin_id + '/_pages/' + this.joinedPage, 'PUT', {
        name: this.currentPage.name[this.currentPage.name.length - 1],
        content: newContent,
      }).then((res) => {
        this.description = newContent
      }) // TODO: Handle error here
    },
    deletePage() {
      const page = this.requestPage
      const pageSlug = page.parent ? page.parent + '/' + page.name : page.name
      // TODO

      API.request('projects/' + this.project.plugin_id + '/_pages/' + pageSlug, 'DELETE')
        .then((res) => {
          $('#edit-page').modal('toggle')
          this.resetPutPage()

          if (pageSlug === this.joinedPage) {
            this.$router.push({ name: 'home', params: { project: this.project, permissions: this.permissions } })
          } else {
            this.updatePage(true)
          }
        })
        .catch((err) => {
          // TODO: Better error handling here

          this.pagePutError = err
        })
    },
    startEditPage(page) {
      this.$set(this.requestPage, 'existing', true)
      this.$set(this.requestPage, 'oldName', page.name[page.name.length - 1])
      this.$set(this.requestPage, 'name', this.requestPage.oldName)
      this.$set(this.requestPage, 'oldParent', page.slug.length === 1 ? null : page.slug.slice(0, -1).join('/'))
      this.$set(this.requestPage, 'parent', this.requestPage.oldParent)
      this.$set(this.requestPage, 'navigational', page.navigational)

      API.request('projects/' + this.project.plugin_id + '/_pages/' + page.slug.join('/'))
        .then((response) => {
          this.$set(this.requestPage, 'content', response.content)
          $('#edit-page').modal('toggle')
        })
        .catch((error) => {
          if (error === 404) {
            this.$set(this.requestPage, 'existing', undefined)
            $('#edit-page').modal('toggle')
          } else {
            // TODO
          }
        })
    },
    formatStats(number) {
      return numberWithCommas(number)
    },
  },
}
</script>
