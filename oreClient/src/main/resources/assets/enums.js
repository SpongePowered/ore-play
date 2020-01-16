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

export class Permission {
    static ViewPublicInfo = "view_public_info";
    static EditOwnUserSettings = "edit_own_user_settings";
    static EditApiKeys = "edit_api_keys";
    static EditSubjectSettings = "edit_subject_settings";
    static ManageSubjectMembers = "manage_subject_members";
    static IsSubjectOwner = "is_subject_owner";
    static IsSubjectMember = "is_subject_member";
    static CreateProject = "create_project";
    static EditPage = "edit_page";
    static DeleteProject = "delete_project";
    static CreateVersion = "create_version";
    static EditVersion = "edit_version";
    static DeleteVersion = "delete_version";
    static EditChannel = "edit_channel";
    static CreateOrganization = "create_organization";
    static PostAsOrganization = "post_as_organization";
    static ModNotesAndFlags = "mod_notes_and_flags";
    static SeeHidden = "see_hidden";
    static IsStaff = "is_staff";
    static Reviewer = "reviewer";
    static ViewHealth = "view_health";
    static ViewIp = "view_ip";
    static ViewStats = "view_stats";
    static ViewLogs = "view_logs";
    static ManualValueChanges = "manual_value_changes";
    static HardDeleteProject = "hard_delete_project";
    static HardDeleteVersion = "hard_delete_version";
    static EditAllUserSettings = "edit_all_user_settings";
}