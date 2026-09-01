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

float hash21(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

float smoothNoise(vec2 p) {
    vec2 cell = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(
            mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), f.x),
            mix(hash21(cell + vec2(0.0, 1.0)), hash21(cell + vec2(1.0, 1.0)), f.x),
            f.y);
}

float fbm(vec2 p) {
    float value = 0.0;
    float amplitude = 0.54;
    for (int octave = 0; octave < 5; octave++) {
        value += smoothNoise(p) * amplitude;
        p = mat2(1.57, -1.12, 1.12, 1.57) * p + vec2(3.9, 6.1);
        amplitude *= 0.47;
    }
    return value;
}

// Periodic angular coordinates prevent a visible seam through the corona.
float coronalNoise(vec2 radialDirection, float radius, float time) {
    vec2 drift = vec2(sin(time * 0.017), cos(time * 0.013)) * 0.17;
    float broad = fbm(radialDirection * 2.7 + drift + radius * radialDirection * 1.4);
    float filament = fbm(radialDirection * 7.9 - drift.yx
            + radius * radialDirection.yx * 4.2);
    return mix(broad, filament, 0.31);
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float radius = length(p);
    if (radius > 1.0) {
        discard;
    }

    vec2 radialDirection = radius > 0.0001 ? p / radius : vec2(0.0, 1.0);
    float time = TextureMat[3][0];
    float instability = clamp(TextureMat[3][1], 0.0, 1.0);
    float shaderExposure = clamp(TextureMat[3][3], 0.45, 1.0);
    float broad = coronalNoise(radialDirection, radius, time);
    float fine = coronalNoise(
            mat2(0.74, -0.67, 0.67, 0.74) * radialDirection,
            radius * 1.31,
            -time * 0.61);

    const float solarEdge = 0.158;
    float outside = max(radius - solarEdge, 0.0);
    float innerHalo = exp(-outside * 8.8)
            * smoothstep(solarEdge - 0.018, solarEdge + 0.020, radius);
    float plumeReach = 0.19 + broad * 0.34 + pow(fine, 2.7) * 0.25;
    float broadPlumes = (1.0 - smoothstep(plumeReach, plumeReach + 0.13, outside))
            * smoothstep(solarEdge + 0.012, solarEdge + 0.075, radius);
    float hairline = smoothstep(0.66, 0.91, fine)
            * exp(-outside * mix(3.7, 2.7, broad))
            * smoothstep(solarEdge + 0.025, solarEdge + 0.12, radius);
    float lensRing = exp(-abs(radius - 0.335) * 43.0)
            * mix(0.25, 0.58, broad)
            * 0.24;
    float outerFade = 1.0 - smoothstep(0.67, 1.0, radius);

    vec3 ivory = vec3(1.0, 0.98, 0.89);
    vec3 royalGold = vec3(1.0, 0.50, 0.035) * ColorModulator.rgb;
    vec3 deepAmber = vec3(0.68, 0.12, 0.004) * ColorModulator.rgb;
    vec3 color = mix(deepAmber, royalGold, clamp(broadPlumes + innerHalo * 0.52, 0.0, 1.0));
    color = mix(color, ivory, innerHalo * 0.70);
    color += royalGold * hairline * 0.46 + ivory * lensRing * 0.22;

    float energy = (innerHalo * 0.31
            + broadPlumes * 0.13
            + hairline * (0.12 + instability * 0.035)
            + lensRing * 0.10) * outerFade;
    energy *= shaderExposure * vertexColor.a * ColorModulator.a;
    if (energy <= 0.002) {
        discard;
    }
    fragColor = vec4(color * vertexColor.rgb, energy);
}
