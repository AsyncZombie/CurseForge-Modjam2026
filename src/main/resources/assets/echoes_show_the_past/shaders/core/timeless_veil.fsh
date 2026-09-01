#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

in vec3 localDirection;
in vec4 vertexColor;

out vec4 fragColor;

float hash31(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.yzx + 31.32);
    return fract((p.x + p.y) * p.z);
}

float smoothNoise(vec3 p) {
    vec3 cell = floor(p);
    vec3 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = mix(hash31(cell), hash31(cell + vec3(1, 0, 0)), f.x);
    float b = mix(hash31(cell + vec3(0, 1, 0)), hash31(cell + vec3(1, 1, 0)), f.x);
    float c = mix(hash31(cell + vec3(0, 0, 1)), hash31(cell + vec3(1, 0, 1)), f.x);
    float d = mix(hash31(cell + vec3(0, 1, 1)), hash31(cell + vec3(1, 1, 1)), f.x);
    return mix(mix(a, b, f.y), mix(c, d, f.y), f.z);
}

float fbm(vec3 p) {
    float value = 0.0;
    float amplitude = 0.53;
    for (int i = 0; i < 3; i++) {
        value += smoothNoise(p) * amplitude;
        p = p * 2.06 + vec3(5.2, 7.3, 2.8);
        amplitude *= 0.48;
    }
    return value;
}

float memoryVeil(vec3 direction, float time, float offset) {
    float warp = fbm(direction * (3.0 + offset) + vec3(time * 0.010, offset, -time * 0.006));
    float field = direction.y * (1.14 + offset * 0.08)
            + sin(direction.x * (2.8 + offset) + direction.z * 1.9 + time * 0.014) * 0.22
            + (warp - 0.50) * 0.72 - offset * 0.12;
    return exp(-field * field * (10.0 + offset * 4.0));
}

void main() {
    float layer = clamp(TextureMat[0][1], 0.0, 1.0);
    vec3 cameraDrift = vec3(TextureMat[0][0], TextureMat[1][1], TextureMat[2][2]);
    vec3 direction = normalize(localDirection + cameraDrift * mix(0.18, 0.72, layer));
    float time = TextureMat[3][0];
    float instability = clamp(TextureMat[3][1], 0.0, 1.0);
    float goldStrength = clamp(TextureMat[3][2], 0.0, 1.0);
    float veilStrength = clamp(TextureMat[0][3], 0.0, 1.0);
    float horizonStrength = clamp(TextureMat[1][3], 0.0, 1.0);
    float shaderExposure = clamp(TextureMat[3][3], 0.45, 1.0);

    float layerTime = mix(time, -time * 0.81, layer);
    float veilA = memoryVeil(direction, layerTime, layer * 0.58);
    float veilB = memoryVeil(direction.zxy, -layerTime * 0.73, 0.72 + layer * 0.31)
            * mix(0.52, 0.34, layer);
    float granular = fbm(direction * mix(7.4, 10.2, layer)
            + vec3(-layerTime * 0.005, 2.4 + layer * 3.1, layerTime * 0.004));
    float veil = (veilA + veilB) * mix(0.64, 1.08, granular) * veilStrength;
    float horizon = pow(max(0.0, 1.0 - abs(direction.y + layer * 0.04)),
            mix(5.0, 7.2, layer)) * horizonStrength;

    vec3 eclipseDirection = normalize(vec3(-0.57, 0.56, -0.60));
    float alignment = max(dot(direction, eclipseDirection), 0.0);
    float eclipseAura = pow(alignment, 14.0) * 0.20 + pow(alignment, 48.0) * 0.28;

    vec3 ivory = vec3(0.93, 0.95, 1.0);
    vec3 royalGold = vec3(1.0, 0.56, 0.075);
    vec3 spectralViolet = vec3(0.20, 0.13, 0.46);
    vec3 veilColor = mix(ivory, royalGold, 0.26 + goldStrength * 0.50);
    veilColor = mix(veilColor, spectralViolet, (1.0 - granular) * 0.18);
    veilColor *= ColorModulator.rgb;

    float energy = veil * (0.052 + instability * 0.048)
            + horizon * 0.030
            + eclipseAura * (0.075 + instability * 0.025);
    energy *= mix(0.88, 0.57, layer)
            * shaderExposure
            * vertexColor.a
            * ColorModulator.a;
    if (energy <= 0.002) {
        discard;
    }
    fragColor = vec4(veilColor * energy, energy);
}
