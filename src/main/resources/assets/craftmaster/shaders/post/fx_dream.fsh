#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// Dream / flashback: the sharp picture with a soft glow of its blurred copy, pastel colours, a luminous misty rim.
uniform sampler2D BlurSampler;

void main() {
    vec3 c = texture(InSampler, texCoord).rgb;
    vec3 b = texture(BlurSampler, texCoord).rgb;
    float k = Fx.x;
    // Glow: screen blend of the blurred highlights
    vec3 glow = max(b - 0.25, 0.0) * 1.3;
    vec3 o = 1.0 - (1.0 - mix(c, b, 0.35)) * (1.0 - glow);
    // Pastel: a little less saturation, lifted shadows, a lilac-pink tint
    float l = fxLuma(o);
    o = mix(vec3(l), o, 0.8);
    o = o * 0.88 + vec3(0.10, 0.07, 0.12);
    // Misty white rim
    vec2 p = fxCentered(texCoord);
    float d = length(p) / length(vec2(0.5 * fxAspect(), 0.5));
    float rim = smoothstep(0.55, 1.05, d);
    o = mix(o, vec3(1.0, 0.96, 1.0), rim * 0.55);
    // Slow floating sparkles
    float t = Fx.y;
    vec2 g = texCoord * vec2(fxAspect(), 1.0) * 26.0 + vec2(t * 0.15, -t * 0.35);
    vec2 cellId = floor(g);
    float h = fxHash(cellId);
    vec2 f = fract(g) - vec2(fxHash(cellId + 3.1), fxHash(cellId + 7.7));
    float twinkle = 0.5 + 0.5 * sin(t * 2.0 + h * 40.0);
    float spark = step(0.965, h) * smoothstep(0.08, 0.0, length(f)) * twinkle;
    o += vec3(1.0, 0.95, 0.85) * spark * 0.6;
    fragColor = vec4(mix(c, clamp(o, 0.0, 1.0), k), 1.0);
}
