package fr.lordfinn.craftmaster.trial.kit;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.lordfinn.craftmaster.CraftMaster;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.SharedConstants;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Names as a player sees them in THEIR game language, on the server (which only ships English): the game's own
 * language file for that locale, fetched once from Mojang's public asset server and kept in
 * {@code config/craftmaster/lang/}, plus the mods' language files found in their jars. Unknown keys fall back to
 * English. Used by the alphabetical inventory (each contestant sorts in their own language).
 */
public final class Lang {

    private Lang() {}

    private static final Map<String, Map<String, String>> LOADED = new ConcurrentHashMap<>();
    private static final Set<String> FETCHING = ConcurrentHashMap.newKeySet();
    private static final Pattern ARG = Pattern.compile("%(?:(\\d+)\\$)?([sd%])");

    private static Path dir() {
        return FabricLoader.getInstance().getConfigDir().resolve("craftmaster/lang");
    }

    public static String norm(String locale) {
        return locale == null || locale.isBlank() ? "en_us" : locale.toLowerCase(Locale.ROOT);
    }

    /** The table of that locale if it is ready (null while it is being fetched); English: an empty table. */
    public static Map<String, String> table(String locale) {
        String l = norm(locale);
        if (l.equals("en_us")) return Map.of();
        Map<String, String> t = LOADED.get(l);
        if (t != null) return t;
        Path cached = dir().resolve(l + ".json");
        if (Files.exists(cached)) {
            t = new HashMap<>(read(cached));
            t.putAll(modLang(l));
            LOADED.put(l, t);
            return t;
        }
        prefetch(l);
        return null;
    }

    /** Fetches the locale's file in the background (once). */
    public static void prefetch(String locale) {
        String l = norm(locale);
        if (l.equals("en_us") || LOADED.containsKey(l) || Files.exists(dir().resolve(l + ".json")) || !FETCHING.add(l)) return;
        Thread t = new Thread(() -> {
            try {
                download(l);
                CraftMaster.LOG.info("[Langues] {} téléchargée", l);
            } catch (Exception e) {
                CraftMaster.LOG.warn("[Langues] {} introuvable : les noms resteront en anglais ({})", l, e.toString());
            } finally {
                FETCHING.remove(l);
            }
        }, "craftmaster-lang-" + l);
        t.setDaemon(true);
        t.start();
    }

    private static void download(String locale) throws Exception {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NORMAL).build();
        String version = SharedConstants.getCurrentVersion().id();
        JsonObject manifest = get(http, "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json");
        String versionUrl = null;
        for (JsonElement v : manifest.getAsJsonArray("versions"))
            if (v.getAsJsonObject().get("id").getAsString().equals(version)) versionUrl = v.getAsJsonObject().get("url").getAsString();
        if (versionUrl == null) throw new IllegalStateException("version " + version + " absente du manifeste");
        JsonObject index = get(http, get(http, versionUrl).getAsJsonObject("assetIndex").get("url").getAsString());
        JsonObject obj = index.getAsJsonObject("objects").getAsJsonObject("minecraft/lang/" + locale + ".json");
        if (obj == null) throw new IllegalStateException("pas de langue " + locale);
        String hash = obj.get("hash").getAsString();
        String body = http.send(HttpRequest.newBuilder(URI.create("https://resources.download.minecraft.net/" + hash.substring(0, 2) + "/" + hash))
                .timeout(Duration.ofSeconds(30)).build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8)).body();
        JsonParser.parseString(body).getAsJsonObject(); // valid JSON before keeping it
        Files.createDirectories(dir());
        Files.writeString(dir().resolve(locale + ".json"), body, StandardCharsets.UTF_8);
    }

    private static JsonObject get(HttpClient http, String url) throws Exception {
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20)).build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (r.statusCode() != 200) throw new IllegalStateException(url + " → " + r.statusCode());
        return JsonParser.parseString(r.body()).getAsJsonObject();
    }

    private static Map<String, String> read(Path f) {
        Map<String, String> out = new HashMap<>();
        try {
            for (Map.Entry<String, JsonElement> e : JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject().entrySet())
                if (e.getValue().isJsonPrimitive()) out.put(e.getKey(), e.getValue().getAsString());
        } catch (Exception e) {
            CraftMaster.LOG.warn("[Langues] {} illisible", f, e);
        }
        return out;
    }

    /** The mods' own translations for that locale (assets/<mod>/lang/<locale>.json in their jars). */
    private static Map<String, String> modLang(String locale) {
        Map<String, String> out = new HashMap<>();
        for (ModContainer mod : FabricLoader.getInstance().getAllMods())
            for (Path root : mod.getRootPaths()) {
                Path assets = root.resolve("assets");
                if (!Files.isDirectory(assets)) continue;
                try (var ns = Files.list(assets)) {
                    ns.forEach(n -> {
                        Path f = n.resolve("lang/" + locale + ".json");
                        if (Files.exists(f)) out.putAll(read(f));
                    });
                } catch (Exception ignored) {
                }
            }
        return out;
    }

    /** The text of a component in that language (translations resolved with their arguments). */
    public static String text(Component c, Map<String, String> table) {
        StringBuilder sb = new StringBuilder();
        append(c, table, sb);
        return sb.toString();
    }

    private static void append(Component c, Map<String, String> table, StringBuilder sb) {
        if (c.getContents() instanceof TranslatableContents t) {
            String pattern = table != null ? table.get(t.getKey()) : null;
            if (pattern == null) pattern = Language.getInstance().has(t.getKey()) ? Language.getInstance().getOrDefault(t.getKey())
                    : t.getFallback() != null ? t.getFallback() : t.getKey();
            Object[] args = t.getArgs();
            Matcher m = ARG.matcher(pattern);
            int next = 0, last = 0;
            while (m.find()) {
                sb.append(pattern, last, m.start());
                if (m.group(2).equals("%")) sb.append('%');
                else {
                    int i = m.group(1) != null ? Integer.parseInt(m.group(1)) - 1 : next++;
                    if (i >= 0 && i < args.length) {
                        if (args[i] instanceof Component a) append(a, table, sb);
                        else sb.append(args[i]);
                    }
                }
                last = m.end();
            }
            sb.append(pattern.substring(last));
        } else if (c.getContents() instanceof PlainTextContents p) {
            sb.append(p.text());
        } else {
            sb.append(c.plainCopy().getString());
        }
        for (Component s : c.getSiblings()) append(s, table, sb);
    }
}
