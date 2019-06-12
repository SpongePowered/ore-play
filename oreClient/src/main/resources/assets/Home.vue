<template>
    <div class="row">
        <div class="col-md-9">
            <div class="project-search">
                <input type="text" class="form-control" v-model="properties.q" @keydown="deboundedUpdateProps"
                       placeholder="Search in all projects, proudly made by the community..." />
            </div>
            <project-list v-bind="changedProperties" ref="list"></project-list>
        </div>
        <div class="col-md-3">
            <select class="form-control select-sort" v-model="properties.sort" @change="deboundedUpdateProps">
                <option v-for="option in sortOptions" :value="option.id">{{ option.name }}</option>
            </select>

            <div>
                <input type="checkbox" id="relevanceBox" v-model="properties.relevance" @change="deboundedUpdateProps">
                <label for="relevanceBox">Sort with relevance</label>
                <div class="panel panel-default">
                    <div class="panel-heading">
                        <h3 class="panel-title">Categories</h3>
                        <a class="category-reset" @click="clearCategory" v-if="properties.categories.length > 0">
                            <i class="fas fa-times white"></i>
                        </a>
                    </div>

                    <div class="list-group category-list">
                        <a v-for="category in categories" class="list-group-item" @click="changeCategory(category)"
                           v-bind:class="{ active: properties.categories.includes(category.id) }">
                            <i class="fas fa-fw" :class="'fa-' + category.icon"></i>
                            <strong>{{ category.name }}</strong>
                        </a>
                    </div>
                </div>
                <div class="panel panel-default">
                    <div class="panel-heading">
                        <h3 class="panel-title">Platforms</h3>
                    </div>

                    <div class="list-group platform-list">
                        <a v-for="platform in platforms" class="list-group-item" @click="changePlatform(platform)">
                            <span :class="{parent: platform.parent}">{{ platform.name }}</span>
                        </a>
                    </div>
                </div>
            </div>
        </div>
    </div>
</template>

<script>
    import ProjectList from "./components/ProjectList.vue"
    import debounce from "lodash/debounce"
    import queryString from "query-string"
    import {clearFromDefaults, clearFromEmpty} from "./utils"
    import {Category, Platform} from "./home";

    const defaultData = {
        properties: {
            q: "",
            sort: "updated",
            relevance: true,
            categories: [],
            tags: ["Sponge"]
        }
    };

    export default {
        components: {
            ProjectList
        },
        data: function () {
            return {
                properties: JSON.parse(JSON.stringify(defaultData.properties)),
                sortOptions: [
                    {id: "stars",          name: "Most Stars"},
                    {id: "downloads",      name: "Most Downloads"},
                    {id: "views",          name: "Most Views"},
                    {id: "newest",         name: "Newest"},
                    {id: "updated",        name: "Recently updated"},
                    {id: "only_relevance", name: "Only relevance"}
                ],
            };
        },
        computed: {
            changedProperties: function () {
                return clearFromDefaults(clearFromEmpty(this.properties), defaultData.properties);
            },
            categories: function () {
                return Category.values;
            },
            platforms: function () {
                return Platform.values;
            }
        },
        methods: {
            changeCategory: function(category) {
                if(this.properties.categories.includes(category.id)) {
                    this.properties.categories.splice(this.properties.categories.indexOf(category.id), 1)
                } else {
                    this.properties.categories.push(category.id);
                }
                this.updateProps();
            },
            clearCategory: function() {
                this.properties.categories.splice(0, this.properties.categories.length);
                this.updateProps();
            },
            changePlatform: function(platform) {
                this.properties.tags.splice(0, this.properties.tags.length);
                this.properties.tags.push(platform.id);
                this.updateProps();
            },
            updateProps: function () {
                this.$refs.list.update();
                window.history.pushState(null, null, "?" + queryString.stringify(this.changedProperties, {arrayFormat: 'bracket'}))
            }
        },
        created() {
            this.deboundedUpdateProps = debounce(this.updateProps, 500);

            const props = Object.entries(queryString.parse(location.search, {arrayFormat: 'bracket'}))
                .filter(([key, value]) => defaultData.properties.hasOwnProperty(key));

            if(props.length > 0) {
                props.forEach(([key, value]) => this.properties[key] = value);
                this.deboundedUpdateProps();
            }
        }
    }
</script>

<style lang="scss">
    @import "../../../../../ore/app/assets/stylesheets/sponge_variables";
    @import "../../../../../ore/app/assets/stylesheets/pallette";

    .select-sort {
        margin-bottom: 10px;
    }
    .category-reset {
        display: flex;
        cursor: pointer;
    }
    .category-list {
        a.list-group-item {
            svg {
                margin-right: 0.5rem;
            }

            &:hover {
                cursor: pointer;
                background-color: $mainBackground;
            }

            &.active {
                background: #FFFFFF;
                border-bottom: 1px solid #dddddd;
                border-top: 1px solid #dddddd;
                box-shadow: inset -10px 0px 0px 0px $sponge_yellow;
            }
        }
    }
    .platform-list {
        .list-group-item {
            cursor: pointer;
        }
        .parent {
            font-weight: bold;
        }
    }
</style>
