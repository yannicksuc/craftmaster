package fr.lordfinn.craftmaster.client.task;

import com.google.gson.Gson;
import com.mojang.blaze3d.platform.InputConstants;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.client.CraftMasterClient;
import fr.lordfinn.craftmaster.task.TaskNetwork;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.Map;

/** Client side of the tasks, envelopes and notes (chantier D): screens, the J key, the overlay card, Twitch. */
public final class TaskClient {

    private TaskClient() {}

    /** Shows the task of the moment (organisers: even secret, with "reveal"). */
    public static final KeyMapping TASK_KEY = new KeyMapping("key.craftmaster.task", InputConstants.Type./*$ keyboard_type {*/KEYBOARD/*$}*/, InputConstants.KEY_J,
            CraftMasterClient.CATEGORY);

    private static final Gson GSON = new Gson();

    public static void init() {
        KeyMappingHelper.registerKeyMapping(TASK_KEY);
        HudElementRegistry.addLast(CraftMaster.id("task_card"), new CurrentTask.Hud());
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (TASK_KEY.consumeClick()) openTask(mc);
        });

        ClientPlayNetworking.registerGlobalReceiver(TaskNetwork.State.TYPE, (p, ctx) -> CurrentTask.set(p));
        ClientPlayNetworking.registerGlobalReceiver(TaskNetwork.ShowCard.TYPE, (p, ctx) -> CurrentTask.showCard(p.ticks()));
        ClientPlayNetworking.registerGlobalReceiver(TaskNetwork.Paper.TYPE, (p, ctx) -> {
            Minecraft mc = ctx.client();
            // Opened from an editor: closing the card goes back to it
            Screen parent = mc.gui.screen() instanceof TaskEditorScreen || mc.gui.screen() instanceof NoteEditorScreen ? mc.gui.screen() : null;
            mc.gui.setScreen(open(p, parent));
        });
        ClientPlayNetworking.registerGlobalReceiver(TaskNetwork.PaperUpdate.TYPE, (p, ctx) -> {
            if (ctx.client().gui.screen() instanceof LetterScreen l) l.update(p);
        });
        ClientPlayNetworking.registerGlobalReceiver(TaskNetwork.OpenEditor.TYPE, (p, ctx) ->
                ctx.client().gui.setScreen(p.mode() == 1 ? new SealScreen(p) : new TaskEditorScreen(p)));
        ClientPlayNetworking.registerGlobalReceiver(TaskNetwork.CloseCard.TYPE, (p, ctx) -> {
            Screen s = ctx.client().gui.screen();
            if (s instanceof PaperScreen || s instanceof LetterScreen || s instanceof TaskEditorScreen || s instanceof SealScreen || s instanceof NoteEditorScreen)
                ctx.client().gui.setScreen(null);
        });
        ClientPlayNetworking.registerGlobalReceiver(TaskNetwork.OpenNote.TYPE, (p, ctx) -> ctx.client().gui.setScreen(new NoteEditorScreen(p.note())));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {
            CurrentTask.set(new TaskNetwork.State(TaskNetwork.Card.NONE, false));
            CurrentTask.hideCard();
        });

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, access) -> {
            for (String name : new String[]{"craftMasterTwitch", "craftmastertwitch"})
                dispatcher.register(ClientCommands.literal(name)
                        .executes(c -> {
                            c.getSource().sendFeedback(Component.translatable("message.craftmaster.twitch_status", TwitchChat.status()).withStyle(ChatFormatting.GOLD));
                            return 1;
                        })
                        .then(ClientCommands.literal("reload").executes(c -> {
                            TwitchChat.reload();
                            c.getSource().sendFeedback(Component.translatable("message.craftmaster.twitch_status", TwitchChat.status()).withStyle(ChatFormatting.GOLD));
                            return 1;
                        }))
                        // Same as a viewer typing !tache (cooldown included): to check the broadcast set-up
                        .then(ClientCommands.literal("test").executes(c -> {
                            TwitchChat.trigger("test");
                            return 1;
                        }))
                        // Twitch clip links posted in the chat: a click prepares /cmtele <tele> lien <link>
                        .then(ClientCommands.literal("clips").executes(c -> {
                            var links = TwitchChat.clipLinks();
                            if (links.isEmpty()) {
                                c.getSource().sendFeedback(Component.literal("Aucun lien de clip Twitch vu dans le chat.").withStyle(ChatFormatting.GRAY));
                                return 0;
                            }
                            c.getSource().sendFeedback(Component.literal("Clips Twitch du chat (clic : /cmtele <tele> lien …) :").withStyle(ChatFormatting.GOLD));
                            int i = 1;
                            for (TwitchChat.ClipLink l : links) {
                                String cmd = "/cmtele <tele> lien " + l.url();
                                long ago = (System.currentTimeMillis() - l.at()) / 60_000;
                                c.getSource().sendFeedback(Component.literal(" " + (i++) + ". ").withStyle(ChatFormatting.YELLOW)
                                        .append(Component.literal(l.url()).withStyle(ChatFormatting.WHITE))
                                        .append(Component.literal("  " + l.user() + ", il y a " + ago + " min").withStyle(ChatFormatting.GRAY))
                                        .withStyle(st -> st.withClickEvent(new net.minecraft.network.chat.ClickEvent.SuggestCommand(cmd))));
                            }
                            return links.size();
                        }))
                        // As if a viewer had posted this message (checks the link detection without Twitch)
                        .then(ClientCommands.literal("message").then(ClientCommands.argument("texte", com.mojang.brigadier.arguments.StringArgumentType.greedyString())
                                .executes(c -> {
                                    TwitchChat.clipLinks("test", com.mojang.brigadier.arguments.StringArgumentType.getString(c, "texte"));
                                    c.getSource().sendFeedback(Component.literal("Liens de clips retenus : " + TwitchChat.clipLinks().size()).withStyle(ChatFormatting.GOLD));
                                    return 1;
                                }))));
        });
        TwitchChat.reload();
    }

    /** The reading screen of a paper: the folded letter for a task or a free text, the sheet for a note. */
    static Screen open(TaskNetwork.Paper p, Screen parent) {
        return "task".equals(p.kind()) || "text".equals(p.kind()) ? new LetterScreen(p, parent) : new PaperScreen(p, parent);
    }

    static void openTask(Minecraft mc) {
        if (ClientPlayNetworking.canSend(TaskNetwork.RequestTask.TYPE)) ClientPlayNetworking.send(new TaskNetwork.RequestTask());
        else if (mc.player != null) mc.player.sendOverlayMessage(Component.translatable("message.craftmaster.no_server_mod"));
    }

    /** Organisers' screens: actions run on the server like the commands (answers in the chat). */
    static void runActions(List<Map<String, Object>> actions) {
        if (ClientPlayNetworking.canSend(TaskNetwork.RunActions.TYPE)) ClientPlayNetworking.send(new TaskNetwork.RunActions(GSON.toJson(actions)));
    }

    static void requestEditor(String mancheId) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        mc.player.connection.sendCommand(mancheId == null || mancheId.isEmpty() ? "craftMasterTask edit" : "craftMasterTask edit " + mancheId);
    }

    static void noteOrder(String id, String order) {
        if (ClientPlayNetworking.canSend(TaskNetwork.NoteOrder.TYPE)) ClientPlayNetworking.send(new TaskNetwork.NoteOrder(id, order));
    }
}
