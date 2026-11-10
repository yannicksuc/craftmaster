#ifndef CRAFTMASTER_FX_COMMON_GLSL
#define CRAFTMASTER_FX_COMMON_GLSL

// Shared by the CraftMaster screen effects (post_effect/fx/*.json). Fx is written every frame by the mod
// (client.fx.FxUniforms): x = intensity 0..1 (fades in / out), y = time in seconds.

uniform sampler2D InSampler;

layout(location = 0) in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 InSize;
};

layout(std140) uniform FxConfig {
    vec4 Fx;
};

layout(location = 0) out vec4 fragColor;

float fxLuma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

float fxHash(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// Screen position with square units: y from -0.5 to 0.5, x scaled by the aspect ratio
vec2 fxCentered(vec2 uv) {
    return (uv - 0.5) * vec2(OutSize.x / OutSize.y, 1.0);
}

float fxAspect() {
    return OutSize.x / OutSize.y;
}

#endif
