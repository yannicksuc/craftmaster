package fr.lordfinn.craftmaster.regie;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.camera.CameraSpot;
import fr.lordfinn.craftmaster.data.CraftMasterData;
import fr.lordfinn.craftmaster.data.Live;
import fr.lordfinn.craftmaster.data.Manche;
import fr.lordfinn.craftmaster.data.Partie;
import fr.lordfinn.craftmaster.data.Season;
import fr.lordfinn.craftmaster.data.SeasonPlayer;
import fr.lordfinn.craftmaster.show.ActionJournal;
import fr.lordfinn.craftmaster.show.Actor;
import fr.lordfinn.craftmaster.show.Moderators;
import fr.lordfinn.craftmaster.show.Rights;


/**
 * What a moderators' director shows (in game and on the site), for one actor: only the parts their rights open, never
 * anything the viewers must not see unless the actor may change it (live points need the "jeu" right).
 * <pre>
 * rights [ids] · paused · admin
 * cameras [{n, key, name, role, lieu}] · shot {kind, camera, player} · auto · transition     (camera)
 * manche {id, name, number} | null · timer {name, endTick, total} · manches [{id, name, current}] · tick   (jeu)
 * players [{name, online, round, total}]                                                         (jeu: live points)
 * cast [names]                                                                                    (always: follow / POV)
 * journal [{at, actor, origin, ok, text}]                                                         (organiser)
 * </pre>
 * TVs and replays come with their branches (tele.show, replay.*).
 */
public final class ModoState {

    private ModoState() {}

    public static JsonObject build(CraftMasterData data, BroadcastNetwork.ShowState show, Actor actor) {
        JsonObject o = new JsonObject();
        JsonArray rights = new JsonArray();
        for (Rights.Right r : actor.rights()) rights.add(r.id);
        o.add("rights", rights);
        o.addProperty("admin", actor.isAdmin());
        o.addProperty("paused", Moderators.paused());
        boolean cam = actor.isAdmin() || actor.rights().contains(Rights.Right.CAMERA);
        boolean game = actor.isAdmin() || actor.rights().contains(Rights.Right.GAME);

        Season s = data == null ? null : data.activeSeason();
        JsonArray cast = new JsonArray();
        if (s != null) for (SeasonPlayer sp : s.players.values()) if (!sp.archived) cast.add(sp.name);
        o.add("cast", cast);

        if (cam && show != null) {
            JsonArray cams = new JsonArray();
            for (CameraSpot c : show.cameras()) {
                JsonObject j = new JsonObject();
                j.addProperty("n", c.number());
                j.addProperty("key", c.key());
                j.addProperty("name", c.name());
                j.addProperty("role", c.role());
                j.addProperty("lieu", c.lieu() + (c.zone().isEmpty() ? "" : ":" + c.zone()));
                cams.add(j);
            }
            o.add("cameras", cams);
            JsonObject shot = new JsonObject();
            shot.addProperty("kind", shotKind(show.shot().kind()));
            shot.addProperty("camera", show.shot().camera());
            shot.addProperty("player", show.shot().player());
            o.add("shot", shot);
            o.addProperty("auto", show.auto());
            o.addProperty("focus", show.focus());
        }
        if (cam) {
            // Stage: looks and cue lists to play in one click (stage/)
            JsonArray looks = new JsonArray();
            fr.lordfinn.craftmaster.stage.Scenography.book().looks.keySet().forEach(looks::add);
            o.add("looks", looks);
            JsonArray cues = new JsonArray();
            fr.lordfinn.craftmaster.stage.Scenography.book().cues.keySet().forEach(cues::add);
            o.add("cues", cues);
            o.addProperty("cueStatus", fr.lordfinn.craftmaster.stage.Cues.status());
            o.addProperty("spotlights", fr.lordfinn.craftmaster.studio.SpotlightBlockEntity.loaded().size());
        }

        if (game && data != null) {
            Manche cur = data.currentManche();
            Live.Current lc = data.live().current;
            Partie p = s == null ? null : s.activePartie();
            if (cur != null && lc != null) {
                JsonObject m = new JsonObject();
                m.addProperty("id", cur.id);
                m.addProperty("name", cur.name);
                m.addProperty("number", p == null ? 0 : p.number(cur));
                o.add("manche", m);
            }
            if (show != null && !show.timerName().isEmpty()) {
                JsonObject t = new JsonObject();
                t.addProperty("name", show.timerName());
                t.addProperty("endTick", show.timerEnd());
                t.addProperty("total", show.timerTotal());
                o.add("timer", t);
            }
            if (show != null) o.addProperty("tick", show.serverTick());
            JsonArray manches = new JsonArray();
            if (p != null) for (Manche m : p.activeManches()) {
                JsonObject j = new JsonObject();
                j.addProperty("id", m.id);
                j.addProperty("name", m.name);
                j.addProperty("current", cur != null && cur.id.equals(m.id));
                manches.add(j);
            }
            o.add("manches", manches);
            JsonArray players = new JsonArray();
            if (s != null) for (SeasonPlayer sp : s.players.values()) {
                if (sp.archived) continue;
                JsonObject j = new JsonObject();
                j.addProperty("name", sp.name);
                if (show != null) for (BroadcastNetwork.Contestant c : show.contestants())
                    if (c.name().equals(sp.name)) j.addProperty("online", c.online());
                j.addProperty("round", p == null || cur == null ? 0 : p.score(cur.id, sp.name));
                j.addProperty("total", s.total(sp.name));
                players.add(j);
            }
            o.add("players", players);
        }

        if (actor.isAdmin()) {
            JsonArray journal = new JsonArray();
            for (ActionJournal.Line l : ActionJournal.last(30)) {
                JsonObject j = new JsonObject();
                j.addProperty("at", l.at());
                j.addProperty("actor", l.actor());
                j.addProperty("origin", l.origin());
                j.addProperty("ok", l.ok());
                j.addProperty("text", String.join(" ; ", l.lines()));
                journal.add(j);
            }
            o.add("journal", journal);
        }
        return o;
    }

    static String shotKind(int kind) {
        return switch (kind) {
            case BroadcastNetwork.SHOT_CAMERA -> "camera";
            case BroadcastNetwork.SHOT_FOLLOW -> "follow";
            case BroadcastNetwork.SHOT_POV -> "pov";
            case BroadcastNetwork.SHOT_PATH -> "path";
            case BroadcastNetwork.SHOT_MOSAIC -> "mosaic";
            case BroadcastNetwork.SHOT_FREE -> "free";
            default -> "none";
        };
    }
}
