#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// Colour effects in one shader, chosen by Mode.x: 0 black & white, 1 sepia, 2 thermal, 3 negative.
layout(std140) uniform FxMode {
    vec4 Mode;
};

vec3 thermal(float h) {
    // cold → hot: deep blue, violet, red, orange, yellow, white
    vec3 c0 = vec3(0.02, 0.01, 0.12);
    vec3 c1 = vec3(0.32, 0.02, 0.55);
    vec3 c2 = vec3(0.86, 0.08, 0.24);
    vec3 c3 = vec3(1.00, 0.52, 0.02);
    vec3 c4 = vec3(1.00, 0.92, 0.30);
    vec3 c5 = vec3(1.00, 1.00, 0.95);
    h = clamp(h, 0.0, 1.0) * 5.0;
    if (h < 1.0) return mix(c0, c1, h);
    if (h < 2.0) return mix(c1, c2, h - 1.0);
    if (h < 3.0) return mix(c2, c3, h - 2.0);
    if (h < 4.0) return mix(c3, c4, h - 3.0);
    return mix(c4, c5, h - 4.0);
}

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    vec3 o = c;
    int mode = int(Mode.x + 0.5);
    if (mode == 0) {
        float l = fxLuma(c);
        l = clamp((l - 0.5) * 1.18 + 0.5, 0.0, 1.0);
        o = vec3(l);
    } else if (mode == 1) {
        vec3 s = vec3(dot(c, vec3(0.393, 0.769, 0.189)), dot(c, vec3(0.349, 0.686, 0.168)), dot(c, vec3(0.272, 0.534, 0.131)));
        s = min(s, vec3(1.0));
        // a touch of faded paper: lifted blacks, soft highlights
        o = s * vec3(0.96, 0.92, 0.84) + vec3(0.05, 0.035, 0.015);
    } else if (mode == 2) {
        // "Heat": brightness, plus warm colours (skin, fire, lava) hotter and blue (sky, water) colder
        float l = fxLuma(c);
        // (grass and leaves are green: cold ground; red, orange and skin tones are warm bodies)
        float warm = max(0.0, c.r - max(c.g, c.b)) * 1.2 + max(0.0, c.r - c.b) * 0.35;
        float heat = 0.10 + l * 0.38 + warm - max(0.0, c.g - c.r) * 0.6 - max(0.0, c.b - c.r) * 0.35;
        o = thermal(heat);
    } else {
        o = vec3(1.0) - c;
    }
    fragColor = vec4(mix(c, o, Fx.x), 1.0);
}
