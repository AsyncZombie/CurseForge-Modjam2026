#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    float textureCoverage = texture(Sampler0, texCoord0).a;
    if (textureCoverage <= 0.002) {
        discard;
    }

    vec4 pulse = vertexColor;
    pulse.a *= smoothstep(0.02, 0.30, textureCoverage);
    pulse *= ColorModulator;
    if (pulse.a <= 0.002) {
        discard;
    }

    // Ordinary alpha blending keeps the low-opacity outer layers soft and
    // chromatically stable. A separate alpha-only pass writes the technical
    // marker used by the post effect.
    fragColor = pulse;
}
