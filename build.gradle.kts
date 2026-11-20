// Per-version build: Stonecutter applies it to every node of settings.gradle.kts (versions/26.3, versions/26.2).
// minecraft_version, fabric_api_version... come from that node's versions/<node>/gradle.properties.
plugins {
    java
    id("net.fabricmc.fabric-loom")
}

val minecraftVersion = property("minecraft_version") as String

// Fabric-style versioned jar: craftmaster-2.0.0+26.2.jar
version = "${property("mod_version")}+$minecraftVersion"
group = property("maven_group") as String

base {
    archivesName = property("mod_id") as String
}

// Minecraft 26.x needs Java 25
java.toolchain.languageVersion = JavaLanguageVersion.of(25)

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

repositories {
    // WaterMedia (videos on the studio screens, clips): optional, only compiled against
    exclusiveContent {
        forRepository { maven("https://api.modrinth.com/maven") { name = "Modrinth" } }
        filter { includeGroup("maven.modrinth") }
    }
    // Simple Voice Chat's API (who is speaking, for the automatic direction): optional, only compiled against
    exclusiveContent {
        forRepository { maven("https://maven.maxhenkel.de/repository/public") { name = "henkelmax" } }
        filter { includeGroup("de.maxhenkel.voicechat") }
    }
}

// Axiom's client API (axiomclientapi) is nested in Axiom's jar: extracted once to build/axiom-api
val axiomJar: Configuration by configurations.creating
dependencies { axiomJar("maven.modrinth:axiom:${property("axiom_version")}") }
val axiomApi = layout.buildDirectory.file("axiom-api/axiomclientapi.jar").get().asFile
if (!axiomApi.exists()) {
    copy {
        from(zipTree(axiomJar.singleFile).matching { include("META-INF/jars/axiomclientapi*.jar") })
        eachFile { path = "axiomclientapi.jar" }
        includeEmptyDirs = false
        into(axiomApi.parentFile)
    }
}

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    // Optional at runtime (the modpack ships it with its Binaries, 26.2 only): every use goes through
    // client/media/Media.available(); the dev client loads it from run-26.2/mods
    compileOnly("maven.modrinth:watermedia:${property("watermedia_version")}")
    // Optional at runtime: with Axiom, the travelling steps are moved / turned with its gizmos (client/axiom, mixins
    // applied only when Axiom is loaded); its client API, nested in its jar, is needed to compile against it
    compileOnly("maven.modrinth:axiom:${property("axiom_version")}")
    compileOnly("de.maxhenkel.voicechat:voicechat-api:2.6.0")
    compileOnly(files(axiomApi))
    implementation("net.fabricmc:fabric-loader:${property("fabric_loader_version")}")
    implementation("net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")

    // YAML reader/writer for config/craftmaster/config.yml (same file format as the old Paper plugin), shipped inside the jar
    implementation("org.yaml:snakeyaml:${property("snakeyaml_version")}")
    include("org.yaml:snakeyaml:${property("snakeyaml_version")}")

    // Unit tests (data store, command arguments): .\gradlew.bat test
    testImplementation(platform("org.junit:junit-bom:5.11.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}

// Dev run folders at the repository root (run/, run2/, run-server/ for 26.3; run-26.2/... for 26.2)
fun rootRunDir(key: String) = rootProject.file(property(key) as String).relativeTo(projectDir).invariantSeparatorsPath

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
            // -PquickPlayMultiplayer=localhost:25691 joins the dev server straight from launch
            (findProperty("quickPlayMultiplayer") as String?)?.let { programArgs("--quickPlayMultiplayer", it) }
            // -PbackgroundClient: the window opens without taking the focus (Minecraft 26.x windows are SDL3,
            // which reads this hint from the environment), see scripts/dev-client.ps1
            if (hasProperty("backgroundClient")) {
                environmentVariable("SDL_WINDOW_ACTIVATE_WHEN_SHOWN", "0")
                // Minecraft 26.2 windows are GLFW: client.mixin.DevWindowMixin
                vmArg("-Dcraftmaster.dev.backgroundClient=true")
            }
            // -PregieTrigger: the voice control plays the .wav files dropped in run/regie-trigger as if the key
            // were held while they are spoken (tests without keyboard or microphone, docs/wiki/RegieVocale.md)
            if (hasProperty("regieTrigger")) vmArg("-Dcraftmaster.regie.triggerDir=" + rootProject.file(project.property("run_dir") as String + "/regie-trigger").absolutePath)
            runDir = rootRunDir("run_dir")
        }
        // Second player without admin rights, to see what participants see: .\gradlew.bat runClient2
        create("client2") {
            client()
            programArgs("--username", "Joueur2")
            (findProperty("quickPlayMultiplayer") as String?)?.let { programArgs("--quickPlayMultiplayer", it) }
            // -PwindowSize=1920x1080: the broadcast picture at Twitch's size (scripts/dev-client.ps1 -Size)
            (findProperty("windowSize") as String?)?.split("x")?.let { programArgs("--width", it[0], "--height", it[1]) }
            if (hasProperty("backgroundClient")) {
                environmentVariable("SDL_WINDOW_ACTIVATE_WHEN_SHOWN", "0")
                // Minecraft 26.2 windows are GLFW: client.mixin.DevWindowMixin
                vmArg("-Dcraftmaster.dev.backgroundClient=true")
            }
            // -PregieUrl=http://127.0.0.1:25791: a voice control service of its own (tests of the régie agent)
            (findProperty("regieUrl") as String?)?.let { vmArg("-Dcraftmaster.regie.url=$it") }
            runDir = rootRunDir("run2_dir")
        }
        named("server") {
            server()
            runDir = rootRunDir("run_server_dir")
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
    // The shaders are written in 26.3's GLSL. 26.2 still uses #moj_import, and no explicit in/out locations (its
    // vertex shaders do not give any, the separate shader objects extension is 26.3's)
    inputs.property("glsl_dialect", project.property("glsl_dialect"))
    if (project.property("glsl_dialect") == "moj_import") {
        filesMatching(listOf("**/shaders/**/*.fsh", "**/shaders/**/*.vsh", "**/shaders/**/*.glsl")) {
            filter { line ->
                if (line.trim() == "#extension GL_ARB_separate_shader_objects : require") ""
                else line.replace(Regex("^#include <"), "#moj_import <")
                    .replace(Regex("""layout\(location = \d+\) (in|out) """), "$1 ")
                    .replace("gl_VertexIndex", "gl_VertexID")
            }
        }
    }
}
