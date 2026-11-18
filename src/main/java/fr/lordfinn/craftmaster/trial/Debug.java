package fr.lordfinn.craftmaster.trial;

import com.mojang.brigadier.tree.LiteralCommandNode;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.broadcast.Director;
import fr.lordfinn.craftmaster.data.BlockSpot;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.show.Show;
import fr.lordfinn.craftmaster.stage.Look;
import fr.lordfinn.craftmaster.stage.Scenography;
import fr.lordfinn.craftmaster.trial.kit.Arena;
import fr.lordfinn.craftmaster.trial.kit.Arenas;
import fr.lordfinn.craftmaster.trial.kit.InventoryCam;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * The debug mode of the organisers ({@code /cm debug}): for them only, never on the broadcast nor for the
 * contestants. Particles sent to their game alone outline the arenas (reference gold, copies green), the spawn
 * points (white column), the drawing fields (blue), the trial zones (red), the stage anchor and the stage points;
 * {@link Lines} sends the state of the show to their HUD (manche, timer, the arena they stand in, the options of
 * each arena, what is on air, the inventory camera, the restart after a crash).
 * <p>
 * A game in broadcast mode never gets them, even an organiser's ({@link Director#broadcasters()}).
 */
public final class Debug {

    private Debug() {}

    /** Server → organiser: the HUD lines of the debug mode (empty = mode off). */
    public record Lines(List<String> lines) implements CustomPacketPayload {
        public static final Type<Lines> TYPE = new Type<>(CraftMaster.id("debug_lines"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Lines> CODEC = CustomPacketPayload.codec(
                (p, buf) -> {
                    buf.writeVarInt(p.lines.size());
                    for (String l : p.lines) buf.writeUtf(l, 512);
                },
                buf -> {
                    int n = Math.min(64, buf.readVarInt());
                    List<String> out = new ArrayList<>(n);
                    for (int i = 0; i < n; i++) out.add(buf.readUtf(512));
                    return new Lines(out);
                });

        @Override
        public Type<Lines> type() {
            return TYPE;
        }
    }

    private static final Set<UUID> ON = new HashSet<>();

    public static boolean on(ServerPlayer p) {
        return ON.contains(p.getUUID());
    }

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(Lines.TYPE, Lines.CODEC);
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> ON.remove(handler.getPlayer().getUUID()));
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (ON.isEmpty() || server.getTickCount() % 10 != 0) return;
            for (UUID id : List.copyOf(ON)) {
                ServerPlayer p = server.getPlayerList().getPlayer(id);
                if (p == null) continue;
                if (!Permissions.isAdmin(p)) {
                    set(p, false);
                    continue;
                }
                if (Director.broadcasters().contains(id)) {
                    // A broadcast game: nothing on its picture
                    send(p, List.of());
                    continue;
                }
                draw(server, p);
                send(p, lines(server, p));
            }
        });
    }

    public static void set(ServerPlayer p, boolean on) {
        if (on) ON.add(p.getUUID());
        else {
            ON.remove(p.getUUID());
            send(p, List.of());
        }
    }

    private static void send(ServerPlayer p, List<String> lines) {
        if (ServerPlayNetworking.canSend(p, Lines.TYPE)) ServerPlayNetworking.send(p, new Lines(lines));
    }

    /** {@code /cm debug [on|off]}. */
    public static LiteralCommandNode<CommandSourceStack> command() {
        return Commands.literal("debug").requires(Permissions.ADMIN)
                .executes(c -> toggle(c.getSource(), null))
                .then(Commands.literal("on").executes(c -> toggle(c.getSource(), true)))
                .then(Commands.literal("off").executes(c -> toggle(c.getSource(), false)))
                .build();
    }

    private static int toggle(CommandSourceStack s, Boolean want) {
        ServerPlayer p = s.getPlayer();
        if (p == null) {
            s.sendFailure(Component.literal("Le mode debug s'affiche dans le jeu d'un organisateur."));
            return 0;
        }
        boolean on = want == null ? !on(p) : want;
        set(p, on);
        s.sendSuccess(() -> Component.literal(on
                ? "Mode debug activé : zones des arènes, départs, champs et zones d'épreuve en particules, état du show en haut à gauche (pour toi seul)."
                : "Mode debug coupé.").withStyle(ChatFormatting.GOLD), false);
        return 1;
    }

    // ---------------------------------------------------------------- the particles

    private static final DustParticleOptions GOLD = new DustParticleOptions(0xFFC23D, 1.2f), GREEN = new DustParticleOptions(0x4CC266, 1.0f),
            BLUE = new DustParticleOptions(0x3497D4, 1.0f), RED = new DustParticleOptions(0xE03030, 1.2f), WHITE = new DustParticleOptions(0xFFFFFF, 1.0f);

    private static void draw(MinecraftServer server, ServerPlayer p) {
        ServerLevel level = p.level();
        String world = level.dimension().identifier().toString();
        for (Arena a : Arenas.all()) {
            if (!a.world.equals(world)) continue;
            box(p, GOLD, a.x0, a.y0, a.z0, a.x1 + 1, a.y1 + 1, a.z1 + 1);
            spawn(p, a, null);
            if (a.field != null) field(p, a.field, 0, 0, 0);
            for (Map.Entry<String, int[]> e : a.copies.entrySet()) {
                int[] o = e.getValue();
                box(p, GREEN, a.x0 + o[0], a.y0 + o[1], a.z0 + o[2], a.x1 + o[0] + 1, a.y1 + o[1] + 1, a.z1 + o[2] + 1);
                spawn(p, a, o);
                if (a.field != null) field(p, a.field, o[0], o[1], o[2]);
            }
        }
        for (Zone z : TrialZones.zones().values())
            if (z.world().equals(world)) box(p, RED, z.x0(), z.y0(), z.z0(), z.x1() + 1, z.y1() + 1, z.z1() + 1);
        BlockSpot anchor = CraftMaster.data() == null ? null : CraftMaster.data().stage().anchor;
        if (anchor != null && anchor.world().equals(world)) column(p, ParticleTypes.END_ROD, anchor.x() + 0.5, anchor.y() + 1, anchor.z() + 0.5, 3);
        for (Look.Point pt : Scenography.book().plateau.points.values())
            if (world.equals(pt.world)) column(p, ParticleTypes.HAPPY_VILLAGER, pt.x, pt.y, pt.z, 1);
    }

    private static void spawn(ServerPlayer p, Arena a, int[] off) {
        double[] s = Arenas.spawn(a, off);
        column(p, ParticleTypes.END_ROD, s[0], s[1], s[2], 2);
    }

    private static void field(ServerPlayer p, Arena.Field f, int dx, int dy, int dz) {
        double y = f.y + dy + 1.05;
        box(p, BLUE, f.x0 + dx, y, f.z0 + dz, f.x1 + dx + 1, y, f.z1 + dz + 1);
    }

    private static void column(ServerPlayer p, ParticleOptions o, double x, double y, double z, double height) {
        for (double h = 0; h <= height; h += 0.5) particle(p, o, x, y + h, z);
    }

    private static void box(ServerPlayer p, ParticleOptions o, double x0, double y0, double z0, double x1, double y1, double z1) {
        double[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        for (int i = 0; i < 4; i++) {
            double[] a = corners[i], b = corners[(i + 1) % 4];
            edge(p, o, a[0], y0, a[1], b[0], y0, b[1]);
            if (y1 != y0) {
                edge(p, o, a[0], y1, a[1], b[0], y1, b[1]);
                edge(p, o, a[0], y0, a[1], a[0], y1, a[1]);
            }
        }
    }

    private static void edge(ServerPlayer p, ParticleOptions o, double ax, double ay, double az, double bx, double by, double bz) {
        double len = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        int n = (int) Math.min(120, Math.max(1, len));
        for (int i = 0; i <= n; i++) {
            double t = i / (double) n;
            particle(p, o, ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t);
        }
    }

    private static void particle(ServerPlayer p, ParticleOptions o, double x, double y, double z) {
        // Far ones are not worth the packets
        if (p.distanceToSqr(x, y, z) > 96 * 96) return;
        p.level().sendParticles(p, o, true, false, x, y, z, 1, 0, 0, 0, 0);
    }

    // ---------------------------------------------------------------- the HUD

    static List<String> lines(MinecraftServer server, ServerPlayer p) {
        List<String> out = new ArrayList<>();
        out.add("§6§lDEBUG §7(toi seul, /cm debug off)");
        Manche m = CraftMaster.data() == null ? null : CraftMaster.data().currentManche();
        int left = Show.timerRemaining(server);
        out.add("§eManche : §f" + (m == null ? "aucune" : m.name) + (left > 0 ? " §7— chrono " + left / 60 + ":" + String.format("%02d", left % 60) : "")
                + (fr.lordfinn.craftmaster.task.RoundStart.waiting() ? " §c(attend sa lettre / son bouton)" : ""));
        if (fr.lordfinn.craftmaster.reprise.Reprise.frozen()) out.add("§c§lShow gelé §7— /cm reprise reprendre");
        Arenas.Owner here = Arenas.ownerAt(p.level(), p.blockPosition());
        if (here != null) out.add("§eTu es dans : §f" + here.arena().name + (here.player() == null ? " §7(référence)" : " §7— copie de §f" + here.player()));
        for (Arena a : Arenas.all()) out.add("§7• §f" + a.name + " §7" + describe(a));
        for (Zone z : TrialZones.zones().values()) out.add("§7• zone §f" + z.name() + (z.enabled() ? "" : " §8(coupée)"));
        out.add("§eAntenne : §f" + onAir(fr.lordfinn.craftmaster.show.ShowContext.onAir()) + (Director.autoDirection() ? " §7(auto)" : " §7(à la main)"));
        out.add("§eCaméra d'inventaire : §f" + InventoryCam.describe());
        return out;
    }

    /** "camera 3", "follow Ensuri"... in words. */
    static String onAir(String shot) {
        if (shot == null || shot.equals("none")) return "rien (vue du joueur)";
        String[] p = shot.split(" ", 2);
        String rest = p.length > 1 ? p[1] : "";
        return switch (p[0]) {
            case "camera" -> "caméra " + rest;
            case "follow" -> "suit " + rest;
            case "path" -> "travelling " + rest;
            case "mosaic" -> "écran partagé " + rest;
            case "pov" -> "vue de " + rest;
            case "free" -> "caméra libre";
            default -> shot;
        };
    }

    public static String describe(Arena a) {
        List<String> o = new ArrayList<>();
        if (a.manche != null) o.add("manche " + a.manche);
        o.add(a.copies.size() + " copie(s)");
        if (a.chronoLettre) o.add("chrono lettre");
        if (a.chronoSuite > 0) o.add("chrono suite " + a.chronoSuite + " s");
        if (a.panneauxSuite) o.add("panneaux");
        if (!a.walls) o.add("sans murs");
        if (a.clearInventory) o.add("inventaire vidé");
        if (a.solFin) o.add(a.alphabet ? "sol=fin, alphabet" : "sol=fin");
        if (a.field != null) o.add("champ");
        if (a.inventoryCam != null) o.add(a.inventoryCam ? "cam. inventaire" : "sans cam. inventaire");
        return String.join(", ", o);
    }
}
