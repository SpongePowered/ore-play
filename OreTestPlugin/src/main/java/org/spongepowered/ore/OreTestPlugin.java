package org.spongepowered.ore;

import org.spongepowered.api.plugin.Dependency;
import org.spongepowered.api.plugin.Plugin;

@Plugin(id = "oretest",
        dependencies = {
            @Dependency(id = "bookotd", version = "1.0.0"),
            @Dependency(id = "forge", version = "12.18.2.2151")
        },
        authors = { "SpongePowered", "windy", "Zidane", "gabizou" }
)
public class OreTestPlugin {}
