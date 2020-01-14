<template>
    <div class="row">
        <div class="col-md-9">
            <div class="row">
                <div class="col-md-12">
                    <editor :enabled="permissions && permissions.includes('edit_page')" :raw="description !== null ? description : ''"
                            subject="Page"></editor>
                </div>
            </div>
        </div>

        <div class="col-md-3">

            <div class="stats minor">
                <p>@messages("project.category.info", p.project.category.title)</p>
                <p>@messages("project.publishDate", prettifyDate(p.project.createdAt))</p>
                <p>{{ p.stats.views }} views</p>
                <p>{{ p.stats.stars }} <a
                        :href="routes.Projects.showStargazers(p.namespace.owner, p.namespace.slug, null)">stars</a></p>
                <p>{{ p.stats.watchers }} <a
                        :href="routes.Projects.showWatchers(p.namespace.owner, p.namespace.slug, null)">watchers</a></p>
                <p>{{ p.stats.downloads }} total downloads</p>
                <p v-if="p.settings.license.name !== null">
                    @Html(messages("project.license.link"))
                    <a target="_blank" rel="noopener" :href="p.settings.license.url">{{p.settings.license.name}}</a>
                </p>
            </div>

            <div class="panel panel-default">
                <div class="panel-heading">
                    <h3 class="panel-title">@messages("project.promotedVersions")</h3>
                </div>

                <ul class="list-group promoted-list">
                    <li v-for="version in p.promoted_versions" class="list-group-item">
                        <a href="TODO">{{ version.version }}</a>
                    </li>
                </ul>
            </div>
        </div>

        <member-list v-if="members && permissions" :members="members" :permissions="permissions"></member-list>
    </div>
</template>

<script>
    import MemberList from "./MemberList";
    import {API} from "../api";
    import Editor from "./Editor";

    export default {
        components: {
            Editor,
            MemberList
        },
        data: function () {
            return {
                members: null,
                description: null
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
        },
        computed: {
            routes: function () {
                return jsRoutes.controllers.project;
            }
        },
        created() {
            /*
            API.request('projects/' + this.p.plugin_id + '/members').then((response) => {
                this.members = response;
            });
            */
            API.request('projects/' + this.p.plugin_id + '/description').then((response) => {
                this.description = response.description;
            })
        }
    }
</script>
