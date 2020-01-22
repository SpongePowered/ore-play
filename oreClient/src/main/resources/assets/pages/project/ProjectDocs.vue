<template>
    <div class="row">
        <div class="col-md-9">
            <div class="row">
                <div class="col-md-12">
                    <editor :enabled="permissions && permissions.includes('edit_page')" :raw="description"
                            subject="Page"/>
                </div>
            </div>
        </div>

        <div class="col-md-3">

            <div class="stats minor">
                <p>Category: {{ parseCategory(project.category) }}</p>
                <p>Published on {{ parseDate(project.created_at) }}</p>
                <p>{{ project.stats.views }} views</p>
                <p>{{ project.stats.stars }} <a
                        :href="routes.Projects.showStargazers(project.namespace.owner, project.namespace.slug, null).absoluteURL()">stars</a></p>
                <p>{{ project.stats.watchers }} <a
                        :href="routes.Projects.showWatchers(project.namespace.owner, project.namespace.slug, null).absoluteURL()">watchers</a></p>
                <p>{{ project.stats.downloads }} total downloads</p>
                <p v-if="project.settings.license.name !== null">
                    <span>Licensed under </span>
                    <a target="_blank" rel="noopener" :href="project.settings.license.url">{{project.settings.license.name}}</a>
                </p>
            </div>

            <div class="panel panel-default" v-if="project.promoted_versions">
                <div class="panel-heading">
                    <h3 class="panel-title">Promoted Versions</h3>
                </div>

                <ul class="list-group promoted-list">
                    <li v-for="version in project.promoted_versions" class="list-group-item">
                        <a href="TODO">{{ version.version }}</a>
                    </li>
                </ul>
            </div>

            <member-list :members="members" :permissions="permissions" role-category="project"/>
        </div>
    </div>
</template>

<script>

    import {API} from "../../api";
    import Editor from "../../components/Editor";
    import MemberList from "../../components/MemberList";
    import {Category} from "../../enums";

    export default {
        components: {
            Editor,
            MemberList
        },
        data() {
            return {
                description: ""
            }
        },
        props: {
            project: {
                type: Object,
                required: true
            },
            permissions: {
                type: Array,
                required: true
            },
            page: {
                type: String,
                required: true
            },
            members: {
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
            this.updatePage();
        },
        watch: {
            '$route': 'updatePage'
        },
        methods: {
            updatePage() {
                API.request('projects/' + this.project.plugin_id + '/_pages/' + this.page).then((response) => {
                    this.description = response.content;
                }).catch((error) => {
                    this.description = "";

                    if(error === 404) {
                        //TODO
                    } else {

                    }
                })
            },
            parseDate(rawDate) {
                return moment(rawDate).format("MMM DD[,] YYYY");  ;
            },
            parseCategory(category) {
                return Category.fromId(category).name;
            }
        }
    }
</script>
