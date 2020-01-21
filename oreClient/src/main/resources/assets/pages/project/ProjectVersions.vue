<template>
    <div class="row">
        <div class="col-md-9">
            <version-list :project="project" :permissions="permissions"></version-list>
        </div>

        <div class="col-md-3">
            <div class="panel panel-default channels">
                <div class="panel-heading">
                    <h3 class="panel-title pull-left">Stability</h3>
                    <input type="checkbox" class="pull-right channels-all" checked>
                </div>

                <ul class="list-group list-channel">
                    <li class="list-group-item" v-for="stability in stabilities.values">
                        <span class="channel" :style="{background: stability.color}">{{ stability.title }}</span>
                        <input type="checkbox" class="pull-right">
                    </li>
                </ul>
            </div>

            <div class="panel panel-default channels">
                <div class="panel-heading">
                    <h3 class="panel-title pull-left">Platforms</h3>
                    <input type="checkbox" class="pull-right channels-all" checked>
                </div>

                <ul class="list-group list-channel">
                    <li class="list-group-item" v-for="platform in platforms.values">
                        <span class="channel" :style="{background: platform.color.background}">{{ platform.shortName }}</span>
                        <select class="pull-right">
                            <option>All</option>
                            <option>Foo</option>
                            <option>Bar</option>
                            <option>Baz</option>
                        </select>
                    </li>
                </ul>
            </div>

            <member-list :permissions="permissions"></member-list>
        </div>
    </div>
</template>

<script>
    import VersionList from "./VersionList";
    import MemberList from "../../components/MemberList";
    import {Stability, Platform} from "../../enums";

    export default {
        components: {
            MemberList,
            VersionList
        },
        props: {
            permissions: Array,
            project: {
                type: Object,
                required: true
            }
        },
        computed: {
            stabilities() {
                return Stability;
            },
            platforms() {
                return Platform
            }
        }
    }
</script>
