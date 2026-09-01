#version 330

uniform sampler2D SceneSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 SceneSize;
};

layout(std140) uniform GrailConfig {
    // strength, channel, release, aura
    vec4 Phase;
    // water color, elapsed seconds
    vec4 Water;
    // sacred highlight color, recharge envelope
    vec4 Gold;
    // echo darkness, sonar marker enabled, Horus strength, reserved
    vec4 Composite;
    // grade, reflection, bloom, reserved
    vec4 Style;
};

out vec4 fragColor;

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

float smoother(float value) {
    value = clamp(value, 0.0, 1.0);
    return value * value * value
        * (value * (value * 6.0 - 15.0) + 10.0);
}

// Two broad interference fields produce a nearly motionless reflection. There
// are no screen-space rings, borders or waterlines: this pattern only changes
// how light already present in the world is interpreted.
float stillWater(vec2 p, float time) {
    float drift = time * 0.075;
    float first = sin(p.x * 10.5 + drift)
        * sin(p.y * 8.0 - drift * 0.73);
    float second = sin((p.x + p.y) * 14.0 - drift * 0.46)
        * sin((p.x - p.y) * 6.5 + drift * 0.61);
    return clamp(0.5 + first * 0.28 + second * 0.16, 0.0, 1.0);
}

float brightResponse(vec3 sampleColor) {
    return smoother((luminance(sampleColor) - 0.42) / 0.48);
}

void main() {
    float strength = clamp(Phase.x, 0.0, 1.0);
    float channel = clamp(Phase.y, 0.0, 1.0);
    float release = clamp(Phase.z, 0.0, 1.0);
    float aura = clamp(Phase.w, 0.0, 1.0);
    float recharge = clamp(Gold.a, 0.0, 1.0);
    float time = Water.a;

    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 p = (texCoord - 0.5) * vec2(aspect, 1.0);
    float reflectionField = stillWater(p, time);

    float preRelease = 1.0 - smoother(release / 0.16);
    vec2 calmNormal = vec2(
        sin(p.y * 7.5 - time * 0.065),
        cos(p.x * 8.5 + time * 0.052));
    float refraction = strength
        * preRelease
        * (0.00008 + channel * 0.00024);
    vec2 sceneUv = clamp(
        texCoord + calmNormal * refraction,
        vec2(0.0),
        vec2(1.0));
    vec4 sceneSample = texture(SceneSampler, sceneUv);
    vec3 undarkenedScene = sceneSample.rgb;

    // Echo darkness remains a property of the world. Sacred light is added
    // afterwards and the protected sonar crest is restored at the end.
    float echoDarkening = clamp(Composite.x, 0.0, 1.0);
    vec3 scene = undarkenedScene * (1.0 - echoDarkening);
    float sceneLight = luminance(scene);
    float highlight = smoother((sceneLight - 0.30) / 0.58);

    // Channeling creates stillness rather than a painted overlay: saturated
    // midtones settle, cool shadows deepen, and existing highlights become
    // warm reflections. Black terrain remains black.
    float quietAmount = strength
        * Style.x
        * (0.20 + channel * preRelease * 0.42 + aura * 0.10);
    vec3 quietScene = mix(vec3(sceneLight), scene, 0.76);
    quietScene *= mix(
        vec3(0.88, 0.94, 0.985),
        vec3(1.025, 1.015, 0.985),
        highlight);
    vec3 color = mix(scene, quietScene, quietAmount);

    float sanctifiedReflection = reflectionField
        * reflectionField
        * highlight
        * strength
        * (0.16 + channel * preRelease * 0.52 + aura * 0.12);
    color += mix(Water.rgb, Gold.rgb, 0.64 + highlight * 0.24)
        * sanctifiedReflection
        * Style.y
        * 0.22;

    // A small highlight-only bloom is the culmination. It does not invent a
    // central beam or whiten the screen; luminous parts of the actual scene
    // reflect once, as if the world briefly shared the water in the vessel.
    vec2 pixel = 1.0 / max(SceneSize, vec2(1.0));
    vec2 bloomStep = pixel * (2.2 + release * 3.8);
    vec3 bloom = vec3(0.0);
    float bloomWeight = 0.0;
    for (int x = -1; x <= 1; ++x) {
        for (int y = -1; y <= 1; ++y) {
            vec3 tap = texture(
                SceneSampler,
                clamp(
                    sceneUv + vec2(float(x), float(y)) * bloomStep,
                    vec2(0.0),
                    vec2(1.0))).rgb;
            float tapBright = brightResponse(tap);
            float weight = (x == 0 && y == 0) ? 0.72 : 0.41;
            bloom += tap * tapBright * weight;
            bloomWeight += weight;
        }
    }
    bloom /= max(bloomWeight, 0.001);
    float releaseBloom = smoother(min(1.0, release * 5.0))
        * (1.0 - smoother(max(0.0, (release - 0.48) / 0.52)));
    color += mix(Water.rgb, Gold.rgb, 0.72)
        * luminance(bloom)
        * releaseBloom
        * Style.z
        * 0.54;
    color += bloom
        * releaseBloom
        * Style.z
        * 0.24;

    // Recharging is intentionally quiet on screen. The hand-bound reflection
    // carries the readable action; the scene merely lets highlights answer it.
    color += Gold.rgb
        * highlight
        * recharge
        * (0.010 + reflectionField * 0.012);

    // Horus is interpreted inside this chain when both relics overlap.
    float horus = clamp(Composite.z, 0.0, 1.0);
    float interpretedLight = luminance(color);
    vec3 horusGrade = mix(vec3(interpretedLight), color, 0.62)
        * vec3(1.045, 0.95, 0.80);
    color = mix(color, horusGrade, horus * 0.24);
    float edgeResponse = smoothstep(
        0.055,
        0.30,
        length(dFdx(color)) + length(dFdy(color)));
    color += vec3(1.00, 0.62, 0.16)
        * edgeResponse
        * horus
        * 0.05;

    // Preserve the sonar crest independently from screen darkening.
    const float waveMarkerBase = 8.0 / 255.0;
    const float waveMarkerScale = 64.0 / 255.0;
    float sonarWave = clamp(
        (sceneSample.a - waveMarkerBase) / waveMarkerScale,
        0.0,
        1.0);
    float markerRange = step(
        waveMarkerBase - 0.006,
        sceneSample.a) * (1.0 - step(
            waveMarkerBase + waveMarkerScale + 0.006,
            sceneSample.a));
    sonarWave *= markerRange * step(0.5, Composite.y);
    color = mix(color, undarkenedScene, sonarWave * echoDarkening);

    float darkProtection = 1.0 - smoother((sceneLight - 0.02) / 0.18);
    color *= 1.0 - darkProtection * strength * 0.025;
    fragColor = vec4(max(color, vec3(0.0)), 1.0);
}
