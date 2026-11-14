package fr.lordfinn.craftmaster.regie;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Client of the local voice control service (tools/voice-regie, {@code POST /command}). Pure Java (no game
 * classes) so it can be unit tested; called from a worker thread, never the render thread.
 */
public final class RegieService {

    public static final String DEFAULT_URL = "http://127.0.0.1:25790";

    /**
     * What the service understood. {@code error}: null when it worked, otherwise a code — the service's own
     * (no_api_key, claude_error, stt_error, empty_transcript, refused, bad_request) or ours (unreachable, timeout,
     * bad_response).
     */
    public record Reply(String transcript, String actionsJson, int actionCount, String reply, List<String> warnings, String error,
                        String agentJob) {
        public static Reply failure(String error, String detail) {
            return new Reply("", "[]", 0, detail, List.of(), error, null);
        }

        public Reply withWarnings(List<String> w) {
            return new Reply(transcript, actionsJson, actionCount, reply, w, error, agentJob);
        }
    }

    private final String url;
    private final HttpClient http;

    public RegieService(String url) {
        this.url = url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    /** The request body: the WAV in base64 next to the context JSON (already serialized by the server). */
    public static String body(byte[] wav, String contextJson) {
        return body(wav, contextJson, true);
    }

    /** {@code agent}: may "change the setup" requests go to the régie agent (off: the service says it is off). */
    public static String body(byte[] wav, String contextJson, boolean agent) {
        return "{\"audio\":\"" + Base64.getEncoder().encodeToString(wav) + "\",\"agent\":" + agent + ",\"context\":"
                + (contextJson == null || contextJson.isBlank() ? "{}" : contextJson) + "}";
    }

    public Reply command(byte[] wav, String contextJson) {
        return command(wav, contextJson, true);
    }

    public Reply command(byte[] wav, String contextJson, boolean agent) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/command"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body(wav, contextJson, agent), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return parse(res.body());
        } catch (ConnectException e) {
            return Reply.failure("unreachable", e.toString());
        } catch (HttpTimeoutException e) {
            return Reply.failure("timeout", e.toString());
        } catch (IOException e) {
            return Reply.failure(e.getCause() instanceof ConnectException ? "unreachable" : "unreachable", e.toString());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Reply.failure("timeout", "interrupted");
        }
    }

    /** Reads the service's JSON answer; a malformed one gives a "bad_response" reply instead of an exception. */
    public static Reply parse(String json) {
        try {
            JsonObject o = new Gson().fromJson(json, JsonObject.class);
            if (o == null) return Reply.failure("bad_response", "empty answer");
            JsonArray actions = o.has("actions") && o.get("actions").isJsonArray() ? o.getAsJsonArray("actions") : new JsonArray();
            List<String> warnings = new ArrayList<>();
            if (o.has("warnings") && o.get("warnings").isJsonArray())
                for (JsonElement w : o.getAsJsonArray("warnings")) warnings.add(w.getAsString());
            return new Reply(str(o, "transcript"), actions.toString(), actions.size(), str(o, "reply"), warnings,
                    o.has("error") && !o.get("error").isJsonNull() ? o.get("error").getAsString() : null,
                    o.has("agentJob") && !o.get("agentJob").isJsonNull() ? o.get("agentJob").getAsString() : null);
        } catch (JsonParseException | IllegalStateException | ClassCastException | UnsupportedOperationException e) {
            return Reply.failure("bad_response", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ régie agent (regie/agent.py)

    /** A tool call the agent waits for. */
    public record AgentCall(int cid, String tool, String argsJson) {}

    /** {@code GET /agent/poll}: calls to relay to the server, and whether the agent has finished (its reply). */
    public record AgentPoll(List<AgentCall> calls, boolean done, String reply, String error, boolean gone) {}

    /** Starts the agent on a typed request; the job id, or null (service unreachable). */
    public String agentStart(String text, String contextJson, String player) {
        JsonObject o = new JsonObject();
        o.addProperty("text", text);
        o.addProperty("player", player);
        String body = o.toString();
        if (contextJson != null && !contextJson.isBlank()) body = body.substring(0, body.length() - 1) + ",\"context\":" + contextJson + "}";
        JsonObject r = post("/agent/start", body);
        return r != null && r.has("job") ? r.get("job").getAsString() : null;
    }

    public AgentPoll agentPoll(String job) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/agent/poll?job=" + java.net.URLEncoder.encode(job, StandardCharsets.UTF_8)))
                    .timeout(Duration.ofSeconds(5)).GET().build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (res.statusCode() == 404) return new AgentPoll(List.of(), true, "", "unknown_job", true);
            return parsePoll(res.body());
        } catch (IOException e) {
            return new AgentPoll(List.of(), false, "", "unreachable", false);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new AgentPoll(List.of(), true, "", "interrupted", true);
        }
    }

    public static AgentPoll parsePoll(String json) {
        JsonObject o = new Gson().fromJson(json, JsonObject.class);
        List<AgentCall> calls = new ArrayList<>();
        if (o.has("calls") && o.get("calls").isJsonArray())
            for (JsonElement e : o.getAsJsonArray("calls")) {
                JsonObject c = e.getAsJsonObject();
                calls.add(new AgentCall(c.get("cid").getAsInt(), c.get("tool").getAsString(),
                        c.has("args") && c.get("args").isJsonObject() ? c.get("args").toString() : "{}"));
            }
        return new AgentPoll(calls, o.has("done") && o.get("done").getAsBoolean(), str(o, "reply"),
                o.has("error") && !o.get("error").isJsonNull() ? o.get("error").getAsString() : null, false);
    }

    public void agentResult(String job, int cid, boolean ok, String output) {
        JsonObject o = new JsonObject();
        o.addProperty("job", job);
        o.addProperty("cid", cid);
        o.addProperty("ok", ok);
        o.addProperty("output", output);
        post("/agent/result", o.toString());
    }

    private JsonObject post(String path, String body) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url + path)).timeout(Duration.ofSeconds(10))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8)).build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return new Gson().fromJson(res.body(), JsonObject.class);
        } catch (IOException | JsonParseException e) {
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return null;
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }
}
