import Vue from 'vue'

export class Category {
    static get values() {
        return [
            {id: "admin_tools",      name: "Admin Tools",      icon: "server"},
            {id: "chat",             name: "Chat",             icon: "comment"},
            {id: "dev_tools",        name: "Developer Tools",  icon: "wrench"},
            {id: "economy",          name: "Economy",          icon: "money-bill-alt"},
            {id: "gameplay",         name: "Gameplay",         icon: "puzzle-piece"},
            {id: "games",            name: "Games",            icon: "gamepad"},
            {id: "protection",       name: "Protection",       icon: "lock"},
            {id: "role_playing",     name: "Role Playing",     icon: "magic"},
            {id: "world_management", name: "World Management", icon: "globe"},
            {id: "misc",             name: "Miscellaneous",    icon: "asterisk"}
        ];
    }

    static fromId(id) {
        return this.values.filter(category => category.id === id)[0];
    }
}

export class Platform {
    static get values() {
        return [
            {id: "Sponge", name: "Sponge Plugins", parent: true},
            {id: "SpongeForge", name: "SpongeForge"},
            {id: "SpongeVanilla", name: "SpongeVanilla"},
            {id: "SpongeCommon", name: "SpongeCommon"},
            {id: "Lantern", name: "Lantern"},
            {id: "Forge",  name: "Forge Mods", parent: true}
        ];
    }

    static get keys() {
        return this.values.map(platform => platform.id)
    }

    static filterTags(tags) {
        return tags.filter(tag => this.keys.includes(tag.name));
    }
}

export const SortOptions = [
    {id: "stars",          name: "Most Stars"},
    {id: "downloads",      name: "Most Downloads"},
    {id: "views",          name: "Most Views"},
    {id: "newest",         name: "Newest"},
    {id: "updated",        name: "Recently updated"},
    {id: "only_relevance", name: "Only relevance"}
];

export class Visibility {
    static get values() {
        return [
            { name: "public",        class: ""},
            { name: "new",           class: "project-new"},
            { name: "needsChanges",  class: "striped project-needsChanges"},
            { name: "needsApproval", class: "striped project-needsChanges"},
            { name: "softDelete",    class: "striped project-hidden"},
        ];
    }

    static fromName(name) {
        return this.values.filter(visibility => visibility.name === name)[0];
    }
}

const root = require('./Home.vue').default;
const app = new Vue({
    el: '#home',
    render: createElement => createElement(root),
});
