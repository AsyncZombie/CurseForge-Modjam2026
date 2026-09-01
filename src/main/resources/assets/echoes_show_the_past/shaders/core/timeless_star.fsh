#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec2 texCoord0;
in vec3 starPosition;
in vec4 vertexColor;

out vec4 fragColor;

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float radiusSquared = dot(p, p);
    float core = exp(-radiusSquared * 12.0);
    float halo = exp(-radiusSquared * 3.8) * 0.18;
    float shape = (core + halo) * (1.0 - smoothstep(0.72, 1.0, sqrt(radiusSquared)));
    if (shape <= 0.006) {
        discard;
    }

    float seed = hash31(floor(starPosition * 0.34));
    float layer = clamp(TextureMat[0][1], 0.0, 1.0);
    float twinkle = 0.84 + 0.16 * sin(
            TextureMat[3][0] * mix(0.25, 0.48, layer) * (0.78 + seed * 0.28)
                    + seed * 31.0);
    float starBrightness = clamp(TextureMat[2][3], 0.0, 1.0);
    float shaderExposure = clamp(TextureMat[3][3], 0.45, 1.0);
    vec3 memoryWhite = vec3(0.96, 0.95, 0.90);
    vec3 paleGold = vec3(1.0, 0.73, 0.22);
    vec3 color = mix(memoryWhite, paleGold, vertexColor.b * 0.38)
            * vertexColor.rgb * ColorModulator.rgb;
    fragColor = vec4(color,
            shape * vertexColor.a * ColorModulator.a * twinkle * starBrightness * shaderExposure);
}
