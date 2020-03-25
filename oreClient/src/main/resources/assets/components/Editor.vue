<template>
    <div v-if="enabled">
        <!-- Edit -->
        <button type="button" class="btn btn-sm btn-edit btn-page btn-default" :class="editClasses" title="Edit" @click="state = 'edit'">
            <i class="fas fa-edit"></i> Edit
        </button>

        <!-- Preview -->
        <div class="btn-edit-container btn-preview-container" :class="buttonStateClasses" title="Preview">
            <button v-if="state !== 'display'" type="button" class="btn btn-sm btn-preview btn-page btn-default" :class="previewClasses" @click="state = 'preview'">
                <i class="fas fa-eye"></i>
            </button>
        </div>

        <!-- Save -->
        <div v-if="savable" class="btn-edit-container btn-save-container" :class="buttonStateClasses" title="Save">
            <button class="btn btn-sm btn-save btn-page btn-default" @click="$emit('saved', rawData)">
                <i class="fas fa-save"></i>
            </button>
        </div>

        <!-- Cancel -->
        <div v-if="cancellable" class="btn-edit-container btn-cancel-container" :class="buttonStateClasses" title="Cancel">
            <button type="button" class="btn btn-sm btn-cancel btn-page btn-default" @click="reset">
                <i class="fas fa-times"></i>
            </button>
        </div>

        <!-- Delete -->
        <template v-if="deletable">
            <div class="btn-edit-container btn-delete-container" :class="buttonStateClasses" title="Delete">
                <button type="button" class="btn btn-sm btn-page-delete btn-page btn-default"
                        data-toggle="modal" data-target="#modal-page-delete">
                    <i class="fas fa-trash"></i>
                </button>
            </div>

            <div class="modal fade" id="modal-page-delete" tabindex="-1" role="dialog"
                 aria-labelledby="label-page-delete">
                <div class="modal-dialog" role="document">
                    <div class="modal-content">
                        <div class="modal-header">
                            <button type="button" class="close" data-dismiss="modal" aria-label="Close">
                                <span aria-hidden="true">&times;</span>
                            </button>
                            <h4 class="modal-title" id="label-page-delete">Delete {{ subject.toLowerCase() }}</h4>
                        </div>
                        <div class="modal-body">
                            Are you sure you want to delete this {{ subject.toLowerCase() }}? This cannot be undone.
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
                            <button class="btn btn-danger" @click="$emit('delete')">Delete</button>
                        </div>
                    </div>
                </div>
            </div>
        </template>

        <div class="page-edit" v-if="state === 'edit'">
            <textarea name="content" class="form-control" v-model="rawData"></textarea>
        </div>
        <div class="page-preview page-rendered" v-else-if="state === 'preview'" v-html="cooked"></div>
        <div class="page-content page-rendered" v-else-if="state === 'display'" v-html="cooked"></div>
    </div>
    <div v-else>
        <!-- Saved window -->
        <div class="page-content page-rendered" v-html="cooked"></div>
    </div>
</template>

<script>
    import markdownIt from "markdown-it"
    import markdownItAnchor from "markdown-it-anchor"
    import markdownItWikilinks from "markdown-it-wikilinks"
    import markdownItTaskLists from "markdown-it-task-lists"

    const md = markdownIt({
        linkify: true,
        typographer: true
    }).use(markdownItAnchor).use(markdownItWikilinks({relativeBaseURL: location.pathname + "/pages/", uriSuffix: ''})).use(markdownItTaskLists);

    export default {
        data() {
            return {
                state: 'display',
                rawData: this.raw
            }
        },
        props: {
            savable: {
                type: Boolean,
                default: true
            },
            deletable: {
                type: Boolean,
                default: false
            },
            enabled: {
                type: Boolean,
                required: true
            },
            cancellable: {
                type: Boolean,
                default: true
            },
            raw: {
                type: String,
                default: ""
            },
            subject: String
        },
        computed: {
            cooked: function () {
                return md.render(this.raw);
            },
            buttonStateClasses() {
                return {
                    'btn-editor-display': this.state === 'display',
                    'btn-editor-active': this.state !== 'display'
                }
            },
            editClasses() {
                return {
                    'editor-tab-active': this.state === 'edit',
                    'editor-tab-inactive': this.state !== 'edit',
                    ...this.buttonStateClasses
                }
            },
            previewClasses() {
                return {
                    'editor-tab-active': this.state === 'preview',
                    'editor-tab-inactive': this.state !== 'preview',
                }
            }
        },
        methods: {
            reset() {
                this.rawData = this.raw;
                this.state = 'display'
            }
        },
        watch: {
            raw(newVal, oldVal) {
                this.rawData = newVal
            }
        }
    }
</script>
