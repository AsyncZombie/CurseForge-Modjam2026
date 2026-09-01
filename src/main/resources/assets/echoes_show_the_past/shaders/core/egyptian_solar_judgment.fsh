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
    float whiteCore = smoothstep(0.72, 0.985, centre);
    float goldEdge = smoothstep(0.64, 0.985, 1.0 - centre);
    float procession = 0.5 + 0.5 * sin(texCoord0.x * 18.8495559);
    float carvedBand = smoothstep(0.74, 0.96, procession)
            * smoothstep(0.10, 0.42, centre)
            * (1.0 - whiteCore);
    float innerCarving = smoothstep(0.82, 0.985,
            0.5 + 0.5 * cos(texCoord0.x * 37.6991118 + across * 3.1415926));

    vec3 deepAmber = vec3(0.58, 0.15, 0.0);
    vec3 sunGold = vec3(1.0, 0.50, 0.0);
    vec3 sunWhite = vec3(1.0, 0.975, 0.80);
    vec3 base = mix(vertexColor.rgb, deepAmber, goldEdge * 0.36);
    base = mix(base, sunGold, goldEdge * 0.82 + carvedBand * 0.30);
    vec3 spectral = mix(base, sunWhite,
            whiteCore * 0.92 + carvedBand * 0.28 + innerCarving * whiteCore * 0.12);
    spectral *= ColorModulator.rgb;

    float shapedAlpha = mix(0.48, 0.96, max(whiteCore, goldEdge));
    shapedAlpha += carvedBand * 0.10;
    float alpha = shapedAlpha * vertexColor.a * ColorModulator.a;
    if (alpha <= 0.006) {
        discard;
    }
    fragColor = vec4(spectral, alpha);
}
