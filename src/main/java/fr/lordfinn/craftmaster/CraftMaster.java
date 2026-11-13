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
        // The show frozen after a crash: first, so its stop listener saves the clocks before the others forget them
        fr.lordfinn.craftmaster.reprise.Reprise.register();
        // Studio set (chantier F): blocks, screens, timer sync, commands
        fr.lordfinn.craftmaster.studio.Studio.register();
        Network.register();
        // The referee's score entry screen
        fr.lordfinn.craftmaster.network.ScoreEntryNetwork.register();
        RegieServer.register();
        fr.lordfinn.craftmaster.regie.ModoDesk.register();
        Portraits.register();
        // Chantier H: task props, judging tools, creative objects
        fr.lordfinn.craftmaster.trial.Trials.register();
        // Wardrobe: change into a complete outfit in one click (docs/wiki/GardeRobe.md)
        fr.lordfinn.craftmaster.wardrobe.Wardrobes.register();
        // Task toys: rubber ducks, balls, eggs, golf, balloons, targets that count, graduated glass (toy/)
        fr.lordfinn.craftmaster.toy.Toys.register();
        // TV direction (chantier E): cameras, show state, overlay cues, credits
        Director.register();
        // Replays of the rounds with Server Replay, if installed (optional)
        fr.lordfinn.craftmaster.replay.Replays.register();
        fr.lordfinn.craftmaster.replay.ReplayViewing.register();
        // Tasks, envelopes, hidden notes (chantier D)
        fr.lordfinn.craftmaster.task.Tasks.register();
        Curtains.register();
        // Garage doors that slide up into the lintel (garage/), WorldEdit without block updates at login (compat/)
        fr.lordfinn.craftmaster.garage.GarageDoors.register();
        fr.lordfinn.craftmaster.compat.WorldEditPerf.register();
        // Screen effects (fx/): post-processing of the view, wearable glasses and masks, /fx
        fr.lordfinn.craftmaster.fx.ScreenEffectsServer.register();
        // Clips recorded in the game, videos and Twitch on the studio screens (media/): /cmtele, /cmclip, /cmchaine
        fr.lordfinn.craftmaster.media.MediaServer.register();

        // Synced to the players' game (the mod is on both sides), so it parses the same way there
        ArgumentTypeRegistry.registerArgumentType(id("word"), WordArgument.class, SingletonArgumentInfo.contextFree(WordArgument::word));
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> CraftMasterCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> PortraitCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> CameraCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> fr.lordfinn.craftmaster.command.ModoCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> fr.lordfinn.craftmaster.command.FxCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> fr.lordfinn.craftmaster.command.ReplayCommands.register(dispatcher));
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> fr.lordfinn.craftmaster.command.RepriseCommands.register(dispatcher));

        // Stage looks and cue lists (docs/wiki/Scenographie.md)
        ServerLifecycleEvents.SERVER_STARTED.register(server -> fr.lordfinn.craftmaster.stage.Scenography.load());
        fr.lordfinn.craftmaster.stage.Cues.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, ctx, selection) -> fr.lordfinn.craftmaster.stage.StageCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            FabricLoader fl = FabricLoader.getInstance();
            data = new CraftMasterData(fl.getConfigDir().resolve("craftmaster/config.yml"), LOG);
            // A server coming from the Paper plugin keeps its seasons, scores and busts
            data.load(fl.getGameDir().resolve("plugins/CraftMaster/config.yml"));
            Cameras.load(fl.getConfigDir().resolve("craftmaster/cameras.json"));
            fr.lordfinn.craftmaster.camera.CameraPaths.load(fl.getConfigDir().resolve("craftmaster/trajets.json"));
            fr.lordfinn.craftmaster.show.Moderators.load(fl.getConfigDir().resolve("craftmaster/moderateurs.json"));
            fr.lordfinn.craftmaster.regie.SiteLink.load(fl.getConfigDir().resolve("craftmaster/site.json"));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            Busts.updateAll(server);
            ActionExecutor.onServerStarted();
            // Moderators' page on lordfinn.fr: this server calls the site (never the reverse)
            fr.lordfinn.craftmaster.regie.SiteLink.start(server);
        });
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            fr.lordfinn.craftmaster.regie.SiteLink.stop();
            Show.reset(server);
            if (data != null) data.save();
            Curtains.clear();
        });

        // Labels above busts whose chunk was not loaded at the last score change get refreshed when it loads
        ServerEntityEvents.ENTITY_LOAD.register(Busts::onEntityLoad);
        // Camera tripods follow the camera registry (a removed or replaced camera's tripod goes)
        ServerEntityEvents.ENTITY_LOAD.register(Cameras::onEntityLoad);
        ServerTickEvents.END_SERVER_TICK.register(Curtains::tick);
        // Dust line of the travellings being recorded or shown (/cam trajet)
        ServerTickEvents.END_SERVER_TICK.register(fr.lordfinn.craftmaster.camera.PathRecording::tick);
        // Round timer, countdown and staged reveal
        ServerTickEvents.END_SERVER_TICK.register(Show::tick);
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> Show.onJoin(handler.getPlayer()));

        LOG.info("CraftMaster chargé.");
    }
}
