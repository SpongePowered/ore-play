<template>
    <div class="row">
        <div class="col-md-9">
            <div class="row">
                <div class="col-md-12">
                    <!-- TODO: Editor here -->
                </div>
            </div>
        </div>

        <div v-if="pages && permissions" class="panel panel-default">
            <div class="panel-heading">
                <h3 class="pull-left panel-title">@messages("page.plural")</h3>
                <template v-if="permissions.includes('edit_pages') && pageCount < maxPages">
                    <button data-toggle="modal" data-target="#new-page" title="New"
                            class="new-page btn yellow btn-xs pull-right">
                        <i class="fas fa-plus"></i>
                    </button>
                    @projects.pages.modalPageCreate(p.project, rootPages.map(_._1))
                </template>
            </div>
            <ul class="list-group">
                <li class="list-group-item">
                    <a href="@routes.Pages.show(p.project.ownerName, p.project.slug, Page.homeName)">
                        @Page.homeName
                    </a>
                </li>
                <li v-for="childPage in nonHomePages" class="list-group-item">
                    <!-- TODO: Page tree -->
                </li>
            </ul>
        </div>
    </div>
</template>

<script>

    export default {
        data: function() {
            return {
                pages: null,
                maxPages: window.MAX_PAGES,
            }
        },
        props: {
            p: {
                type: Object,
                required: true
            },
            permissions: {
                type: Array,
                required: true
            }
        }
    }
</script>