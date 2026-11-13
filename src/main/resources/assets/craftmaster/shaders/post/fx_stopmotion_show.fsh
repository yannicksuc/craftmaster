#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// Stop motion, last pass: the held picture, each one placed and exposed a little differently like frames shot by
// hand (a slight shift, a hint of rotation, the exposure breathing). Fx.z = number of the held picture.
void main() {
    float k = Fx.x;
    float img = Fx.z;
    vec2 jitter = (vec2(fxHash(vec2(img, 3.1)), fxHash(vec2(img, 7.7))) - 0.5) * vec2(0.004, 0.005) * k;
    float angle = (fxHash(vec2(img, 11.3)) - 0.5) * 0.004 * k;
    vec2 p = fxCentered(texCoord);
    p = mat2(cos(angle), -sin(angle), sin(angle), cos(angle)) * p;
    // Slightly zoomed in so the shifted picture never shows its edge
    vec2 uv = p * (1.0 - 0.012 * k) / vec2(fxAspect(), 1.0) + 0.5 + jitter;
    vec3 c = texture(InSampler, uv).rgb;
    c *= 1.0 + (fxHash(vec2(img, 19.9)) - 0.5) * 0.09 * k;
    fragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
