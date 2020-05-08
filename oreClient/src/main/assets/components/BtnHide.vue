<template>
  <div class="btn-group">
    <button
      id="visibility-actions"
      class="btn btn-sm btn-alert btn-hide-dropdown dropdown-toggle"
      type="button"
      data-toggle="dropdown"
      aria-haspopup="true"
      aria-expanded="false"
      style="color: black;"
    >
      <font-awesome-icon :icon="['fas', spinIcon ? 'spinner' : 'eye']" :spin="spinIcon" />
      Visibility actions
      <span class="caret" />
    </button>
    <ul class="dropdown-menu" aria-labelledby="visibility-actions">
      <li v-for="visibility in visibilities.values" :key="visibility.name">
        <a href="#" class="btn-visibility-change" @click="handleVisibilityClick(visibility)">
          {{ visibilityMessage(visibility.name) }}
          <font-awesome-icon
            v-if="currentVisibility === visibility.name"
            :icon="['fas', 'check']"
            style="color: black;"
            aria-hidden="true"
          />
        </a>
      </li>
    </ul>

    <div
      id="modal-visibility-comment"
      class="modal fade"
      tabindex="-1"
      role="dialog"
      aria-labelledby="modal-visibility-comment"
    >
      <div class="modal-dialog" role="document">
        <div class="modal-content">
          <div class="modal-header">
            <button type="button" class="close" data-dismiss="modal" aria-label="Close">
              <span aria-hidden="true">&times;</span>
            </button>
            <h4 class="modal-title" style="color: black;">
              Comment
            </h4>
          </div>
          <div class="modal-body">
            <textarea v-model="comment" class="textarea-visibility-comment form-control" rows="3" />
          </div>
          <div class="modal-footer">
            <button class="btn btn-default" data-dismiss="modal" @click="resetData">
              Close
            </button>
            <button
              class="btn btn-visibility-comment-submit btn-primary"
              @click="sendVisibilityChange(selectedVisibility)"
            >
              <font-awesome-icon :icon="['fas', 'pencil-alt']" />
              Submit
            </button>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import { Visibility } from '../enums'
import { API } from '../api'

export default {
  props: {
    currentVisibility: {
      type: String,
      required: true,
    },
    endpoint: {
      type: String,
      required: true,
    },
    emitLocation: {
      type: String,
      default: null,
    },
    callback: {
      type: Function,
      default: null,
    },
  },
  data() {
    return {
      selectedVisibility: null,
      comment: '',
      spinIcon: false,
    }
  },
  computed: {
    visibilities() {
      return Visibility
    },
  },
  methods: {
    resetData() {
      this.selectedVisibility = null
      this.comment = ''
    },
    visibilityMessage(visibility) {
      switch (visibility) {
        case 'public':
          return 'Public'
        case 'new':
          return 'New'
        case 'needsChanges':
          return 'Needs changes'
        case 'needsApproval':
          return 'Needs approval'
        case 'softDelete':
          return 'Soft deleted'
      }
    },
    handleVisibilityClick(visibility) {
      if (visibility.showModal) {
        this.selectedVisibility = visibility
        $('#modal-visibility-comment').modal('show')
      } else {
        this.sendVisibilityChange(visibility)
      }
    },
    sendVisibilityChange(visibility) {
      this.spinIcon = true
      API.request(this.endpoint, 'POST', {
        visibility: visibility.name,
        comment: this.comment,
      }).then((res) => {
        this.spinIcon = false
        $('#modal-visibility-comment').modal('hide')

        if (this.emitLocation) {
          this.$emit({
            type: this.emitLocation,
            visibility: visibility.name,
          })
        }

        if (this.callback) {
          this.callback(visibility.name)
        }
      })
    },
  },
}
</script>
