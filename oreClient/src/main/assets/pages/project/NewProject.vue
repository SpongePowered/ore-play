<template>
    <div class="row">
        <div class="col-md-10">
            <div class="panel panel-default">
                <div class="panel-heading">
                    <h3 class="panel-title">
                        Create a new project
                    </h3>
                </div>

                <div class="panel-body project-body" v-if="memberships && currentUser">
                    <div class="minor create-blurb">
                        <p>A project contains your downloads and the documentation for your plugin.</p>
                        <p>Before continuing, please review the <a href="https://docs.spongepowered.org/stable/en/ore/guidelines.html">Ore Submission Guidelines</a></p>
                    </div>

                    <div>
                        <div class="form-group">
                            <label for="projectName">Project name</label>
                            <input type="text" id="projectName" name="name" class="form-control" v-model.trim="projectName" required>
                        </div>

                        <div class="form-group">
                            <label for="projectPluginId">Plugin id</label>
                            <input type="text" id="projectPluginId" name="pluginId" class="form-control" v-model.trim="pluginId" required>
                        </div>

                        <div class="form-group">
                            <label for="projectCategory">Project category</label>
                            <select class="form-control" v-model="category" required>
                                <option v-for="categoryIt in categories.values" :value="categoryIt.id">
                                    {{ categoryIt.name }}
                                </option>
                            </select>
                        </div>

                        <div class="form-group">
                            <label for="projectDescription">Project description</label>
                            <input type="text" id="projectDescription" v-model.trim="projectDescription" name="description" class="form-control">
                        </div>

                        <div class="form-group">
                            <label for="projectCategory">Owner</label>
                            <select id="projectCategory" name="owner" class="form-control" v-model="owner" required>
                                <option :value="currentUser.name">{{ currentUser.name }}</option>
                                <option v-for="membership in availableOwners" :value="membership.organization.name">{{ membership.organization.name }}</option>
                            </select>
                        </div>

                        <button @click="create()" class="btn btn-primary">Create project</button>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
    import {mapState} from "vuex";
    import {Category, Permission} from "../../enums";
    import {API} from "../../api"

    export default {
        data() {
            return {
                projectName: "",
                pluginId: "",
                projectDescription: "",
                category: null,
                owner: "",
                availableOwners: []
            };
        },
        computed: {
            categories() {
                return Category
            },
            ...mapState('global', [
                'currentUser',
                'memberships'
            ])
        },
        methods: {
            create() {
                API.request("projects", "POST", {
                    name: this.projectName,
                    plugin_id: this.pluginId,
                    category: this.category,
                    description: this.projectDescription,
                    owner_name: this.owner
                }).then((data) => {
                    this.$router.push({path: `/${data.namespace.owner}/${data.namespace.slug}`})
                }).catch((xhr) => {
                    console.log(xhr)
                })
            }
        },
        watch: {
            memberships(newVal, oldVal) {
                this.availableOwners = [];

                newVal.filter(m => m.scope === 'organization').forEach(o => {
                   API.request("permissions/hasAny", "GET",{'permissions': [Permission.CreateProject], 'organizationName': o.organization.name}).then(res => {
                        if(res.result === true) this.availableOwners.push(o)
                   })
                });
            }
        }
    }
</script>
