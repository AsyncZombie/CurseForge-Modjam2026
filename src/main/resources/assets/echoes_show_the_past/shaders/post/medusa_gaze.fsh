#version 330

uniform sampler2D SceneSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 SceneSize;
};

layout(std140) uniform MedusaConfig {
    // strength, channel progress, impact progress, cancel progress
    vec4 Phase;
    // venom color, elapsed seconds
    vec4 Venom;
    vec4 Stone;
    vec4 Reserved;
    // strength, release progress, elapsed seconds, reserved
    vec4 Grail;
};

out vec4 fragColor;

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

float smoothCurve(float value) {
    value = clamp(value, 0.0, 1.0);
    return value * value * (3.0 - 2.0 * value);
}

float gaussian(float value, float width) {
    float normalized = value / max(width, 0.0001);
    return exp(-normalized * normalized);
}

float hash21(vec2 point) {
    point = fract(point * vec2(123.34, 456.21));
    point += dot(point, point + 45.32);
    return fract(point.x * point.y);
}

float noise21(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);
    return mix(
        mix(hash21(cell), hash21(cell + vec2(1.0, 0.0)), local.x),
        mix(hash21(cell + vec2(0.0, 1.0)), hash21(cell + 1.0), local.x),
        local.y);
}

void main() {
    float strength = clamp(Phase.x, 0.0, 1.0);
    float channel = clamp(Phase.y, 0.0, 1.0);
    float impact = clamp(Phase.z, 0.0, 1.0);
    float time = Venom.a;
    float aspect = OutSize.x / max(OutSize.y, 1.0);
    vec2 p = (texCoord - 0.5) * vec2(aspect, 1.0);
    float radius = length(p);

    // The scene barely bends along two opposing, serpentine currents. The
    // displacement grows during the channel but never disconnects the gaze
    // from what the player is aiming at.
    float coilA = sin(p.y * 17.0 + time * 2.2 + sin(p.x * 8.0) * 1.3);
    float coilB = sin(p.x * 13.0 - time * 1.7 + sin(p.y * 11.0));
    vec2 distortion = vec2(coilA, coilB)
        * (0.00055 + channel * 0.00090)
        * strength
        * smoothstep(0.12, 0.92, radius);
    vec4 sceneSample = texture(SceneSampler, clamp(texCoord + distortion, 0.0, 1.0));
    vec3 undarkenedScene = sceneSample.rgb;
    float echoDarkening = clamp(Reserved.y, 0.0, 1.0);
    vec3 scene = undarkenedScene * (1.0 - echoDarkening);
    float light = luminance(scene);

    // Stone enters from the periphery while the centre remains readable. Skin,
    // terrain and UI colour relationships survive beneath the mineral grade.
    float edge = smoothstep(0.16, 0.92, radius);
    float mineralReach = smoothCurve(channel * 1.18);
    float mineralThreshold = mix(1.02, 0.14, mineralReach);
    float mineralMask = smoothstep(
        mineralThreshold - 0.10,
        mineralThreshold + 0.12,
        radius) * strength;
    vec3 muted = mix(vec3(light), scene, 0.27);
    vec3 stoneGrade = muted * mix(vec3(0.88, 0.90, 0.84), Stone.rgb, 0.34);
    stoneGrade *= mix(0.78, 1.03, smoothCurve(light));
    vec3 color = mix(scene, stoneGrade, mineralMask * 0.78);

    // Fine mineral fractures grow inwards. They are intentionally sparse and
    // uneven, avoiding a screen-wide generic noise overlay.
    float field = noise21(p * 8.5 + vec2(time * 0.10, -time * 0.07));
    float fractureA = abs(sin(
        p.x * 21.0
        + p.y * 14.0
        + field * 7.0
        + sin(p.y * 31.0) * 0.42));
    float fractureB = abs(sin(
        p.y * 25.0
        - p.x * 10.0
        + field * 5.0));
    float fracture = smoothstep(0.965, 0.997, max(fractureA, fractureB));
    fracture *= edge
        * smoothstep(0.18, 0.72, channel)
        * strength
        * (0.60 + field * 0.40);
    color = mix(color, Venom.rgb * 0.44, fracture * 0.46);
    color += Stone.rgb * fracture * 0.10;

    // Two living coils sit near the lateral edge of vision. Their movement is
    // slow enough to read as snakes without becoming an animated HUD border.
    float sideDistance = abs(abs(p.x) - (0.62 + 0.025 * sin(time * 0.9)));
    float snakePath = abs(p.y
        - sin(p.x * 12.0 + time * 1.45) * 0.055
        - sin(p.x * 27.0 - time * 0.75) * 0.013);
    float coils = gaussian(sideDistance, 0.013)
        * gaussian(snakePath, 0.018)
        * (1.0 - smoothstep(0.79, 1.05, abs(p.x)));
    color += Venom.rgb * coils * strength * (0.09 + channel * 0.18);

    // Completion sends a mineral shock from the point of aim. It is green-gold
    // rather than white, so the impact is forceful without flashing the player.
    float impactRadius = impact * 1.16;
    float shock = gaussian(radius - impactRadius, 0.022 + impact * 0.014);
    float innerStone = 1.0 - smoothstep(
        impactRadius - 0.055,
        impactRadius + 0.035,
        radius);
    float impactEnvelope = smoothCurve(1.0 - impact);
    vec3 impactGrade = vec3(light) * Stone.rgb * 0.86;
    color = mix(color, impactGrade, innerStone * impactEnvelope * 0.46);
    color += mix(Venom.rgb, Stone.rgb, 0.56)
        * shock
        * impactEnvelope
        * 0.48;

    // A restrained vertical pupil closes as the power discharges. It anchors
    // the effect to the crosshair without obscuring the target.
    float pupil = gaussian(p.x, 0.0065)
        * (1.0 - smoothstep(0.045, 0.23, abs(p.y)));
    float pupilEnvelope = strength
        * smoothstep(0.42, 0.94, channel)
        * (1.0 - smoothstep(0.02, 0.32, impact));
    color += Stone.rgb * pupil * pupilEnvelope * 0.20;

    float vignette = smoothstep(0.38, 1.00, radius);
    color *= 1.0 - vignette * strength * 0.25;
    color += Venom.rgb * edge * strength * 0.012;

    // Minecraft can run only one world post chain at a time. When the Eye of
    // Horus overlaps Medusa, its gold interpretation is therefore composed
    // here instead of replacing either effect. It grades the already-darkened
    // scene, so it cannot undo Echo darkness.
    float horus = clamp(Reserved.w, 0.0, 1.0);
    float interpretedLight = luminance(color);
    vec3 horusGrade = mix(vec3(interpretedLight), color, 0.58)
        * vec3(1.055, 0.94, 0.76);
    color = mix(color, horusGrade, horus * 0.28);
    float horusEdge = smoothstep(
        0.055,
        0.30,
        length(dFdx(color)) + length(dFdy(color)));
    color += vec3(1.00, 0.62, 0.16)
        * horusEdge
        * horus
        * 0.055;
    float horusLid = gaussian(
        abs(p.y) - 0.225 * max(0.0, 1.0 - pow(abs(p.x) / 0.92, 1.7)),
        0.008)
        * (1.0 - smoothstep(0.68, 1.02, abs(p.x)));
    color += vec3(1.00, 0.62, 0.16)
        * horusLid
        * horus
        * 0.055;

    // Medusa owns post priority while its gaze is active. The Grail is folded
    // into the mineral scene as moving water highlights, preserving both
    // identities instead of abruptly switching post chains.
    float grail = clamp(Grail.x, 0.0, 1.0);
    float grailRelease = clamp(Grail.y, 0.0, 1.0);
    float grailTime = Grail.z;
    float sacredCaustic = sin(
        p.x * 17.0
        + sin(p.y * 13.0 - grailTime * 0.8));
    sacredCaustic += sin(
        p.y * 19.0
        - sin(p.x * 9.0 + grailTime * 0.6));
    sacredCaustic = smoothstep(
        1.18,
        1.82,
        sacredCaustic);
    sacredCaustic *= 0.22 + smoothCurve(luminance(color)) * 0.78;
    color += mix(
            vec3(0.29, 0.78, 0.91),
            vec3(1.00, 0.86, 0.49),
            0.48)
        * sacredCaustic
        * grail
        * 0.075;
    float sacredColumn = gaussian(
        p.x,
        0.12 + grailRelease * 0.12)
        * (1.0 - smoothstep(0.28, 0.78, abs(p.y)));
    color += vec3(0.74, 0.91, 0.84)
        * sacredColumn
        * grail
        * (1.0 - smoothCurve(grailRelease))
        * 0.10;

    // The Echo grade and Medusa are composed, never mutually exclusive.
    // Scene darkness is retained while Medusa's mineral emissions stay
    // readable. Surface-wave pixels recover their pre-grade brightness so the
    // sonar crest remains independent from screenDarkening.
    const float waveMarkerBase = 8.0 / 255.0;
    const float waveMarkerScale = 64.0 / 255.0;
    float wave = clamp(
        (sceneSample.a - waveMarkerBase) / waveMarkerScale,
        0.0,
        1.0);
    float markerRange = step(waveMarkerBase - 0.006, sceneSample.a)
        * (1.0 - step(
            waveMarkerBase + waveMarkerScale + 0.006,
            sceneSample.a));
    wave *= markerRange * step(0.5, Reserved.z);
    color = mix(color, undarkenedScene, wave * echoDarkening);

    float contact = clamp(Reserved.x, 0.0, 1.0);
    float contactHalo = gaussian(radius - mix(0.025, 0.31, 1.0 - contact), 0.018);
    color += mix(Venom.rgb, Stone.rgb, 0.62)
        * contactHalo
        * contact
        * 0.32;

    fragColor = vec4(max(color, vec3(0.0)), 1.0);
}
