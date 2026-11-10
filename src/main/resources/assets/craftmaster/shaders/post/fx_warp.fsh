#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// Picture distortions, chosen by Mode.x: 0 fisheye (barrel), 1 pixelated, 2 wobble (dizzy).
layout(std140) uniform FxMode {
    vec4 Mode;
};

void main() {
    int mode = int(Mode.x + 0.5);
    float k = Fx.x;
    if (mode == 0) {
        // Barrel distortion: the centre is magnified, the edges squeezed; the corners stay in the corners.
        // The mod widens the field of view at the same time, so the fisheye sees more of the room.
        vec2 aspect = vec2(fxAspect(), 1.0);
        vec2 p = (texCoord - 0.5) * aspect;
        float halfDiagonal = length(0.5 * aspect);
        float rho = length(p) / halfDiagonal;
        float strength = 0.62 * k;
        float f = (1.0 - strength) + strength * rho * rho;
        vec2 uv = p * f / aspect + 0.5;
        // A hint of chromatic fringe towards the rim, like a cheap wide lens
        vec2 shift = (uv - 0.5) * 0.006 * k * rho;
        float r = texture(InSampler, uv + shift).r;
        vec2 gb = texture(InSampler, uv).gb;
        float b = texture(InSampler, uv - shift).b;
        fragColor = vec4(r, gb.x, b, 1.0);
    } else if (mode == 1) {
        // Big square pixels (about 12 px on a 1080p picture); the pixels grow during the fade
        float size = mix(1.0, max(4.0, OutSize.y / 90.0), k);
        size = max(1.0, floor(size + 0.5));
        vec2 cell = (floor(texCoord * OutSize / size) + 0.5) * size / OutSize;
        vec3 c = texture(InSampler, cell).rgb;
        // Fewer colours, like an old console
        vec3 posterized = floor(c * 12.0 + 0.5) / 12.0;
        fragColor = vec4(mix(c, posterized, k), 1.0);
    } else {
        float t = Fx.y;
        vec2 uv = texCoord;
        uv.x += sin(uv.y * 9.0 + t * 2.3) * 0.010 * k + sin(uv.y * 23.0 - t * 3.1) * 0.003 * k;
        uv.y += cos(uv.x * 7.0 + t * 1.7) * 0.008 * k;
        // Double vision that drifts in and out
        vec2 ghost = vec2(sin(t * 0.9), cos(t * 0.7)) * 0.012 * k;
        vec3 a = texture(InSampler, uv).rgb;
        vec3 g = texture(InSampler, uv + ghost).rgb;
        fragColor = vec4(mix(a, g, 0.35 * k), 1.0);
    }
}
