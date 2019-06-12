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

const root = require('./Home.vue').default;
const app = new Vue({
    el: '#app',
    render: createElement => createElement(root),
});
