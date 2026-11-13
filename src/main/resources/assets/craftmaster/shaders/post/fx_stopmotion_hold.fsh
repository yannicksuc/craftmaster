#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// Stop motion, first pass: the picture shown. Fx.w = 1 on the frames that take a new picture (a few times per
// second): the live picture (In); otherwise the held one (Held, a persistent target the next pass copies into).
uniform sampler2D HeldSampler;

void main() {
    if (Fx.w > 0.5) fragColor = vec4(texture(InSampler, texCoord).rgb, 1.0);
    else fragColor = vec4(texture(HeldSampler, texCoord).rgb, 1.0);
}
