package fr.lordfinn.craftmaster.toy;

/**
 * The small task objects that live in the world as {@link ToyEntity}: put down anywhere, picked up, thrown,
 * kicked. Each kind has its own size and physics (pure data, no Minecraft class: tested).
 *
 * @param id          item id (craftmaster:&lt;id&gt;)
 * @param fr          French name, used in the server's messages ("Ensuri a trouvé un canard")
 * @param female      the French name is feminine (colour adjectives agree: "balle rouge", "canard rouge")
 */
public enum ToyKind {
    //           id               fr                       f      width  height restit. ground  floats depth  throw  dye  default colour
    DUCK("rubber_duck", "canard en plastique", false, 0.40, 0.50, 0.30, 0.50, true, 0.30, 1.0, true, 0xF6C632),
    BALL("bouncy_ball", "balle rebondissante", true, 0.25, 0.25, 0.65, 0.80, true, 0.50, 1.3, true, 0xB02E26),
    BEANBAG("bean_bag", "sachet", false, 0.375, 0.1875, 0.02, 0.15, false, 0, 1.05, true, 0xC9A66B),
    EGG("fragile_egg", "œuf fragile", false, 0.25, 0.3125, 0.15, 0.45, false, 0, 1.0, false, 0xF2E8D5),
    GOLF_BALL("golf_ball", "balle de golf", true, 0.1875, 0.1875, 0.45, 0.955, false, 0, 1.0, false, 0xF4F4F0);

    public final String id, fr;
    public final boolean female;
    public final double width, height;
    /** Part of the speed kept by a bounce (0 = dead stop). */
    public final double restitution;
    /** Horizontal speed kept per tick on the ground (rolling or sliding). */
    public final double groundFriction;
    public final boolean floats;
    /** How deep it sits in water, as a part of its height. */
    public final double floatDepth;
    /** Speed of a fully charged throw, blocks per tick (a snowball is 1.5). */
    public final double maxThrowSpeed;
    public final boolean dyeable;
    public final int defaultColor;

    ToyKind(String id, String fr, boolean female, double width, double height, double restitution, double groundFriction,
            boolean floats, double floatDepth, double maxThrowSpeed, boolean dyeable, int defaultColor) {
        this.id = id;
        this.fr = fr;
        this.female = female;
        this.width = width;
        this.height = height;
        this.restitution = restitution;
        this.groundFriction = groundFriction;
        this.floats = floats;
        this.floatDepth = floatDepth;
        this.maxThrowSpeed = maxThrowSpeed;
        this.dyeable = dyeable;
        this.defaultColor = defaultColor;
    }

    /** "un canard en plastique", "une balle de golf". */
    public String withArticle() {
        return (female ? "une " : "un ") + fr;
    }

    public static ToyKind byId(String id) {
        for (ToyKind k : values()) if (k.id.equals(id)) return k;
        return null;
    }
}
