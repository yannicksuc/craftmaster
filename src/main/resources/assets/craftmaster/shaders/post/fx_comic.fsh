#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// Comic book: flat posterized colours, ink outlines (edges of the brightness) and halftone dots in the shadows.
void main() {
    float k = Fx.x;
    vec2 px = 1.5 * OutSize.y / 1080.0 / InSize;
    vec3 c = texture(InSampler, texCoord).rgb;
    float tl = fxLuma(texture(InSampler, texCoord + vec2(-px.x, px.y)).rgb);
    float tc = fxLuma(texture(InSampler, texCoord + vec2(0.0, px.y)).rgb);
    float tr = fxLuma(texture(InSampler, texCoord + vec2(px.x, px.y)).rgb);
    float ml = fxLuma(texture(InSampler, texCoord + vec2(-px.x, 0.0)).rgb);
    float mr = fxLuma(texture(InSampler, texCoord + vec2(px.x, 0.0)).rgb);
    float bl = fxLuma(texture(InSampler, texCoord + vec2(-px.x, -px.y)).rgb);
    float bc = fxLuma(texture(InSampler, texCoord + vec2(0.0, -px.y)).rgb);
    float br = fxLuma(texture(InSampler, texCoord + vec2(px.x, -px.y)).rgb);
    float gx = -tl - 2.0 * ml - bl + tr + 2.0 * mr + br;
    float gy = -tl - 2.0 * tc - tr + bl + 2.0 * bc + br;
    float edge = smoothstep(0.38, 0.7, sqrt(gx * gx + gy * gy));

    // Flat colours: saturated, 5 levels
    float l = fxLuma(c);
    vec3 sat = clamp(mix(vec3(l), c, 1.45), 0.0, 1.0);
    vec3 flat_ = floor(sat * 5.0 + 0.5) / 5.0;
    // Halftone dots where it is dark
    vec2 grid = texCoord * OutSize / (6.0 * OutSize.y / 1080.0);
    float dotR = clamp((0.55 - l) * 1.4, 0.0, 0.7);
    float dots = step(length(fract(grid) - 0.5), dotR * 0.5);
    flat_ *= 1.0 - dots * 0.35;
    vec3 ink = vec3(0.08, 0.06, 0.10);
    vec3 o = mix(flat_, ink, edge);
    fragColor = vec4(mix(c, o, k), 1.0);
}
