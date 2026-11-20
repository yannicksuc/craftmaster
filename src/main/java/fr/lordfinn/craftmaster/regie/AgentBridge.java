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
 * service) works on the show through the mod's own commands — all of /cm and the server's console commands — and
 * reads everything ({@link #read}). Its tool calls come through the organiser's game ({@link RegiePayloads.AgentCall})
 * and run here <b>as that player</b> (his permissions, nothing more), checked again on every packet.
 * <p>Guards ({@link #classify}): the show's setup (cameras, travellings, intros, looks, cue lists, timelines, stage
 * points) and anything that only reads run at once; what deletes something or what the players or the stream would
 * see (on air, launching, teleporting, effects on players, live spotlights, the console) waits for the organiser's
 * click in his chat (or a later request); a few commands are refused outright (server stop, operators, bans,
 * whitelist, the agent's own buttons). Everything a request changes in the setup can be undone in one go (snapshot
 * of the scenography, the cameras, the travellings and the intros taken before its first change); a line in the
 * action journal and the server log per request; no address in any output (masked).
 * <p>Its eyes: « voir » is answered by the organiser's game, which takes the pictures {@link #renderPlan} lists.
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
    static final Set<String> READING = Set.of("list", "liste", "voir", "statut", "trajets", "info", "apercu", "lire", "historique");
    private static final Pattern ADDRESS = Pattern.compile("\\b\\d{1,3}(?:\\.\\d{1,3}){3}(?::\\d{1,5})?\\b");
    private static final long CONFIRM_MS = 5 * 60_000;

    record Snapshot(String book, List<CameraSpot> cameras, Map<String, String> paths, Map<String, fr.lordfinn.craftmaster.data.MancheIntro> intros) {
        static Snapshot take() {
            Map<String, String> paths = new LinkedHashMap<>();
            for (CameraPath p : CameraPaths.all()) paths.put(p.name(), CameraPaths.toJson(p).toString());
            Map<String, fr.lordfinn.craftmaster.data.MancheIntro> intros = new LinkedHashMap<>();
            for (var m : allManches()) intros.put(m.id, m.intro.copy());
            return new Snapshot(Scenography.toJson(), List.copyOf(Cameras.list().all()), paths, intros);
        }

        boolean same(Snapshot o) {
            return book.equals(o.book) && new HashSet<>(cameras).equals(new HashSet<>(o.cameras)) && paths.equals(o.paths)
                    && introsText(intros).equals(introsText(o.intros));
        }

        private static String introsText(Map<String, fr.lordfinn.craftmaster.data.MancheIntro> m) {
            StringBuilder b = new StringBuilder();
            m.forEach((k, v) -> b.append(k).append('=').append(v.shots).append(v.look).append(v.music).append(v.bpm).append(v.auto).append(v.chained).append(';'));
            return b.toString();
        }

        void restore(MinecraftServer server) {
            boolean introsChanged = false;
            for (var m : allManches()) {
                var was = intros.get(m.id);
                if (was != null && !introsText(Map.of(m.id, m.intro)).equals(introsText(Map.of(m.id, was)))) {
                    m.intro = was.copy();
                    introsChanged = true;
                }
            }
            if (introsChanged && CraftMaster.data() != null) CraftMaster.data().save();
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
            case "lire" -> read(server, player, str(a, "sujet"), str(a, "nom"));
            case "commande" -> command(player, job, str(a, "ligne"), false);
            case "completer" -> complete(player, str(a, "ligne"));
            case "_rendu" -> renderPlan(server, a);
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
        Kind kind = classify(words(line));
        if (kind == Kind.REFUSED)
            return "Refusé : « /" + line + " » n'est jamais fait par la régie IA (arrêt du serveur, opérateurs, bannissements, liste blanche, permissions, ses propres boutons).";
        String embedded = embeddedLine(root, words);
        if (embedded != null) {
            String why = lineRefusal(embedded);
            if (why != null) return why;
        }
        boolean reading = kind == Kind.READ;
        if (!confirmed && kind == Kind.CONFIRM) {
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
            boolean deleting = words(line).stream().skip(1).map(w -> w.toLowerCase(Locale.ROOT)).anyMatch(DELETING::contains);
            player.sendSystemMessage(Component.literal("Régie IA veut " + (deleting ? "supprimer" : "faire") + " : /" + mask(line) + " ").withStyle(ChatFormatting.GOLD)
                    .append(button("[Confirmer]", "/cm ia confirmer " + n, "Le faire", ChatFormatting.GREEN))
                    .append(Component.literal(" "))
                    .append(button("[Refuser]", "/cm ia refuser " + n, "Ne pas le faire", ChatFormatting.RED)));
            return "CONFIRMATION REQUISE (n° " + n + ") : « " + line + " » n'a PAS été fait (" + (deleting ? "ça supprime quelque chose"
                    : "les joueurs ou le stream le verraient, ou c'est une commande du serveur") + "). Dis à l'organisateur ce que ça fera ; "
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

    /** The active season's rounds: id, name, their cameras' lieu, their intro in short. */
    static JsonArray manches() {
        JsonArray out = new JsonArray();
        var data = CraftMaster.data();
        var season = data == null ? null : data.activeSeason();
        if (season == null) return out;
        for (var p : season.parties.values())
            for (var m : p.activeManches()) {
                JsonObject j = new JsonObject();
                j.addProperty("id", m.id);
                j.addProperty("nom", m.name);
                j.addProperty("cameras", Cameras.list().lieu(m.id).all().size() + " (" + m.id + ":1, " + m.id + ":2…)");
                j.addProperty("intro", m.intro.shots.isEmpty() ? "aucune" : m.intro.shots.size() + " plans" + (m.intro.auto ? ", jouée au lancement" : "")
                        + (m.intro.chained ? ", enchaînée" : "") + " — lire intro " + m.id);
                out.add(j);
            }
        return out;
    }

    /** One round's intro, shot by shot (what /cm tv intro <id> plan N … changes). */
    static String intro(String name) {
        var m = name == null ? null : fr.lordfinn.craftmaster.camera.Lieux.manche(name);
        if (m == null) return "Manche « " + name + " » inconnue : " + manches();
        var in = m.intro;
        JsonObject o = new JsonObject();
        o.addProperty("manche", m.id + " « " + m.name + " »");
        o.addProperty("auto", in.auto);
        o.addProperty("enchainee", in.chained);
        o.addProperty("musique", in.music.isEmpty() ? "aucune" : in.music);
        o.addProperty("rythme", in.bpm > 0 ? in.bpm + " BPM" : "off");
        o.addProperty("look", in.look.isEmpty() ? "aucun" : in.look);
        o.addProperty("titre", in.titleCard);
        JsonArray shots = new JsonArray();
        var timeline = in.timeline(n -> { CameraPath p = CameraPaths.get(n); return p == null ? 0 : p.duration(); });
        for (var t : timeline) {
            var sh = t.shot();
            JsonObject j = new JsonObject();
            j.addProperty("plan", t.index() + 1);
            j.addProperty("type", sh.kind());
            if (!sh.ref().isEmpty()) j.addProperty("ref", sh.ref());
            j.addProperty("debut_s", Math.round(t.start() * 10) / 10.0);
            j.addProperty("duree_s", Math.round(t.length() * 10) / 10.0);
            j.addProperty("transition", sh.transition().isEmpty() ? "défaut" : sh.transition());
            j.addProperty("effet", sh.effect().isEmpty() ? "aucun" : sh.effect());
            if (sh.focusPull() != null) j.addProperty("focus", sh.focusPull().describe());
            if (!sh.card().isEmpty()) j.addProperty("carte", sh.card());
            CameraSpot c = sh.kind().equals(fr.lordfinn.craftmaster.data.MancheIntro.CAMERA) ? fr.lordfinn.craftmaster.camera.Cameras.list().resolve(sh.ref(), m.id, java.util.Map.of()) : null;
            if (c != null) j.addProperty("camera", c.key() + (c.name() == null || c.name().isEmpty() ? "" : " « " + c.name() + " »") + ", mouvement " + c.motion());
            shots.add(j);
        }
        o.add("plans", shots);
        return o.toString();
    }

    static String read(MinecraftServer server, ServerPlayer player, String subject, String name) {
        String more = readMore(server, player, subject, name);
        if (more != null) return more;
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
                o.add("manches", manches());
            }
            case "manches", "manche" -> {
                return manches().toString();
            }
            case "intro", "intros" -> {
                return intro(name);
            }
            case "effets", "effet", "transitions" -> {
                JsonObject e = new JsonObject();
                JsonArray fx = new JsonArray();
                for (fr.lordfinn.craftmaster.fx.ScreenFx.Kind k : fr.lordfinn.craftmaster.fx.ScreenFx.ORDER) fx.add(k.id() + " (" + k.fr() + ")");
                e.add("effets", fx);
                e.add("combinaisons_toutes_faites", GSON.toJsonTree(fr.lordfinn.craftmaster.fx.ScreenFx.PRESETS));
                e.add("transitions", GSON.toJsonTree(fr.lordfinn.craftmaster.broadcast.BroadcastActions.TRANSITION_IDS));
                e.add("mouvements_camera", GSON.toJsonTree(CameraSpot.MOTIONS));
                return e.toString();
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
                return "Sujet inconnu « " + subject + " » : resume, manches, intro, effets, ambiance, sequence, conduite, cameras, trajet, "
                        + "projecteurs, arenes, zone, mecaniques, joueurs, blocs, commandes, statut, journal, log.";
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

    // ---------------------------------------------------------------- what the agent may do

    enum Kind { READ, FREE, CONFIRM, REFUSED }

    /** Never, even confirmed. */
    static final Set<String> FORBIDDEN = Set.of("stop", "op", "deop", "ban", "ban-ip", "pardon", "pardon-ip", "banlist", "whitelist",
            "kick", "save-off", "lp", "luckperms", "perm", "permissions", "transfer", "jfr", "debug", "perf", "publish", "regieia");
    /** Console commands that only read. */
    static final Set<String> VANILLA_READ = Set.of("list", "help", "seed");
    /** Words of a /cm line that put something on air, start something or move people: the organiser's click first. */
    static final Set<String> VISIBLE = Set.of("lancer", "jouer", "essayer", "couper", "cut", "large", "suivre", "mosaique", "pov",
            "libre", "diffusion", "noir", "flash", "synthe", "stop", "arreter", "tp", "aller", "go", "suite", "terminer", "fin",
            "suivante", "precedente", "reveler", "publier", "donner", "ouvrir", "fermer", "basculer", "activer", "forcer", "plateau",
            "revoir", "reprendre", "geler", "degeler", "start", "play", "show", "montrer", "appliquer", "envoyer", "reset", "recharger");
    /** /cm groups (new tree) whose setup the agent edits freely (nothing seen by the players until played). */
    static final Set<String> SETUP_GROUPS = Set.of("camera", "cam", "tv", "sequence", "conduite", "ambiance");

    /**
     * How a command line is treated: READ (only reads), FREE (the show's setup), CONFIRM (deletes something, is seen
     * by the players or the stream, or is any other command of the server), REFUSED (never).
     */
    static Kind classify(List<String> w) {
        if (w.isEmpty()) return Kind.REFUSED;
        String r = w.getFirst().toLowerCase(Locale.ROOT);
        if (r.contains(":")) r = r.substring(r.indexOf(':') + 1);
        List<String> rest = new ArrayList<>();
        // The old trees' English words as their French form (cam name → nom), so one list fits both
        for (String x : w.subList(1, w.size())) rest.add(fr.lordfinn.craftmaster.command.CommandTree.french(CameraListFold.fold(x)));
        String group;
        if (r.equals("cm") || r.equals("craftmaster")) {
            if (rest.isEmpty()) return Kind.READ;
            group = rest.removeFirst();
        } else {
            String path = fr.lordfinn.craftmaster.command.CommandTree.legacyPath(r);
            if (path == null) {
                // A command of the server (vanilla or another mod)
                if (FORBIDDEN.contains(r)) return Kind.REFUSED;
                if (VANILLA_READ.contains(r) || (r.equals("time") && rest.size() >= 1 && rest.getFirst().equals("query"))
                        || (r.equals("data") && !rest.isEmpty() && rest.getFirst().equals("get"))) return Kind.READ;
                // « execute in … if block … » without run nor store: only answers « Test passed / failed »
                if (r.equals("execute") && w.stream().skip(1).map(x -> x.toLowerCase(Locale.ROOT)).noneMatch(x -> x.equals("run") || x.equals("store"))
                        && w.stream().skip(1).map(x -> x.toLowerCase(Locale.ROOT)).anyMatch(x -> x.equals("if") || x.equals("unless")))
                    return Kind.READ;
                return Kind.CONFIRM;
            }
            String[] p = path.isEmpty() ? new String[0] : path.split(" ");
            group = p.length == 0 ? "" : p[0];
            for (int i = p.length - 1; i >= 1; i--) rest.addFirst(p[i]);
        }
        if (group.equals("ia")) return Kind.REFUSED;
        if (rest.stream().anyMatch(DELETING::contains) || rest.contains("suppr") || rest.contains("retirerpoint")) return Kind.CONFIRM;
        if (rest.isEmpty() && Set.of("statut", "aide", "historique", "menu").contains(group)) return Kind.READ;
        if (!rest.isEmpty() && READING.contains(rest.getLast()) || rest.stream().anyMatch(x -> x.equals("liste") || x.equals("list") || x.equals("statut") || x.equals("voir")))
            return Kind.READ;
        if (group.equals("tv")) {
            // The intro editor (« tv intro <m> plan 2 effet macro »): setup; playing it: on air
            if (rest.size() >= 3 && rest.getFirst().equals("intro") && !rest.contains("jouer") && !rest.contains("essayer")
                    && !rest.contains("effacer")) return Kind.FREE;
            return Kind.CONFIRM;
        }
        if (group.equals("plateau") && !rest.isEmpty() && Set.of("devant", "point", "maitre", "scene", "public", "portrait").contains(rest.getFirst()))
            return Kind.FREE;
        if (group.equals("media") && !rest.isEmpty() && rest.getFirst().equals("tele") && rest.stream().noneMatch(VISIBLE::contains)) return Kind.FREE;
        if (group.equals("camera") || group.equals("cam")) {
            if (rest.isEmpty()) return Kind.READ;
            String sub = rest.getFirst();
            if (Set.of("placer", "ici", "viser", "nom", "zoom", "role", "mouvement", "ajouter", "trajet", "info", "apercu", "liste", "list").contains(sub)
                    && rest.stream().skip(1).noneMatch(x -> Set.of("jouer", "lancer", "couper").contains(x))) return Kind.FREE;
            return Kind.CONFIRM;
        }
        if (SETUP_GROUPS.contains(group) && rest.stream().noneMatch(VISIBLE::contains)) return Kind.FREE;
        return Kind.CONFIRM;
    }

    /** Folds a word like the camera references do (accents, case). */
    private static final class CameraListFold {
        static String fold(String s) {
            return fr.lordfinn.craftmaster.camera.CameraList.fold(s);
        }
    }

    /** What the server's Tab would propose after {@code line} (the agent finds the commands by itself). */
    static String complete(ServerPlayer player, String line) {
        line = line.strip();
        if (line.startsWith("/")) line = line.substring(1);
        var dispatcher = player.level().getServer().getCommands().getDispatcher();
        CommandSourceStack source = player.createCommandSourceStack().withSource(new Capture());
        var parse = dispatcher.parse(line, source);
        try {
            var sugg = dispatcher.getCompletionSuggestions(parse).get(3, java.util.concurrent.TimeUnit.SECONDS);
            List<String> out = new ArrayList<>();
            for (var s : sugg.getList()) {
                if (out.size() >= 60) break;
                out.add(s.getText() + (s.getTooltip() == null ? "" : " — " + s.getTooltip().getString()));
            }
            String usage = "";
            var nodes = parse.getContext().getNodes();
            if (!nodes.isEmpty()) {
                var last = nodes.getLast().getNode();
                var u = dispatcher.getSmartUsage(last, source);
                if (!u.isEmpty()) usage = "\nSuite possible : " + String.join(" | ", u.values().stream().limit(25).toList());
            }
            String err = parse.getExceptions().isEmpty() ? "" : "\n(à cet endroit : " + parse.getExceptions().values().iterator().next().getMessage() + ")";
            return (out.isEmpty() ? "Aucune proposition." : "Propositions : " + String.join(" | ", out)) + usage + err;
        } catch (Exception e) {
            return "Propositions indisponibles : " + e.getMessage();
        }
    }

    /** Every round of the active season (archived ones too). */
    static List<fr.lordfinn.craftmaster.data.Manche> allManches() {
        List<fr.lordfinn.craftmaster.data.Manche> out = new ArrayList<>();
        var data = CraftMaster.data();
        var season = data == null ? null : data.activeSeason();
        if (season != null) for (var p : season.parties.values()) out.addAll(p.manches);
        return out;
    }

    /** The subjects added for the agent to see everything; null when {@code subject} is one of the older ones. */
    static String readMore(MinecraftServer server, ServerPlayer player, String subject, String name) {
        String s = fr.lordfinn.craftmaster.camera.CameraList.fold(subject);
        switch (s) {
            case "arenes", "arene" -> {
                JsonArray out = new JsonArray();
                for (var a : fr.lordfinn.craftmaster.trial.kit.Arenas.all()) {
                    if (name != null && !name.isBlank() && !a.name.equalsIgnoreCase(name) && !name.equalsIgnoreCase(a.manche)) continue;
                    JsonObject j = new JsonObject();
                    j.addProperty("nom", a.name);
                    j.addProperty("manche", a.manche == null ? "" : a.manche);
                    j.addProperty("monde", a.world);
                    j.addProperty("reference", a.x0 + " " + a.y0 + " " + a.z0 + " → " + a.x1 + " " + a.y1 + " " + a.z1);
                    JsonObject copies = new JsonObject();
                    a.copies.forEach((who, o) -> copies.addProperty(who, "décalage " + o[0] + " " + o[1] + " " + o[2]));
                    j.add("copies", copies);
                    j.addProperty("marge_chargement", a.chargementMarge);
                    out.add(j);
                }
                return out.toString();
            }
            case "joueurs", "joueur" -> {
                JsonArray out = new JsonArray();
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    JsonObject j = new JsonObject();
                    j.addProperty("pseudo", p.getPlainTextName());
                    j.addProperty("monde", p.level().dimension().identifier().toString());
                    j.addProperty("pos", num(Math.round(p.getX() * 10) / 10.0) + " " + num(Math.round(p.getY() * 10) / 10.0) + " " + num(Math.round(p.getZ() * 10) / 10.0));
                    j.addProperty("regard", "yaw " + Math.round(p.getYRot()) + ", pitch " + Math.round(p.getXRot()));
                    j.addProperty("mode", p.gameMode.getGameModeForPlayer().getName());
                    j.addProperty("organisateur", Permissions.isAdmin(p));
                    if (p == player) j.addProperty("c_est_lui", true);
                    out.add(j);
                }
                return out.toString();
            }
            case "commandes", "commande", "aide" -> {
                var dispatcher = server.getCommands().getDispatcher();
                CommandSourceStack src = player.createCommandSourceStack().withSource(new Capture());
                var root = fr.lordfinn.craftmaster.command.CommandTree.root();
                if (root == null) return "Arbre des commandes pas encore prêt.";
                com.mojang.brigadier.tree.CommandNode<CommandSourceStack> node = root;
                String path = "cm";
                if (name != null && !name.isBlank()) {
                    for (String part : name.trim().split("\\s+")) {
                        var child = node.getChild(fr.lordfinn.craftmaster.camera.CameraList.fold(part));
                        if (child == null) return "« " + part + " » inconnu sous /" + path + " : " + node.getChildren().stream().map(c -> c.getName()).toList();
                        node = child;
                        path += " " + child.getName();
                    }
                }
                List<String> lines = new ArrayList<>();
                for (String u : dispatcher.getAllUsage(node, src, true)) {
                    if (lines.size() >= 400) {
                        lines.add("… (plus long : précise un groupe, ex. lire commandes « tv intro »)");
                        break;
                    }
                    lines.add("/" + path + " " + u);
                }
                return lines.isEmpty() ? "/" + path + " (rien en dessous)" : String.join("\n", lines);
            }
            case "blocs", "bloc", "chercher" -> {
                // Blocks around the organiser whose id matches (« fleur » = every flower), nearest first
                if (name == null || name.isBlank()) return "Donne un mot : fleur, pot, coffre, lanterne, minecraft:poppy…";
                String q = fr.lordfinn.craftmaster.camera.CameraList.fold(name.trim());
                List<String> keys = switch (q) {
                    case "fleur", "fleurs", "flower" -> List.of("flower", "poppy", "tulip", "dandelion", "orchid", "allium", "daisy",
                            "cornflower", "lily", "rose", "blossom", "bluet", "peony", "lilac", "sunflower", "torchflower", "pitcher");
                    case "pot", "pots" -> List.of("pot");
                    case "coffre" -> List.of("chest");
                    case "lanterne" -> List.of("lantern");
                    case "pomme" -> List.of("apple");
                    default -> List.of(q.replace(' ', '_'));
                };
                var level = player.level();
                net.minecraft.core.BlockPos c = player.blockPosition();
                int r = 32;
                record Hit(double d, String line) {}
                List<Hit> hits = new ArrayList<>();
                for (net.minecraft.core.BlockPos p : net.minecraft.core.BlockPos.betweenClosed(c.offset(-r, -12, -r), c.offset(r, 12, r))) {
                    if (!level.hasChunkAt(p)) continue;
                    var state = level.getBlockState(p);
                    if (state.isAir()) continue;
                    String id = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
                    if (keys.stream().noneMatch(id::contains)) continue;
                    hits.add(new Hit(Math.sqrt(p.distSqr(c)), id + " à " + p.getX() + " " + p.getY() + " " + p.getZ()
                            + " (centre " + (p.getX() + 0.5) + " " + (p.getY() + 0.5) + " " + (p.getZ() + 0.5) + ")"));
                    if (hits.size() > 2000) break;
                }
                hits.sort(java.util.Comparator.comparingDouble(Hit::d));
                if (hits.isEmpty()) return "Aucun bloc « " + name + " » à " + r + " blocs autour de " + player.getPlainTextName() + ".";
                StringBuilder b = new StringBuilder("Autour de " + player.getPlainTextName() + " (" + c.getX() + " " + c.getY() + " " + c.getZ() + "), du plus près :\n");
                for (Hit h : hits.subList(0, Math.min(40, hits.size()))) b.append(String.format(Locale.ROOT, "%.0f bl : ", h.d())).append(h.line()).append('\n');
                return b.toString();
            }
            case "zone", "salle" -> {
                // A trial's room: everything in it, to work out how it is solved
                fr.lordfinn.craftmaster.trial.kit.Arena found = null;
                for (var a : fr.lordfinn.craftmaster.trial.kit.Arenas.all())
                    if (name != null && (a.name.equalsIgnoreCase(name.trim()) || name.trim().equalsIgnoreCase(a.manche))) found = a;
                if (found == null) {
                    List<String> names = new ArrayList<>();
                    for (var a : fr.lordfinn.craftmaster.trial.kit.Arenas.all()) names.add(a.name + " (manche " + a.manche + ")");
                    return "Zone « " + name + " » inconnue : " + String.join(", ", names);
                }
                return ZoneReader.read(server, found);
            }
            case "musiques", "musique", "musics" -> {
                StringBuilder b = new StringBuilder("Musiques d'intro (le thème CraftMaster « ré mi♭ ré — la si♭ la — sol fa♯ mi♭ ré »,"
                        + " en blocs musicaux ; toutes finissent par l'« appel » : la tête du thème, une levée, un grand accord de ré qui"
                        + " ouvre l'épreuve). Nom à donner à « tv intro <m> musique <nom> » ou « construire <nom> » ; temps en secondes"
                        + " depuis le début du morceau.\n");
                for (fr.lordfinn.craftmaster.data.IntroMusic.Track t : fr.lordfinn.craftmaster.data.IntroMusic.ALL) {
                    b.append("- ").append(t.key()).append(" : ").append(t.describe());
                    if (t.key().equals(fr.lordfinn.craftmaster.data.IntroMusic.FAVOURITE)) b.append(" · LA PRÉFÉRÉE DE LORDFINN (à prendre par défaut)");
                    if (t.key().equals(fr.lordfinn.craftmaster.data.IntroMusic.DEFAULT_AUTO)) b.append(" · celle de « construire » sans musique");
                    b.append(" · son ").append(t.sound()).append('\n');
                }
                b.append("Le générique de l'émission (« theme », show.theme) n'est pas calé pour une intro.");
                return b.toString();
            }
            case "mecaniques", "pack", "datapack" -> {
                return ZoneReader.mechanics(server, name == null ? "" : name.trim().toLowerCase(Locale.ROOT));
            }
            case "statut", "etat" -> {
                Capture cap = new Capture();
                server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withSource(cap), "cm statut");
                return String.join("\n", cap.lines);
            }
            case "journal", "historique" -> {
                StringBuilder b = new StringBuilder();
                for (ActionJournal.Line l : ActionJournal.last(25))
                    b.append(java.time.Instant.ofEpochMilli(l.at())).append(' ').append(l.actor()).append(" (").append(l.origin()).append(") ")
                            .append(l.ok() ? "" : "✗ ").append(String.join(" / ", l.lines())).append('\n');
                return b.isEmpty() ? "(journal vide)" : b.toString();
            }
            case "log", "journaux", "logs" -> {
                try {
                    java.nio.file.Path log = server.getServerDirectory().resolve("logs").resolve("latest.log");
                    List<String> all = java.nio.file.Files.readAllLines(log, java.nio.charset.StandardCharsets.UTF_8);
                    String filter = name == null ? "" : name.toLowerCase(Locale.ROOT);
                    List<String> keep = new ArrayList<>();
                    for (String l : all) if (filter.isBlank() ? (l.contains("CraftMaster") || l.contains("Régie") || l.contains("ERROR") || l.contains("WARN") || l.contains("Réalisation") || l.contains("Épreuve")) : l.toLowerCase(Locale.ROOT).contains(filter)) keep.add(l);
                    return String.join("\n", keep.subList(Math.max(0, keep.size() - 60), keep.size()));
                } catch (Exception e) {
                    return "Journal du serveur illisible : " + e.getMessage();
                }
            }
            default -> {
                return null;
            }
        }
    }

    // ---------------------------------------------------------------- the agent's eyes

    /**
     * « voir » (asked by the organiser's game, which then takes the pictures): what to draw. {@code cible} =
     * camera (nom = la-pomme:4 / 4 with {@code manche}), trajet, intro (manche), plan (manche + plan); optional
     * {@code secondes} (« 0 2 4 », camera / travelling), {@code images} (per shot / along a travelling),
     * {@code effet} and {@code focus} (to try on a camera without changing anything), {@code video} (intro: 8
     * pictures per second, made into an MP4).
     */
    static String renderPlan(MinecraftServer server, JsonObject a) {
        String cible = fr.lordfinn.craftmaster.camera.CameraList.fold(str(a, "cible"));
        String nom = str(a, "nom"), manche = str(a, "manche");
        int images = a.has("images") ? Math.max(1, Math.min(12, a.get("images").getAsInt())) : 0;
        boolean video = a.has("video") && a.get("video").getAsBoolean();
        JsonObject out = new JsonObject();
        JsonArray frames = new JsonArray();
        JsonArray notes = new JsonArray();
        String effect = fr.lordfinn.craftmaster.broadcast.BroadcastActions.effectId(str(a, "effet"));
        String focusWords = str(a, "focus");
        if (!focusWords.isBlank()) {
            try {
                var f = fr.lordfinn.craftmaster.fx.FocusPull.parseWords(focusWords, null, 0, null);
                boolean dof = false;
                for (String id : fr.lordfinn.craftmaster.fx.ScreenFx.parseLenient(effect)) if (fr.lordfinn.craftmaster.fx.FocusPull.DOF_EFFECTS.contains(id)) dof = true;
                if (!dof) effect = effect.isEmpty() ? "mise_au_point" : effect + "+mise_au_point";
                effect = fr.lordfinn.craftmaster.fx.FocusPull.attach(effect, f);
            } catch (IllegalArgumentException e) {
                return "Focus : " + e.getMessage();
            }
        }
        List<Double> seconds = new ArrayList<>();
        for (String x : str(a, "secondes").replace(',', ' ').trim().split("\\s+"))
            if (!x.isBlank()) try { seconds.add(Math.max(0, Double.parseDouble(x))); } catch (NumberFormatException ignored) { }
        switch (cible) {
            case "camera", "cam", "plan_camera" -> {
                CameraSpot c = fr.lordfinn.craftmaster.camera.Lieux.resolve(server, nom, manche.isBlank() ? null : manche);
                if (c == null) return "Caméra « " + nom + " » introuvable" + (manche.isBlank() ? " (donne manche, ou la clé complète la-pomme:4)." : " dans " + manche + ".");
                if (seconds.isEmpty()) seconds = c.motion().equals("fixe") || c.motion().isEmpty() ? (effect.contains("|") || effect.contains("mise_au_point") ? List.of(0.2, 1.0, 2.0) : List.of(0.0)) : List.of(0.0, 2.0, 4.0);
                for (double t : seconds) frames.add(frame("caméra " + c.key() + (c.name() == null || c.name().isEmpty() ? "" : " « " + c.name() + " »")
                        + (effect.isEmpty() ? "" : " · " + fr.lordfinn.craftmaster.fx.FocusPull.effectsOf(effect)) + " · " + fmt(t) + " s", c.key(), "", t, effect));
                out.addProperty("titre", "Caméra " + c.key());
                notes.add("caméra " + c.key() + " : " + c.world() + " " + num(c.x()) + " " + num(c.y()) + " " + num(c.z()) + ", yaw " + Math.round(c.yaw())
                        + ", pitch " + Math.round(c.pitch()) + ", champ " + (c.fov() <= 0 ? "joueur" : Math.round(c.fov())) + ", mouvement " + c.motion());
            }
            case "trajet", "travelling" -> {
                CameraPath p = CameraPaths.get(nom);
                if (p == null) return "Travelling « " + nom + " » inconnu : " + String.join(", ", CameraPaths.byName().keySet());
                double d = Math.max(0.1, p.duration());
                if (seconds.isEmpty()) {
                    int n = images > 0 ? images : 6;
                    for (int k = 0; k < n; k++) seconds.add(n == 1 ? d / 2 : d * k / (n - 1));
                }
                for (double t : seconds) frames.add(frame("trajet " + p.name() + " · " + fmt(t) + " / " + fmt(d) + " s", "", p.name(), t, effect));
                out.addProperty("titre", "Travelling " + p.name());
            }
            case "intro", "plan" -> {
                var m = fr.lordfinn.craftmaster.camera.Lieux.manche(manche.isBlank() ? nom : manche);
                if (m == null) return "Manche « " + (manche.isBlank() ? nom : manche) + " » inconnue : " + manches();
                int only = cible.equals("plan") ? (a.has("plan") ? a.get("plan").getAsInt() : 1) : 0;
                List<String> problems = new ArrayList<>();
                var list = fr.lordfinn.craftmaster.broadcast.MancheIntros.previews(server, m.id, images > 0 ? images : only > 0 ? 3 : 1, only,
                        video ? 8 : 0, problems);
                for (var pv : list) frames.add(frame(pv.label(), pv.camera(), pv.path(), pv.t(), pv.effect()));
                problems.forEach(notes::add);
                out.addProperty("titre", "Intro de « " + m.name + " »" + (only > 0 ? ", plan " + only : "") + (video ? " (vidéo)" : ""));
                if (video) {
                    out.addProperty("video", true);
                    out.addProperty("ips", 8);
                }
                if (m.intro.shots.isEmpty()) notes.add("l'intro n'a aucun plan");
            }
            default -> {
                return "cible : camera (nom, manche), trajet (nom), intro (manche) ou plan (manche, plan).";
            }
        }
        if (frames.size() > 160) return "Trop d'images (" + frames.size() + ") : moins d'images par plan.";
        out.add("images", frames);
        out.add("notes", notes);
        return out.toString();
    }

    private static JsonObject frame(String label, String camera, String path, double t, String effect) {
        JsonObject f = new JsonObject();
        f.addProperty("legende", label);
        f.addProperty("camera", camera);
        f.addProperty("trajet", path);
        f.addProperty("t", t);
        f.addProperty("effet", effect);
        f.addProperty("depuis", t);
        return f;
    }

    private static String fmt(double t) {
        return String.format(Locale.ROOT, "%.1f", t).replace('.', ',');
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
