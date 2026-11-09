package fr.lordfinn.craftmaster.show;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.bust.Busts;
import fr.lordfinn.craftmaster.curtain.Curtains;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.network.Network;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

/**
 * STUB (chantier C) — to be REPLACED by chantier A's ActionExecutor. Same signature:
 * {@code execute(ServerPlayer admin, List<Map<String,Object>> actions) -> Result(List<String> lines, boolean ok)}.
 *
 * <p>Just enough of the action schema (docs/design/emission.md section 4) on top of the v1 data to test the voice
 * control end to end: score.set / score.add / score.remove on the latest season (manche = task; "current" /
 * "next"), manche.start (title only), curtain.open / curtain.close (the curtain the admin LOOKS AT, v1 has no
 * named curtains), scores.publish (no-op, v1 has no published scores), say, undo (score changes only). The
 * {@code stub*} fields feed the stub {@link ShowContext}.
 */
public final class ActionExecutor {

    private ActionExecutor() {}

    public record Result(List<String> lines, boolean ok) {}

    static String stubCurrentManche;
    static Long stubCurrentStartedAt;
    static Long stubLastScoreAt;
    static String stubLastScoreManche;
    static final Deque<String> stubRecent = new ArrayDeque<>();
    private static final Deque<List<Runnable>> UNDO = new ArrayDeque<>();

    public static Result execute(ServerPlayer admin, List<Map<String, Object>> actions) {
        MinecraftServer server = admin.level().getServer();
        CraftMasterData data = CraftMaster.data();
        List<String> lines = new ArrayList<>();
        List<Runnable> inverse = new ArrayList<>();
        boolean ok = true;
        int season = data.allSeasons().isEmpty() ? 1 : data.allSeasons().getLast();
        for (Map<String, Object> a : actions) {
            String type = String.valueOf(a.get("type"));
            try {
                String line = switch (type) {
                    case "score.set", "score.add", "score.remove" -> score(data, season, type, a, inverse);
                    case "manche.start" -> startManche(server, data, season, String.valueOf(a.get("manche")));
                    case "curtain.open", "curtain.close" -> curtain(admin, type.equals("curtain.open"));
                    case "scores.publish" -> "Scores publiés (stub : rien à faire en v1).";
                    case "say" -> String.valueOf(a.get("text"));
                    case "undo" -> undo(server, season, a.get("count") instanceof Number n ? n.intValue() : 1);
                    default -> throw new IllegalArgumentException("« " + type + " » pas encore géré (stub)");
                };
                lines.add(line);
                if (!type.equals("say") && !type.equals("undo")) remember(type + " " + describe(a));
            } catch (IllegalArgumentException e) {
                lines.add("✗ " + e.getMessage());
                ok = false;
            }
        }
        if (!inverse.isEmpty()) {
            UNDO.push(inverse);
            while (UNDO.size() > 50) UNDO.removeLast();
            Busts.updateAll(server);
            Network.broadcast(server, season);
        }
        return new Result(lines, ok);
    }

    private static String score(CraftMasterData data, int season, String type, Map<String, Object> a, List<Runnable> inverse) {
        String manche = resolveManche(data, season, a.get("manche") == null ? "current" : String.valueOf(a.get("manche")));
        String player = String.valueOf(a.get("player"));
        int before = data.score(season, manche, player);
        boolean had = data.scores(season, player).containsKey(manche);
        inverse.add(() -> {
            if (had) data.setScore(season, manche, player, before);
            else data.removeScore(season, manche, player);
        });
        stubLastScoreAt = System.currentTimeMillis();
        stubLastScoreManche = manche;
        return switch (type) {
            case "score.set" -> {
                int v = ((Number) a.get("value")).intValue();
                data.setScore(season, manche, player, v);
                yield player + " → " + v + " pts (" + manche + ")";
            }
            case "score.add" -> {
                int p = ((Number) a.get("points")).intValue();
                int now = data.addScore(season, manche, player, p);
                yield player + " " + (p >= 0 ? "+" : "") + p + " (" + manche + ") → " + now + " pts";
            }
            default -> {
                data.removeScore(season, manche, player);
                yield "Score de " + player + " effacé (" + manche + ")";
            }
        };
    }

    private static String resolveManche(CraftMasterData data, int season, String ref) {
        List<String> tasks = data.tasks(season);
        if (ref.equals("current")) {
            if (stubCurrentManche == null) throw new IllegalArgumentException("aucune manche en cours");
            return stubCurrentManche;
        }
        if (ref.equals("next")) {
            int i = stubCurrentManche == null ? -1 : tasks.indexOf(stubCurrentManche);
            if (i + 1 >= tasks.size()) throw new IllegalArgumentException("pas de manche suivante");
            return tasks.get(i + 1);
        }
        for (String t : tasks) if (t.equalsIgnoreCase(ref)) return t;
        throw new IllegalArgumentException("manche « " + ref + " » inconnue");
    }

    private static String startManche(MinecraftServer server, CraftMasterData data, int season, String ref) {
        String manche = resolveManche(data, season, ref);
        stubCurrentManche = manche;
        stubCurrentStartedAt = System.currentTimeMillis();
        int n = data.tasks(season).indexOf(manche) + 1;
        Component title = Component.literal("Manche " + n + " — " + manche).withStyle(ChatFormatting.GOLD);
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            p.connection.send(new ClientboundSetTitlesAnimationPacket(10, 50, 15));
            p.connection.send(new ClientboundSetTitleTextPacket(title));
        }
        return "Manche " + n + " lancée : " + manche;
    }

    private static String curtain(ServerPlayer admin, boolean open) {
        HitResult hit = admin.pick(64, 0, false);
        if (hit instanceof BlockHitResult bh && hit.getType() == HitResult.Type.BLOCK) {
            Boolean state = Curtains.animate(admin.level(), bh.getBlockPos(), open);
            if (state != null) return open ? "Ouverture du rideau." : "Fermeture du rideau.";
        }
        throw new IllegalArgumentException("stub : vise un rideau pour l'ouvrir / le fermer");
    }

    private static String undo(MinecraftServer server, int season, int count) {
        int done = 0;
        for (; done < count && !UNDO.isEmpty(); done++) UNDO.pop().reversed().forEach(Runnable::run);
        if (done == 0) throw new IllegalArgumentException("rien à annuler");
        Busts.updateAll(server);
        Network.broadcast(server, season);
        return done + " commande(s) annulée(s)";
    }

    private static String describe(Map<String, Object> a) {
        StringBuilder sb = new StringBuilder();
        a.forEach((k, v) -> { if (!k.equals("type")) sb.append(k).append('=').append(v).append(' '); });
        return sb.toString().trim();
    }

    private static void remember(String s) {
        stubRecent.addLast(s);
        while (stubRecent.size() > 10) stubRecent.removeFirst();
    }
}
