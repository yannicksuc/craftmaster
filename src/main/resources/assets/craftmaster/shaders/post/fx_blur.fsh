#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// One direction of a Gaussian blur (Mode.xy = direction, Mode.z = spread in pixels at 1080p, at full intensity).
// Uses bilinear filtering: 9 fetches cover 17 texels.
layout(std140) uniform FxMode {
    vec4 Mode;
};

void main() {
    float spread = Mode.z * Fx.x * OutSize.y / 1080.0;
    if (spread < 0.05) {
        fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
        return;
    }
    vec2 stepUv = Mode.xy * spread / InSize;
    // weights of a 9-tap Gaussian (sigma about 2 steps)
    float w[5] = float[](0.2270270270, 0.1945945946, 0.1216216216, 0.0540540541, 0.0162162162);
    vec3 sum = texture(InSampler, texCoord).rgb * w[0];
    for (int i = 1; i < 5; i++) {
        sum += texture(InSampler, texCoord + stepUv * float(i)).rgb * w[i];
        sum += texture(InSampler, texCoord - stepUv * float(i)).rgb * w[i];
    }
    fragColor = vec4(sum, 1.0);
}
