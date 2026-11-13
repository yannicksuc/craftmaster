package fr.lordfinn.craftmaster.client.portrait;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/**
 * Vertex consumers that reshape the figure on its way to the GPU, quad by quad, in camera space (the camera is at
 * the origin when custom geometry is drawn). Minecraft 26.3's render pipelines have no stencil, so both effects
 * are done on the geometry itself:
 * <ul>
 *   <li>{@link #cut}: the canvas is a real cut. What lies on the hidden side of the canvas plane is clipped away
 *   (exact polygon clipping), so a figure leaning out of the painting keeps its legs "in the picture".</li>
 *   <li>{@link #portal}: the window (Antichamber) look. Everything is behind the opening; each vertex is moved
 *   along its line of sight onto the canvas plane (it stays exactly where the eye sees it), then the flattened
 *   polygons are clipped to the opening. A tiny offset keeps their depth order. Seen through the opening the
 *   inside looks deep, from anywhere else the portrait is a thin flat canvas: nothing exists outside it.</li>
 * </ul>
 * Planes and the opening are given in "frame space" (pixels, see PortraitGeometry) with the matrix that took that
 * space to camera space when the portrait was submitted.
 */
public final class PortraitClipper implements VertexConsumer {

    /** One vertex with every attribute an entity render type uses, plus how far behind the plane it was. */
    private static final class V {
        float x, y, z, u, v, nx, ny, nz, depth;
        int color = 0xFFFFFFFF, overlay, light;

        V copy() {
            V c = new V();
            c.x = x; c.y = y; c.z = z; c.u = u; c.v = v; c.nx = nx; c.ny = ny; c.nz = nz; c.depth = depth;
            c.color = color; c.overlay = overlay; c.light = light;
            return c;
        }

        static V lerp(V a, V b, float t) {
            V c = a.copy();
            c.x = a.x + (b.x - a.x) * t;
            c.y = a.y + (b.y - a.y) * t;
            c.z = a.z + (b.z - a.z) * t;
            c.u = a.u + (b.u - a.u) * t;
            c.v = a.v + (b.v - a.v) * t;
            c.depth = a.depth + (b.depth - a.depth) * t;
            return c;
        }
    }

    /** Half-space a·X + w ≥ 0 (X in camera space). */
    private record Plane(Vector3f a, float w) {
        float eval(V p) {
            return a.x * p.x + a.y * p.y + a.z * p.z + w;
        }
    }

    private final VertexConsumer out;
    private final boolean portal;
    /** Front normal of the canvas in camera space, and the plane's constant (n·X = d on the plane). */
    private final Vector3f n;
    private final float d;
    private final Plane keep;
    private final List<Plane> opening = new ArrayList<>();
    /** The cut only hides what is behind the canvas inside the frame's outline (a box, not a whole half-space). */
    private final List<Plane> outline = new ArrayList<>();
    private final List<V> quad = new ArrayList<>(4);

    private PortraitClipper(VertexConsumer out, Matrix4f frameToCamera, float planeZ, boolean keepFront, boolean portal,
                            float openHalfW, float openHalfH, float cutHalfW, float cutHalfH) {
        this.out = out;
        this.portal = portal;
        Vector3f origin = frameToCamera.transformPosition(new Vector3f(0, 0, planeZ));
        n = frameToCamera.transformDirection(new Vector3f(0, 0, 1)).normalize();
        d = n.dot(origin);
        keep = keepFront ? new Plane(new Vector3f(n), -d) : new Plane(new Vector3f(n).negate(), d);
        // The four sides of a rectangle (the opening, or the frame's outline for the cut), normals pointing inside
        float hw = portal ? openHalfW : cutHalfW, hh = portal ? openHalfH : cutHalfH;
        List<Plane> sides = portal ? opening : outline;
        Vector3f ex = frameToCamera.transformDirection(new Vector3f(1, 0, 0)).normalize();
        Vector3f ey = frameToCamera.transformDirection(new Vector3f(0, 1, 0)).normalize();
        Vector3f c0 = frameToCamera.transformPosition(new Vector3f(-hw, -hh, planeZ));
        Vector3f c1 = frameToCamera.transformPosition(new Vector3f(hw, hh, planeZ));
        sides.add(new Plane(new Vector3f(ex), -ex.dot(c0)));
        sides.add(new Plane(new Vector3f(ex).negate(), ex.dot(c1)));
        sides.add(new Plane(new Vector3f(ey), -ey.dot(c0)));
        sides.add(new Plane(new Vector3f(ey).negate(), ey.dot(c1)));
    }

    /**
     * The canvas as a cut: keeps what is in front of frame-space plane z = planeZ ({@code keepFront}) or behind it,
     * for polygons inside the frame's outline (half sizes, frame space).
     */
    public static PortraitClipper cut(VertexConsumer out, Matrix4f frameToCamera, float planeZ, boolean keepFront,
                                      float frameHalfW, float frameHalfH) {
        return new PortraitClipper(out, frameToCamera, planeZ, keepFront, false, 0, 0, frameHalfW, frameHalfH);
    }

    /** The window look: flattens onto frame-space plane z = planeZ, inside the opening (half sizes). */
    public static PortraitClipper portal(VertexConsumer out, Matrix4f frameToCamera, float planeZ, float openHalfW, float openHalfH) {
        return new PortraitClipper(out, frameToCamera, planeZ, false, true, openHalfW, openHalfH, 0, 0);
    }

    /** True when the camera is in front of the canvas (the window can only be looked through from the front). */
    public static boolean cameraInFront(Matrix4f frameToCamera, float planeZ) {
        Vector3f origin = frameToCamera.transformPosition(new Vector3f(0, 0, planeZ));
        Vector3f n = frameToCamera.transformDirection(new Vector3f(0, 0, 1));
        return n.dot(origin) < 0;
    }

