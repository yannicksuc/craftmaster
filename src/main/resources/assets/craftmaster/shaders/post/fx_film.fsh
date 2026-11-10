#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// Old film: warm black and white, flickering exposure, grain, vertical scratches, dust and a jumpy frame,
// at 18 images per second like a hand-cranked projector.
void main() {
    float k = Fx.x;
    float frame = floor(Fx.y * 18.0);
    vec2 uv = texCoord;
    // Gate weave: the image jumps a little from frame to frame
    uv += (vec2(fxHash(vec2(frame, 1.0)), fxHash(vec2(frame, 2.0))) - 0.5) * vec2(0.0015, 0.004) * k;
    vec3 src = texture(InSampler, uv).rgb;
    float l = fxLuma(src);
    l = clamp((l - 0.5) * 1.25 + 0.5, 0.0, 1.0);
    // Flicker
    l *= 0.92 + 0.12 * fxHash(vec2(frame, 3.0));
    // Grain
    vec2 px = floor(texCoord * OutSize / 1.5);
    l += (fxHash(px + frame * 13.0) - 0.5) * 0.12;
    // Scratches: up to three thin vertical lines, each living a few frames
    for (int i = 0; i < 3; i++) {
        float life = floor(frame / (3.0 + float(i)));
        float seed = fxHash(vec2(life, float(i) * 7.0));
        if (seed > 0.45) {
            float x = fxHash(vec2(life, float(i) * 11.0 + 1.0));
            float w = 0.6 / OutSize.x * OutSize.y / 1080.0 * 2.0;
            float line = smoothstep(w, 0.0, abs(texCoord.x - x));
            l = mix(l, seed > 0.75 ? 0.95 : 0.12, line * 0.6);
        }
    }
    // Dust: a couple of dark specks per frame
    for (int i = 0; i < 2; i++) {
        vec2 c = vec2(fxHash(vec2(frame, 20.0 + float(i))), fxHash(vec2(frame, 30.0 + float(i))));
        float r = (0.002 + 0.004 * fxHash(vec2(frame, 40.0 + float(i))));
        vec2 d = (texCoord - c) * vec2(fxAspect(), 1.0);
        l = mix(l, 0.08, smoothstep(r, r * 0.4, length(d)) * 0.8);
    }
    // Strong rounded vignette
    vec2 p = fxCentered(texCoord);
    float d = length(p) / length(vec2(0.5 * fxAspect(), 0.5));
    l *= 1.0 - smoothstep(0.35, 1.0, d) * 0.7;
    vec3 tone = vec3(l) * vec3(1.06, 1.0, 0.86) + vec3(0.02, 0.012, 0.0);
    fragColor = vec4(mix(src, clamp(tone, 0.0, 1.0), k), 1.0);
}
