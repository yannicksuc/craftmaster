pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "FabricMC" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie" }
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.8"
}

stonecutter {
    create(rootProject) {
        // One node per supported Minecraft version (versions/<node>/gradle.properties). The source is shared; the
        // few differences are Stonecutter `//?` conditions, see docs/wiki/Developpement.md.
        versions("26.3", "26.2")
        // The source in src/ is written for 26.3; the 26.2 node builds a transformed copy
        vcsVersion = "26.3"
    }
}

rootProject.name = "CraftMaster"
