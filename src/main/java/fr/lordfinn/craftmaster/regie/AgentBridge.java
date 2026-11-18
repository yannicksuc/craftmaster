package fr.lordfinn.craftmaster.regie;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.Permissions;
import fr.lordfinn.craftmaster.camera.CameraPath;
import fr.lordfinn.craftmaster.camera.CameraPaths;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.camera.Cameras;
import fr.lordfinn.craftmaster.show.ActionJournal;
import fr.lordfinn.craftmaster.show.Actor;
import fr.lordfinn.craftmaster.show.Rights;
import fr.lordfinn.craftmaster.stage.Look;
import fr.lordfinn.craftmaster.stage.Scenography;
import fr.lordfinn.craftmaster.stage.Sequence;
import fr.lordfinn.craftmaster.stage.Sequences;
import fr.lordfinn.craftmaster.studio.SpotlightBlockEntity;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * Server side of the régie agent (docs/wiki/RegieIA.md): an agent running on the organiser's PC (voice control
 * service) edits the show's setup — cameras, travellings, looks, cue lists, timelines — through the mod's own
 * commands. Its tool calls come through the organiser's game ({@link RegiePayloads.AgentCall}) and run here
 * <b>as that player</b> (his permissions, nothing more), checked again on every packet.
 * <p>Guards: only the show's commands (a list of roots, also for the lines a cue list or a timeline will run
 * later); deleting something waits for the organiser's confirmation (a click, or a later request); everything a
 * request changes can be undone in one go (snapshot of the scenography, the cameras and the travellings taken
 * before its first change); a line in the action journal and the server log per request; no address in any
 * output (masked).
 */
public final class AgentBridge {

    private AgentBridge() {}

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();

