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
    public record Reply(String transcript, String actionsJson, int actionCount, String reply, List<String> warnings, String error) {
        public static Reply failure(String error, String detail) {
            return new Reply("", "[]", 0, detail, List.of(), error);
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
        return "{\"audio\":\"" + Base64.getEncoder().encodeToString(wav) + "\",\"context\":"
                + (contextJson == null || contextJson.isBlank() ? "{}" : contextJson) + "}";
    }

    public Reply command(byte[] wav, String contextJson) {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url + "/command"))
                .timeout(Duration.ofSeconds(30))
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(body(wav, contextJson), StandardCharsets.UTF_8))
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
                    o.has("error") && !o.get("error").isJsonNull() ? o.get("error").getAsString() : null);
        } catch (JsonParseException | IllegalStateException | ClassCastException | UnsupportedOperationException e) {
            return Reply.failure("bad_response", e.getMessage());
        }
    }

    private static String str(JsonObject o, String key) {
        return o.has(key) && o.get(key).isJsonPrimitive() ? o.get(key).getAsString() : "";
    }
}
