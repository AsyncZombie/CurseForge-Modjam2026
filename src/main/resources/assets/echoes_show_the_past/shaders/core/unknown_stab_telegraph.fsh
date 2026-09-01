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
    float across = clamp(texCoord0.y, 0.0, 1.0);
    float centre = 1.0 - abs(across * 2.0 - 1.0);
    float rail = smoothstep(0.72, 0.96, 1.0 - centre);
    float inner = smoothstep(0.04, 0.32, centre);

    vec3 memoryWhite = vec3(0.96, 0.95, 0.90);
    vec3 memoryGold = vec3(1.0, 0.72, 0.10);
    vec3 spectral = mix(memoryWhite, memoryGold, rail);
    spectral *= vertexColor.rgb * ColorModulator.rgb;

    float shapedAlpha = mix(inner * 0.48, 0.88, rail);
    float alpha = shapedAlpha * vertexColor.a * ColorModulator.a;
    if (alpha <= 0.008) {
        discard;
    }
    fragColor = vec4(spectral, alpha);
}
