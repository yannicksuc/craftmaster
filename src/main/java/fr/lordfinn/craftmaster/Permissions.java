package fr.lordfinn.craftmaster;

import net.fabricmc.fabric.api.permission.v1.PermissionPredicates;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.PermissionLevel;

import java.util.function.Predicate;

/** Who organises the event: the craftmaster:admin permission (LuckPerms...) or operator level 2. */
public final class Permissions {

    private Permissions() {}

    public static final Predicate<CommandSourceStack> ADMIN =
            PermissionPredicates.require(CraftMaster.id("admin"), PermissionLevel.GAMEMASTERS);

    public static boolean isAdmin(ServerPlayer player) {
        return ADMIN.test(player.createCommandSourceStack());
    }

    /** TV direction (chantier E): organisers, or whoever has craftmaster:camera, drive the studio cameras. */
    public static final Predicate<CommandSourceStack> CAMERA =
            ADMIN.or(PermissionPredicates.require(CraftMaster.id("camera"), PermissionLevel.GAMEMASTERS));

    public static boolean canDriveCameras(ServerPlayer player) {
        return CAMERA.test(player.createCommandSourceStack());
    }
}
