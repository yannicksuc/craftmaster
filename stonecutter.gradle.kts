// Stonecutter controller (root project). The per-version build is build.gradle.kts, applied to every node of
// settings.gradle.kts.
plugins {
    id("dev.kikugie.stonecutter")
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" apply false
}

stonecutter active "26.3" /* [SC] DO NOT EDIT */

// The usual commands keep working on 26.3 (the active version): runServer, runClient, runClient2, test.
// For 26.2: .\gradlew.bat :26.2:runServer, :26.2:runClient...
for (name in listOf("runServer", "runClient", "runClient2", "test")) {
    tasks.register(name) {
        group = "craftmaster"
        description = "$name on Minecraft ${stonecutter.current?.project} (use :26.2:$name for 26.2)"
        dependsOn(":${stonecutter.current?.project}:$name")
    }
}

// `.\gradlew.bat build`: builds and tests every version, then copies the jars to build/libs
// (craftmaster-<version>+26.3.jar, craftmaster-<version>+26.2.jar)
val collectJars = tasks.register<Copy>("collectJars") {
    group = "craftmaster"
    description = "Copies the jar of every Minecraft version to build/libs"
    for (v in stonecutter.versions) {
        dependsOn(":${v.project}:build")
        from(project(":${v.project}").layout.buildDirectory.dir("libs")) { exclude("*-sources.jar") }
    }
    into(layout.buildDirectory.dir("libs"))
}
tasks.register("build") {
    group = "build"
    description = "Builds and tests every Minecraft version, jars in build/libs"
    dependsOn(collectJars)
}
tasks.register<Delete>("clean") {
    group = "build"
    dependsOn(stonecutter.versions.map { ":${it.project}:clean" })
    delete(layout.buildDirectory)
}

// Names that differ between Minecraft versions, written /*$ name {*/26.3 name/*$}*/ in the source (Stonecutter
// swaps). Bigger differences are //? if <26.3 { ... } blocks.
stonecutter parameters {
    val is263 = semantics.eval(current.version, ">=26.3")
    // 26.3 renamed every PushReaction (POPPED was DESTROY, IMMOVEABLE was BLOCK)
    swaps.put("push_popped", if (is263) "POPPED" else "DESTROY")
    // Player#drop(stack, throwRandomly, prediction): the prediction argument is new in 26.3
    swaps.put("drop_prediction", if (is263) ", net.minecraft.util.Prediction.SERVER_ONLY" else "")
    swaps.put("structure_manager", if (is263) "getStructureTemplateManager" else "getStructureManager")
    swaps.put("within_clipped_manhattan", if (is263) "withinClippedManhattan" else "withinManhattan")
    // Client
    // PoseStack#mulPose(Quaternionf) is rotate in 26.3
    swaps.put("pose_rotate", if (is263) "rotate" else "mulPose")
    swaps.put("keyboard_type", if (is263) "KEYBOARD" else "KEYSYM")
    // Some SoundEvents constants are Holder<SoundEvent> in 26.3
    swaps.put("sound_value", if (is263) ".value()" else "")
    // 26.3 moved part of blaze3d into com.mojang.renderpearl
    swaps.put("gpu_buffer_class", if (is263) "com.mojang.renderpearl.api.buffers.GpuBuffer" else "com.mojang.blaze3d.buffers.GpuBuffer")
    swaps.put("gpu_device_class", if (is263) "com.mojang.renderpearl.api.device.GpuDevice" else "com.mojang.blaze3d.systems.GpuDevice")
    swaps.put("filter_mode_class", if (is263) "com.mojang.renderpearl.api.textures.FilterMode" else "com.mojang.blaze3d.textures.FilterMode")
}
