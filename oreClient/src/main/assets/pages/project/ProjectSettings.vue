<template>
  <div v-if="project">
    <div class="row">
      <div class="col-md-8">
        <!-- Main settings -->
        <div class="panel panel-default panel-settings">
          <div class="panel-heading">
            <h3 class="panel-title pull-left">
              Settings
            </h3>
            <template v-if="permissions.includes('see_hidden')">
              <btn-hide
                :current-visibility="project.visibility"
                :endpoint="'projects/' + project.plugin_id + '/visibility'"
                emit-location="project/setVisibility"
              />
            </template>
          </div>

          <div class="panel-body">
            <!-- Summary -->
            <div class="setting" :set="(maxLength = config.ore.projects.maxDescLen)">
              <div class="setting-description">
                <h4>Summary</h4>
                <p>
                  <label for="summary-setting">A short summary of your project (max {{ maxLength }}).</label>
                </p>
              </div>
              <input id="summary-setting" v-model="summary" class="form-control" type="text" :maxlength="maxLength" />
              <div class="clearfix" />
            </div>

            <div class="setting">
              <div class="setting-description">
                <h4>Category</h4>
                <p>
                  <label for="category-setting">
                    Categorize your project into one of {{ categories.values.length }} categories. Appropriately
                    categorizing your project makes it easier for people to find.
                  </label>
                </p>
              </div>
              <div class="setting-content">
                <select id="category-setting" v-model="category" class="form-control">
                  <option v-for="categoryIt in categories.values" :key="categoryIt.id" :value="categoryIt.id">
                    {{ categoryIt.name }}
                  </option>
                </select>
              </div>
              <div class="clearfix" />
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
              <input
                id="keyword-setting"
                v-model="keywords"
                type="text"
                class="form-control"
                placeholder="sponge server plugins mods"
              />
              <div class="clearfix" />
            </div>

            <div class="setting">
              <div class="setting-description">
                <h4>Homepage <i>(optional)</i></h4>
                <p>
                  <label for="homepage-setting">
                    Having a custom homepage for your project helps you look more proper, official, and gives you
                    another place to gather information about your project.
                  </label>
                </p>
              </div>
              <input
                id="homepage-setting"
                v-model="homepage"
                type="url"
                class="form-control"
                placeholder="https://spongepowered.org"
              />
              <div class="clearfix" />
            </div>

            <div class="setting">
              <div class="setting-description">
                <h4>Issue tracker <i>(optional)</i></h4>
                <p>
                  <label for="issues-setting">
                    Providing an issue tracker helps your users get support more easily and provides you with an easy
                    way to track bugs.
                  </label>
                </p>
              </div>
              <input
                id="issues-setting"
                v-model="issues"
                type="url"
                class="form-control"
                placeholder="https://github.com/SpongePowered/SpongeAPI/issues"
              />
              <div class="clearfix" />
            </div>

            <div class="setting">
              <div class="setting-description">
                <h4>Source code <i>(optional)</i></h4>
                <p>
                  <label for="sources-setting"
                    >Support the community of developers by making your project open source!</label
                  >
                </p>
              </div>
              <input
                id="sources-setting"
                v-model="sources"
                type="url"
                class="form-control"
                placeholder="https://github.com/SpongePowered/SpongeAPI"
              />
            </div>

            <div class="setting">
              <div class="setting-description">
                <h4>External support <i>(optional)</i></h4>
                <p>
                  <label for="support-setting">
                    An external place where you can offer support to your users. Could be a forum, a Discord server, or
                    somewhere else.
                  </label>
                </p>
              </div>
              <input
                id="support-setting"
                v-model="support"
                type="url"
                class="form-control"
                placeholder="https://discord.gg/sponge"
              />
              <div class="clearfix" />
            </div>

            <div class="setting">
              <div class="setting-description">
                <h4>License <i>(optional)</i></h4>
                <p>What can people do (and not do) with your project?</p>
              </div>
              <div class="input-group pull-left">
                <div class="input-group-btn">
                  <button
                    type="button"
                    class="btn btn-default btn-license dropdown-toggle"
                    data-toggle="dropdown"
                    aria-haspopup="true"
                    aria-expanded="false"
                  >
                    <span class="license">{{ licenseName }}</span>
                    <span class="caret" />
                  </button>
                  <input v-model="licenseName" type="text" class="form-control" style="display: none;" />
                  <ul class="dropdown-menu dropdown-license">
                    <li><a>MIT</a></li>
                    <li><a>Apache 2.0</a></li>
                    <li><a>GNU General Public License (GPL)</a></li>
                    <li><a>GNU Lesser General Public License (LGPL)</a></li>
                    <li role="separator" class="divider" />
                    <li><a class="license-custom">Custom&hellip;</a></li>
                  </ul>
                </div>
                <input
                  v-model="licenseUrl"
                  type="text"
                  class="form-control"
                  placeholder="https://github.com/SpongePowered/SpongeAPI/LICENSE.md"
                />
              </div>
              <div class="clearfix" />
            </div>

            <div class="setting">
              <div class="setting-description">
                <h4>Create posts on the forums</h4>
                <p>Sets if events like a new release should automatically create a post on the forums</p>
              </div>
              <div class="setting-content">
                <label>
                  <input v-model="forumSync" type="checkbox" />
                  Make forum posts
                </label>
              </div>
              <div class="clearfix" />
            </div>

            <button name="save" class="btn btn-success" @click="sendProjectUpdate(dataToSend)">
              <FontAwesomeIcon :icon="saveChangesIcon" :spin="sendingChanges" /> Save changes
            </button>
          </div>
        </div>

        <div class="panel panel-default panel-settings">
          <div clasS="panel-heading">
            <h3 class="panel-title pull-left">
              Danger area
            </h3>
          </div>

          <div class="panel-body">
            <!-- Project icon -->
            <div class="setting setting-icon">
              <form id="form-icon" enctype="multipart/form-data" method="post">
                <CSRFField />
                <div class="setting-description">
                  <h4>Icon</h4>

                  <icon :src="iconUrl" extra-classes="user-avatar-md" />

                  <input id="icon" class="form-control-static" type="file" name="icon" @change="selectedLogo = true" />
                </div>
                <div class="setting-content">
                  <div class="icon-description">
                    <p>Upload an image representative of your project.</p>
                    <div class="btn-group pull-right">
                      <button class="btn btn-default btn-reset" @click.prevent="resetIcon">
                        Reset
                      </button>
                      <button
                        class="btn btn-info btn-upload pull-right"
                        :disabled="!selectedLogo"
                        @click.prevent="updateIcon"
                      >
                        <FontAwesomeIcon :icon="['fas', 'upload']" /> Upload
                      </button>
                    </div>
                  </div>
                </div>
                <div class="clearfix" />
              </form>
            </div>

            <div v-if="permissions.includes('edit_api_keys')" class="setting">
              <div class="setting-description">
                <h4>Deployment key</h4>
                <p>
                  Generate a unique deployment key to enable build deployment from Gradle
                  <a href="#"><FontAwesomeIcon :icon="['fas', 'question-circle']" /></a>
                </p>
                <input class="form-control input-key" type="text" :value="deployKey ? deployKey.key : ''" readonly />
              </div>
              <div class="setting-content">
                <template v-if="deployKey !== null">
                  <button class="btn btn-danger btn-block" @click="revokeDeployKey">
                    <span class="spinner" :style="{ display: showDeployKeySpinner ? 'inline' : 'none' }">
                      <FontAwesomeIcon :icon="['fas', 'spinner']" spin />
                    </span>
                    <span class="text">Revoke key</span>
                  </button>
                </template>
                <template v-else>
                  <button class="btn btn-info btn-block" @click="generateDeployKey">
                    <span class="spinner" :style="{ display: showDeployKeySpinner ? 'inline' : 'none' }">
                      <FontAwesomeIcon :icon="['fas', 'spinner']" spin />
                    </span>
                    <span class="text">Generate key</span>
                  </button>
                </template>
              </div>
              <div class="clearfix" />
            </div>

            <!-- Rename -->
            <div class="setting">
              <div class="setting-description">
                <h4 class="danger">
                  Rename
                </h4>
                <p>
                  Rename project. <strong>NOTE: This will not change the project's plugin id, only it's name.</strong>
                </p>
              </div>
              <div class="setting-content">
                <input v-model="newName" class="form-control" type="text" :maxlength="config.ore.projects.maxNameLen" />
                <button
                  id="btn-rename"
                  data-toggle="modal"
                  data-target="#modal-rename"
                  class="btn btn-warning"
                  :disabled="newName === project.name"
                >
                  Rename
                </button>
              </div>
              <div class="clearfix" />
            </div>

            <!-- Transfer -->
            <div class="setting">
              <div class="setting-description">
                <h4 class="danger">
                  Transfer
                </h4>
                <p>Transfer project ownership.</p>
              </div>
              <div class="setting-content">
                <select v-model="newOwner" class="form-control">
                  <option v-for="member in adminMembers" :key="member.user" :value="member.user">
                    {{ member.user }}
                  </option>
                </select>
                <button
                  id="btn-transfer"
                  data-toggle="modal"
                  data-target="#modal-transfer"
                  class="btn btn-warning"
                  :disabled="newOwner === project.namespace.owner"
                >
                  Transfer
                </button>
              </div>
              <div class="clearfix" />
            </div>

            <!-- Delete -->
            <div v-if="permissions.includes('delete_project')" class="setting">
              <div class="setting-description">
                <h4 class="danger">
                  Delete
                </h4>
                <p>Once you delete a project, it cannot be recovered.</p>
              </div>
              <div class="setting-content">
                <button class="btn btn-delete btn-danger" data-toggle="modal" data-target="#modal-delete">
                  Delete
                </button>
              </div>
              <div class="clearfix" />
            </div>

            <div v-if="permissions.includes('hard_delete_project')" class="setting striped">
              <div class="setting-description">
                <h4 class="danger">
                  Hard Delete
                </h4>
                <p>Once you delete a project, it cannot be recovered.</p>
              </div>
              <div class="setting-content">
                <button
                  class="btn btn-delete btn-danger btn-visibility-change"
                  data-toggle="modal"
                  data-target="#modal-delete"
                  @click="hardDelete = true"
                >
                  <strong>Hard Delete</strong>
                </button>
              </div>
              <div class="clearfix" />
            </div>
          </div>
        </div>
      </div>

      <!-- Side panel -->
      <div class="col-md-4">
        <router-link :to="{ name: 'settings' }">
          <member-list
            :editable="true"
            :members="members"
            :permissions="permissions"
            role-category="project"
            new-role="Project_Support"
            :endpoint="'projects/' + project.plugin_id + '/members'"
            commit-location="project/updateMembers"
          />
        </router-link>
      </div>
    </div>

    <div id="modal-rename" class="modal fade" tabindex="-1" role="dialog" aria-labelledby="label-rename">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal" aria-label="Cancel">
              <span aria-hidden="true">&times;</span>
            </button>
            <h4 id="label-rename" class="modal-title">
              Rename project
            </h4>
          </div>
          <div class="modal-body">
            Changing your projects name can have undesired consequences. We will not setup any redirects.
          </div>
          <div class="modal-footer">
            <div class="form-inline">
              <button type="button" class="btn btn-default" data-dismiss="modal">
                Close
              </button>
              <button name="rename" class="btn btn-warning" @click="sendProjectUpdate({ name: newName })">
                Rename
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div id="modal-transfer" class="modal fade" tabindex="-1" role="dialog" aria-labelledby="label-transfer">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal" aria-label="Cancel">
              <span aria-hidden="true">&times;</span>
            </button>
            <h4 id="label-transfer" class="modal-title">
              Transfer project ownership
            </h4>
          </div>
          <div class="modal-body">
            Changing the owner of a project can have undesired consequences. We will not setup any redirects.
          </div>
          <div class="modal-footer">
            <div class="form-inline">
              <button type="button" class="btn btn-default" data-dismiss="modal">
                Close
              </button>
              <button
                name="transfer"
                class="btn btn-warning"
                @click="sendProjectUpdate({ namespace: { owner: newOwner } })"
              >
                Transfer
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>

    <div id="modal-delete" class="modal fade" tabindex="-1" role="dialog" aria-labelledby="label-delete">
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal" aria-label="Cancel">
              <span aria-hidden="true">&times;</span>
            </button>
            <h4 id="label-delete" class="modal-title">
              Delete project
            </h4>
          </div>
          <div class="modal-body">
            Are you sure you want to delete your Project? This action cannot be undone. Please explain why you want to
            delete it.
            <br />
            <textarea v-model="deleteReason" name="comment" class="textarea-delete-comment form-control" rows="3" />
            <br />
            <div class="alert alert-warning">
              WARNING: You or anybody else will not be able to use the plugin ID "{0}" in the future if you continue. If
              you are deleting your project to recreate it, please do not delete your project and contact the Ore staff
              for help.
            </div>
          </div>
          <div class="modal-footer">
            <div class="form-inline">
              <button type="button" class="btn btn-default" data-dismiss="modal" @click="resetDeleteData">
                Close
              </button>
              <button name="delete" class="btn btn-danger" @click="deleteProject">
                Delete
              </button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </div>
  <div v-else>
    <FontAwesomeIcon :icon="['fas', 'spinner']" spin />
    Loading
  </div>
