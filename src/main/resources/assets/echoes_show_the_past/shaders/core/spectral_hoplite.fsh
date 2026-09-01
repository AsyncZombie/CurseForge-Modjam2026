#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    // The dedicated rig reserves the lower-right UV tile for authored metal.
    // This keeps gold on the crest, cuirass, dory and aspis instead of letting
    // arbitrary humanoid UV seams decide where an accent appears.
    float goldMask = step(0.82, texCoord0.x) * step(0.82, texCoord0.y);
    vec3 memoryWhite = vec3(0.985, 0.975, 0.925);
    vec3 memoryGold = vec3(1.0, 0.72, 0.10);
    vec3 spectral = mix(memoryWhite, memoryGold, goldMask);
    spectral *= vertexColor.rgb * ColorModulator.rgb;

    float materialAlpha = mix(0.90, 1.0, goldMask);
    float alpha = materialAlpha * vertexColor.a * ColorModulator.a;
    if (alpha <= 0.008) {
        discard;
    }
    fragColor = vec4(spectral, alpha);
}
