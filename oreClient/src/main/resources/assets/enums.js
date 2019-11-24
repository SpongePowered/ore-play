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
            {id: "spongeapi", shortName: "Sponge", name: "Sponge Plugins", parent: true, color: { background: "#F7Cf0D", foreground: "#333333" }},
            {id: "spongeforge", shortName: "SpongeForge", name: "SpongeForge", color: { background: "#910020", foreground: "#FFFFFF" }},
            {id: "spongevanilla", shortName: "SpongeVanilla", name: "SpongeVanilla", color: { background: "#50C888", foreground: "#FFFFFF" }},
            {id: "sponge", shortName: "SpongeCommon", name: "SpongeCommon", color: { background: "#5D5DFF", foreground: "#FFFFFF" }},
            {id: "lantern", shortName: "Lantern", name: "Lantern", color: { background: "#4EC1B4", foreground: "#FFFFFF" }},
            {id: "forge", shortName: "Forge",  name: "Forge Mods", parent: true, color: { background: "#DFA86A", foreground: "#FFFFFF" }}
        ];
    }

    static get keys() {
        return this.values.map(platform => platform.id)
    }

    static isPlatformTag(tag) {
        return this.keys.includes(tag.name);
    }
}

export const SortOptions = [
    {id: "stars",            name: "Most Stars"},
    {id: "downloads",        name: "Most Downloads"},
    {id: "views",            name: "Most Views"},
    {id: "newest",           name: "Newest"},
    {id: "updated",          name: "Recently updated"},
    {id: "only_relevance",   name: "Only relevance"},
    {id: "recent_views",     name: "Recent Views"},
    {id: "recent_downloads", name: "Recent Downloads"}
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
