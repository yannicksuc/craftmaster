#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// Depth of field from the depth buffer, first pass: reads the picture (In) and the depth (Depth = minecraft:main's
// depth buffer) and writes the picture with its circle of confusion in alpha (0.5 = sharp, below = in front of the
// focus, above = behind it). The second pass (fx_dof_blur) blurs it.
// Mode.y: 0 = macro (focused on what is at the centre of the picture), 1 = focus pull (focused on the background,
// then after 0.4 s the focus slides to the centre subject over 1.2 s, Fx.z = seconds since the effect was switched
// on). Mode.w = aperture (how fast things leave the focus).
uniform sampler2D DepthSampler;

layout(std140) uniform FxMode {
    vec4 Mode;
};

const float NEAR = 0.05;
const float FAR = 1024.0;

// 1 / distance in blocks. Same formula for OpenGL (depth stored as (z + 1) / 2) and zero-to-one depth.
float invDistance(float d) {
    return (1.0 - d) / NEAR + d / FAR;
}

float focusInv() {
    // The nearest of five taps around the centre: the subject, even if the very centre falls just beside it
    float best = invDistance(texture(DepthSampler, vec2(0.5)).r);
    vec2 o = vec2(0.015 * OutSize.y / OutSize.x, 0.015);
    best = max(best, invDistance(texture(DepthSampler, vec2(0.5) + vec2(o.x, 0.0)).r));
    best = max(best, invDistance(texture(DepthSampler, vec2(0.5) - vec2(o.x, 0.0)).r));
    best = max(best, invDistance(texture(DepthSampler, vec2(0.5) + vec2(0.0, o.y)).r));
    best = max(best, invDistance(texture(DepthSampler, vec2(0.5) - vec2(0.0, o.y)).r));
    return best;
}

void cocPass() {
    vec3 c = texture(InSampler, texCoord).rgb;
    float inv = invDistance(texture(DepthSampler, texCoord).r);
    float focus = focusInv();
    if (Mode.y > 0.5) {
        // Focus pull: from far behind the subject (at least 24 blocks) to the subject, moving in 1 / distance like a
        // real lens ring
        float background = min(focus / 6.0, 1.0 / 24.0);
        float s = smoothstep(0.4, 1.6, Fx.z);
        focus = mix(background, focus, s);
    }
    // Thin lens: the blur grows with |1 - focus distance / distance|
    float signedCoc = clamp((1.0 - inv / focus) * Mode.w, -1.0, 1.0) * Fx.x;
    fragColor = vec4(c, 0.5 + 0.5 * signedCoc);
}

void main() {
    cocPass();
}
