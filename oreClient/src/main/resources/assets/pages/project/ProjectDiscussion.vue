<template>
    <div>
        <div id="discourse-comments"></div>
        <div class="row">
            <div v-if="currentUser">
                <div class="col-md-8">
                    <div class="reply-box">
                        <template v-if="permissions.includes('post_as_organization')">
                            <div class="pull-right push-down">
                                <i class="minor">Posting as:</i>
                                <select name="poster" form="form-editor-save">
                                    <option selected>{{ currentUser.name }}</option>
                                    <option>{{ project.namespace.owner }}</option>
                                </select>
                            </div>
                            <div class="clearfix"></div>
                        </template>

                        <div class="push-down">
                            <editor :save-call="routes.project.Project.postDiscussionReply(project.namespace.owner, project.namespace.slug)"
                                    :cancellable="false"
                                    :enabled="true"></editor>
                        </div>
                    </div>
                    <div class="reply-controls">
                        <a class="forums" target="_blank" rel="noopener"
                           href="@config.app.discourseUrl/t/@p.project.topicId">View on Sponge Forums</a>
                    </div>
                </div>
            </div>
            <div v-else class="pull-right">
                <a :href="routes.Users.logIn(null, null, path)">Log in</a>
                <span class="minor"> to reply to this discussion</span>
            </div>
        </div>
    </div>
</template>

<script>
    import Editor from "../../components/Editor";

    //TODO
    let todo = `
    @scripts = {
        <script @CSPNonce.attr>
                DiscourseEmbed = {
                    discourseUrl: '@config.app.discourseUrl/',
                    topicId: @p.project.topicId
                };
        <\script>
    <script type="text/javascript" src="@assetsFinder.path("javascripts/projectDiscuss.js")"><\script>
    <script @CSPNonce.attr>$(function() { $('.btn-edit').click(); });<\script>
    }
    `;

    export default {
        components: {Editor},
        props: {
            currentUser: Object,
            project: {
                type: Object,
                required: true
            },
            permissions: Array
        },
        computed: {
            routes() {
                return jsRoutes.controllers
            },
            discourseEmbed() {
                return {
                    discourseUrl: '@config.app.discourseUrl/',
                    topicId: project.topicId
                }
            }
        },
        methods: {
            path() {
                return document.location.pathname
            }
        }
    }
</script>
