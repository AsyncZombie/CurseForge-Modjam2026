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

    float pulseAlpha = vertexColor.a
        * smoothstep(0.02, 0.30, textureCoverage)
        * ColorModulator.a;
    if (pulseAlpha <= 0.002) {
        discard;
    }

    // Only alpha is writable. The reserved range stores the actual pulse
    // opacity, allowing the post effect to preserve the wave without restoring
    // the bright world underneath its faint outer edge.
    float encodedAlpha = (8.0 + clamp(pulseAlpha, 0.0, 1.0) * 64.0) / 255.0;
    fragColor = vec4(0.0, 0.0, 0.0, encodedAlpha);
}
