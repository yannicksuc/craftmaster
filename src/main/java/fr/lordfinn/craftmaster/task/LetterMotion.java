package fr.lordfinn.craftmaster.task;

/**
 * The geometry and the motion of the task letter (pure maths, drawn by the client's LetterScreen).
 *
 * <p>The letter is a cream sheet {@link #WIDTH} letter-pixels wide, folded in three like the real ones: a short top
 * panel ({@link #TOP}), the middle panel and the bottom panel ({@link #PANEL} each), plus extra panels for a long
 * text (concertina, unfolding downwards one after the other). Closed, the bottom panel is folded up over the middle
 * and the top panel down over both, its edge across the middle, sealed there with wax. Tearing the seal off flips the
 * top panel up, then the bottom one down, then the extra ones, while the view zooms out to show the width and the
 * first three panels.
 *
 * <p>Times are in seconds from the moment the seal pops. Lengths are letter pixels; the screen draws them at an
 * integer number of screen pixels ({@link #pixelScale}) so the pixel art and the text stay crisp.
 */
public final class LetterMotion {

    private LetterMotion() {}

    public static final int WIDTH = 256, TOP = 64, PANEL = 112;
    /** Where the seal sits when closed: on the edge of the top panel, centred. */
    public static final int SEAL_X = WIDTH / 2, SEAL_Y = 2 * TOP;

    static final float TOP_START = 0.10f, TOP_END = 0.62f, BOTTOM_START = 0.50f, BOTTOM_END = 1.02f;
    static final float EXTRA_START = 0.92f, EXTRA_STEP = 0.40f, EXTRA_LENGTH = 0.40f;

    /** Panels needed for a content reaching {@code bottom} (letter y): at least 3. */
    public static int panels(int bottom) {
        int n = 3;
        while (height(n) < bottom) n++;
        return n;
    }

    public static int height(int panels) {
        return TOP + PANEL * (panels - 1);
    }

    /** Top of a panel on the flat sheet (0 = top panel, 1 = middle, 2 = bottom, 3+ = extra). */
    public static int panelTop(int panel) {
        return panel == 0 ? 0 : TOP + PANEL * (panel - 1);
    }

    public static int panelHeight(int panel) {
        return panel == 0 ? TOP : PANEL;
    }

    /** How open a panel is, 0 folded .. 1 flat, eased. The middle one is always flat. */
    public static float open(int panel, float t) {
        if (panel == 1) return 1;
        float a, b;
        if (panel == 0) {
            a = TOP_START;
            b = TOP_END;
        } else if (panel == 2) {
            a = BOTTOM_START;
            b = BOTTOM_END;
        } else {
            a = EXTRA_START + EXTRA_STEP * (panel - 2);
            b = a + EXTRA_LENGTH;
        }
        return easeInOut((t - a) / (b - a));
    }

    /** When the last panel is flat. */
    public static float end(int panels) {
        return panels <= 3 ? BOTTOM_END : EXTRA_START + EXTRA_STEP * (panels - 3) + EXTRA_LENGTH;
    }

    /** Progress of the zoom-out (0 close on the folded letter .. 1 the open letter), during the main opening. */
    public static float zoom(float t) {
        return easeInOut((t - TOP_START) / (BOTTOM_END + 0.1f - TOP_START));
    }

    /**
     * Where a panel is drawn (letter y, top to bottom) for an opening {@code open}: the folding is a rotation about
     * its hinge seen from the front, i.e. a vertical squash by cos. {@code front}: its inside (the text) faces the
     * reader; otherwise its outside, lying folded over the panel before it. {@code light}: 1 flat .. 0 edge-on.
     */
    public record Projection(float y0, float y1, boolean front, float light) {}

    public static Projection project(int panel, float open) {
        int top = panelTop(panel), h = panelHeight(panel);
        if (panel == 1) return new Projection(top, top + h, true, 1);
        float c = (float) -Math.cos(Math.PI * clamp(open));
        float k = Math.abs(c);
        if (panel == 0) {
            // Hinge at its bottom edge (the top of the middle panel)
            return c >= 0 ? new Projection(TOP - h * k, TOP, true, k) : new Projection(TOP, TOP + h * k, false, k);
        }
        // Hinge at its top edge
        return c >= 0 ? new Projection(top, top + h * k, true, k) : new Projection(top - h * k, top, false, k);
    }

    // ---------------------------------------------------------------- view

    /** The largest whole number of screen pixels per letter pixel showing {@code w x h} letter pixels (at least 1). */
    public static int pixelScale(int screenW, int screenH, float w, float h, float fill) {
        double s = Math.min(screenW * fill / w, screenH * fill / h);
        return Math.max(1, (int) Math.floor(s + 1e-6));
    }

    /** Scale while closed: the folded letter (the middle panel) filling most of the width. */
    public static int closedScale(int screenW, int screenH) {
        return pixelScale(screenW, screenH, WIDTH + 24, PANEL + 36, 0.92f);
    }

