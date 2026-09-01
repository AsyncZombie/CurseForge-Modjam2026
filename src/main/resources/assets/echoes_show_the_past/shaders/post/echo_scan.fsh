#version 330

#moj_import <minecraft:globals.glsl>
#moj_import <minecraft:projection.glsl>

uniform sampler2D SceneSampler;
uniform sampler2D WorldDepthSampler;
uniform sampler2D ForegroundDepthSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 SceneSize;
    vec2 WorldDepthSize;
    vec2 ForegroundDepthSize;
};

layout(std140) uniform EchoConfig {
    vec4 TintAndStrength;
    // exposure, edge glow, distortion, screen-darkening stage
    vec4 GradeSettings;
    mat4 InverseLevelProjection;
    // wave count, depth convention (0 = -1..1, 1 = 0..1), reserved
    vec4 LowFrequencyMeta;
    // xyz is the origin in camera view space; w is the current radius.
    vec4 LowFrequencyOrigins[16];
    // width, returning flag, intensity, active flag.
    vec4 LowFrequencyStyles[16];
    // RGB is the response color; alpha is the optional Past Echo handoff start.
    vec4 LowFrequencyColors[16];
    // xyz aim direction in view space; w is cos(halfAngle) or >= 1.5 for omni.
    vec4 LowFrequencyAim;
};

out vec4 fragColor;

vec3 reconstructViewPosition(vec2 uv, float depth) {
    float clipDepth = mix(depth * 2.0 - 1.0, depth, LowFrequencyMeta.y);
    vec4 clip = vec4(uv * 2.0 - 1.0, clipDepth, 1.0);
    vec4 view = InverseLevelProjection * clip;
    return view.xyz / max(abs(view.w), 0.00001);
}

float gaussian(float value, float width) {
    float normalized = value / width;
    return exp(-0.5 * normalized * normalized);
}

float filteredGaussian(float value, float width, float filterVariance) {
    // Convolving with half a fragment footprint keeps a narrow world-space
    // filament continuous after it becomes smaller than one screen pixel. Use
    // variance directly to avoid a square root for every pulse component.
    float variance = max(width * width + filterVariance, 0.00001);
    return exp(-0.5 * value * value / variance);
}

float lowFrequencyPulse(float delta, bool returning, float filterVariance) {
    if (delta > 0.92 || delta < -1.82) {
        return 0.0;
    }
    float precursor = filteredGaussian(
        delta - 0.38, 0.30, filterVariance) * (returning ? 0.045 : 0.060);
    float corona = filteredGaussian(
        delta - 0.12, 0.19, filterVariance) * (returning ? 0.18 : 0.14);
    float crest = filteredGaussian(
        delta, returning ? 0.12 : 0.135, filterVariance) * (returning ? 0.70 : 0.62);
    float filament = filteredGaussian(
        delta + (returning ? 0.075 : 0.095),
        returning ? 0.042 : 0.050,
        filterVariance) * (returning ? 0.34 : 0.28);
    float echoLayer = filteredGaussian(
        delta + (returning ? 0.34 : 0.42),
        returning ? 0.15 : 0.18,
        filterVariance) * (returning ? 0.34 : 0.22);
    float wake = delta < -0.18
        ? exp((delta + 0.18) * 1.55)
            * smoothstep(0.0, 1.0, clamp((-delta - 0.18) / 0.34, 0.0, 1.0))
            * (returning ? 0.055 : 0.035)
        : 0.0;
    return clamp(precursor + corona + crest + filament + echoLayer + wake, 0.0, 0.92);
}

