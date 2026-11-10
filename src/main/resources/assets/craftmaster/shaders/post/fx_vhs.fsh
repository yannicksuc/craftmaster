#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// VHS tape: wobbling lines, a tracking band rolling down, colour bleeding, scanlines, tape noise, soft colours.
void main() {
    float k = Fx.x;
    float t = Fx.y;
    vec2 uv = texCoord;
    float lineY = floor(uv.y * OutSize.y / 2.0);
    // Tracking band: a strip of distorted lines that rolls down the screen
    float band = fract(-t * 0.09);
    float inBand = smoothstep(0.07, 0.0, abs(uv.y - band));
    float jitter = (fxHash(vec2(lineY, floor(t * 30.0))) - 0.5);
    uv.x += (sin(uv.y * 40.0 + t * 7.0) * 0.0012 + jitter * 0.0018 + inBand * jitter * 0.03) * k;
    // Whole frame gently wobbling
    uv.x += sin(t * 1.3) * 0.0015 * k;

    // Colour bleeding: red and blue a few pixels apart, smeared horizontally
    float off = 2.6 / OutSize.x * k;
    vec3 c;
    c.r = (texture(InSampler, uv + vec2(off, 0.0)).r + texture(InSampler, uv + vec2(off * 2.0, 0.0)).r) * 0.5;
    c.g = texture(InSampler, uv).g;
    c.b = (texture(InSampler, uv - vec2(off, 0.0)).b + texture(InSampler, uv - vec2(off * 2.0, 0.0)).b) * 0.5;

    // Soft, slightly washed colours with raised blacks
    float l = fxLuma(c);
    c = mix(vec3(l), c, 0.82);
    c = c * 0.9 + vec3(0.04, 0.035, 0.05);

    // Scanlines (every other pair of pixel rows)
    float scan = 0.88 + 0.12 * sin(texCoord.y * OutSize.y * 3.14159 / 2.0);
    c *= scan;
    // Tape noise, stronger in the tracking band, with a few white streaks
    float n = fxHash(vec2(floor(texCoord.x * OutSize.x / 2.0), lineY) + floor(t * 30.0) * 17.0);
    c += (n - 0.5) * (0.07 + inBand * 0.25);
    float streak = step(0.995 - inBand * 0.03, fxHash(vec2(lineY * 0.37, floor(t * 15.0))));
    c = mix(c, vec3(0.92), streak * step(0.55, n) * 0.6);
    // Slightly darker corners
    vec2 p = fxCentered(texCoord);
    c *= 1.0 - smoothstep(0.45, 0.95, length(p) / length(vec2(0.5 * fxAspect(), 0.5))) * 0.35;

    vec3 base = texture(InSampler, texCoord).rgb;
    fragColor = vec4(mix(base, clamp(c, 0.0, 1.0), k), 1.0);
}
