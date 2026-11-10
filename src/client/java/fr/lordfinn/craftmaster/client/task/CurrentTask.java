package fr.lordfinn.craftmaster.client.task;

import fr.lordfinn.craftmaster.task.TaskNetwork;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;
import java.util.Optional;

/**
 * The task of the moment as this game knows it (contract for the other chantiers, see docs/design/diffusion.md
 * "Interfaces livrées" D). Sent by the server on join and whenever it changes; a secret task arrives without its
 * text for non-admins ({@link Info#revealed()} false, empty title / text / rules).
 *
 * <p>{@link #showCard(int)} slides the task card in over the game for a while (the TV overlay, {@code !tache} from
 * the Twitch chat, the organisers' "show on screen"). The card never shows a secret text, even on an organiser's
 * game: it is meant for the broadcast.
 */
public final class CurrentTask {

    private CurrentTask() {}

    /** The task of the moment. {@code seconds}: duration shown on the card (0 = none). */
    public record Info(String mancheId, String mancheName, int mancheNumber, String title, String text, List<String> rules,
                       int seconds, boolean revealed) {}

    private static TaskNetwork.State state = new TaskNetwork.State(TaskNetwork.Card.NONE, false);
    private static long shownAt, shownUntil;
    private static PaperView cardView;
    private static TaskNetwork.Card cardFor;

    /** The task of the moment, if there is one (revealed or not). */
    public static Optional<Info> get() {
        TaskNetwork.Card c = state.card();
        if (!c.present()) return Optional.empty();
        return Optional.of(new Info(c.mancheId(), c.mancheName(), c.number(), c.title(), c.text(), c.rules(), c.seconds(), c.revealed()));
    }

    /** Wax colour of the task of the moment ("red", "gold"...). */
    public static String wax() {
        return state.card().wax();
    }

    /** This player organises (the server says so). */
    public static boolean admin() {
        return state.admin();
    }

    static TaskNetwork.Card card() {
        return state.card();
    }

    static void set(TaskNetwork.State s) {
        state = s;
    }

    /** Shows the task card over the game for {@code ticks} (1/20 s); a new call restarts it. */
    public static void showCard(int ticks) {
        if (!state.card().present()) return;
        long now = System.nanoTime();
        // Already on screen: keep it there, just longer (no second entrance)
        if (now >= shownUntil) shownAt = now;
        shownUntil = now + Math.max(20, ticks) * 50_000_000L;
    }

    /** Hides the card at once. */
    public static void hideCard() {
        shownUntil = 0;
    }

    public static boolean cardShown() {
        return System.nanoTime() < shownUntil;
    }

    /** The paper shown on the card: the task if revealed, else the "still secret" card. */
    static TaskNetwork.Paper paperOf(TaskNetwork.Card c) {
        String header = Component.translatable("paper.craftmaster.round", c.number(), c.mancheName()).getString();
        if (!c.revealed())
            return new TaskNetwork.Paper("text", header, Component.translatable("paper.craftmaster.secret").getString(),
                    Component.translatable("paper.craftmaster.secret_hint").getString(), List.of(), 0, c.wax(), "paper", 0, List.of(), "", 0, "");
        return new TaskNetwork.Paper("task", header, c.title(), c.text(), c.rules(), c.seconds(), c.wax(), "paper", 0, List.of(), c.mancheId(), c.season(), "");
    }

    /** The overlay card: slides in from the right, scrolls slowly if it is long, slides out. */
    static final class Hud implements HudElement {
        @Override
        public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker delta) {
            long now = System.nanoTime();
            if (now >= shownUntil + 400_000_000L || !state.card().present()) return;
            Minecraft mc = Minecraft.getInstance();
            TaskNetwork.Card c = state.card();
            int w = Math.min(220, g.guiWidth() / 3 + 20);
            if (cardView == null || !c.equals(cardFor) || cardView.contentWidth() != w) {
                cardView = new PaperView(mc.font, paperOf(c), 1.5f);
                cardView.layout(w);
                cardFor = c;
            }
            int maxH = (int) (g.guiHeight() * 0.72f);
            int h = Math.min(cardView.contentHeight(), maxH);
            float in = PaperScreen.ease((now - shownAt) / 400_000_000f);
            float out = now > shownUntil ? PaperScreen.ease((now - shownUntil) / 400_000_000f) : 0;
            int x = g.guiWidth() - w - 12 + (int) ((1 - in + out) * (w + 24));
            int y = (g.guiHeight() - h) / 2;
            // Long text: scrolls like a prompter after 2 s, at the pace of reading
            int overflow = cardView.contentHeight() - h;
            float shown = (now - shownAt) / 1_000_000_000f;
            float total = (shownUntil - shownAt) / 1_000_000_000f;
            int scroll = overflow <= 0 ? 0 : (int) (overflow * Mth.clamp((shown - 2f) / Math.max(1f, total - 4f), 0, 1));
            g.pose().pushMatrix();
            g.pose().translate(x, y);
            g.pose().rotate(-0.012f);
            cardView.draw(g, 0, 0, w, h, scroll, 245, 255);
            g.pose().popMatrix();
        }
    }
}
