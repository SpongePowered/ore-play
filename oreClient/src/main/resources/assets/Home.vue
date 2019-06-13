<template>
    <div class="row">
        <div class="col-md-9">
            <div class="project-search">
                <input type="text" class="form-control" v-model="q" @keydown="deboundedUpdateProps"
                       placeholder="Search in all projects, proudly made by the community..." />
            </div>
            <project-list v-bind="listBinding" ref="list" @prevPage="previousPage"
                          @nextPage="nextPage" @jumpToPage="jumpToPage($event)"></project-list>
        </div>
        <div class="col-md-3">
            <select class="form-control select-sort" v-model="sort" @change="deboundedUpdateProps">
                <option v-for="option in sortOptions" :value="option.id">{{ option.name }}</option>
            </select>

            <div>
                <input type="checkbox" id="relevanceBox" v-model="relevance" @change="deboundedUpdateProps">
                <label for="relevanceBox">Sort with relevance</label>
                <div class="panel panel-default">
                    <div class="panel-heading">
                        <h3 class="panel-title">Categories</h3>
                        <a class="category-reset" @click="clearCategory" v-if="categories.length > 0">
                            <i class="fas fa-times white"></i>
                        </a>
                    </div>

                    <div class="list-group category-list">
                        <a v-for="category in allCategories" class="list-group-item" @click="changeCategory(category)"
                           v-bind:class="{ active: categories.includes(category.id) }">
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
                        <a class="list-group-item" @click="clearPlatform" v-bind:class="{ active: tags.length === 0 }">
                            <span class="parent">Any</span>
                        </a>
                        <a v-for="platform in platforms" class="list-group-item" @click="changePlatform(platform)"
                           v-bind:class="{ active: tags.includes(platform.id) }">
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
    import {Category, Platform, SortOptions} from "./home";

    function defaultData() {
        return {
            q: "",
            sort: "updated",
            relevance: true,
            categories: [],
            tags: [],
            page: 1,
            offset: 0,
            limit: 25
        };
    }

    export default {
        components: {
            ProjectList
        },
        data: defaultData,
        computed: {
            nonDefaults: function () {
                return clearFromDefaults(clearFromEmpty(this.$data), defaultData);
            },
            allCategories: function () {
                return Category.values;
            },
            platforms: function () {
                return Platform.values;
            },
            sortOptions: function () {
                return SortOptions;
            },
            baseBinding: function () {
                return {
                    q: this.q,
                    sort: this.sort,
                    relevance: this.relevance,
                    categories: this.categories,
                    tags: this.tags
                }
            },
            listBinding: function () {
                return clearFromDefaults(Object.assign({}, this.baseBinding, {offset: (this.page - 1) * this.limit, limit: this.limit}), defaultData())
            },
            urlBinding: function () {
                return clearFromDefaults(Object.assign({}, this.baseBinding, {page: this.page}), defaultData())
            }
        },
        methods: {
            changeCategory: function(category) {
                if(this.categories.includes(category.id)) {
                    this.categories.splice(this.categories.indexOf(category.id), 1);
                    this.page = 1;
                } else if(this.categories.length + 1 === Category.values.length) {
                    this.clearCategory();
                } else {
                    this.categories.push(category.id);
                    this.page = 1;
                }
            },
            clearCategory: function() {
                this.categories.splice(0, this.categories.length);
                this.page = 1;
            },
            changePlatform: function(platform) {
                this.clearPlatform();
                this.tags.push(platform.id);
                this.page = 1;
            },
            clearPlatform: function() {
                this.tags.splice(0, this.tags.length);
                this.page = 1;
            },
            updateProps: function () {
                const query = queryString.stringify(this.urlBinding, {arrayFormat: 'bracket'});
                window.history.pushState(null, null, query !== "" ? "?" + query : "/");
                this.$refs.list.update();
            },
            nextPage: function () {
                this.page++;
                window.scrollTo(0,0);
            },
            previousPage: function () {
                this.page--;
                window.scrollTo(0,0);
            },
            jumpToPage: function (newPage) {
                this.page = newPage;
                window.scrollTo(0,0);
            }
        },
        created() {
            this.deboundedUpdateProps = debounce(this.updateProps, 500);

            Object.entries(queryString.parse(location.search, {arrayFormat: 'bracket'}))
                .filter(([key, value]) => defaultData().hasOwnProperty(key))
                .forEach(([key, value]) => this.$data[key] = value);
        },
        updated() {
            this.deboundedUpdateProps();
        }
    }
</script>

<style lang="scss">
    @import "./scss/variables";

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