vec3 lowFrequencyEmission(
        vec3 viewPosition,
        float worldDepth) {
    // Preserved world depth supplies the surface reconstructed behind the
    // first-person layer. ForegroundDepth is applied once to the final
    // emission so it cannot distort the wave's physical radius.
    if (worldDepth >= 0.9999) {
        return vec3(0.0);
    }
    vec3 emission = vec3(0.0);
    int waveCount = int(clamp(LowFrequencyMeta.x, 0.0, 16.0));
    for (int index = 0; index < 16; index++) {
        if (index >= waveCount || LowFrequencyStyles[index].w < 0.5) {
            continue;
        }
        vec4 originAndRadius = LowFrequencyOrigins[index];
        vec4 style = LowFrequencyStyles[index];
        float width = max(style.x, 1.0);
        bool returning = style.y > 0.5;
        float surfaceRadius = length(viewPosition - originAndRadius.xyz);
        float radialDelta = surfaceRadius - originAndRadius.w;
        float delta = (returning ? -radialDelta : radialDelta) / width;
        float pixelFootprint = min(fwidth(delta), 1.5);
        float filterVariance = pixelFootprint * pixelFootprint * 0.25;
        float pulse = lowFrequencyPulse(delta, returning, filterVariance) * style.z;
        float screenHandoffStart = LowFrequencyColors[index].a;
        float screenWeight = screenHandoffStart <= 0.001
            ? 1.0
            : smoothstep(screenHandoffStart, screenHandoffStart + 3.0, surfaceRadius);
        pulse *= screenWeight;
        if (!returning && LowFrequencyAim.w <= 1.01) {
            vec3 fromOrigin = viewPosition - originAndRadius.xyz;
            float lenSq = dot(fromOrigin, fromOrigin);
            if (lenSq > 1.0e-6) {
                float align = dot(fromOrigin * inversesqrt(lenSq), normalize(LowFrequencyAim.xyz));
                float feather = max(0.035, (1.0 - LowFrequencyAim.w) * 0.08);
                pulse *= smoothstep(LowFrequencyAim.w - feather, LowFrequencyAim.w + feather * 0.35, align);
            }
        }
        float whiteHeat = filteredGaussian(
            delta,
            returning ? 0.13 : 0.15,
            filterVariance);
        vec3 signatureColor = LowFrequencyColors[index].rgb;
        vec3 pulseColor = mix(
            signatureColor,
            vec3(0.85, 0.99, 1.0),
            clamp(whiteHeat * 0.78, 0.0, 1.0));
        emission += pulseColor * pulse;
    }
    return min(emission, vec3(1.6));
}

float noise(vec2 position) {
    vec3 p = fract(vec3(position.xyx) * 0.1031);
    p += dot(p, p.yzx + 33.33);
    return fract((p.x + p.y) * p.z);
}

