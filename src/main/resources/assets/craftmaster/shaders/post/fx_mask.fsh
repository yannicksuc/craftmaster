#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// Masks over the view, chosen by Mode.x: 0 vignette, 1 round mask (door peephole), 3 binoculars.
// (The carved pumpkin is vanilla's own overlay, drawn by the mod on the HUD: client.fx.FxOverlay.)
layout(std140) uniform FxMode {
    vec4 Mode;
};

void main() {
    int mode = int(Mode.x + 0.5);
    float k = Fx.x;
    vec3 c = texture(InSampler, texCoord).rgb;
    vec2 p = fxCentered(texCoord);
    float halfDiagonal = length(vec2(0.5 * fxAspect(), 0.5));
    float pixel = 1.0 / OutSize.y;
    vec3 o;
    if (mode == 0) {
        float d = length(p) / halfDiagonal;
        o = c * (1.0 - smoothstep(0.42, 1.08, d) * 0.72 * k);
    } else if (mode == 1) {
        // The circle closes from beyond the corners down to the peephole, like an iris
        float r = mix(halfDiagonal * 1.04, 0.47, k);
        float d = length(p);
        float inside = smoothstep(r + 1.5 * pixel, r - 1.5 * pixel, d);
        // Lens rim: darker towards the edge, a thin bright ring
        float rim = smoothstep(r - 0.10, r, d);
        vec3 lens = c * (1.0 - rim * 0.55) + vec3(0.9, 0.85, 0.7) * smoothstep(3.0 * pixel, 0.0, abs(d - (r - 4.0 * pixel))) * 0.18 * k;
        o = mix(vec3(0.012, 0.010, 0.012), lens, inside);
    } else {
        // Binoculars: two overlapping circles that close in and move apart during the fade
        float r = mix(halfDiagonal * 1.2, 0.36, k);
        vec2 off = vec2(mix(0.0, 0.25, k), 0.0);
        float d = min(length(p - off), length(p + off)) - r;
        float inside = smoothstep(1.5 * pixel, -1.5 * pixel, d);
        float rim = smoothstep(-0.08, 0.0, d);
        o = mix(vec3(0.01), c * (1.0 - rim * 0.5), inside);
    }
    fragColor = vec4(o, 1.0);
}
