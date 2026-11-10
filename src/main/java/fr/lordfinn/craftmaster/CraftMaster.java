package fr.lordfinn.craftmaster;

import fr.lordfinn.craftmaster.broadcast.Director;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.camera.Cameras;
import fr.lordfinn.craftmaster.command.CameraCommands;
import fr.lordfinn.craftmaster.command.CraftMasterCommands;
import fr.lordfinn.craftmaster.curtain.Curtains;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.network.Network;
import fr.lordfinn.craftmaster.regie.RegieServer;
import fr.lordfinn.craftmaster.portrait.PortraitCommands;
import fr.lordfinn.craftmaster.portrait.Portraits;
import fr.lordfinn.craftmaster.registry.ModContent;
import fr.lordfinn.craftmaster.show.ActionExecutor;
import fr.lordfinn.craftmaster.show.Show;
import net.fabricmc.api.ModInitializer;
import fr.lordfinn.craftmaster.command.WordArgument;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CraftMaster implements ModInitializer {

    public static final String MOD_ID = "craftmaster";
    public static final Logger LOG = LoggerFactory.getLogger("CraftMaster");

    private static CraftMasterData data;

    public static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(MOD_ID, path);
    }

    /** Loaded when the server (or the single-player world) starts. */
    public static CraftMasterData data() {
        return data;
    }

    @Override
    public void onInitialize() {
        ModContent.register();
        Network.register();
        RegieServer.register();
        Portraits.register();
        // TV direction (chantier E): cameras, show state, overlay cues, credits
        Director.register();

        // Synced to the players' game (the mod is on both sides), so it parses the same way there
        ArgumentTypeRegistry.registerArgumentType(id("word"), WordArgument.class, SingletonArgumentInfo.contextFree(WordArgument::word));
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> CraftMasterCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> PortraitCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> CameraCommands.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            FabricLoader fl = FabricLoader.getInstance();
            data = new CraftMasterData(fl.getConfigDir().resolve("craftmaster/config.yml"), LOG);
            // A server coming from the Paper plugin keeps its seasons, scores and busts
            data.load(fl.getGameDir().resolve("plugins/CraftMaster/config.yml"));
            Cameras.load(fl.getConfigDir().resolve("craftmaster/cameras.json"));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Busts.updateAll(server);
            ActionExecutor.onServerStarted();
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            Show.reset(server);
            if (data != null) data.save();
            Curtains.clear();
        });

        // Labels above busts whose chunk was not loaded at the last score change get refreshed when it loads
        ServerEntityEvents.ENTITY_LOAD.register(Busts::onEntityLoad);
        // Camera tripods follow the camera registry (a removed or replaced camera's tripod goes)
        ServerEntityEvents.ENTITY_LOAD.register(Cameras::onEntityLoad);
        ServerTickEvents.END_SERVER_TICK.register(Curtains::tick);
        // Round timer, countdown and staged reveal
        ServerTickEvents.END_SERVER_TICK.register(Show::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Show.onJoin(handler.getPlayer()));

        LOG.info("CraftMaster chargé.");
    }
}