void main() {
    vec3 tint = TintAndStrength.rgb;
    float strength = TintAndStrength.a;
    float exposure = GradeSettings.x;
    float edgeGlowStrength = GradeSettings.y;
    float distortion = GradeSettings.z;
    float effectAmount = clamp(GradeSettings.w, 0.0, 1.0);
    const float grain = 0.009;
    vec2 pixel = 1.0 / WorldDepthSize;
    float worldDepth = texture(WorldDepthSampler, texCoord).r;
    float foregroundDepth = texture(ForegroundDepthSampler, texCoord).r;
    // GameRenderer clears depth after the world and then draws first-person
    // hands/items. A clear pixel is therefore visible world; any written
    // depth is foreground that must remain in front of the crest.
    float foregroundVisibility = step(0.9999, foregroundDepth);
    float depthRight = texture(WorldDepthSampler, texCoord + vec2(pixel.x, 0.0)).r;
    float depthLeft = texture(WorldDepthSampler, texCoord - vec2(pixel.x, 0.0)).r;
    float depthUp = texture(WorldDepthSampler, texCoord + vec2(0.0, pixel.y)).r;
    float depthDown = texture(WorldDepthSampler, texCoord - vec2(0.0, pixel.y)).r;
    float dx = abs(worldDepth - depthRight) + abs(worldDepth - depthLeft);
    float dy = abs(worldDepth - depthUp) + abs(worldDepth - depthDown);
    vec3 viewPosition = reconstructViewPosition(texCoord, worldDepth);
    float distanceScale = clamp(abs(viewPosition.z) * 0.000012, 0.00010, 0.0018);
    float edge = smoothstep(distanceScale, distanceScale * 7.0, dx + dy);
    float worldSurface = 1.0 - step(0.9999, worldDepth);
    float enclosedWorldEdge = worldSurface
        * (1.0 - step(0.9999, depthRight))
        * (1.0 - step(0.9999, depthLeft))
        * (1.0 - step(0.9999, depthUp))
        * (1.0 - step(0.9999, depthDown));
    edge *= enclosedWorldEdge;

    vec2 centered = texCoord - 0.5;
    vec2 radial = centered / max(length(centered), 0.001);
    float lensShift = distortion * effectAmount
        * smoothstep(0.30, 0.72, length(centered))
        * 0.00075;
    vec2 sceneUv = clamp(texCoord + radial * lensShift, vec2(0.0), vec2(1.0));
    vec4 sceneSample = texture(SceneSampler, sceneUv);
    vec3 scene = sceneSample.rgb;
    vec4 undistortedSample = texture(SceneSampler, texCoord);

    // Darkness is deliberately uniform. Sunlight and bright blocks no longer
    // receive a protected exposure curve, so a full-strength grade reaches black.
    vec3 gradedScene = scene * (1.0 - effectAmount) * exposure;

    // The alpha-only wave pass reserves values 8..72/255 and encodes the real
    // pulse opacity inside that interval. LowFrequencyMeta.z is an explicit
    // lifetime gate: weather and cloud alpha can never become a false pulse
    // while only the low-frequency listening grade is active.
    const float waveMarkerBase = 8.0 / 255.0;
    const float waveMarkerScale = 64.0 / 255.0;
    float encodedWave = clamp(
        (undistortedSample.a - waveMarkerBase) / waveMarkerScale,
        0.0,
        1.0);
    float markerRange = step(waveMarkerBase - 0.006, undistortedSample.a)
        * (1.0 - step(waveMarkerBase + waveMarkerScale + 0.006, undistortedSample.a));
    float surfaceWaveGate = step(0.5, LowFrequencyMeta.z);
    // Rain and clouds may write alpha values inside the marker range. The
    // preserved pre-weather depth tells us whether this pixel belongs to real
    // world geometry, so sky/weather pixels can never become false crests.
    float waveAmount = encodedWave * markerRange * surfaceWaveGate * worldSurface;
    waveAmount *= foregroundVisibility;

    // Depth only adds a quiet sense of space; the world-space crest remains the visual focus.
    float distanceHaze = smoothstep(22.0, 78.0, abs(viewPosition.z))
        * effectAmount
        * worldSurface;
    gradedScene *= 1.0 - distanceHaze * 0.05;
    float edgeGlow = edge
        * edgeGlowStrength
        * strength
        * 0.035
        * effectAmount
        * (1.0 - effectAmount);

    float vignette = smoothstep(0.42, 0.84, length(centered));
    gradedScene *= 1.0 - vignette * 0.055 * effectAmount;
    float fineGrain = (noise(gl_FragCoord.xy) - 0.5)
        * grain
        * 0.15
        * effectAmount
        * (1.0 - effectAmount)
        * worldSurface;
    vec3 waveEmissionColor = mix(
        vec3(0.40, 0.78, 0.90),
        tint,
        smoothstep(0.28, 0.82, waveAmount));
    vec3 composedScene = gradedScene
        + tint * edgeGlow
        + vec3(fineGrain)
        + waveEmissionColor * waveAmount * effectAmount
        + lowFrequencyEmission(viewPosition, worldDepth) * foregroundVisibility;
    fragColor = vec4(
        max(composedScene, vec3(0.0)),
        1.0);
}
