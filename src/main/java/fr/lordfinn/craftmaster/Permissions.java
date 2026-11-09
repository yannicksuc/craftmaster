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
}
