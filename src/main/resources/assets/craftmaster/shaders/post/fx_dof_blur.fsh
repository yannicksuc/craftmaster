#version 330
#extension GL_ARB_separate_shader_objects : require

#include <craftmaster:fx_common.glsl>

// Depth of field, second pass: disc blur (bokeh) of the picture written by fx_dof_coc, each sample spreading as far
// as its own circle of confusion (alpha). Mode.z = largest blur radius in pixels at 1080p.
layout(std140) uniform FxMode {
    vec4 Mode;
};

void blurPass() {
    vec4 centre = texture(InSampler, texCoord);
    float cc = centre.a * 2.0 - 1.0;
    float maxR = Mode.z * OutSize.y / 1080.0;
    if (maxR * abs(cc) < 0.5 && Fx.x < 0.001) {
        fragColor = vec4(centre.rgb, 1.0);
        return;
    }
    vec3 sum = centre.rgb;
    float wsum = 1.0;
    const int N = 64;
    const float GOLDEN = 2.39996323;
    for (int i = 1; i < N; i++) {
        float r = sqrt(float(i) / float(N)) * maxR;
        float a = float(i) * GOLDEN;
        vec2 uv = texCoord + vec2(cos(a), sin(a)) * r / OutSize;
        vec4 s = texture(InSampler, uv);
        float sc = s.a * 2.0 - 1.0;
        // A sample behind the pixel cannot blur over it more than the pixel itself is blurred (no halo of
        // background over a sharp subject)
        float spread = sc > cc ? min(abs(sc), abs(cc)) : abs(sc);
        float w = clamp(spread * maxR - r + 1.0, 0.0, 1.0);
        // Bright points bloom into discs, like the highlights of a real bokeh
        w *= 1.0 + 2.0 * pow(fxLuma(s.rgb), 4.0) * abs(sc);
        sum += s.rgb * w;
        wsum += w;
    }
    fragColor = vec4(sum / wsum, 1.0);
}

void main() {
    blurPass();
}