    // ---------------------------------------------------------------- VertexConsumer

    private V current() {
        return quad.getLast();
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        if (quad.size() == 4) processQuad();
        V v = new V();
        v.x = x; v.y = y; v.z = z;
        quad.add(v);
        return this;
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float nx, float ny, float nz) {
        addVertex(x, y, z);
        V c = current();
        c.color = color; c.u = u; c.v = v; c.overlay = overlay; c.light = light; c.nx = nx; c.ny = ny; c.nz = nz;
        if (quad.size() == 4) processQuad();
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        current().color = (a & 255) << 24 | (r & 255) << 16 | (g & 255) << 8 | (b & 255);
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        current().color = color;
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        current().u = u;
        current().v = v;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        current().overlay = (u & 0xFFFF) | (v & 0xFFFF) << 16;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        current().light = (u & 0xFFFF) | (v & 0xFFFF) << 16;
        return this;
    }

    //? if >=26.3
    @Override
    public VertexConsumer setUv3(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        current().nx = x; current().ny = y; current().nz = z;
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }

    /** Sends the last quad (chained vertices complete only when the next one starts). */
    public void flush() {
        if (quad.size() == 4) processQuad();
        quad.clear();
    }

    // ---------------------------------------------------------------- clipping

    private static List<V> clip(List<V> poly, Plane plane) {
        List<V> res = new ArrayList<>(poly.size() + 2);
        for (int i = 0; i < poly.size(); i++) {
            V a = poly.get(i), b = poly.get((i + 1) % poly.size());
            float da = plane.eval(a), db = plane.eval(b);
            if (da >= 0) res.add(a);
            if (da >= 0 != db >= 0) res.add(V.lerp(a, b, da / (da - db)));
        }
        return res;
    }

    private static Plane flip(Plane plane) {
        return new Plane(new Vector3f(plane.a()).negate(), -plane.w());
    }

    /**
     * The cut: removes exactly the part of the polygon that is on the hidden side of the canvas AND inside the
     * frame's outline (convex region subtraction); what sticks out below or beside the frame stays whole.
     */
    private void emitOutsideHidden(List<V> poly) {
        emit(clip(poly, keep));
        List<V> hidden = clip(poly, flip(keep));
        for (Plane side : outline) {
            if (hidden.size() < 3) return;
            emit(clip(hidden, flip(side)));
            hidden = clip(hidden, side);
        }
    }

    private void processQuad() {
        List<V> poly = new ArrayList<>(quad);
        quad.clear();
        if (!portal) {
            emitOutsideHidden(poly);
            return;
        }
        // Behind the canvas only, then flattened along the lines of sight, then cut to the opening
        if (d >= 0) return;
        // Flattening is a projection: a big quad would get its texture bent by the GPU's interpolation (it moves
        // with the point of view). Small pieces keep it straight; each is flattened on its own.
        int n = pieces(poly);
        if (n > 1) {
            for (int i = 0; i < n; i++)
                for (int j = 0; j < n; j++)
                    flattenAndEmit(List.of(at(poly, i / (float) n, j / (float) n), at(poly, (i + 1) / (float) n, j / (float) n),
                            at(poly, (i + 1) / (float) n, (j + 1) / (float) n), at(poly, i / (float) n, (j + 1) / (float) n)));
            return;
        }
        flattenAndEmit(poly);
    }

    /** Pieces per side: about one every 2 px (1/8 block), at most 12. */
    private static int pieces(List<V> q) {
        float max = 0;
        for (int i = 0; i < 4; i++) {
            V a = q.get(i), b = q.get((i + 1) % 4);
            max = Math.max(max, (float) Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z)));
        }
        return Math.max(1, Math.min(12, (int) Math.ceil(max / 0.125f)));
    }

    /** Bilinear point of quad (a, b, c, d) at (s along a→b, t along a→d), every attribute interpolated. */
    private static V at(List<V> q, float s, float t) {
        V top = V.lerp(q.get(0), q.get(1), s), bottom = V.lerp(q.get(3), q.get(2), s);
        return V.lerp(top, bottom, t);
    }

    private void flattenAndEmit(List<V> in) {
        List<V> poly = clip(new ArrayList<>(in), keep);
        if (poly.size() < 3) return;
        for (V p : poly) {
            float along = n.x * p.x + n.y * p.y + n.z * p.z;     // < d: behind the plane
            p.depth = d - along;                                 // distance behind, in blocks
            float s = d / along;
            p.x *= s; p.y *= s; p.z *= s;
        }
        for (Plane side : opening) {
            poly = clip(poly, side);
            if (poly.size() < 3) return;
        }
        // Depth order kept with a tiny step back (at most 0.8 px, for 6 blocks of depth: the deepest room)
        for (V p : poly) {
            float back = Math.min(1f, p.depth / 6f) * (0.8f / 16f);
            p.x -= n.x * back; p.y -= n.y * back; p.z -= n.z * back;
        }
        emit(poly);
    }

    /** Fan of quads (a triangle is sent as a quad repeating its last corner). */
    private void emit(List<V> poly) {
        if (poly.size() < 3) return;
        for (int i = 1; i + 1 < poly.size(); i += 2) {
            V a = poly.getFirst(), b = poly.get(i), c = poly.get(i + 1), e = i + 2 < poly.size() ? poly.get(i + 2) : c;
            put(a); put(b); put(c); put(e);
        }
    }

    private void put(V p) {
        out.addVertex(p.x, p.y, p.z, p.color, p.u, p.v, p.overlay, p.light, p.nx, p.ny, p.nz);
    }
}
