package fr.lordfinn.craftmaster.client.regie;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.regie.RegiePayloads;
import fr.lordfinn.craftmaster.regie.RegieService;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The régie agent on the organiser's side (docs/wiki/RegieIA.md): relays between the voice control service, where
 * the agent runs, and the server, where its tools run.
 * <ol>
 *   <li>a request starts: from the voice control (the service answered an {@code agentJob}) or {@code /ia <texte>};</li>
 *   <li>a worker polls the service every 200 ms; each tool call goes to the server ({@link RegiePayloads.AgentCall}),
 *       its answer ({@link RegiePayloads.AgentReply}) back to the service;</li>
 *   <li>when the agent is done, its reply is written in this player's chat (only his: never on the broadcast
 *       picture), with [Annuler] when something changed.</li>
 * </ol>
 */
public final class RegieAgent {

    private RegieAgent() {}

    private static final String URL = System.getProperty("craftmaster.regie.url", RegieService.DEFAULT_URL);
    private static final AtomicInteger IDS = new AtomicInteger();
    /** Call id sent to the server → (job, cid in the service; or "_fin" with cid -1). */
    private static final Map<Integer, Pending> PENDING = new ConcurrentHashMap<>();
    private static final Map<String, String> RUNNING = new ConcurrentHashMap<>();   // job → request text

    record Pending(String job, int cid, String request, String reply) {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(RegiePayloads.AgentReply.TYPE, (p, ctx) -> onReply(p));
        ClientPlayConnectionEvents.DISCONNECT.register((h, mc) -> {
            RUNNING.clear();
            PENDING.clear();
        });
        // Typed requests: when the microphone is not an option (or to try something precisely)
        ClientCommandRegistrationCallback.EVENT.register((d, ctx) -> d.register(ClientCommands.literal("ia")
                .then(ClientCommands.argument("demande", StringArgumentType.greedyString()).executes(c -> {
                    ask(StringArgumentType.getString(c, "demande"));
                    return 1;
                }))));
    }

    /** {@code /ia …}: starts the agent on a typed request. */
    static void ask(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (!VoiceRegie.isAdmin()) {
            chat(Component.translatable("message.craftmaster.regie.not_admin").withStyle(ChatFormatting.RED));
            return;
        }
        String player = mc.player.getPlainTextName();
        Thread.ofPlatform().daemon().name("CraftMaster-regie-agent").start(() -> {
            String job = new RegieService(URL).agentStart(text, null, player);
            mc.execute(() -> {
                if (job == null) chat(prefix().append(Component.translatable("message.craftmaster.regie.unreachable").withStyle(ChatFormatting.RED)));
                else track(job, text);
            });
        });
    }

    /** Follows an agent request started by the service (voice) or by {@link #ask}. */
    public static void track(String job, String request) {
        if (RUNNING.putIfAbsent(job, request) != null) return;
        chat(prefix().append(Component.literal("« " + request + " » — je m'en occupe…").withStyle(ChatFormatting.GRAY)));
        Thread.ofPlatform().daemon().name("CraftMaster-regie-agent").start(() -> poll(job, request));
    }

    // ------------------------------------------------------------------ worker

    private static void poll(String job, String request) {
        Minecraft mc = Minecraft.getInstance();
        RegieService service = new RegieService(URL);
        long unreachableSince = 0;
        while (RUNNING.containsKey(job)) {
            RegieService.AgentPoll p = service.agentPoll(job);
            if ("unreachable".equals(p.error())) {
                if (unreachableSince == 0) unreachableSince = System.currentTimeMillis();
                if (System.currentTimeMillis() - unreachableSince > 10_000) {
                    end(mc, job, request, "", "Service de régie injoignable : arrêté.");
                    return;
                }
            } else unreachableSince = 0;
            for (RegieService.AgentCall c : p.calls()) {
                int id = IDS.incrementAndGet();
                PENDING.put(id, new Pending(job, c.cid(), request, null));
                mc.execute(() -> {
                    if (ClientPlayNetworking.canSend(RegiePayloads.AgentCall.TYPE))
                        ClientPlayNetworking.send(new RegiePayloads.AgentCall(id, job, c.tool(), c.argsJson()));
                    else {
                        PENDING.remove(id);
                        Thread.ofPlatform().daemon().start(() -> service.agentResult(job, c.cid(), false, "Le serveur n'a pas le mod CraftMaster."));
                    }
                });
            }
            if (p.done()) {
                end(mc, job, request, p.reply(), p.gone() ? "La demande n'existe plus côté service." : null);
                return;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    /** The agent is done: asks the server what changed (journal, undo), then writes the reply. */
    private static void end(Minecraft mc, String job, String request, String reply, String error) {
        RUNNING.remove(job);
        mc.execute(() -> {
            if (error != null) {
                chat(prefix().append(Component.literal(error).withStyle(ChatFormatting.RED)));
                return;
            }
            JsonObject args = new JsonObject();
            args.addProperty("demande", request);
            args.addProperty("reponse", reply);
            int id = IDS.incrementAndGet();
            PENDING.put(id, new Pending(job, -1, request, reply));
            if (ClientPlayNetworking.canSend(RegiePayloads.AgentCall.TYPE))
                ClientPlayNetworking.send(new RegiePayloads.AgentCall(id, job, "_fin", args.toString()));
            else showEnd(job, reply, null);
        });
    }

    // ------------------------------------------------------------------ server answers (client thread)

    private static void onReply(RegiePayloads.AgentReply r) {
        Pending p = PENDING.remove(r.id());
        if (p == null) return;
        if (p.cid() < 0) {
            JsonObject o = null;
            try {
                o = new Gson().fromJson(r.output(), JsonObject.class);
            } catch (RuntimeException ignored) {
            }
            showEnd(p.job(), p.reply(), o);
            return;
        }
        RegieService service = new RegieService(URL);
        Thread.ofPlatform().daemon().name("CraftMaster-regie-agent").start(() -> service.agentResult(p.job(), p.cid(), r.ok(), r.output()));
    }

    private static void showEnd(String job, String reply, JsonObject summary) {
        MutableComponent line = prefix().append(Component.literal(reply.isBlank() ? "C'est fait." : reply).withStyle(ChatFormatting.WHITE));
        if (summary != null && summary.has("done") && !summary.getAsJsonArray("done").isEmpty()) {
            StringBuilder done = new StringBuilder("Ce qui a été fait :");
            summary.getAsJsonArray("done").forEach(e -> done.append("\n").append(e.getAsString()));
            line.append(Component.literal(" [détail]").withStyle(st -> st.withColor(ChatFormatting.DARK_AQUA)
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal(done.toString())))));
        }
        if (summary != null && summary.has("changed") && summary.get("changed").getAsBoolean())
            line.append(Component.literal(" [Annuler]").withStyle(st -> st.withColor(ChatFormatting.RED)
                    .withClickEvent(new ClickEvent.RunCommand("/regieia annuler " + job))
                    .withHoverEvent(new HoverEvent.ShowText(Component.literal("Remettre comme avant cette demande")))));
        chat(line);
    }

    private static MutableComponent prefix() {
        return Component.literal("[Régie IA] ").withStyle(ChatFormatting.AQUA);
    }

    /** Only in this player's chat, and never in a broadcasting game (the picture the public sees). */
    private static void chat(Component c) {
        Minecraft mc = Minecraft.getInstance();
        CraftMaster.LOG.info("Régie IA : {}", c.getString());
        if (mc.player == null || fr.lordfinn.craftmaster.client.broadcast.Broadcast.active()) return;
        mc.player.sendSystemMessage(c);
    }
}
