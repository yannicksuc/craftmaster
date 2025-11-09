package fr.lordfinn.craftMaster.utils;

import org.bukkit.entity.TextDisplay;

public class TextDisplayUtils {
    public static String safeGetText(TextDisplay td) {
        try {
            return td.getText() == null ? "" : td.getText();
        } catch (Exception e) {
            return "";
        }
    }
}