</template>

<script>
import { mapState } from 'vuex'
import BtnHide from '../../components/BtnHide'
import CSRFField from '../../components/CSRFField'
import MemberList from '../../components/MemberList'
import Icon from '../../components/Icon'
import { avatarUrl as avatarUrlUtils, cleanEmptyObject, clearFromDefaults, nullIfEmpty } from '../../utils'
import { Category } from '../../enums'
import { API } from '../../api'
import config from '../../config.json5'

export default {
  components: {
    BtnHide,
    MemberList,
    CSRFField,
    Icon,
  },
  data() {
    // Seems project hasn't been initialized yet, so we use the store directly
    const project = this.$store.state.project.project

    const alwaysPresent = {
      deployKey: null,
      showDeployKeySpinner: false,
      sendingChanges: false,
      selectedLogo: false,
      deleteReason: '',
      hardDelete: false,
    }

    if (project) {
      this.updateDataFromProject(project, alwaysPresent)

      return alwaysPresent
    } else {
      return {
        newName: null,
        newOwner: null,
        category: null,
        keywords: [],
        homepage: null,
        issues: null,
        sources: null,
        support: null,
        licenseName: null,
        licenseUrl: null,
        forumSync: null,
        summary: null,
        iconUrl: null,
        ...alwaysPresent,
      }
    }
  },
  computed: {
    routes() {
      return jsRoutes.controllers
    },
    config() {
      return config
    },
    categories() {
      return Category
    },
    keywordArr() {
      return this.keywords.length ? this.keywords.split(' ') : []
    },
    dataToSend() {
      const base = clearFromDefaults({ category: this.category, summary: this.summary }, this.project)
      base.settings = clearFromDefaults(
        {
          keywords: this.keywordArr,
          homepage: nullIfEmpty(this.homepage),
          issues: nullIfEmpty(this.issues),
          support: nullIfEmpty(this.support),
          forum_sync: this.forumSync,
        },
        this.project.settings
      )
      base.settings.license = clearFromDefaults(
        { name: nullIfEmpty(this.licenseName), url: nullIfEmpty(this.licenseUrl) },
        this.project.settings.license
      )

      const ret = cleanEmptyObject(base)
      return ret || {}
    },
    saveChangesIcon() {
      return ['fas', this.sendingChanges ? 'spinner' : 'check']
    },
    adminMembers() {
      return this.members.filter((member) => {
        return (member.role.name === 'Project_Owner' || member.role.name === 'Project_Admin') && member.role.is_accepted
      })
    },
    ...mapState('project', ['project', 'permissions', 'members']),
  },
  watch: {
    project(val, oldVal) {
      if (!oldVal || val.plugin_id !== oldVal.plugin_id) {
        this.updateDataFromProject(val, this)
      }
    },
  },
  created() {
    if (this.project) {
      this.getDeployKey(this.project)
    }
  },
  methods: {
    updateDataFromProject(project, self) {
      this.getDeployKey(project)

      self.newName = project.name
      self.newOwner = project.namespace.owner
      self.category = project.category
      self.keywords = ''
      self.homepage = project.settings.homepage
      self.issues = project.settings.issues
      self.sources = project.settings.sources
      self.support = project.settings.support
      self.licenseName = project.settings.license.name
      self.licenseUrl = project.settings.license.url
      self.forumSync = project.settings.forum_sync
      self.summary = project.summary
      self.iconUrl = project.icon_url
    },
    avatarUrl(name) {
      return avatarUrlUtils(name)
    },
    sendProjectUpdate(update) {
      if (Object.entries(update).length) {
        this.sendingChanges = true
        $('#modal-rename').modal('hide')
        $('#modal-transfer').modal('hide')
        const changedName = this.project.name !== this.newName
        const changedOwner = this.project.namespace.owner !== this.newOwner

        API.request('projects/' + this.project.plugin_id, 'PATCH', update)
          .then((result) => {
            this.$store.commit({
              type: 'project/updateProject',
              project: result,
            })
            this.sendingChanges = false
            this.updateDataFromProject(result, this)

            if (changedName || changedOwner) {
              this.$router.replace({ name: 'settings', params: result.namespace })
            }
          })
          .catch((failed) => {
            this.sendingChanges = false
            // TODO
          })
      } else {
        // Some "illusion" of the change being acknowledged is always good
        this.sendingChanges = true
        setTimeout(() => {
          this.sendingChanges = false
        }, 150)
      }
    },
    updateIcon() {
      const form = document.getElementById('form-icon')
      const data = new FormData(form)
      const iconUrl = '/' + this.project.namespace.owner + '/' + this.project.namespace.slug + '/icon'
      fetch(iconUrl, {
        credentials: 'same-origin',
        method: 'post',
        body: data,
      }).then((res) => {
        if (res.ok) {
          form.reset()
          this.selectedLogo = false
          // Makes sure we update the image
          this.iconUrl = iconUrl + '?temp=' + Math.random()
        } else {
          // TODO
        }
      })
    },
    resetIcon() {
      const data = new FormData(document.getElementById('form-icon'))
      const iconUrl = '/' + this.project.namespace.owner + '/' + this.project.namespace.slug + '/icon'
      fetch(iconUrl + '/reset', {
        credentials: 'same-origin',
        method: 'post',
        body: data,
      }).then((res) => {
        if (res.ok) {
          this.selectedLogo = false
          // Makes sure we update the image
          this.iconUrl = iconUrl + '?temp=' + Math.random()
        } else {
          // TODO
        }
      })
    },
    getDeployKey(project) {
      fetch('/api/v1/projects/' + project.plugin_id + '/keys', {
        credentials: 'same-origin',
      }).then((res) => {
        if (res.ok) {
          res.json().then((json) => {
            this.deployKey = json
          })
        } else if (res.status === 404) {
          // Do nothing
        } else {
          // TODO
        }
      })
    },
    generateDeployKey() {
      this.showDeployKeySpinner = true
      const data = new FormData()
      data.append('csrfToken', window.csrf)
      fetch('/api/v1/projects/' + this.project.plugin_id + '/keys/new', {
        method: 'POST',
        credentials: 'same-origin',
        body: data,
      }).then((res) => {
        this.showDeployKeySpinner = false
        if (res.ok) {
          res.json().then((json) => {
            this.deployKey = {
              key: json.value,
              id: json.id,
            }
          })
        } else {
          // TODO
        }
      })
    },
    revokeDeployKey() {
      this.showDeployKeySpinner = true
      const data = new FormData()
      data.append('id', this.deployKey.id)
      data.append('csrfToken', window.csrf)
      fetch('/api/v1/projects/' + this.project.plugin_id + '/keys/revoke', {
        method: 'POST',
        credentials: 'same-origin',
        body: data,
      }).then((res) => {
        this.showDeployKeySpinner = false
        if (res.ok) {
          this.deployKey = null
        } else {
          // TODO
        }
      })
    },
    resetDeleteData() {
      this.hardDelete = false
      this.deleteReason = ''
    },
    deleteProject() {
      if (this.hardDelete) {
        API.request('projects/' + this.project.plugin_id, 'DELETE').then((res) => {
          this.$store.commit('project/clearProject')
          $('#modal-delete').modal('hide')
          this.$router.push({ name: 'home' })
        })
      } else {
        API.request('projects/' + this.project.plugin_id + '/visibility', 'POST', {
          visibility: 'softDelete',
          comment: this.deleteReason,
        }).then((res) => {
          if (this.permissions.includes('see_hidden')) {
            this.$store.commit({
              type: 'project/setVisibility',
              visibility: 'softDelete',
            })
            $('#modal-delete').modal('hide')
          } else {
            this.$store.commit('project/clearProject')
            $('#modal-delete').modal('hide')
            this.$router.push({ name: 'home' })
          }
        })
      }
    },
  },
}
</script>
