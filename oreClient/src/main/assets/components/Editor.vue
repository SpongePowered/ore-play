<template>
  <div v-if="enabled">
    <!-- Edit -->
    <button
      type="button"
      class="btn btn-sm btn-edit btn-page btn-default"
      :class="editClasses"
      title="Edit"
      @click="state = 'edit'"
    >
      <font-awesome-icon :icon="['fas', 'edit']" /> Edit
    </button>

    <!-- Preview -->
    <div class="btn-edit-container btn-preview-container" :class="buttonStateClasses" title="Preview">
      <button
        v-if="state !== 'display'"
        type="button"
        class="btn btn-sm btn-preview btn-page btn-default"
        :class="previewClasses"
        @click="state = 'preview'"
      >
        <font-awesome-icon :icon="['fas', 'eye']" />
      </button>
    </div>

    <!-- Save -->
    <div v-if="savable" class="btn-edit-container btn-save-container" :class="buttonStateClasses" title="Save">
      <button class="btn btn-sm btn-save btn-page btn-default" @click="$emit('saved', rawData)">
        <font-awesome-icon :icon="['fas', 'save']" />
      </button>
    </div>

    <!-- Cancel -->
    <div v-if="cancellable" class="btn-edit-container btn-cancel-container" :class="buttonStateClasses" title="Cancel">
      <button type="button" class="btn btn-sm btn-cancel btn-page btn-default" @click="reset">
        <font-awesome-icon :icon="['fas', 'times']" />
      </button>
    </div>

    <!-- Delete -->
    <template v-if="deletable">
      <div class="btn-edit-container btn-delete-container" :class="buttonStateClasses" title="Delete">
        <button
          type="button"
          class="btn btn-sm btn-page-delete btn-page btn-default"
          data-toggle="modal"
          data-target="#modal-page-delete"
        >
          <font-awesome-icon :icon="['fas', 'trash']" />
        </button>
      </div>

      <div id="modal-page-delete" class="modal fade" tabindex="-1" role="dialog" aria-labelledby="label-page-delete">
        <div class="modal-dialog" role="document">
          <div class="modal-content">
            <div class="modal-header">
              <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                <span aria-hidden="true">&times;</span>
              </button>
              <h4 id="label-page-delete" class="modal-title">Delete {{ subject.toLowerCase() }}</h4>
            </div>
            <div class="modal-body">
              Are you sure you want to delete this {{ subject.toLowerCase() }}? This cannot be undone.
            </div>
            <div class="modal-footer">
              <button type="button" class="btn btn-default" data-dismiss="modal">
                Close
              </button>
              <button class="btn btn-danger" @click="$emit('delete')">
                Delete
              </button>
            </div>
          </div>
        </div>
      </div>
    </template>

    <div v-if="state === 'edit'" class="page-edit">
      <textarea v-model="rawData" name="content" class="form-control" />
    </div>
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div v-else-if="state === 'preview'" class="page-preview page-rendered" v-html="previewCooked" />
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div v-else-if="state === 'display'" class="page-content page-rendered" v-html="cooked" />
  </div>
  <div v-else>
    <!-- Saved window -->
    <!-- eslint-disable-next-line vue/no-v-html -->
    <div class="page-content page-rendered" v-html="cooked" />
  </div>
</template>

<script>
import markdownIt from 'markdown-it'
import markdownItAnchor from 'markdown-it-anchor'
import markdownItWikilinks from 'markdown-it-wikilinks'
import markdownItTaskLists from 'markdown-it-task-lists'

const md = markdownIt({
  html: true,
  linkify: true,
  typographer: true,
})
  .use(markdownItAnchor)
  .use(markdownItWikilinks({ relativeBaseURL: location.pathname + '/pages/', uriSuffix: '' }))
  .use(markdownItTaskLists)

export default {
  props: {
    savable: {
      type: Boolean,
      default: true,
    },
    deletable: {
      type: Boolean,
      default: false,
    },
    enabled: {
      type: Boolean,
      required: true,
    },
    cancellable: {
      type: Boolean,
      default: true,
    },
    raw: {
      type: String,
      default: '',
    },
    subject: {
      type: String,
      default: '',
    },
  },
  data() {
    return {
      state: 'display',
      rawData: this.raw,
    }
  },
  computed: {
    previewCooked() {
      return md.render(this.rawData)
    },
    cooked() {
      return md.render(this.raw)
    },
    buttonStateClasses() {
      return {
        'btn-editor-display': this.state === 'display',
        'btn-editor-active': this.state !== 'display',
      }
    },
    editClasses() {
      return {
        'editor-tab-active': this.state === 'edit',
        'editor-tab-inactive': this.state !== 'edit',
        ...this.buttonStateClasses,
      }
    },
    previewClasses() {
      return {
        'editor-tab-active': this.state === 'preview',
        'editor-tab-inactive': this.state !== 'preview',
      }
    },
  },
  watch: {
    raw(newVal, oldVal) {
      this.rawData = newVal
    },
  },
  methods: {
    reset() {
      this.rawData = this.raw
      this.state = 'display'
    },
  },
}
</script>
