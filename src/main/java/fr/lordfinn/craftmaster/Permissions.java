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

    // ---------------------------------------------------------------- studio set (chantier F)

    /** Showing images on the studio screens (/cm media image), like the plugin's craftmaster.image; admins can too. */
    private static final Predicate<CommandSourceStack> IMAGE_ONLY =
            PermissionPredicates.require(CraftMaster.id("image"), PermissionLevel.GAMEMASTERS);
    public static final Predicate<CommandSourceStack> IMAGE = s -> IMAGE_ONLY.test(s) || ADMIN.test(s);

    public static boolean canShowImages(ServerPlayer player) {
        return IMAGE.test(player.createCommandSourceStack());
    }

    // ---------------------------------------------------------------- moderators' director (show.Rights)

    private static final Predicate<CommandSourceStack> GAME = PermissionPredicates.require(CraftMaster.id("jeu"), PermissionLevel.GAMEMASTERS);
    private static final Predicate<CommandSourceStack> TV = PermissionPredicates.require(CraftMaster.id("tv"), PermissionLevel.GAMEMASTERS);
    private static final Predicate<CommandSourceStack> REPLAY = PermissionPredicates.require(CraftMaster.id("replay"), PermissionLevel.GAMEMASTERS);
    /** craftmaster:modo = cameras, TVs and replays. */
    private static final Predicate<CommandSourceStack> MODO = PermissionPredicates.require(CraftMaster.id("modo"), PermissionLevel.GAMEMASTERS);

    /** The rights a player's permissions give, the organiser's aside (craftmaster:camera, image, tv, replay, jeu, modo). */
    public static java.util.Set<fr.lordfinn.craftmaster.show.Rights.Right> rights(ServerPlayer player) {
        CommandSourceStack s = player.createCommandSourceStack();
        var out = java.util.EnumSet.noneOf(fr.lordfinn.craftmaster.show.Rights.Right.class);
        if (MODO.test(s)) out.addAll(fr.lordfinn.craftmaster.show.Rights.MODO);
        if (CAMERA.test(s)) out.add(fr.lordfinn.craftmaster.show.Rights.Right.CAMERA);
        if (TV.test(s) || IMAGE_ONLY.test(s)) out.add(fr.lordfinn.craftmaster.show.Rights.Right.TV);
        if (REPLAY.test(s)) out.add(fr.lordfinn.craftmaster.show.Rights.Right.REPLAY);
        if (GAME.test(s)) out.add(fr.lordfinn.craftmaster.show.Rights.Right.GAME);
        return out;
    }
}