    /** The show's commands the agent may run (lower case, aliases included). */
    static final Set<String> ROOTS = Set.of("cam", "craftmastercamera", "tv", "craftmastertv", "sequence", "cfsq",
            "craftmastersequence", "conduite", "cfco", "craftmastercue", "ambiance", "cfam", "craftmasterlook", "cfsp",
            "craftmasterspotlight", "plateau", "cfpl", "cmtele", "craftmastertele", "cfpt", "craftmasterportrait", "fx",
            "craftmasterfx");
    /** What a cue list line or a timeline's command key may run, besides {@link #ROOTS}. */
    static final Set<String> LINE_ROOTS = Set.of("attendre", "noir", "playsound", "stopsound", "title", "particle");
    /** Words that delete a whole thing: confirmation first. */
    static final Set<String> DELETING = Set.of("supprimer", "remove", "effacer", "delete");
    /** Sub-commands that only read. */
    static final Set<String> READING = Set.of("list", "liste", "voir", "statut", "trajets", "info", "apercu", "montrer");
    private static final Pattern ADDRESS = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d{1,5})?\\b");
    private static final long CONFIRM_MS = 5 * 60_000;

    record Snapshot(String book, List<CameraSpot> cameras, Map<String, String> paths) {
        static Snapshot take() {
            Map<String, String> paths = new LinkedHashMap<>();
            for (CameraPath p : CameraPaths.all()) paths.put(p.name(), CameraPaths.toJson(p).toString());
            return new Snapshot(Scenography.toJson(), List.copyOf(Cameras.list().all()), paths);
        }

        boolean same(Snapshot o) {
            return book.equals(o.book) && new HashSet<>(cameras).equals(new HashSet<>(o.cameras)) && paths.equals(o.paths);
        }

        void restore(MinecraftServer server) {
            Scenography.restore(book);
            Map<String, CameraSpot> want = new LinkedHashMap<>();
            for (CameraSpot s : cameras) want.put(s.key(), s);
            for (CameraSpot s : List.copyOf(Cameras.list().all()))
                if (!want.containsKey(s.key())) Cameras.remove(server, s.key());
            for (CameraSpot s : cameras) {
                CameraSpot now = Cameras.list().get(s.key());
                if (now == null) Cameras.placeViewpoint(server, s);
                else if (!now.equals(s)) Cameras.update(server, s.key(), x -> s);
            }
            for (CameraPath p : List.copyOf(CameraPaths.all()))
                if (!paths.containsKey(p.name())) CameraPaths.remove(server, p.name());
            for (Map.Entry<String, String> e : paths.entrySet()) {
                CameraPath now = CameraPaths.byName().get(e.getKey());
                if (now == null || !CameraPaths.toJson(now).toString().equals(e.getValue()))
                    CameraPaths.put(server, CameraPaths.fromJson(JsonParser.parseString(e.getValue()).getAsJsonObject()));
            }
        }
    }

    /** One agent request: who, what it ran, the state before its first change. */
    static final class Job {
        final String id;
        final UUID player;
        final String who;
        final List<String> done = new ArrayList<>();
        Snapshot before;
        boolean finished, undone;

        Job(String id, ServerPlayer p) {
            this.id = id;
            this.player = p.getUUID();
            this.who = p.getPlainTextName();
        }
    }

    record Pending(int id, String job, String command, UUID player, long at) {}

    private static final LinkedHashMap<String, Job> JOBS = new LinkedHashMap<>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Job> e) {
            return size() > 30;
        }
    };
    private static final Map<Integer, Pending> PENDING = new LinkedHashMap<>();
    private static int nextPending = 1;

    public static void register() {
        PayloadTypeRegistry.serverboundPlay().register(RegiePayloads.AgentCall.TYPE, RegiePayloads.AgentCall.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(RegiePayloads.AgentReply.TYPE, RegiePayloads.AgentReply.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(RegiePayloads.AgentCall.TYPE, (p, ctx) -> onCall(ctx.player(), p));
        CommandRegistrationCallback.EVENT.register((d, ctx, sel) -> commands(d));
    }

    // ---------------------------------------------------------------- tool calls

    private static void onCall(ServerPlayer player, RegiePayloads.AgentCall call) {
        String out;
        boolean ok = true;
        if (!Permissions.isAdmin(player)) {
            out = "Refusé : réservé aux organisateurs.";
            ok = false;
        } else {
            try {
                JsonObject args = call.json().isBlank() ? new JsonObject() : JsonParser.parseString(call.json()).getAsJsonObject();
                out = handle(player, job(call.job(), player), call.tool(), args);
            } catch (RuntimeException e) {
                CraftMaster.LOG.warn("Régie IA : outil {} en échec", call.tool(), e);
                out = "Erreur : " + e.getMessage();
                ok = false;
            }
        }
        ServerPlayNetworking.send(player, new RegiePayloads.AgentReply(call.id(), ok, mask(out)));
    }

    private static Job job(String id, ServerPlayer p) {
        Job j = JOBS.get(id);
        if (j == null || !j.player.equals(p.getUUID())) {
            j = new Job(id, p);
            JOBS.put(id, j);
        }
        return j;
    }

    static String handle(ServerPlayer player, Job job, String tool, JsonObject a) {
        MinecraftServer server = player.level().getServer();
        return switch (tool) {
            case "lire" -> read(str(a, "sujet"), str(a, "nom"));
            case "commande" -> command(player, job, str(a, "ligne"), false);
            case "ecrire_sequence" -> writeSequence(job, str(a, "nom"), a.get("sequence"));
            case "confirmer" -> confirm(player, job, a.has("numero") ? a.get("numero").getAsInt() : -1);
            case "annuler" -> undo(server, player, job);
            case "_fin" -> finish(player, job, str(a, "demande"), str(a, "reponse"));
            default -> "Outil inconnu : " + tool;
        };
    }

    // ---------------------------------------------------------------- commands

    /** Runs a show command as the player, its chat output captured; checks roots, embedded lines and deletions. */
    static String command(ServerPlayer player, Job job, String line, boolean confirmed) {
        line = line.strip();
        if (line.startsWith("/")) line = line.substring(1);
        if (line.isEmpty()) return "Commande vide.";
        List<String> words = words(line);
        String root = words.getFirst().toLowerCase(Locale.ROOT);
        // The new tree (/cm cam …): checked as the group it stands for, run as it is
        List<String> asOld = oldForm(words);
        if (asOld != null) {
            words = asOld;
            root = words.getFirst().toLowerCase(Locale.ROOT);
        }
        if (!ROOTS.contains(root))
            return "Refusé : « " + root + " » n'est pas une commande de la régie (cam, tv, sequence, conduite, ambiance, cfsp, plateau, cmtele, cfpt, fx).";
        String embedded = embeddedLine(root, words);
        if (embedded != null) {
            String why = lineRefusal(embedded);
            if (why != null) return why;
        }
        boolean reading = words.size() == 1 || words.stream().skip(1).map(w -> w.toLowerCase(Locale.ROOT)).anyMatch(READING::contains);
        if (!confirmed && words.stream().skip(1).map(w -> w.toLowerCase(Locale.ROOT)).anyMatch(DELETING::contains)) {
            // The same deletion asked again in a LATER request (« oui, supprime-la ») is the confirmation
            expire();
            final String asked = line;
            Pending again = PENDING.values().stream().filter(p -> p.player.equals(player.getUUID()) && !p.job.equals(job.id)
                    && p.command.equalsIgnoreCase(asked)).findFirst().orElse(null);
            if (again != null) {
                PENDING.remove(again.id);
                return command(player, job, line, true);
            }
            int n = nextPending++;
            PENDING.put(n, new Pending(n, job.id, line, player.getUUID(), System.currentTimeMillis()));
            player.sendSystemMessage(Component.literal("Régie IA veut : /" + line + " ").withStyle(ChatFormatting.GOLD)
                    .append(button("[Confirmer]", "/cm ia confirmer " + n, "Le faire", ChatFormatting.GREEN))
                    .append(Component.literal(" "))
                    .append(button("[Refuser]", "/cm ia refuser " + n, "Ne pas le faire", ChatFormatting.RED)));
            return "CONFIRMATION REQUISE (n° " + n + ") : « " + line + " » n'a PAS été fait. Dis à l'organisateur ce que ça supprime ; "
                    + "il confirme en cliquant [Confirmer] dans son chat, ou en te le redisant (outil confirmer, numéro " + n + ", dans une demande suivante).";
        }
        if (!reading && job.before == null) job.before = Snapshot.take();
        Capture cap = new Capture();
        CommandSourceStack source = player.createCommandSourceStack().withSource(cap);
        player.level().getServer().getCommands().performPrefixedCommand(source, line);
        if (!reading && !cap.failed) job.done.add("/" + line);
        CraftMaster.LOG.info("Régie IA ({}) : /{} -> {}", job.who, line, mask(String.join(" | ", cap.lines)));
        return cap.lines.isEmpty() ? "(fait, aucune réponse)" : String.join("\n", cap.lines);
    }

    /** Groups of /cm the control may use, and the old root they stand for (absent: not for the control). */
    static final Map<String, String> GROUPS = Map.of("cam", "cam", "camera", "cam", "tv", "tv", "sequence", "sequence",
            "conduite", "conduite", "ambiance", "ambiance", "projecteur", "cfsp", "fx", "fx");

    /**
     * A {@code /cm} line as the old command it stands for ({@code cm cam couper 2} -> {@code cam couper 2}), for the
     * checks; null if the line is not a {@code /cm} one. A group the control may not use keeps "cm group" (refused).
     */
    static List<String> oldForm(List<String> w) {
        if (w.isEmpty() || !Set.of("cm", "craftmaster").contains(w.getFirst().toLowerCase(Locale.ROOT))) return null;
        if (w.size() < 2) return List.of("cm");
        String g = w.get(1).toLowerCase(Locale.ROOT);
        List<String> rest = w.subList(2, w.size());
        String old = GROUPS.get(g);
        if (old == null && g.equals("media") && !rest.isEmpty() && rest.getFirst().equalsIgnoreCase("tele")) {
            old = "cmtele";
            rest = rest.subList(1, rest.size());
        }
        if (old == null && g.equals("plateau") && !rest.isEmpty()) {
            String sub = rest.getFirst().toLowerCase(Locale.ROOT);
            if (sub.equals("portrait")) {
                old = "cfpt";
                rest = rest.subList(1, rest.size());
            } else if (Set.of("devant", "point", "retirerpoint", "maitre", "scene", "public").contains(sub)) old = "plateau";
        }
        List<String> out = new ArrayList<>();
        out.add(old == null ? "cm " + g : old);
        out.addAll(rest);
        return out;
    }

    /** The line a cue list or a timeline will run later, in "conduite ajouter <nom> <ligne>" / "inserer <nom> <n> <ligne>". */
    static String embeddedLine(String root, List<String> w) {
        if (!Set.of("conduite", "cfco", "craftmastercue").contains(root) || w.size() < 4) return null;
        String sub = w.get(1).toLowerCase(Locale.ROOT);
        if (sub.equals("ajouter")) return String.join(" ", w.subList(3, w.size()));
        if (sub.equals("inserer") && w.size() >= 5) return String.join(" ", w.subList(4, w.size()));
        return null;
    }

    /** Why a cue line / timeline command may not be stored (null: it may). */
    static String lineRefusal(String line) {
        String l = line.strip();
        if (l.startsWith("/")) l = l.substring(1);
        if (l.isEmpty()) return null;
        List<String> asOld = oldForm(words(l));
        String r = asOld != null ? asOld.getFirst().toLowerCase(Locale.ROOT) : l.split("\\s+")[0].toLowerCase(Locale.ROOT);
        if (ROOTS.contains(r) || LINE_ROOTS.contains(r)) return null;
        return "Refusé : une ligne « " + r + " … » ne peut pas être ajoutée (commandes de la régie, attendre, noir, playsound, title, particle).";
    }

    /** Splits on spaces, keeping "quoted names" whole (quotes kept, as the command expects them). */
    static List<String> words(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (char ch : line.toCharArray()) {
            if (ch == '"') quoted = !quoted;
            if (ch == ' ' && !quoted) {
                if (!cur.isEmpty()) out.add(cur.toString());
                cur.setLength(0);
            } else cur.append(ch);
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }

    /** Collects what a command says to its source; a red line (sendFailure) is marked ✗. */
    static final class Capture implements CommandSource {
        final List<String> lines = new ArrayList<>();
        boolean failed;

        @Override
        public void sendSystemMessage(Component c) {
            TextColor red = TextColor.fromLegacyFormat(ChatFormatting.RED);
            boolean bad = red.equals(c.getStyle().getColor()) || c.getSiblings().stream().anyMatch(s -> red.equals(s.getStyle().getColor()));
            if (bad) failed = true;
            if (lines.size() < 80) lines.add((bad ? "✗ " : "") + c.getString());
        }

        @Override public boolean acceptsSuccess() { return true; }
        @Override public boolean acceptsFailure() { return true; }
        @Override public boolean shouldInformAdmins() { return false; }
    }

    // ---------------------------------------------------------------- timelines

    static String writeSequence(Job job, String name, JsonElement json) {
        if (name.isBlank()) return "Donne le nom de la séquence.";
        if (json == null || json.isJsonNull()) return "Donne la séquence (objet JSON : duree, pistes…).";
        String text = json.isJsonPrimitive() ? json.getAsString() : json.toString();
        Sequence seq;
        try {
            seq = Sequences.parse(text);
        } catch (RuntimeException e) {
            return "Séquence illisible : " + e.getMessage();
        }
        // Only new command lines are checked: the ones already there were written by hand
        Set<String> known = new HashSet<>();
        Sequence old = Sequences.get(name);
        if (old != null) for (Sequence.Track t : old.pistes) for (Sequence.Key k : t.cles) if (k.commande != null) known.add(k.commande.strip());
        for (Sequence.Track t : seq.pistes)
            for (Sequence.Key k : t.cles)
                if (k.commande != null && !known.contains(k.commande.strip())) {
                    String why = lineRefusal(k.commande);
                    if (why != null) return why;
                }
        if (job.before == null) job.before = Snapshot.take();
        Sequences.put(name, seq);
        job.done.add("séquence « " + Scenography.key(name) + " » " + (old == null ? "créée" : "réécrite"));
        CraftMaster.LOG.info("Régie IA ({}) : séquence {} écrite ({} pistes)", job.who, Scenography.key(name), seq.pistes.size());
        return "Séquence « " + Scenography.key(name) + " » " + (old == null ? "créée" : "remplacée") + " : " + seq.pistes.size() + " piste(s), "
                + seq.keyCount() + " keypoint(s), " + seq.duree + " s.";
    }

    // ---------------------------------------------------------------- confirmation, undo, end

    static String confirm(ServerPlayer player, Job job, int n) {
        expire();
        Pending p = PENDING.get(n);
        if (p == null || !p.player.equals(player.getUUID())) return "Aucune demande de confirmation n° " + n + " en attente (expirée ?).";
        // A human sentence must come between the question and the yes: never in the request that asked
        if (p.job.equals(job.id)) return "Refusé : la confirmation doit venir de l'organisateur, dans une demande suivante (ou par le bouton du chat).";
        PENDING.remove(n);
        return command(player, job, p.command, true);
    }

    private static void expire() {
        long now = System.currentTimeMillis();
        PENDING.values().removeIf(p -> now - p.at > CONFIRM_MS);
    }

    /** Puts back what the player's last agent request (before this one) changed. */
    static String undo(MinecraftServer server, ServerPlayer player, Job current) {
        Job last = null;
        for (Job j : JOBS.values())
            if (j != current && j.player.equals(player.getUUID()) && j.before != null && !j.undone) last = j;
        if (last == null) return "Rien à annuler : aucune demande précédente n'a changé quelque chose.";
        if (current.before == null) current.before = Snapshot.take();
        last.before.restore(server);
        last.undone = true;
        current.done.add("annulé : " + String.join(", ", last.done));
        CraftMaster.LOG.info("Régie IA ({}) : demande {} annulée", player.getPlainTextName(), last.id);
        return "Annulé (" + last.done.size() + " changement(s) défaits) : " + String.join(" ; ", last.done);
    }

    /** The client says the request is over: journal line, and what to show in the chat. */
    static String finish(ServerPlayer player, Job job, String request, String reply) {
        job.finished = true;
        boolean changed = job.before != null && !job.before.same(Snapshot.take());
        if (job.before != null && !changed) job.before = null;   // nothing to undo
        JsonObject o = new JsonObject();
        o.addProperty("changed", changed);
        JsonArray done = new JsonArray();
        job.done.forEach(done::add);
        o.add("done", done);
        if (!job.done.isEmpty() || changed) {
            List<String> lines = new ArrayList<>();
            lines.add("Régie IA : « " + request + " »");
            lines.addAll(job.done);
            ActionJournal.add(new Actor(job.who, "ia", EnumSet.of(Rights.Right.ADMIN)), true, lines);
        }
        CraftMaster.LOG.info("Régie IA ({}) : « {} » -> {} changement(s) ; {}", job.who, request, job.done.size(), mask(reply));
        return o.toString();
    }

    // ---------------------------------------------------------------- reading

    static String read(String subject, String name) {
        Look.Book book = Scenography.book();
        String s = fr.lordfinn.craftmaster.camera.CameraList.fold(subject);
        JsonObject o = new JsonObject();
        switch (s) {
            case "resume", "", "tout" -> {
                JsonObject looks = new JsonObject();
                book.looks.forEach((k, v) -> looks.addProperty(k, v.describe()));
                o.add("ambiances", looks);
                JsonObject cues = new JsonObject();
                book.cues.forEach((k, v) -> cues.add(k, GSON.toJsonTree(v)));
                o.add("conduites", cues);
                JsonObject seqs = new JsonObject();
                book.sequences.forEach((k, v) -> {
                    JsonObject q = new JsonObject();
                    q.addProperty("duree", v.duree);
                    q.addProperty("description", v.description);
                    JsonArray tracks = new JsonArray();
                    v.pistes.forEach(t -> tracks.add(t.type + (t.cible == null || t.cible.isEmpty() ? "" : ":" + t.cible) + " (" + t.cles.size() + ")"));
                    q.add("pistes", tracks);
                    seqs.add(k, q);
                });
                o.add("sequences", seqs);
                o.add("lancements_auto", GSON.toJsonTree(book.auto));
                o.add("par_manche", GSON.toJsonTree(book.rounds));
                JsonObject points = new JsonObject();
                book.plateau.points.forEach((k, p) -> points.addProperty(k, num(p.x) + " " + num(p.y) + " " + num(p.z)));
                o.add("points", points);
                o.add("projecteurs", GSON.toJsonTree(fr.lordfinn.craftmaster.studio.Spotlights.names()));
                JsonArray cams = new JsonArray();
                for (CameraSpot c : Cameras.list().all()) cams.add(c.key() + (c.name() == null || c.name().isEmpty() ? "" : " « " + c.name() + " »")
                        + (c.role().isEmpty() ? "" : " rôle " + c.role()));
                o.add("cameras", cams);
                JsonArray paths = new JsonArray();
                for (CameraPath p : CameraPaths.all()) paths.add(p.name() + (p.camera().isEmpty() ? "" : " (caméra " + p.camera() + ")") + ", " + p.steps().size() + " étapes");
                o.add("travellings", paths);
            }
            case "ambiance" -> {
                Look l = book.looks.get(Scenography.key(name));
                if (l == null) return "Ambiance « " + name + " » inconnue : " + String.join(", ", book.looks.keySet());
                o.addProperty("fondu", l.fade);
                JsonArray spots = new JsonArray();
                if (l.spots != null) for (Look.Spot p : l.spots) {
                    JsonObject j = new JsonObject();
                    j.addProperty("nom", p.name);
                    j.addProperty("pos", p.x + " " + p.y + " " + p.z);
                    j.addProperty("allume", p.lit);
                    j.addProperty("couleur", String.format("#%06X", p.color & 0xFFFFFF));
                    j.addProperty("intensite", p.intensity);
                    j.addProperty("cone", p.cone);
                    if (p.follow != null && !p.follow.isEmpty()) j.addProperty("suit", p.follow);
                    spots.add(j);
                }
                o.add("projecteurs", spots);
                if (l.curtains != null) o.add("rideaux", GSON.toJsonTree(l.curtains));
                if (l.portraits != null) o.addProperty("tableaux", l.portraits.size() + " retenus");
                if (l.screens != null) o.add("ecrans", GSON.toJsonTree(l.screens));
            }
            case "sequence", "timeline" -> {
                Sequence q = Sequences.get(name);
                if (q == null) return "Séquence « " + name + " » inconnue : " + String.join(", ", book.sequences.keySet());
                return Sequences.json(q);
            }
            case "conduite" -> {
                List<String> lines = book.cues.get(Scenography.key(name));
                if (lines == null) return "Conduite « " + name + " » inconnue : " + String.join(", ", book.cues.keySet());
                StringBuilder b = new StringBuilder();
                for (int i = 0; i < lines.size(); i++) b.append(i + 1).append(". ").append(lines.get(i)).append('\n');
                return b.isEmpty() ? "(conduite vide)" : b.toString();
            }
            case "cameras", "camera" -> {
                JsonArray cams = new JsonArray();
                for (CameraSpot c : Cameras.list().all()) {
                    JsonObject j = new JsonObject();
                    j.addProperty("cle", c.key());
                    j.addProperty("nom", c.name() == null ? "" : c.name());
                    j.addProperty("role", c.role());
                    j.addProperty("pos", num(c.x()) + " " + num(c.y()) + " " + num(c.z()));
                    j.addProperty("yaw", Math.round(c.yaw() * 10) / 10.0);
                    j.addProperty("pitch", Math.round(c.pitch() * 10) / 10.0);
                    j.addProperty("champ", c.fov() <= 0 ? "joueur" : String.valueOf(Math.round(c.fov())));
                    j.addProperty("mouvement", c.motion());
                    cams.add(j);
                }
                o.add("cameras", cams);
            }
            case "trajet", "travelling" -> {
                CameraPath p = CameraPaths.get(name);
                if (p == null) return "Travelling « " + name + " » inconnu : " + String.join(", ", CameraPaths.byName().keySet());
                return CameraPaths.toJson(p).toString();
            }
            case "projecteurs" -> {
                JsonArray spots = new JsonArray();
                for (SpotlightBlockEntity p : SpotlightBlockEntity.loaded()) {
                    JsonObject j = new JsonObject();
                    j.addProperty("nom", p.name());
                    j.addProperty("pos", p.getBlockPos().getX() + " " + p.getBlockPos().getY() + " " + p.getBlockPos().getZ());
                    j.addProperty("allume", p.lit());
                    j.addProperty("couleur", String.format("#%06X", p.color() & 0xFFFFFF));
                    j.addProperty("intensite", p.intensity());
                    j.addProperty("cone", p.cone());
                    if (!p.follow().isEmpty()) j.addProperty("suit", p.follow());
                    spots.add(j);
                }
                o.add("projecteurs", spots);
            }
            default -> {
                return "Sujet inconnu « " + subject + " » : resume, ambiance, sequence, conduite, cameras, trajet, projecteurs.";
            }
        }
        return o.toString();
    }

    private static String num(double d) {
        return d == Math.rint(d) ? String.valueOf((long) d) : String.format(Locale.ROOT, "%.2f", d);
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : "";
    }

    /** No server address ever leaves through the agent (LordFinn streams his screen). */
    static String mask(String s) {
        return s == null ? "" : ADDRESS.matcher(s).replaceAll("‹adresse masquée›");
    }

    // ---------------------------------------------------------------- /cm ia (the chat's buttons)

    private static void commands(CommandDispatcher<CommandSourceStack> d) {
        d.register(literal("regieia").requires(Permissions.ADMIN)
                .then(literal("confirmer").then(argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    expire();
                    Pending pending = PENDING.remove(IntegerArgumentType.getInteger(c, "n"));
                    if (pending == null || !pending.player.equals(p.getUUID())) {
                        c.getSource().sendFailure(Component.literal("Plus rien à confirmer (expiré ?)."));
                        return 0;
                    }
                    String out = command(p, job(pending.job, p), pending.command, true);
                    c.getSource().sendSuccess(() -> Component.literal("Régie IA : " + mask(out)).withStyle(ChatFormatting.GRAY), false);
                    return 1;
                })))
                .then(literal("refuser").then(argument("n", IntegerArgumentType.integer(1)).executes(c -> {
                    boolean had = PENDING.remove(IntegerArgumentType.getInteger(c, "n")) != null;
                    c.getSource().sendSuccess(() -> Component.literal(had ? "Régie IA : rien n'a été fait." : "Plus rien en attente.").withStyle(ChatFormatting.GRAY), false);
                    return 1;
                })))
                .then(literal("annuler").then(argument("job", StringArgumentType.word()).executes(c -> {
                    ServerPlayer p = c.getSource().getPlayerOrException();
                    Job j = JOBS.get(StringArgumentType.getString(c, "job"));
                    if (j == null || j.before == null || j.undone || !j.player.equals(p.getUUID())) {
                        c.getSource().sendFailure(Component.literal("Rien à annuler pour cette demande."));
                        return 0;
                    }
                    j.before.restore(c.getSource().getServer());
                    j.undone = true;
                    ActionJournal.add(new Actor(j.who, "ia", EnumSet.of(Rights.Right.ADMIN)), true, List.of("Régie IA : demande annulée (" + j.done.size() + " changement(s))"));
                    CraftMaster.LOG.info("Régie IA ({}) : demande {} annulée au clic", j.who, j.id);
                    c.getSource().sendSuccess(() -> Component.literal("Régie IA : annulé, la scénographie, les caméras et les travellings sont revenus à l'état d'avant.").withStyle(ChatFormatting.GRAY), false);
                    return 1;
                }))));
    }

    static MutableComponent button(String label, String command, String hover, ChatFormatting color) {
        return Component.literal(label).withStyle(st -> st.withColor(color)
                .withClickEvent(new ClickEvent.RunCommand(command))
                .withHoverEvent(new HoverEvent.ShowText(Component.literal(hover))));
    }
}
