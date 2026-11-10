package fr.lordfinn.craftmaster.studio;

import java.net.URI;
import java.util.Locale;

/**
 * What {@code /image <URL> [width] [height]} asks for, checked: an http(s) address and a size in blocks (default
 * 5 × 3 like the plugin's ImageFrame command). Pure, unit tested.
 */
public record ImageRequest(String url, int width, int height) {

    public static final int DEFAULT_WIDTH = 5, DEFAULT_HEIGHT = 3;
    public static final int MAX_WIDTH = 16, MAX_HEIGHT = 9;
    public static final int MAX_URL_LENGTH = 1024;

    /** A refused request: the message is French and shown as is. */
    public static final class Invalid extends Exception {
        public Invalid(String message) {
            super(message);
        }
    }

    /** "URL", "URL 4", "URL 4 2" (sizes are clamped to 1..16 × 1..9; a missing height keeps the 5:3 look). */
    public static ImageRequest parse(String text) throws Invalid {
        String[] parts = text == null ? new String[0] : text.trim().split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) throw new Invalid("Usage : /image <URL> [largeur] [hauteur]");
        if (parts.length > 3) throw new Invalid("Trop d'arguments : /image <URL> [largeur] [hauteur] (l'adresse ne doit pas contenir d'espace).");
        String url = parts[0];
        String error = urlError(url);
        if (error != null) throw new Invalid(error);
        int w = DEFAULT_WIDTH, h = DEFAULT_HEIGHT;
        if (parts.length >= 2) {
            w = size(parts[1], "largeur");
            h = parts.length == 3 ? size(parts[2], "hauteur") : Math.max(1, Math.round(w * DEFAULT_HEIGHT / (float) DEFAULT_WIDTH));
        }
        return new ImageRequest(url, clamp(w, MAX_WIDTH), clamp(h, MAX_HEIGHT));
    }

    private static int size(String s, String what) throws Invalid {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            throw new Invalid("La " + what + " doit être un nombre de blocs (« " + s + " »).");
        }
    }

    public static int clamp(int v, int max) {
        return Math.max(1, Math.min(max, v));
    }

    /** Null when the address can be downloaded by the players' games, else why not (French). */
    public static String urlError(String url) {
        if (url == null || url.isBlank()) return "Adresse vide.";
        if (url.length() > MAX_URL_LENGTH) return "Adresse trop longue (" + MAX_URL_LENGTH + " caractères au plus).";
        String lower = url.toLowerCase(Locale.ROOT);
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return "Adresse invalide : http:// ou https:// attendu.";
        try {
            URI uri = URI.create(url);
            if (uri.getHost() == null || uri.getHost().isEmpty()) return "Adresse invalide : pas de nom de site.";
        } catch (IllegalArgumentException e) {
            return "Adresse invalide (caractère interdit ?).";
        }
        return null;
    }
}
