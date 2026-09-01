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
    float edge = smoothstep(0.66, 0.98, 1.0 - centre);
    float fineCarving = 0.5 + 0.5 * cos(texCoord0.x * 12.5663706);
    fineCarving = smoothstep(0.78, 0.98, fineCarving) * (1.0 - edge);

    vec3 sekhmetGold = vec3(1.0, 0.61, 0.018);
    vec3 desertIvory = vec3(1.0, 0.965, 0.80);
    vec3 lapisInlay = vec3(0.035, 0.20, 0.52);

    float goldMask = smoothstep(0.10, 0.48, vertexColor.r - vertexColor.b);
    float lapisMask = smoothstep(0.06, 0.34,
            vertexColor.b - vertexColor.r * 0.55);
    float ivoryMask = clamp(1.0 - max(goldMask, lapisMask), 0.0, 1.0);
    vec3 ceremonial = desertIvory * ivoryMask
            + sekhmetGold * goldMask
            + lapisInlay * lapisMask;

    float bladeFlash = smoothstep(0.58, 0.98, centre)
            * (0.78 + fineCarving * 0.22);
    ceremonial = mix(ceremonial, desertIvory,
            bladeFlash * (0.18 + goldMask * 0.28));
    ceremonial += sekhmetGold * edge * goldMask * 0.18;
    ceremonial *= ColorModulator.rgb;

    float solidCore = mix(0.76, 1.0, centre);
    float cutEdge = mix(1.0, 0.82, edge);
    float alpha = vertexColor.a * solidCore * cutEdge * ColorModulator.a;
    if (alpha <= 0.008) {
        discard;
    }
    fragColor = vec4(ceremonial, alpha);
}
