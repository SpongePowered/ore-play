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

    static fromId(id) {
        return this.values.filter(platform => platform.id === id)[0];
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

export class Permission {}
Permission.ViewPublicInfo = "view_public_info";
Permission.EditOwnUserSettings = "edit_own_user_settings";
Permission.EditApiKeys = "edit_api_keys";
Permission.EditSubjectSettings = "edit_subject_settings";
Permission.ManageSubjectMembers = "manage_subject_members";
Permission.IsSubjectOwner = "is_subject_owner";
Permission.IsSubjectMember = "is_subject_member";
Permission.CreateProject = "create_project";
Permission.EditPage = "edit_page";
Permission.DeleteProject = "delete_project";
Permission.CreateVersion = "create_version";
Permission.EditVersion = "edit_version";
Permission.DeleteVersion = "delete_version";
Permission.EditChannel = "edit_channel";
Permission.CreateOrganization = "create_organization";
Permission.PostAsOrganization = "post_as_organization";
Permission.ModNotesAndFlags = "mod_notes_and_flags";
Permission.SeeHidden = "see_hidden";
Permission.IsStaff = "is_staff";
Permission.Reviewer = "reviewer";
Permission.ViewHealth = "view_health";
Permission.ViewIp = "view_ip";
Permission.ViewStats = "view_stats";
Permission.ViewLogs = "view_logs";
Permission.ManualValueChanges = "manual_value_changes";
Permission.HardDeleteProject = "hard_delete_project";
Permission.HardDeleteVersion = "hard_delete_version";
Permission.EditAllUserSettings = "edit_all_user_settings";

export class FlagReason {
    static get values() {
        return [
            {value: 0, title: 'Inappropriate Content'},
            {value: 1, title: 'Impersonation or Deception'},
            {value: 2, title: 'Spam'},
            {value: 3, title: 'Malicious Intent'},
            {value: 4, title: 'Other'}
        ]
    }
}

export class Stability {
    static get values() {
        return [
            {id: 'stable', title: 'Stable', color: '#00C800'},
            {id: 'beta', title: 'Beta', color: '#FFC800'},
            {id: 'alpha', title: 'Alpha', color: '#FF6000'},
            {id: 'bleeding', title: 'Bleeding', color: '#FF0000'},
            {id: 'unsupported', title: 'Unsupported', color: '#784646'},
            {id: 'broken', title: 'Broken', color: '#7F7F7F'}
        ]
    }

    static fromId(id) {
        return this.values.filter(stability => stability.id === id)[0];
    }
}

export class ReleaseType {
    static get values() {
        return [
            {id: 'major_update', title: 'Major update', color: '#4080FF'},
            {id: 'minor_update', title: 'Minor update', color: '#009600'},
            {id: 'patches', title: 'Patches', color: '#7F7F7F'},
            {id: 'hotfix', title: 'Hotfix', color: '#C80000'}
        ]
    }

    static fromId(id) {
        return this.values.filter(releaseType => releaseType.id === id)[0];
    }
}
