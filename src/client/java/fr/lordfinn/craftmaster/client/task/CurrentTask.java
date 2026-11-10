package fr.lordfinn.craftmaster.client.task;

import fr.lordfinn.craftmaster.CraftMaster;

import java.util.List;
import java.util.Optional;

/**
 * ====================================================================================================================
 * STUB — chantier E's stand-in for chantier D's class (docs/design/diffusion.md, D, "Contrat pour les autres
 * chantiers"). Chantier D's real class REPLACES this file at merge: keep D's version, delete this one.
 * Same API exactly: {@link #get()}, {@link Info}, {@link #showCard(int)}. Here nothing is known about the tasks:
 * {@code get()} is always empty and {@code showCard} only logs.
 * ====================================================================================================================
 */
public final class CurrentTask {

    private CurrentTask() {}

    /** The current round's task as this player may see it. */
    public record Info(String mancheId, String mancheName, int mancheNumber, String title, String text, List<String> rules,
                       int seconds, boolean revealed) {}

    /** STUB: always empty (chantier D syncs the task texts). */
    public static Optional<Info> get() {
        return Optional.empty();
    }

    /** STUB: chantier D draws the task card over the screen for {@code ticks}; here it is only logged. */
    public static void showCard(int ticks) {
        CraftMaster.LOG.info("[Réalisation] Carte de la tâche demandée pour {} ticks (stub du chantier D)", ticks);
    }
}
