<template>
    <div class="row">
        <div class="col-md-9">
            <version-list :platforms="concatPlatforms" :stability="flatStability"></version-list>
        </div>

        <div class="col-md-3">
            <div class="panel panel-default channels">
                <div class="panel-heading">
                    <h3 class="panel-title pull-left">Stability</h3>
                    <input type="checkbox" class="pull-right channels-all" aria-label="Reset stability"
                           :checked="stability.length === 0"
                           @click="stability = []">
                </div>

                <ul class="list-group list-channel">
                    <li class="list-group-item" v-for="stabilityFor in stabilityObj.values">
                        <label :for="'stability-' + stabilityFor.id" class="channel" :style="{background: stabilityFor.color}">{{ stabilityFor.title }}</label>
                        <input :id="'stability-' + stabilityFor.id" type="checkbox" class="pull-right"
                               :checked="stability.includes(stabilityFor.id)"
                               @click="toggleStability($event, stabilityFor.id)">
                    </li>
                </ul>
            </div>

            <div class="panel panel-default channels">
                <div class="panel-heading">
                    <h3 class="panel-title pull-left">Platforms</h3>
                    <input type="checkbox" class="pull-right channels-all" aria-label="Reset platforms"
                        :checked="true"
                        @click="resetPlatforms">
                </div>

                <ul class="list-group list-channel">
                    <li class="list-group-item">
                        <label for="minecraftVersionToggle">Use Minecraft versions:</label>
                        <input id="minecraftVersionToggle" type="checkbox" v-model="minecraftVersions">
                    </li>
                    <li class="list-group-item" v-for="platform in platformObj.values">
                        <span class="channel" :style="{background: platform.color.background}">{{ platform.shortName }}</span>
                        <template v-if="minecraftVersions">
                            <select :value="platformValue(platform.id)" @change="processSelection(platform.id, $event, 'minecraftVersions')" class="pull-right">
                                <option value="$all">All</option>
                                <option :value="name" v-for="(v, name) in platform.minecraftVersions">{{ name }}</option>
                                <option value="$none">None</option>
                            </select>
                        </template>
                        <template v-else>
                            <select :value="platformValue(platform.id)" @change="processSelection(platform.id, $event, 'versions')" class="pull-right">
                                <option value="$all">All</option>
                                <option :value="name" v-for="(v, name) in platform.versions">{{ name }}</option>
                                <option value="$none">None</option>
                            </select>
                        </template>
                    </li>
                </ul>
            </div>

            <member-list :permissions="permissions" :members="members" role-category="project" :settings-route="{name: 'settings'}"></member-list>
        </div>
    </div>
</template>

<script>
    import VersionList from "../../components/VersionList";
    import MemberList from "../../components/MemberList";
    import {Stability, Platform, Category} from "../../enums";
    import { mapState } from 'vuex'

    export default {
        components: {
            MemberList,
            VersionList
        },
        data() {
            return {
                platforms: Platform.values.reduce((o, p) => ({...o, [p.id]: {name: '$all', versions: [p.id]}}), {}),
                stability: ['recommended', 'stable', 'beta', 'alpha'],
                minecraftVersions: true
            }
        },
        computed: {
            stabilityObj() {
                return Stability;
            },
            platformObj() {
                return Platform
            },
            flatStability() {
                return this.stability.flat()
            },
            concatPlatforms() {
                if(this.isAllSame('$all')) {
                    return []
                }
                else {
                    let res = [];
                    for (let id in this.platforms) {
                        if(this.platforms.hasOwnProperty(id)) {
                            res.push(this.platforms[id].versions)
                        }
                    }

                    return res.flat()
                }
            },
            ...mapState('project', [
                'permissions',
                'members'
            ])
        },
        methods: {
            toggleStability(event, id) {
                if(this.stability.includes(id)) {
                    this.stability.splice(this.stability.indexOf(id), 1);
                } else if(this.stability.length + 1 === Stability.values.length) {
                    this.stability = [];
                    event.preventDefault();
                } else {
                    this.stability.push(id);
                }
            },
            platformValue(id) {
                return this.platforms[id].name;
            },
            resetPlatforms() {
                this.platforms = Platform.values.reduce((o, p) => ({...o, [p.id]: {name: '$all', versions: [p.id]}}), {});
            },
            isAllSame(name) {
                return Platform.values.filter(p => this.platforms[p.id].name === name).length === Platform.values.length
            },
            processSelection(id, event, field) {
                let allAll = this.isAllSame('$all');

                let versionName = event.target.value;
                this.platforms[id].name = versionName;

                if(versionName === '$all') {
                    this.platforms[id].versions = [id];
                }
                else if(versionName === '$none') {
                    this.platforms[id].versions = [];
                    let allNone = this.isAllSame('$none');
                    if(allNone) {
                        this.resetPlatforms()
                    }
                }
                else {
                    if(allAll) {
                        for (let p of Platform.values) {
                            this.platforms[p.id].name = '$none';
                            this.platforms[p.id].versions = []
                        }
                    }

                    this.platforms[id].name = versionName;
                    this.platforms[id].versions = Platform.fromId(id)[field][versionName].map(v => id + ':' + v)
                }
            }
        }
    }
</script>
