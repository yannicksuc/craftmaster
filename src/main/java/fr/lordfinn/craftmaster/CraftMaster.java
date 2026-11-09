package fr.lordfinn.craftmaster;

import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.command.CraftMasterCommands;
import fr.lordfinn.craftmaster.curtain.Curtains;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.network.Network;
import fr.lordfinn.craftmaster.regie.RegieServer;
import fr.lordfinn.craftmaster.registry.ModContent;
import net.fabricmc.api.ModInitializer;
import fr.lordfinn.craftmaster.command.WordArgument;
import net.fabricmc.fabric.api.command.v2.ArgumentTypeRegistry;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.commands.synchronization.SingletonArgumentInfo;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
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

        // Synced to the players' game (the mod is on both sides), so it parses the same way there
        ArgumentTypeRegistry.registerArgumentType(id("word"), WordArgument.class, SingletonArgumentInfo.contextFree(WordArgument::word));
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> CraftMasterCommands.register(dispatcher));

        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            FabricLoader fl = FabricLoader.getInstance();
            data = new CraftMasterData(fl.getConfigDir().resolve("craftmaster/config.yml"), LOG);
            // A server coming from the Paper plugin keeps its seasons, scores and busts
            data.load(fl.getGameDir().resolve("plugins/CraftMaster/config.yml"));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> Busts.updateAll(server));
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            if (data != null) data.save();
            Curtains.clear();
        });

        // Labels above busts whose chunk was not loaded at the last score change get refreshed when it loads
        ServerEntityEvents.ENTITY_LOAD.register(Busts::onEntityLoad);
        ServerTickEvents.END_SERVER_TICK.register(Curtains::tick);

        LOG.info("CraftMaster chargé.");
    }
}
