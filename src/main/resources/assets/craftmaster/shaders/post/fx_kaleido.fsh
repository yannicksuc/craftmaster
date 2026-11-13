#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// "Miroir" transition: the picture folds into six mirrored segments that spin, more and more until the cut (Fx.x =
// how folded, 1 at the cut), then the new picture unfolds the same way. Fx.z = progress of the transition 0..1.
void main() {
    float k = Fx.x;
    vec3 src = texture(InSampler, texCoord).rgb;
    vec2 p = fxCentered(texCoord);
    float r = length(p);
    // Keeps spinning the same way through the cut, a little faster near it
    float spin = Fx.z * 6.2831853 * 0.75 + k * 0.6;
    float seg = 6.2831853 / 6.0;
    float a = atan(p.y, p.x) + spin;
    a = mod(a, seg);
    if (a > seg * 0.5) a = seg - a;
    // Zooms in as it folds, so the segments show the middle of the picture
    vec2 q = vec2(cos(a), sin(a)) * r * (1.0 - 0.3 * k);
    vec2 uv = q / vec2(fxAspect(), 1.0) + 0.5;
    // Mirror repeat: never outside the picture
    uv = 1.0 - abs(1.0 - mod(uv, 2.0));
    vec3 folded = texture(InSampler, uv).rgb;
    // A touch brighter at the peak: the cut is in a flash of mirrors
    folded *= 1.0 + 0.15 * smoothstep(0.7, 1.0, k);
    fragColor = vec4(mix(src, clamp(folded, 0.0, 1.0), smoothstep(0.0, 0.55, k)), 1.0);
}
