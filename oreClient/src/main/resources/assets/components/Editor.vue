<template>
    <div v-if="enabled">
        <!-- Edit -->
        <button type="button" class="btn btn-sm btn-edit btn-page btn-default" title="Edit">
            <i class="fas fa-edit"></i> Edit
        </button>

        <!-- Preview -->
        <div class="btn-edit-container btn-preview-container" title="Preview">
            <button type="button" class="btn btn-sm btn-preview btn-page btn-default">
                <i class="fas fa-eye"></i>
            </button>
        </div>

        <!-- Save -->
        <div v-if="savable" class="btn-edit-container btn-save-container" title="Save">
            <button form="form-editor-save" type="submit" class="btn btn-sm btn-save btn-page btn-default">
                <i class="fas fa-save"></i>
            </button>
        </div>

        <!-- Cancel -->
        <div v-if="cancellable" class="btn-edit-container btn-cancel-container" title="Cancel">
            <button type="button" class="btn btn-sm btn-cancel btn-page btn-default">
                <i class="fas fa-times"></i>
            </button>
        </div>

        <!-- Delete -->
        <template v-if="deletable">
            <div class="btn-edit-container btn-delete-container" title="Delete">
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
                            <h4 class="modal-title" id="label-page-delete">Delete {{ subject.toLowerCase }}</h4>
                        </div>
                        <div class="modal-body">
                            Are you sure you want to delete this {{ subject.toLowerCase }}? This cannot be undone.
                        </div>
                        <div class="modal-footer">
                            <button type="button" class="btn btn-default" data-dismiss="modal">Close</button>
                            <form :action="deleteCall" :method="deleteCallMethod" class="form-inline">
                                <CSRFField></CSRFField>
                                <button type="submit" class="btn btn-danger">Delete</button>
                            </form>
                        </div>
                    </div>
                </div>
            </div>
        </template>

        <!-- Edit window -->
        <div class="page-edit" style="display: none ;">
            <textarea name="content" class="form-control" :form="textForm">{{ raw }}</textarea>
        </div>

        <!-- Preview window -->
        <div class="page-preview page-rendered" style="display: none ;"></div>

        <form v-if="savable" :action="saveCall" method="post" id="form-editor-save">
            <CSRFField></CSRFField>
            <input v-if="extraFormValue !== null" type="hidden" :value="extraFormValue" name="name">
        </form>

        <!-- Saved window -->
        <div class="page-content page-rendered" v-html="cooked"></div>
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

    import CSRFField from "./CSRFField"

    const md = markdownIt({
        linkify: true,
        typographer: true
    }).use(markdownItAnchor).use(markdownItWikilinks({relativeBaseURL: location.pathname + "/pages/", uriSuffix: ''})).use(markdownItTaskLists);

    export default {
        components: {
            CSRFField
        },
        data: function () {
            return {
                members: null,
                description: null
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
            targetForm: String,
            raw: {
                type: String,
                default: ""
            },
            subject: String,
            saveCall: String,
            extraFormValue: String
        },
        computed: {
            textForm: function () {
                if (typeof targetForm !== 'undefined') {
                    return targetForm
                } else {
                    return "form-editor-save"
                }
            },
            cooked: function () {
                return md.render(this.raw);
            }
        }
    }
</script>
