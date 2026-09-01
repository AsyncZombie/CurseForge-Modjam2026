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
    vec4 sigil = texture(Sampler0, texCoord0);
    // Hard cutout so soft fringe texels cannot reveal bright geometry behind the glyph.
    float sigilAlpha = sigil.a * vertexColor.a * ColorModulator.a;
    if (sigil.a <= 0.08 || sigilAlpha <= 0.04) {
        discard;
    }

    vec3 gold = sigil.rgb * vertexColor.rgb * ColorModulator.rgb;
    fragColor = vec4(gold, sigilAlpha);
}
