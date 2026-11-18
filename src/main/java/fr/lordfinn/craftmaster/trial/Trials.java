package fr.lordfinn.craftmaster.trial;

import fr.lordfinn.craftmaster.command.TrialCommands;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.server.level.ServerPlayer;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;

/**
 * Chantier H, "épreuves": task props, buzzers, judging tools (results sheet, stopwatches, tape, counter, scales,
 * zones, red card) and creative objects (drawing boards, plinth, costume trunk, free naming). One call from
 * {@code CraftMaster#onInitialize} wires it all.
 */
public final class Trials {

    private Trials() {}

    public static void register() {
        TrialContent.register();
        TrialNetwork.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> TrialCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> fr.lordfinn.craftmaster.command.ArenaCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            TrialZones.load();
            fr.lordfinn.craftmaster.trial.kit.Arenas.load();
        });
        fr.lordfinn.craftmaster.trial.kit.Arenas.register();
        // The trial kit: banners of a drawing field, password-locked pickups and vases (vanilla entities)
        UseBlockCallback.EVENT.register(fr.lordfinn.craftmaster.trial.kit.Arenas::onUseBlock);
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> fr.lordfinn.craftmaster.trial.kit.Pickups.onUse(player, level, hand, entity));
        net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.register((player, level, hand, entity, hit) ->
                fr.lordfinn.craftmaster.trial.kit.Pickups.onUse(player, level, hand, entity));
        ServerTickEvents.END_SERVER_TICK.register(fr.lordfinn.craftmaster.trial.kit.Drawing::tick);
        ServerTickEvents.END_SERVER_TICK.register(fr.lordfinn.craftmaster.trial.kit.GroundEnd::tick);
        fr.lordfinn.craftmaster.trial.kit.InventoryCam.register();
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            TrialProps.clear(server);
            Judging.reset();
            Buzzers.LOCK.reset();
            TrialLog.clear();
        });
        ServerTickEvents.END_SERVER_TICK.register(Buzzers::tick);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            Judging.tick(server);
            TrialProps.tick(server);
            TrialZones.tick(server);
        });
        // The measuring tools work on any block, even one that opens on use (a chest): they come first
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            var stack = player.getItemInHand(hand);
            if (player.isSpectator() || !(stack.is(TrialContent.TALLY_COUNTER) || stack.is(TrialContent.MEASURING_TAPE)
                    || stack.is(TrialContent.PROP_WAND))) return InteractionResult.PASS;
            return stack.getItem().useOn(new UseOnContext(player, hand, hit));
        });
        // The tally counter on any entity holding items (an item alone only gets living entities, and after them)
        UseEntityCallback.EVENT.register((player, level, hand, entity, hit) -> {
            if (player.isSpectator() || !player.getItemInHand(hand).is(TrialContent.TALLY_COUNTER)) return InteractionResult.PASS;
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
            return fr.lordfinn.craftmaster.trial.item.CounterItem.useOnEntity(sp, hand, entity);
        });
        TapeRibbons.register();
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Judging.onJoin(handler.getPlayer()));
    }
}
