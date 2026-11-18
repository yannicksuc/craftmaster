package fr.lordfinn.craftmaster.client.broadcast;

import fr.lordfinn.craftmaster.CraftMaster;
import fr.lordfinn.craftmaster.broadcast.BroadcastNetwork;
import fr.lordfinn.craftmaster.client.mixin.ContainerScreenAccessor;
import fr.lordfinn.craftmaster.client.ui.Theme;
import fr.lordfinn.craftmaster.client.ui.Ui;
import fr.lordfinn.craftmaster.trial.kit.InventoryCam;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3x2fStack;

import java.util.HashMap;
import java.util.Map;

/**
 * The show's inventory camera, both sides:
 * <ul>
 *   <li>a player's game tells the server when its inventory screen (or any screen showing the inventory) opens and
 *       closes, the slot under the mouse and the mouse's place on the inventory's grid, at most every tick and only
 *       when it changes;</li>
 *   <li>the broadcast game (F8) and the show feed (F7) draw the inventory of the player on air when it is open: it
 *       slides in from the right edge, the hovered slot lights up, the items move as they are moved, the item carried
 *       follows a smoothed cursor, and its name (or the hovered item's) is written under it.</li>
 * </ul>
 * Data: {@link InventoryCam}.
 */
public final class InventoryCamClient {

    private InventoryCamClient() {}

    // ---------------------------------------------------------------- the player's side

    private static InventoryCam.Screen lastSent = new InventoryCam.Screen(false, -1, 0, 0);

    private static void tick(Minecraft mc) {
        if (mc.player == null || !ClientPlayNetworking.canSend(InventoryCam.Screen.TYPE) || Broadcast.active()) return;
        InventoryCam.Screen now = new InventoryCam.Screen(false, -1, 0, 0);
        if (mc.gui.screen() instanceof AbstractContainerScreen<?> screen) {
            ContainerScreenAccessor acc = (ContainerScreenAccessor) screen;
            double mx = mc.mouseHandler.getScaledXPos(mc.getWindow()), my = mc.mouseHandler.getScaledYPos(mc.getWindow());
            double[] dev = fr.lordfinn.craftmaster.client.DevInput.hover(); // scripted mouse of the dev tests
            if (dev != null) {
                mx = dev[0];
                my = dev[1];
            }
            int left = acc.craftmaster$leftPos(), top = acc.craftmaster$topPos();
            Slot origin = null;
            int hovered = -1;
            for (Slot s : screen.getMenu().slots) {
                if (s.container != mc.player.getInventory()) continue;
                if (s.getContainerSlot() == 9) origin = s;
                // The slot under the mouse, as the screen finds it (16 × 16 from the slot's corner)
                if (s.getContainerSlot() < 36 && mx >= left + s.x - 1 && mx < left + s.x + 17 && my >= top + s.y - 1 && my < top + s.y + 17)
                    hovered = s.getContainerSlot();
            }
            int x = -200, y = -200;
            if (origin != null) {
                x = (int) Math.round(mx - left - origin.x);
                y = (int) Math.round(my - top - origin.y);
            }
            now = new InventoryCam.Screen(true, hovered, x, y);
        }
        if (now.equals(lastSent)) return;
        lastSent = now;
        ClientPlayNetworking.send(now);
    }

    // ---------------------------------------------------------------- the broadcast's side

    private static final Map<String, InventoryCam.View> VIEWS = new HashMap<>();
    /** What is drawn (kept while it slides out), its slide 0 → 1, the smoothed cursor. */
    private static InventoryCam.View shown;
    private static float slide;
    private static double cx, cy;
    private static long lastFrame;

