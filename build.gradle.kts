plugins {
    java
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT"
}

version = property("mod_version") as String
group = property("maven_group") as String

base {
    archivesName = property("mod_id") as String
}

// Minecraft 26.x needs Java 25
java.toolchain.languageVersion = JavaLanguageVersion.of(25)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

val minecraftVersion = property("minecraft_version") as String

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    // YAML reader/writer for config/craftmaster/config.yml (same file format as the old Paper plugin), shipped inside the jar
    implementation("org.yaml:snakeyaml:${property("snakeyaml_version")}")
    include("org.yaml:snakeyaml:${property("snakeyaml_version")}")
}

loom {
    // Client-only code (screens, key binding, renderers) lives in src/client so the dedicated server never loads it
    splitEnvironmentSourceSets()
    mods {
        create("craftmaster") {
            sourceSet(sourceSets.main.get())
            sourceSet(sourceSets.getByName("client"))
        }
    }
    runs {
        named("client") {
            client()
            programArgs("--username", "LordFinn")
            runDir = "run"
        }
        named("server") {
            server()
            runDir = "run-server"
        }
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to project.version,
        "minecraft_version" to minecraftVersion,
        "loader_version" to project.property("fabric_loader_version"),
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"
    filesMatching("fabric.mod.json") { expand(props) }
}
