<template>
    <div class="row">
        <div class="col-md-9">
            <div class="row">
                <div class="col-md-12">
                    <!-- TODO: Editor here -->
                </div>
            </div>
        </div>

        <div class="col-md-3">

            <div class="stats minor">
                <p>@messages("project.category.info", p.project.category.title)</p>
                <p>@messages("project.publishDate", prettifyDate(p.project.createdAt))</p>
                <p><span id="view-count"></span> views</p>
                <p><span id="star-count"></span> <ahref="@routes.Projects.showStargazers(p.project.ownerName, p.project.slug, None)">stars</a></p>
                <p><span id="watcher-count"></span> <a href="@routes.Projects.showWatchers(p.project.ownerName, p.project.slug, None)">watchers</a></p>
                <p><span id="download-count"></span> total downloads</p>
                <p v-if="p.settings.license.name !== null">
                    @Html(messages("project.license.link"))
                    <a target="_blank" rel="noopener" :href="p.settings.license.url">{{p.settings.license.name}}</a>
                </p>
            </div>

            <div class="panel panel-default">
                <div class="panel-heading">
                    <h3 class="panel-title">@messages("project.promotedVersions")</h3>
                </div>

                <ul class="list-group promoted-list"></ul>
            </div>
        </div>

        <div class="panel panel-default">
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

        <member-list :members="members" :permissions="permissions"></member-list>
    </div>

</template>

<script>
    import MemberList from "./MemberList";

    export default {
        components: {
            MemberList
        },
        data: function () {
            return {
                permissions: [],
                p: {},
                maxPages: window.MAX_PAGES,
                pages: [],
                members: []
            }
        },
        props: {
            pluginId: {
                type: String,
                required: true
            },
            page: {
                type: String,
                required: true
            }
        },
        computed: {
            pageCount: function () {
                //TODO
            },
            nonHomePages: function () {
                return pages.filter(p => p.name !== 'Home')
            }
        }
    }
</script>