    private static final int SLOT = 18, GX = 8, GY = 25, HOTBAR_GAP = 4;
    private static final int W = 9 * SLOT + 2 * 7, H = GY + 3 * SLOT + HOTBAR_GAP + SLOT + 18;

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(InventoryCamClient::tick);
        ClientPlayNetworking.registerGlobalReceiver(InventoryCam.View.TYPE, (payload, ctx) -> VIEWS.put(payload.player().toLowerCase(), payload));
        HudElementRegistry.addLast(CraftMaster.id("inventory_cam"), InventoryCamClient::extract);
    }

    /** The player on air: followed by the camera or seen through their eyes. */
    private static String onAir() {
        BroadcastNetwork.Shot s = DirectorCamera.shown();
        if (s == null || s.player() == null || s.player().isEmpty()) return null;
        return s.kind() == BroadcastNetwork.SHOT_FOLLOW || s.kind() == BroadcastNetwork.SHOT_POV ? s.player() : null;
    }

    private static void extract(GuiGraphicsExtractor g, DeltaTracker delta) {
        long t = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (t - lastFrame) / 1e9);
        lastFrame = t;
        boolean feed = Broadcast.active() || ShowFeedClient.active();
        String player = feed ? onAir() : null;
        InventoryCam.View v = player == null ? null : VIEWS.get(player.toLowerCase());
        boolean want = v != null && v.open();
        if (want) {
            if (shown == null || !shown.player().equalsIgnoreCase(v.player())) {
                cx = v.x();
                cy = v.y();
            }
            shown = v;
        }
        slide = (float) Math.max(0, Math.min(1, slide + (want ? 1 : -1) * dt * 5));
        if (shown == null || slide <= 0) {
            if (!want) shown = null;
            return;
        }
        InventoryCam.View view = shown;
        double k = 1 - Math.exp(-dt * 22);
        cx += (view.x() - cx) * k;
        cy += (view.y() - cy) * k;

        Minecraft mc = Minecraft.getInstance();
        float scale = Math.max(1f, g.guiHeight() * 0.30f / H);
        float ease = 1 - (1 - slide) * (1 - slide) * (1 - slide);
        float x0 = g.guiWidth() - 10 - W * scale + (1 - ease) * (W * scale + 20);
        float y0 = (g.guiHeight() - H * scale) / 2f;
        Matrix3x2fStack pose = g.pose();
        pose.pushMatrix();
        pose.translate(x0, y0);
        pose.scale(scale, scale);
        Ui.panel(g, mc.font, 0, 0, W, H, Component.literal("Inventaire · " + view.player()));
        for (int i = 0; i < 36; i++) {
            int[] p = slotPos(i);
            g.fill(p[0] - 1, p[1] - 1, p[0] + 17, p[1] + 17, 0xFF2A2024);
            g.fill(p[0], p[1], p[0] + 16, p[1] + 16, 0xFF3C3036);
            if (i == view.hovered()) g.fill(p[0], p[1], p[0] + 16, p[1] + 16, 0x60FFE9A8);
            ItemStack st = i < view.slots().size() ? view.slots().get(i) : ItemStack.EMPTY;
            if (!st.isEmpty()) {
                g.item(st, p[0], p[1]);
                g.itemDecorations(mc.font, st, p[0], p[1]);
            }
        }
        boolean cursor = view.x() > -200 && view.y() > -200;
        int mx = (int) Math.round(GX + cx), my = (int) Math.round(GY + cy);
        if (cursor && !view.carried().isEmpty()) {
            g.item(view.carried(), mx - 8, my - 8);
            g.itemDecorations(mc.font, view.carried(), mx - 8, my - 8);
        } else if (cursor) {
            pointer(g, mx, my);
        }
        ItemStack named = !view.carried().isEmpty() ? view.carried()
                : view.hovered() >= 0 && view.hovered() < view.slots().size() ? view.slots().get(view.hovered()) : ItemStack.EMPTY;
        if (!named.isEmpty()) {
            Component name = named.getHoverName();
            int tw = mc.font.width(name);
            g.text(mc.font, name, (W - tw) / 2, H - 13, Theme.TITLE, true);
        }
        pose.popMatrix();
    }

    /** Item position of an inventory slot in the panel: backpack rows (9-35) on top, the hot bar (0-8) below. */
    private static int[] slotPos(int i) {
        if (i < 9) return new int[]{GX + i * SLOT, GY + 3 * SLOT + HOTBAR_GAP};
        int r = (i - 9) / 9, c = (i - 9) % 9;
        return new int[]{GX + c * SLOT, GY + r * SLOT};
    }

    /** The game's own arrow cursor, pixel for pixel (black edge, white inside), its tip where the mouse is. */
    private static final String[] ARROW = {
            "X", "XX", "XWX", "XWWX", "XWWWX", "XWWWWX", "XWWWWWX", "XWWWXXXX", "XWXWX", "XX.XWX", "X..XWX", "....XX"};

    private static void pointer(GuiGraphicsExtractor g, int x, int y) {
        for (int r = 0; r < ARROW.length; r++)
            for (int c = 0; c < ARROW[r].length(); c++) {
                char ch = ARROW[r].charAt(c);
                if (ch == 'X') g.fill(x + c, y + r, x + c + 1, y + r + 1, 0xFF000000);
                else if (ch == 'W') g.fill(x + c, y + r, x + c + 1, y + r + 1, 0xFFFFFFFF);
            }
    }
}
