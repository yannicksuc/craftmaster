#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// Intro look, the film texture of a task intro: soft edges (a blur that grows towards the borders, like an old lens),
// a two-tone grade (dark teal-brown shadows, warm brown mid-tones, cream highlights, a little of the real colour
// kept), fine animated grain and a vignette. Lighter than the old film: no scratches, no flicker, no jumps.
void main() {
    float k = Fx.x;
    vec3 src = texture(InSampler, texCoord).rgb;
    vec2 p = fxCentered(texCoord);
    float d = length(p) / length(vec2(0.5 * fxAspect(), 0.5));

    // Soft edges: a disc blur whose radius rises from nothing in the middle third to ~9 px (1080p) in the corners
    vec3 c = src;
    float radius = smoothstep(0.42, 1.0, d) * 9.0 * k * OutSize.y / 1080.0;
    if (radius > 0.4) {
        const int N = 20;
        const float GOLDEN = 2.39996323;
        vec3 sum = src;
        for (int i = 1; i < N; i++) {
            float r = sqrt(float(i) / float(N)) * radius;
            float a = float(i) * GOLDEN;
            sum += texture(InSampler, texCoord + vec2(cos(a), sin(a)) * r / OutSize).rgb;
        }
        c = sum / float(N);
    }

    // Two-tone grade: a gradient map on a slightly contrasted luminance, mixed over the desaturated picture
    float l = fxLuma(c);
    float g = clamp((l - 0.5) * 1.12 + 0.5, 0.0, 1.0);
    vec3 shadow = vec3(0.06, 0.09, 0.10);
    vec3 middle = vec3(0.34, 0.25, 0.18);
    vec3 cream = vec3(1.00, 0.94, 0.80);
    vec3 tone = g < 0.4 ? mix(shadow, middle, smoothstep(0.0, 0.4, g)) : mix(middle, cream, smoothstep(0.4, 1.0, g));
    vec3 desat = mix(vec3(l), c, 0.55);
    vec3 o = mix(desat, tone, 0.6);

    // Fine grain, 24 images per second, strongest in the mid-tones
    float frame = floor(Fx.y * 24.0);
    vec2 px = floor(texCoord * OutSize / max(1.0, OutSize.y / 1080.0));
    float n = fxHash(px + frame * 37.0) - 0.5;
    o += n * 0.07 * (1.0 - abs(g - 0.5) * 1.2);

    // Vignette
    o *= 1.0 - smoothstep(0.5, 1.15, d) * 0.5;

    fragColor = vec4(mix(src, clamp(o, 0.0, 1.0), k), 1.0);
}
