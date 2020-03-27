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
            {
                id: "spongeapi",
                shortName: "Sponge",
                name: "Sponge Plugins",
                parent: true,
                color: { background: "#F7Cf0D", foreground: "#333333" },
                priority: 0,
                url: "https://spongepowered.org/downloads",
                versions: {
                    "8.x": ["8.0"],
                    "7.x": ["7.0"],
                    "6.x": ["6.0"],
                    "5.x": ["5.0"],
                    "4.x": ["4.0"]
                },
                minecraftVersions: {
                    "1.12.x": ["7.0"],
                    "1.11.x": ["6.0"],
                    "1.10.x": ["5.0"],
                    "1.8.9": ["4.0"],
                }
            },
            {
                id: "spongeforge",
                shortName: "SpongeForge",
                name: "SpongeForge",
                color: { background: "#910020", foreground: "#FFFFFF" },
                priority: 2,
                url: "https://www.spongepowered.org/downloads/spongeforge",
                versions: {
                    "7.x": ["1.12.2-2838-7.1.10"],
                    "6.x": ["1.11.2-2476-6.1.0-BETA-2792"],
                    "5.x": ["1.10.2-2477-5.2.0-BETA-2793"]
                },
                minecraftVersions: {
                    "1.12.x": ["1.12.2-2838-7.1.10"],
                    "1.11.x": ["1.11.2-2476-6.1.0-BETA-2792"],
                    "1.10.x": ["1.10.2-2477-5.2.0-BETA-2793"]
                }
            },
            {
                id: "spongevanilla",
                shortName: "SpongeVanilla",
                name: "SpongeVanilla",
                color: { background: "#50C888", foreground: "#FFFFFF" },
                priority: 2,
                url: "https://www.spongepowered.org/downloads/spongevanilla",
                versions: {
                    "7.x": ["1.12.2-7.1.10"],
                    "6.x": ["1.11.2-6.1.0-BETA-27"],
                    "5.x": ["1.10.2-5.2.0-BETA-403"],
                    "4.x": ["1.8.9-4.2.0-BETA-352"],
                },
                minecraftVersions: {
                    "1.12.x": ["1.12.2-7.1.10"],
                    "1.11.x": ["1.11.2-6.1.0-BETA-27"],
                    "1.10.x": ["1.10.2-5.2.0-BETA-403"],
                    "1.8.9": ["1.8.9-4.2.0-BETA-352"],
                }
            },
            {
                id: "sponge",
                shortName: "SpongeCommon",
                name: "SpongeCommon",
                color: { background: "#5D5DFF", foreground: "#FFFFFF" },
                priority: 1,
                url: "https://www.spongepowered.org/downloads",
                versions: {},
                minecraftVersions: {}
            },
            {
                id: "lantern",
                shortName: "Lantern",
                name: "Lantern",
                color: { background: "#4EC1B4", foreground: "#FFFFFF" },
                priority: 2,
                url: "https://www.lanternpowered.org/",
                versions: {},
                minecraftVersions: {}
            },
            {
                id: "forge",
                shortName: "Forge",
                name: "Forge Mods",
                parent: true,
                color: { background: "#DFA86A", foreground: "#FFFFFF" },
                priority: 0,
                url: "https://files.minecraftforge.net/",
                versions: {},
                minecraftVersions: {
                    "1.15.2": ["31.1.0"],
                    "1.15.1": ["30.0.51"],
                    "1.15": ["29.0.4"],
                    "1.14.4": ["28.2.0"],
                    "1.14.3": ["27.0.60"],
                    "1.14.2": ["26.0.63"],
                    "1.13.2": ["25.0.219"],
                    "1.12.2": ["14.23.5.2768"],
                    "1.12.1": ["14.22.1.2478"],
                    "1.12": ["14.21.1.2387"],
                    "1.11.2": ["13.20.1.2386"],
                    "1.11": ["13.19.1.2189"],
                    "1.10.2": ["12.18.3.2185"],
                    "1.10": ["12.18.0.2000"],
                    "1.9.4": ["12.17.0.2051"],
                    "1.9": ["12.16.1.1887"],
                    "1.8.9": ["11.15.1.2318"]
                }
            }
        ];
    }

    static get keys() {
        return this.values.map(platform => platform.id)
    }

    static isPlatformDependency(dependency) {
        return this.keys.includes(dependency.pluginId);
    }

    static fromId(id) {
        return this.values.filter(platform => platform.id === id)[0];
    }

    static getPlatforms(dependencyIds) {
        /* TODO
        return this.values
            .filter(p => dependencyIds.includes(p.id))
            .groupBy(_.platformCategory)
            .flatMap(_._2.groupBy(_.priority).maxBy(_._1)._2)
         */

        return this.values.filter(p => dependencyIds.includes(p.id))
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
            { name: "public",        class: "", showModal: false},
            { name: "new",           class: "project-new", showModal: false},
            { name: "needsChanges",  class: "striped project-needsChanges", showModal: false},
            { name: "needsApproval", class: "striped project-needsChanges", showModal: false},
            { name: "softDelete",    class: "striped project-hidden", showModal: true},
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
            {id: 'recommended', title: 'Recommended', color: '#00C8FF'},
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