    /** Scale once open: the width and the first three panels (a longer letter scrolls). */
    public static int openScale(int screenW, int screenH, int panels) {
        return pixelScale(screenW, screenH, WIDTH + 20, Math.min(height(panels), height(3)) + 20, 0.94f);
    }

    /** Most the letter can scroll, in letter pixels, when the view shows {@code viewH} of them. */
    public static float maxScroll(int letterH, float viewH) {
        return Math.max(0, letterH + 12 - viewH + 12);
    }

    // ---------------------------------------------------------------- the seal

    /**
     * The wax seal being torn off. Pulled with the mouse (it follows, resisting more and more, trembles, then
     * pops), or with a held key / a click (it pulls by itself). Released too early, it springs back.
     */
    public static final class Seal {
        /** Pull needed to pop it (letter px of mouse travel), radius that can be grabbed. */
        public static final float POP = 26, GRAB = 25;
        /** Seconds to pull it off with a held key or after a click. */
        public static final float AUTO = 0.55f;

        private boolean grabbed, auto, popped, locked;
        private float gx, gy, dx, dy, tension, ox, oy, heldTime;
        private float popX, popY, popVx, popVy;

        /** A sealed letter that cannot be opened (secret): the seal resists and never pops. */
        public void lock(boolean locked) {
            this.locked = locked;
        }

        public boolean popped() {
            return popped;
        }

        public boolean grabbed() {
            return grabbed;
        }

        /** 0 at rest .. 1 about to pop. */
        public float tension() {
            return tension;
        }

        /** Offset of the seal from its place (letter px). */
        public float offsetX() {
            return ox;
        }

        public float offsetY() {
            return oy;
        }

        /** Is (x, y) (letter px, relative to the seal's centre) on the seal? */
        public static boolean hit(float x, float y) {
            return x * x + y * y <= GRAB * GRAB;
        }

        /** Mouse pressed on the seal at (x, y) relative to its centre. */
        public void grab(float x, float y) {
            if (popped) return;
            grabbed = true;
            gx = x;
            gy = y;
            dx = dy = 0;
            heldTime = 0;
        }

        /** Mouse dragged to (x, y) relative to the seal's centre. @return true when this pull made it pop */
        public boolean drag(float x, float y) {
            if (!grabbed || popped) return false;
            dx = x - gx;
            dy = y - gy;
            float d = (float) Math.hypot(dx, dy);
            tension = Math.min(locked ? 0.6f : 1, d / POP);
            follow();
            if (tension >= 1) return pop(dx, dy);
            return false;
        }

        /** Mouse released: a click without a pull starts the automatic pull; otherwise it springs back. */
        public void release() {
            if (!grabbed) return;
            grabbed = false;
            if (Math.hypot(dx, dy) < 2.5f && heldTime < 0.35f) auto = true;
        }

        /** Key held (or not) this frame; {@code dt} seconds. @return true when it popped */
        public boolean update(float dt, boolean keyHeld) {
            if (popped) return false;
            if (grabbed) {
                heldTime += dt;
                return false;
            }
            if (keyHeld || auto) {
                tension = Math.min(locked ? 0.6f : 1, tension + dt / AUTO);
                // Pulled toward the reader and a little up
                dx = 0;
                dy = -tension * POP * 0.6f;
                follow();
                if (tension >= 1) return pop(0, -1);
                if (locked && tension >= 0.6f) auto = false;
                return false;
            }
            // Springs back
            float k = (float) Math.exp(-dt * 14);
            tension *= k;
            ox *= k;
            oy *= k;
            if (tension < 0.01f) tension = ox = oy = 0;
            return false;
        }

        /** The seal follows the pull with resistance: a fraction of it, growing as it gives way. */
        private void follow() {
            float f = 0.22f + 0.3f * tension * tension;
            ox = dx * f;
            oy = dy * f;
        }

        private boolean pop(float vx, float vy) {
            popped = true;
            grabbed = false;
            auto = false;
            popX = ox;
            popY = oy;
            float n = (float) Math.max(1e-3, Math.hypot(vx, vy));
            popVx = vx / n * 180;
            popVy = vy / n * 180 - 90;
            return true;
        }

        /** Where the popped seal flies, {@code t} seconds after: {x, y, rotation (radians), alpha 0..1}. */
        public float[] flight(float t) {
            float x = popX + popVx * t;
            float y = popY + popVy * t + 0.5f * 900 * t * t;
            float rot = (popVx >= 0 ? 1 : -1) * 5f * t;
            float alpha = clamp(1 - (t - 0.35f) / 0.35f);
            return new float[]{x, y, rot, alpha};
        }
    }

    // ---------------------------------------------------------------- easing

    public static float clamp(float x) {
        return x < 0 ? 0 : x > 1 ? 1 : x;
    }

    public static float easeInOut(float x) {
        float c = clamp(x);
        return c < 0.5f ? 4 * c * c * c : 1 - (float) Math.pow(-2 * c + 2, 3) / 2;
    }

    public static float easeOut(float x) {
        float c = clamp(x);
        return 1 - (1 - c) * (1 - c) * (1 - c);
    }
}
