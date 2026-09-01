#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

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
    vec3 local = fract(p);
    local = local * local * (3.0 - 2.0 * local);
    float n000 = hash31(cell + vec3(0.0, 0.0, 0.0));
    float n100 = hash31(cell + vec3(1.0, 0.0, 0.0));
    float n010 = hash31(cell + vec3(0.0, 1.0, 0.0));
    float n110 = hash31(cell + vec3(1.0, 1.0, 0.0));
    float n001 = hash31(cell + vec3(0.0, 0.0, 1.0));
    float n101 = hash31(cell + vec3(1.0, 0.0, 1.0));
    float n011 = hash31(cell + vec3(0.0, 1.0, 1.0));
    float n111 = hash31(cell + vec3(1.0, 1.0, 1.0));
    return mix(
            mix(mix(n000, n100, local.x), mix(n010, n110, local.x), local.y),
            mix(mix(n001, n101, local.x), mix(n011, n111, local.x), local.y),
            local.z);
}

float fbm(vec3 p) {
    float value = 0.0;
    float amplitude = 0.52;
    for (int octave = 0; octave < 3; octave++) {
        value += smoothNoise(p) * amplitude;
        p = p * 2.03 + vec3(7.7, 3.1, 5.4);
        amplitude *= 0.49;
    }
    return value;
}

float memoryVeil(vec3 direction, float time) {
    float warp = fbm(direction * 3.15 + vec3(time * 0.011, -time * 0.007, time * 0.005));
    float first = direction.y * 1.32
            + sin(direction.x * 3.4 + direction.z * 2.1 + time * 0.018) * 0.21
            + (warp - 0.48) * 0.68;
    float second = direction.y * 1.18 - 0.31
            + sin(direction.z * 3.0 - direction.x * 1.7 - time * 0.013) * 0.17
            - (warp - 0.52) * 0.47;
    return exp(-first * first * 10.5) * 0.68
            + exp(-second * second * 15.0) * 0.32;
}

void main() {
    vec3 direction = normalize(localDirection);
    vec3 weights = pow(abs(direction), vec3(4.0));
    weights /= max(weights.x + weights.y + weights.z, 0.0001);

    float time = TextureMat[3][0];
    float instability = clamp(TextureMat[3][1], 0.0, 1.0);
    float goldStrength = clamp(TextureMat[3][2], 0.0, 1.0);
    float veilStrength = clamp(TextureMat[0][3], 0.0, 1.0);
    float horizonStrength = clamp(TextureMat[1][3], 0.0, 1.0);
    float shaderExposure = clamp(TextureMat[3][3], 0.45, 1.0);
    vec2 driftA = vec2(sin(time * 0.008), cos(time * 0.006)) * 0.022;
    vec2 driftB = vec2(cos(time * 0.005), -sin(time * 0.007)) * 0.018;

    // Both scales stay inside the texture, removing wrap seams as well as cube seams.
    vec3 lowX = texture(Sampler0, direction.zy * 0.34 + 0.5 + driftA).rgb;
    vec3 lowY = texture(Sampler0, direction.xz * 0.34 + 0.5 + driftB).rgb;
    vec3 lowZ = texture(Sampler0, direction.xy * 0.34 + 0.5 - driftA).rgb;
    vec3 detailX = texture(Sampler0, direction.yz * 0.43 + 0.5 - driftB).rgb;
    vec3 detailY = texture(Sampler0, direction.zx * 0.43 + 0.5 - driftA).rgb;
    vec3 detailZ = texture(Sampler0, direction.xy * 0.43 + 0.5 + driftB).rgb;
    vec3 lowCloud = lowX * weights.x + lowY * weights.y + lowZ * weights.z;
    vec3 detailCloud = detailX * weights.x + detailY * weights.y + detailZ * weights.z;
    vec3 memoryCloud = mix(lowCloud, detailCloud, 0.34);
    float luminance = dot(memoryCloud, vec3(0.22, 0.70, 0.08));

    vec3 deepVoid = vec3(0.0025, 0.0035, 0.014);
    vec3 epochTint = max(ColorModulator.rgb, vec3(0.001));
    float turbulence = fbm(direction * 4.6 + vec3(time * 0.004, 1.7, -time * 0.003));
    vec3 cloudColor = memoryCloud * (0.61 + epochTint * 1.18);
    vec3 color = mix(deepVoid, cloudColor, 0.63 + turbulence * 0.08);
    color += epochTint * smoothstep(0.035, 0.45, luminance) * 0.064;

    float warmMemory = smoothstep(0.26, 0.72, memoryCloud.r - memoryCloud.b * 0.18);
    vec3 antiqueGold = vec3(1.0, 0.61, 0.13);
    color += antiqueGold * warmMemory * goldStrength * 0.105;

    float veil = memoryVeil(direction, time) * veilStrength;
    float horizon = pow(max(0.0, 1.0 - abs(direction.y)), 4.2) * horizonStrength;
    vec3 memoryWhite = vec3(0.94, 0.95, 1.0);
    color += mix(memoryWhite, antiqueGold, goldStrength * 0.62)
            * veil * (0.007 + instability * turbulence * 0.010);
    color += mix(epochTint, antiqueGold, goldStrength * 0.42)
            * horizon * 0.024;

    // A broad, non-geometric bloom embeds the eclipse in the same sky volume.
    vec3 eclipseDirection = normalize(vec3(-0.57, 0.56, -0.60));
    float eclipseAlignment = max(dot(direction, eclipseDirection), 0.0);
    float distantBloom = pow(eclipseAlignment, 18.0) * 0.055
            + pow(eclipseAlignment, 64.0) * 0.12;
    color += mix(memoryWhite, antiqueGold, 0.48 + goldStrength * 0.30)
            * distantBloom * (0.62 + veilStrength * 0.38);

    // Ruins breathe and churn instead of drawing straight fracture decals.
    float ruinPulse = 0.88 + 0.12 * sin(time * 0.19 + turbulence * 6.28318);
    color *= mix(1.0, ruinPulse, instability * 0.28);

    color *= mix(0.88, 1.0, shaderExposure);
    fragColor = vec4(color * vertexColor.rgb, vertexColor.a * ColorModulator.a);
}
