#version 330

uniform sampler2D SceneSampler;
uniform sampler2D WorldDepthSampler;

in vec2 texCoord;

layout(std140) uniform SamplerInfo {
    vec2 OutSize;
    vec2 SceneSize;
    vec2 WorldDepthSize;
};

layout(std140) uniform PhilosophersStoneConfig {
    // strength, block front, +1 materialize / -1 restore / 0 stable past, time
    vec4 Phase;
    // centre in view space and authored span
    vec4 ViewCenter;
    // world basis in view space and volume half-extents
    vec4 ViewAxisX;
    vec4 ViewAxisY;
    vec4 ViewAxisZ;
    // retained transition direction and depth convention
    vec4 Sweep;
    // Echo darkness, protected sonar marker, Horus, Medusa
    vec4 Composite;
    // Grail, Grail release, normalized progress, distortion scale
    vec4 Relics;
    // Medusa channel, impact, cancel and elapsed seconds
    vec4 MedusaPhase;
    mat4 InverseProjection;
};

out vec4 fragColor;

const vec3 ECHO_COLD = vec3(0.350, 0.790, 0.920);
const vec3 MEMORY_WHITE = vec3(1.000, 0.965, 0.825);
const vec3 ALCHEMICAL_GOLD = vec3(1.000, 0.585, 0.145);
const vec3 STONE_HEART = vec3(0.720, 0.075, 0.235);
/*
 * The anomaly follows the authored X/Z footprint instead of enclosing it in
 * a bulbous lens. Its sides are mathematically vertical; only the horizontal
 * corners are rounded. The margin keeps the curtain outside block faces while
 * the capped corner radius guarantees that every authored corner stays inside.
 */
const float CURTAIN_MARGIN = 0.65;
const float CURTAIN_CORNER_FACTOR = 0.18;
const float CURTAIN_MIN_CORNER = 0.45;
const float CURTAIN_MAX_CORNER = 2.0;

float luminance(vec3 color) {
    return dot(color, vec3(0.2126, 0.7152, 0.0722));
}

float smoother(float value) {
    value = clamp(value, 0.0, 1.0);
    return value * value * value
        * (value * (value * 6.0 - 15.0) + 10.0);
}

float gaussian(float value, float width) {
    float normalized = value / max(width, 0.0001);
    return exp(-0.5 * normalized * normalized);
}

float medusaHash21(vec2 point) {
    point = fract(point * vec2(123.34, 456.21));
    point += dot(point, point + 45.32);
    return fract(point.x * point.y);
}

float medusaNoise(vec2 point) {
    vec2 cell = floor(point);
    vec2 localPoint = fract(point);
    localPoint = localPoint * localPoint
        * (3.0 - 2.0 * localPoint);
    return mix(
        mix(
            medusaHash21(cell),
            medusaHash21(cell + vec2(1.0, 0.0)),
            localPoint.x),
        mix(
            medusaHash21(cell + vec2(0.0, 1.0)),
            medusaHash21(cell + 1.0),
            localPoint.x),
        localPoint.y);
}

float hash31(vec3 value) {
    value = fract(value * 0.1031);
    value += dot(value, value.yzx + 33.33);
    return fract((value.x + value.y) * value.z);
}

float filamentRidge(float value, float sharpness) {
    return pow(
        clamp(1.0 - abs(sin(value)), 0.0, 1.0),
        sharpness);
}

vec2 safeNormalize(vec2 value) {
    float magnitude = length(value);
    return magnitude > 0.00001
        ? value / magnitude
        : vec2(1.0, 0.0);
}

vec3 reconstructViewPosition(vec2 uv, float depth) {
    float clipDepth = mix(depth * 2.0 - 1.0, depth, Sweep.w);
    vec4 clip = vec4(uv * 2.0 - 1.0, clipDepth, 1.0);
    vec4 view = InverseProjection * clip;
    return view.xyz / max(abs(view.w), 0.00001);
}

float roundedFootprintDistance(
        vec2 point,
        vec2 outerHalf,
        float cornerRadius) {
    vec2 corner = abs(point)
        - outerHalf
        + vec2(cornerRadius);
    return min(max(corner.x, corner.y), 0.0)
        + length(max(corner, vec2(0.0)))
        - cornerRadius;
}

/*
 * The X/Z intersection is analytic: four straight faces and four circular
 * corners. Besides making the boundary exact at any distance, this is cheaper
 * than marching a ray through the old enclosing superellipsoid.
 */
float roundedFootprintIntersection(
        vec2 rayOrigin,
        vec2 rayDirection,
        vec2 outerHalf,
        float cornerRadius,
        out vec2 localHit,
        out vec2 localNormal) {
    float bestDistance = 1.0e20;
    vec2 bestNormal = vec2(0.0);
    vec2 straightHalf = max(
        outerHalf - vec2(cornerRadius),
        vec2(0.0));

    if (abs(rayDirection.x) > 0.000001) {
        for (int side = 0; side < 2; side++) {
            float sideSign = side == 0 ? -1.0 : 1.0;
            float candidate = (
                sideSign * outerHalf.x - rayOrigin.x)
                / rayDirection.x;
            float hitZ = rayOrigin.y
                + rayDirection.y * candidate;
            if (candidate > 0.001
                    && candidate < bestDistance
                    && abs(hitZ) <= straightHalf.y + 0.001) {
                bestDistance = candidate;
                bestNormal = vec2(sideSign, 0.0);
            }
        }
    }

    if (abs(rayDirection.y) > 0.000001) {
        for (int side = 0; side < 2; side++) {
            float sideSign = side == 0 ? -1.0 : 1.0;
            float candidate = (
                sideSign * outerHalf.y - rayOrigin.y)
                / rayDirection.y;
            float hitX = rayOrigin.x
                + rayDirection.x * candidate;
            if (candidate > 0.001
                    && candidate < bestDistance
                    && abs(hitX) <= straightHalf.x + 0.001) {
                bestDistance = candidate;
                bestNormal = vec2(0.0, sideSign);
            }
        }
    }

    float rayLengthSquared = dot(
        rayDirection,
        rayDirection);
    if (rayLengthSquared > 0.000001) {
        for (int corner = 0; corner < 4; corner++) {
            float signX = corner < 2 ? -1.0 : 1.0;
            float signZ =
                (corner == 0 || corner == 2)
                ? -1.0
                : 1.0;
            vec2 center = vec2(
                signX * straightHalf.x,
                signZ * straightHalf.y);
            vec2 relative = rayOrigin - center;
            float projected = dot(
                relative,
                rayDirection);
            float discriminant =
                projected * projected
                - rayLengthSquared
                    * (dot(relative, relative)
                        - cornerRadius * cornerRadius);
            if (discriminant >= 0.0) {
                float root = sqrt(discriminant);
                for (int crossing = 0;
                        crossing < 2;
                        crossing++) {
                    float rootSign =
                        crossing == 0 ? -1.0 : 1.0;
                    float candidate = (
                        -projected + rootSign * root)
                        / rayLengthSquared;
                    vec2 candidateHit = rayOrigin
                        + rayDirection * candidate;
                    vec2 cornerVector =
                        candidateHit - center;
                    bool belongsToCorner =
                        signX * cornerVector.x >= -0.001
                        && signZ * cornerVector.y >= -0.001;
                    if (candidate > 0.001
                            && candidate < bestDistance
                            && belongsToCorner) {
                        bestDistance = candidate;
                        bestNormal = cornerVector
                            / cornerRadius;
                    }
                }
            }
        }
    }

    if (bestDistance >= 1.0e19) {
        localHit = vec2(0.0);
        localNormal = vec2(0.0);
        return -1.0;
    }
    localHit = rayOrigin
        + rayDirection * bestDistance;
    localNormal = normalize(bestNormal);
    return bestDistance;
}

/*
 * A rounded vertical prism gives the memory four calm, nearly planar sides.
 * Its top and bottom are retained only as faint closure surfaces so looking
 * down from above cannot expose a hole in the anomaly.
 */
float chronalCurtainIntersection(
        vec3 rayOrigin,
        vec3 rayDirection,
        vec2 outerHalf,
        float verticalHalf,
        float cornerRadius,
        out vec3 localHit,
        out vec3 localNormal,
        out float curtainCap) {
    float bestDistance = 1.0e20;
    vec3 bestNormal = vec3(0.0);
    float bestCap = 0.0;

    vec2 sideHit;
    vec2 sideNormal;
    float sideDistance = roundedFootprintIntersection(
        rayOrigin.xz,
        rayDirection.xz,
        outerHalf,
        cornerRadius,
        sideHit,
        sideNormal);
    if (sideDistance > 0.001) {
        float sideY = rayOrigin.y
            + rayDirection.y * sideDistance;
        if (abs(sideY) <= verticalHalf + 0.001) {
            bestDistance = sideDistance;
            bestNormal = vec3(
                sideNormal.x,
                0.0,
                sideNormal.y);
        }
    }

    if (abs(rayDirection.y) > 0.000001) {
        for (int cap = 0; cap < 2; cap++) {
            float capSign = cap == 0 ? -1.0 : 1.0;
            float candidate = (
                capSign * verticalHalf - rayOrigin.y)
                / rayDirection.y;
            vec3 candidateHit = rayOrigin
                + rayDirection * candidate;
            if (candidate > 0.001
                    && candidate < bestDistance
                    && roundedFootprintDistance(
                            candidateHit.xz,
                            outerHalf,
                            cornerRadius) <= 0.001) {
                bestDistance = candidate;
                bestNormal = vec3(
                    0.0,
                    capSign,
                    0.0);
                bestCap = 1.0;
            }
        }
    }

    if (bestDistance >= 1.0e19) {
        localHit = vec3(0.0);
        localNormal = vec3(0.0, 1.0, 0.0);
        curtainCap = 0.0;
        return -1.0;
    }
    localHit = rayOrigin
        + rayDirection * bestDistance;
    localNormal = bestNormal;
    curtainCap = bestCap;
    return bestDistance;
}

/*
 * A distance-aware soft edge keeps the currents clean at range. Unlike the
 * discarded block-face border, they live in the refractive membrane itself.
 */
float horizonCurrent(
        float phase,
        float width,
        float antialiasWidth) {
    float wave = abs(sin(phase));
    return 1.0 - smoothstep(
        width,
        width + max(antialiasWidth, 0.018),
        wave);
}

vec3 preserveSonar(
        vec3 color,
        vec4 undistortedSample,
        float echoDarkening,
        float worldSurface) {
    const float waveMarkerBase = 8.0 / 255.0;
    const float waveMarkerScale = 64.0 / 255.0;
    float sonarWave = clamp(
        (undistortedSample.a - waveMarkerBase)
            / waveMarkerScale,
        0.0,
        1.0);
    float markerRange = step(
        waveMarkerBase - 0.006,
        undistortedSample.a) * (1.0 - step(
            waveMarkerBase + waveMarkerScale + 0.006,
            undistortedSample.a));
    sonarWave *= markerRange
        * step(0.5, Composite.y)
        * worldSurface;
    return mix(
        color,
        undistortedSample.rgb,
        sonarWave * echoDarkening);
}

void main() {
    float strength = clamp(Phase.x, 0.0, 1.0);
    float front = Phase.y;
    bool restoring = Phase.z < 0.0;
    bool stablePast = abs(Phase.z) < 0.5;
    float time = Phase.w;
    float progress = clamp(Relics.z, 0.0, 1.0);
    float distortionScale = clamp(Relics.w, 0.0, 2.0);

    float depth = texture(WorldDepthSampler, texCoord).r;
    float worldSurface = 1.0 - step(0.9999, depth);
    vec3 viewPosition = reconstructViewPosition(texCoord, depth);
    vec3 relative = viewPosition - ViewCenter.xyz;
    vec3 localBlocks = vec3(
        dot(relative, ViewAxisX.xyz),
        dot(relative, ViewAxisY.xyz),
        dot(relative, ViewAxisZ.xyz));
    vec3 halfExtents = max(
        vec3(ViewAxisX.w, ViewAxisY.w, ViewAxisZ.w),
        vec3(0.5));
    vec3 local = localBlocks / halfExtents;
    float boxDistance = max(
        abs(local.x),
        max(abs(local.y), abs(local.z)));
    float volume = (1.0 - smoothstep(1.0, 1.10, boxDistance))
        * worldSurface
        * smoothstep(0.65, 1.10, length(viewPosition));
    vec4 undistortedSample = texture(SceneSampler, texCoord);
    float echoDarkening = clamp(Composite.x, 0.0, 1.0);
    float externalRelics = max(
        max(Composite.z, Composite.w),
        max(Relics.x, Relics.y));

    /*
     * A vertical chronal curtain follows the remembered footprint. It remains
     * just outside every authored block, with restrained rounded corners rather
     * than swelling into the surrounding present as an enclosing lens.
     */
    float sceneDistance = max(length(viewPosition), 0.001);
    vec3 viewRay = viewPosition / sceneDistance;
    vec3 cameraRelative = -ViewCenter.xyz;
    vec3 cameraLocal = vec3(
        dot(cameraRelative, ViewAxisX.xyz),
        dot(cameraRelative, ViewAxisY.xyz),
        dot(cameraRelative, ViewAxisZ.xyz));
    vec3 rayLocal = vec3(
        dot(viewRay, ViewAxisX.xyz),
        dot(viewRay, ViewAxisY.xyz),
        dot(viewRay, ViewAxisZ.xyz));
    vec2 curtainOuterHalf = max(
        halfExtents.xz + vec2(CURTAIN_MARGIN),
        vec2(1.15));
    float curtainVerticalHalf = max(
        halfExtents.y + CURTAIN_MARGIN,
        1.15);
    float curtainCornerRadius = clamp(
        min(halfExtents.x, halfExtents.z)
            * CURTAIN_CORNER_FACTOR,
        CURTAIN_MIN_CORNER,
        CURTAIN_MAX_CORNER);
    vec3 horizonHitLocal;
    vec3 rawHorizonNormal;
    float curtainCap;
    float horizonDistance = chronalCurtainIntersection(
        cameraLocal,
        rayLocal,
        curtainOuterHalf,
        curtainVerticalHalf,
        curtainCornerRadius,
        horizonHitLocal,
        rawHorizonNormal,
        curtainCap);
    float persistentBoundary = stablePast ? 1.0 : 0.0;
    float horizonDepthVisibility = step(
        horizonDistance,
        sceneDistance + 0.02);
    /*
     * The chronal curtain is world geometry, not an x-ray overlay. Both its
     * travelling and stable forms must therefore lose to any nearer world
     * surface in the preserved depth buffer. The small bias only prevents
     * coplanar flicker where the curtain meets the authored perimeter.
     */
    float horizonOcclusion =
        step(0.001, horizonDistance)
        * horizonDepthVisibility;
    vec3 horizonNormal =
        ViewAxisX.xyz * rawHorizonNormal.x
            + ViewAxisY.xyz * rawHorizonNormal.y
            + ViewAxisZ.xyz * rawHorizonNormal.z;
    horizonNormal *= inversesqrt(max(
        dot(horizonNormal, horizonNormal),
        0.000001));
    float cameraCurtainField = max(
        roundedFootprintDistance(
            cameraLocal.xz,
            curtainOuterHalf,
            curtainCornerRadius),
        abs(cameraLocal.y) - curtainVerticalHalf);
    float cameraInsidePast = 1.0 - step(
        0.0,
        cameraCurtainField);
    float horizonFacing = clamp(
        abs(dot(horizonNormal, -viewRay)),
        0.0,
        1.0);
    float horizonFresnel = pow(
        1.0 - horizonFacing,
        1.75);
    float horizonRim = pow(
        1.0 - horizonFacing,
        4.2);

    /*
     * This envelope is the travelling transmutation front condensed into a
     * stable horizon. On restoration it is swallowed by that same front, so
     * the initial spell and the persistent anomaly are one continuous event.
     */
    float horizonEnvelope = stablePast
        ? 1.0
        : smoother((front - 0.74) / 0.24);
    float horizonEnergy = horizonEnvelope * max(
        strength,
        horizonEnvelope * 0.88);
    float chronalHorizon = horizonOcclusion
        * horizonEnergy;

    /*
     * Most screen pixels lie outside the authored memory. They only need the
     * base Echo grade, so avoid the four depth neighbours, four temporal
     * exposures, derivatives, hashes and trigonometry used by the crest.
     */
    if (volume <= 0.001
            && externalRelics <= 0.001
            && chronalHorizon <= 0.001) {
        vec3 outsideColor = undistortedSample.rgb
            * (1.0 - echoDarkening);
        outsideColor = preserveSonar(
            outsideColor,
            undistortedSample,
            echoDarkening,
            worldSurface);
        fragColor = vec4(
            max(outsideColor, vec3(0.0)),
            1.0);
        return;
    }

    /*
     * Only pixels belonging to the memory or its projected horizon pay for
     * the animated currents. This keeps the stable anomaly inexpensive even
     * when most of the screen looks away from it.
     */
    float horizonPixelWidth = clamp(
        horizonDistance
            / max(min(OutSize.x, OutSize.y), 1.0)
            * 1.8,
        0.022,
        0.14);
    float horizonPerimeterPhase =
        horizonHitLocal.x * 0.31
        - horizonHitLocal.z * 0.27;
    float horizonGold = horizonCurrent(
        horizonHitLocal.y * 0.47
            + horizonHitLocal.x * 0.24
            - horizonHitLocal.z * 0.20
            + sin(
                horizonPerimeterPhase * 0.72
                    + horizonHitLocal.y * 0.09
                    - time * 0.40) * 1.42
            - time * 0.61,
        0.075,
        horizonPixelWidth);
    float horizonCarmine = horizonCurrent(
        horizonHitLocal.y * 0.36
            + horizonHitLocal.z * 0.39
            - horizonHitLocal.x * 0.22
            + sin(
                horizonPerimeterPhase * 0.53
                    - horizonHitLocal.y * 0.12
                    + time * 0.34) * 1.28
            + time * 0.43
            + 1.73,
        0.062,
        horizonPixelWidth);
    float horizonCyan = horizonCurrent(
        horizonHitLocal.y * 0.58
            + horizonHitLocal.x * 0.17
            + horizonHitLocal.z * 0.29
            + sin(
                horizonPerimeterPhase * 0.41
                    + horizonHitLocal.y * 0.14
                    - time * 0.27) * 1.51
            - time * 0.37
            + 3.11,
        0.052,
        horizonPixelWidth);
    float horizonFilaments = max(
        horizonGold,
        max(
            horizonCarmine * 0.91,
            horizonCyan * 0.84));
    vec3 normalizedHorizon = vec3(
        horizonHitLocal.x / curtainOuterHalf.x,
        horizonHitLocal.y / curtainVerticalHalf,
        horizonHitLocal.z / curtainOuterHalf.y);
    float horizonBreath = 0.86
        + 0.14 * sin(
            time * 1.14
                + normalizedHorizon.y * 2.7
                + horizonPerimeterPhase * 0.19);
    horizonFilaments *= (
        0.34 + horizonFresnel * 0.66)
        * horizonBreath;
    float horizonTideA = 0.5
        + 0.5 * sin(
            normalizedHorizon.x * 5.1
                + normalizedHorizon.y * 4.3
                - normalizedHorizon.z * 3.7
                + sin(
                    normalizedHorizon.z * 3.2
                        - time * 0.24) * 1.15
                - time * 0.31);
    float horizonTideB = 0.5
        + 0.5 * sin(
            normalizedHorizon.z * 4.7
                - normalizedHorizon.x * 3.4
                + normalizedHorizon.y * 5.6
                + sin(
                    normalizedHorizon.x * 2.9
                        + time * 0.19) * 1.28
                + time * 0.23
                + 1.91);
    float horizonTide = smoother(
        (horizonTideA * horizonTideB - 0.13)
            / 0.58);
    float horizonConvergence = pow(
        max(
            min(horizonGold, horizonCarmine),
            max(
                min(horizonGold, horizonCyan),
                min(horizonCarmine, horizonCyan))),
        1.35);
    float horizonLock = stablePast
        ? 0.18
            + horizonTide * 0.08
            + horizonConvergence * 0.12
        : gaussian(front - 0.88, 0.085)
            * (0.30
                + horizonTide * 0.42
                + horizonConvergence * 0.55);
    float curtainSurfaceWeight = mix(
        1.0,
        mix(0.10, 0.28, persistentBoundary),
        curtainCap);
    float horizonPresence = clamp(
        mix(0.025, 0.13, persistentBoundary)
            + horizonFresnel * 0.64
            + horizonRim * 0.26
            + horizonFilaments * 0.62
            + horizonTide * 0.065
            + horizonLock * 0.21,
        0.0,
        1.0);
    chronalHorizon *= horizonPresence
        * curtainSurfaceWeight;
    /*
     * Preserve the already-composited alpha of remembered-air blocks. Only
     * sparse currents and grazing intersections may refract the scene; there
     * is deliberately no full-surface exposure that can paste opaque present
     * blocks into the temporal boundary.
     */
    float ghostPreservingRefraction = chronalHorizon
        * (1.0 - curtainCap * 0.95)
        * clamp(
            horizonFresnel * 0.48
                + horizonFilaments * 0.58
                + horizonConvergence * 0.62
                + horizonLock * 0.28,
            0.0,
            1.0);
    float horizonParallax = ghostPreservingRefraction
        * (0.16
            + horizonFresnel * 0.34
            + horizonConvergence * 0.31);

    vec2 depthPixel = 1.0 / max(WorldDepthSize, vec2(1.0));
    float depthRight = texture(
        WorldDepthSampler,
        clamp(texCoord + vec2(depthPixel.x, 0.0), 0.0, 1.0)).r;
    float depthLeft = texture(
        WorldDepthSampler,
        clamp(texCoord - vec2(depthPixel.x, 0.0), 0.0, 1.0)).r;
    float depthUp = texture(
        WorldDepthSampler,
        clamp(texCoord + vec2(0.0, depthPixel.y), 0.0, 1.0)).r;
    float depthDown = texture(
        WorldDepthSampler,
        clamp(texCoord - vec2(0.0, depthPixel.y), 0.0, 1.0)).r;
    vec2 depthGradient = vec2(
        depthRight - depthLeft,
        depthUp - depthDown);
    float depthEdge = smoothstep(
        0.00010,
        0.0075,
        length(depthGradient)) * worldSurface;

    /*
     * The physical swap remains quantized to block centres. The surrounding
     * optical crest uses the continuous surface coordinate, hiding that
     * discrete operation inside a smooth transmutation rather than exposing a
     * staircase of blocks.
     */
    vec3 latticeOffset = vec3(0.5) - fract(halfExtents);
    vec3 boundedBlocks = clamp(
        localBlocks,
        -halfExtents + vec3(0.001),
        halfExtents - vec3(0.001));
    vec3 blockRelativeCenter =
        floor(boundedBlocks - latticeOffset + vec3(0.5))
        + latticeOffset;
    float radialCoordinate =
        length(blockRelativeCenter / halfExtents)
        / 1.7320508;
    float surfaceCoordinate =
        length(local) / 1.7320508;
    float temporalDelta = radialCoordinate - front;
    float surfaceDelta = surfaceCoordinate - front;
    float signedDelta = surfaceDelta
        * (restoring ? -1.0 : 1.0);
    /*
     * Timeline identity is independent of travel direction: surfaces behind
     * the radial front belong to materialized history, while those ahead are
     * still present reality. Both sides meet in one narrow optical boundary.
     */
    float travellingPast = smoother(
        (front - surfaceCoordinate + 0.040) / 0.080) * volume;
    float timelinePast = stablePast
        ? volume
        : travellingPast;
    float timelinePresent = stablePast
        ? 0.0
        : max(0.0, volume - timelinePast);
    float travellingBoundary = stablePast
        ? 0.0
        : gaussian(surfaceDelta, 0.048) * volume;

    float invocation = stablePast
        ? 0.0
        : smoother(progress / 0.13)
            * (1.0 - smoother((progress - 0.24) / 0.16));
    float resolution = stablePast
        ? 0.0
        : smoother((progress - 0.78) / 0.11)
            * (1.0 - smoother((progress - 0.965) / 0.035));

    // Two interwoven, world-anchored ridges read as alchemical current rather
    // than a screen pattern. They are visible only on real depth surfaces.
    float filamentA = filamentRidge(
        localBlocks.x * 1.87
            + localBlocks.y * 1.19
            - localBlocks.z * 1.43
            + sin(
                localBlocks.z * 0.71
                    + localBlocks.y * 0.43
                    - time * 0.72) * 1.35,
        10.0);
    float filamentB = filamentRidge(
        localBlocks.z * 2.11
            - localBlocks.x * 1.07
            + localBlocks.y * 1.57
            + sin(
                localBlocks.x * 0.63
                    - localBlocks.y * 0.51
                    + time * 0.58) * 1.10,
        12.0);
    float filamentNetwork = max(
        filamentA,
        filamentB * 0.78);
    float filamentFlow = filamentNetwork
        * (0.64 + 0.36 * sin(
            localBlocks.x * 0.83
                + localBlocks.y * 0.67
                + localBlocks.z * 0.91
                - time * 3.8));
    // Warm energy gathers in front of the crest, the exact white fixation
    // covers the block swap, and the remembered timeline trails in cold light.
    float temporalSeam = stablePast
        ? 0.0
        : max(
                gaussian(temporalDelta, 0.021) * 0.92,
                gaussian(surfaceDelta, 0.032))
            * volume;
    float anticipationField = stablePast
        ? 0.0
        : gaussian(
            signedDelta - 0.092,
            0.060) * volume;
    float goldHarmonic = stablePast
        ? 0.0
        : gaussian(
            signedDelta - 0.047,
            0.021) * volume;
    float innerHarmonic = stablePast
        ? 0.0
        : gaussian(
            signedDelta + 0.052,
            0.026) * volume;
    float memoryWake = stablePast
        ? 0.0
        : gaussian(
            signedDelta + 0.165,
            0.115) * volume;
    float settlingField = memoryWake
        * (0.58 + filamentFlow * 0.42);
    float refractionField = clamp(
        temporalSeam
            + anticipationField * 0.42
            + innerHarmonic * 0.24
            + ghostPreservingRefraction
                * (0.26 + horizonFilaments * 0.22),
        0.0,
        1.0);

    vec3 derivativeX = dFdx(viewPosition);
    vec3 derivativeY = dFdy(viewPosition);
    vec3 normalVector = cross(derivativeX, derivativeY);
    vec3 viewNormal = normalVector / sqrt(max(
        dot(normalVector, normalVector),
        0.00001));
    vec2 surfaceTangent = safeNormalize(vec2(
        dot(viewNormal, ViewAxisY.xyz)
            - dot(viewNormal, ViewAxisZ.xyz),
        dot(viewNormal, ViewAxisX.xyz)
            + dot(viewNormal, ViewAxisZ.xyz)));
    vec2 surfaceGradient = safeNormalize(
        depthGradient * (31.0 + depthEdge * 58.0)
            + surfaceTangent * 0.20);
    float flowAngle =
        sin(
            localBlocks.x * 0.79
                + localBlocks.y * 1.07
                - localBlocks.z * 0.73
                + time * 0.61)
        * 0.47;
    mat2 flowRotation = mat2(
        cos(flowAngle),
        -sin(flowAngle),
        sin(flowAngle),
        cos(flowAngle));
    vec2 gradientFlow = safeNormalize(
        flowRotation * surfaceGradient);

    vec3 sparkleCell = floor(localBlocks * 3.0);
    float sparkleSeed = hash31(sparkleCell);
    float sparkleClock = fract(
        time * 0.54 + sparkleSeed);
    float sparkleLife = gaussian(
        sparkleClock - 0.16,
        0.055);
    float sparkleShape = smoothstep(
        0.935,
        0.997,
        hash31(sparkleCell + vec3(17.0, 5.0, 29.0)));
    float alchemicalSparks = sparkleShape
        * sparkleLife
        * clamp(
            settlingField * 1.45
                + invocation * filamentNetwork * 0.32
                + resolution * filamentNetwork * 0.26,
            0.0,
            1.0);

    /*
     * Five nearby exposures create an actual temporal shear. The central
     * exposure stays legible while the outer pair and RGB split bloom only in
     * the crest, preventing the whole scene from becoming a generic blur.
     */
    float surfacePulse =
        0.86
        + 0.14 * sin(
            time * 9.2
                + localBlocks.x * 1.51
                + localBlocks.y * 1.23
                + localBlocks.z * 1.67);
    float distortionAmount = strength
        * distortionScale
        * (temporalSeam * 0.043
            + goldHarmonic * 0.018
            + anticipationField * 0.009
            + invocation * filamentNetwork * volume * 0.0025)
        * surfacePulse;
    float horizonFlowAngle =
        sin(
            horizonHitLocal.x * 0.21
                - horizonHitLocal.y * 0.17
                + horizonHitLocal.z * 0.26
                - time * 0.31)
        * 0.52;
    mat2 horizonRotation = mat2(
        cos(horizonFlowAngle),
        -sin(horizonFlowAngle),
        sin(horizonFlowAngle),
        cos(horizonFlowAngle));
    vec2 horizonFlow = safeNormalize(
        horizonRotation
            * vec2(
                horizonNormal.x,
                -horizonNormal.y))
        * mix(1.0, -1.0, cameraInsidePast);
    float horizonPulse = 0.88
        + 0.12 * sin(
            time * 2.35
                + horizonHitLocal.x * 0.29
                + horizonHitLocal.y * 0.17
                - horizonHitLocal.z * 0.23);
    float horizonRefraction = ghostPreservingRefraction
        * distortionScale
        * (0.0028
            + horizonFresnel * 0.0090
            + horizonFilaments * 0.0048
            + horizonConvergence * 0.0036
            + horizonLock * 0.0032
            + horizonParallax * 0.0024)
        * horizonPulse;
    vec2 temporalOffset =
        gradientFlow * distortionAmount
        + horizonFlow * horizonRefraction;
    vec2 prismDirection = vec2(
        -gradientFlow.y,
        gradientFlow.x);
    vec2 horizonPrismDirection = vec2(
        -horizonFlow.y,
        horizonFlow.x);
    vec2 chromaticOffset = prismDirection
        * temporalSeam
        * strength
        * distortionScale
        * 0.0042
        + horizonPrismDirection
            * ghostPreservingRefraction
            * (horizonFresnel * 0.0028
                + horizonFilaments * 0.0012
                + horizonConvergence * 0.0018
                + horizonLock * 0.0012)
            * distortionScale;

    vec3 stableExposure = undistortedSample.rgb;
    vec3 earlierExposure = texture(
        SceneSampler,
        clamp(
            texCoord - temporalOffset * 1.30 - chromaticOffset,
            0.0,
            1.0)).rgb;
    vec3 laterExposure = texture(
        SceneSampler,
        clamp(
            texCoord + temporalOffset * 1.18 + chromaticOffset,
            0.0,
            1.0)).rgb;
    vec3 farEarlierExposure = texture(
        SceneSampler,
        clamp(
            texCoord - temporalOffset * 1.92
                - chromaticOffset * 1.55,
            0.0,
            1.0)).rgb;
    vec3 farLaterExposure = texture(
        SceneSampler,
        clamp(
            texCoord + temporalOffset * 1.76
                + chromaticOffset * 1.55,
            0.0,
            1.0)).rgb;

    vec3 color = stableExposure * (1.0 - echoDarkening);
    vec3 earlierDark = earlierExposure * (1.0 - echoDarkening);
    vec3 laterDark = laterExposure * (1.0 - echoDarkening);
    vec3 farEarlierDark = farEarlierExposure
        * (1.0 - echoDarkening);
    vec3 farLaterDark = farLaterExposure
        * (1.0 - echoDarkening);

    float disagreement = clamp(
        abs(luminance(farEarlierDark)
            - luminance(farLaterDark)) * 2.55,
        0.0,
        1.0);
    vec3 temporalEcho = mix(
        mix(earlierDark, farEarlierDark, 0.38),
        mix(laterDark, farLaterDark, 0.38),
        restoring ? 0.66 : 0.34);
    float temporalOpticalStrength = max(
        strength,
        chronalHorizon);
    float exposureBlend = refractionField
        * temporalOpticalStrength
        * (0.34 + disagreement * 0.32);
    color = mix(color, temporalEcho, exposureBlend);

    vec3 prismaticExposure = vec3(
        farEarlierDark.r,
        mix(earlierDark.g, laterDark.g, 0.5),
        farLaterDark.b);
    color = mix(
        color,
        prismaticExposure,
        temporalSeam * strength * 0.28
            + horizonParallax * 0.11);

    /*
     * History has a restrained mineral-cyan shadow response with warm
     * alchemical highlights; the unresolved present remains subtly warmer.
     * This makes the two realities readable without placing a flat coloured
     * wall between them or recolouring the authored block textures.
     */
    float timelineLuma = luminance(color);
    float historicalTexture = 0.5 + 0.5 * sin(
        localBlocks.x * 0.71
            - localBlocks.y * 0.93
            + localBlocks.z * 0.83
            + hash31(floor(localBlocks * 1.5)) * 3.14159);
    vec3 historicalGrade = mix(
        vec3(timelineLuma) * vec3(0.72, 0.86, 0.91),
        color,
        0.67);
    historicalGrade += mix(
            ECHO_COLD,
            ALCHEMICAL_GOLD,
            0.18 + historicalTexture * 0.16)
        * smoother((timelineLuma - 0.22) / 0.68)
        * 0.045;
    color = mix(
        color,
        historicalGrade,
        timelinePast * strength * 0.34);
    vec3 presentGrade = color * vec3(1.018, 0.992, 0.964);
    color = mix(
        color,
        presentGrade,
        timelinePresent * strength * 0.075);

    // The invocation briefly quiets only the authored surfaces so the gold
    // current reads clearly without dimming the sky or framing the screen.
    float ritualHush = invocation
        * volume
        * (0.055 + filamentNetwork * 0.035);
    vec3 hushed = mix(
        vec3(luminance(color)),
        color,
        0.62) * vec3(0.96, 0.91, 0.86);
    color = mix(color, hushed, ritualHush);
    color *= 1.0 - strength * (
        anticipationField * 0.10
            + innerHarmonic * 0.065);

    float invocationVeins = invocation
        * filamentFlow
        * volume;
    float resolutionVeins = resolution
        * filamentFlow
        * volume;
    vec3 seamEmission =
        ALCHEMICAL_GOLD
            * invocationVeins
            * (0.23 + depthEdge * 0.13)
        + vec3(1.0, 0.77, 0.32)
            * anticipationField
            * (0.17 + filamentFlow * 0.16)
        + ALCHEMICAL_GOLD
            * goldHarmonic
            * (0.43 + filamentFlow * 0.20)
        + MEMORY_WHITE
            * temporalSeam
            * (0.88 + depthEdge * 0.34)
        + STONE_HEART
            * innerHarmonic
            * (0.29 + disagreement * 0.24)
        + ECHO_COLD
            * settlingField
            * (0.13 + depthEdge * 0.055)
        + mix(ALCHEMICAL_GOLD, MEMORY_WHITE, 0.58)
            * alchemicalSparks
            * 0.92
        + mix(ECHO_COLD, ALCHEMICAL_GOLD, 0.47)
            * resolutionVeins
            * 0.16;
    seamEmission *= strength;
    float horizonSceneResponse = mix(
        0.52,
        1.0,
        worldSurface);
    vec3 horizonGoldColor = mix(
        ALCHEMICAL_GOLD,
        ECHO_COLD,
        cameraInsidePast * 0.66);
    vec3 horizonCarmineColor = mix(
        STONE_HEART,
        MEMORY_WHITE,
        cameraInsidePast * 0.43);
    vec3 horizonCyanColor = mix(
        ECHO_COLD,
        MEMORY_WHITE,
        cameraInsidePast * 0.38);
    vec3 horizonTideColor = mix(
        STONE_HEART,
        ECHO_COLD,
        0.35 + cameraInsidePast * 0.48);
    vec3 horizonEmission =
        (
            horizonGoldColor
                * horizonGold
                * 0.50
            + horizonCarmineColor
                * horizonCarmine
                * 0.42
            + horizonCyanColor
                * horizonCyan
                * 0.37
            + horizonTideColor
                * horizonTide
                * (0.035
                    + horizonFresnel * 0.040)
            + MEMORY_WHITE
                * horizonRim
                * (0.040
                    + horizonFilaments * 0.055)
            + mix(
                    ALCHEMICAL_GOLD,
                    MEMORY_WHITE,
                    0.68)
                * horizonConvergence
                * (0.24
                    + horizonLock * 0.34)
            + mix(
                    STONE_HEART,
                    MEMORY_WHITE,
                    0.52)
                * horizonLock
                * (0.08
                    + horizonTide * 0.10)
        )
        * chronalHorizon
        * horizonSceneResponse;
    seamEmission += horizonEmission;
    vec3 bounded = clamp(color, 0.0, 1.0);
    color = 1.0
        - (1.0 - bounded) * exp(-seamEmission);

    // Only one world post chain can be active. Established relic grades are
    // composed here instead of being overwritten by the Stone.
    float horus = clamp(Composite.z, 0.0, 1.0);
    float horusEdge = smoothstep(
        0.055,
        0.30,
        length(dFdx(color)) + length(dFdy(color)));
    vec3 horusGrade = mix(
        vec3(luminance(color)),
        color,
        0.61) * vec3(1.045, 0.95, 0.80);
    color = mix(color, horusGrade, horus * 0.24);
    color += vec3(1.00, 0.62, 0.16)
        * horusEdge
        * horus
        * 0.052;

    /*
     * The Stone owns the post chain, but Medusa retains her actual visual
     * language. Phase data crosses the ownership boundary so these fractures,
     * living coils, pupil and impact keep moving on the same gaze clock.
     */
    float medusa = clamp(Composite.w, 0.0, 1.0);
    if (medusa > 0.001) {
        float medusaChannel = clamp(MedusaPhase.x, 0.0, 1.0);
        float medusaImpact = clamp(MedusaPhase.y, 0.0, 1.0);
        float medusaCancel = clamp(MedusaPhase.z, 0.0, 1.0);
        float medusaTime = MedusaPhase.w;
        float medusaAspect = OutSize.x / max(OutSize.y, 1.0);
        vec2 medusaPoint = (texCoord - 0.5)
            * vec2(medusaAspect, 1.0);
        float medusaRadius = length(medusaPoint);
        float medusaLight = luminance(color);

        float medusaReach = smoother(medusaChannel * 1.18);
        float medusaThreshold = mix(1.02, 0.14, medusaReach);
        float medusaMineralMask = smoothstep(
            medusaThreshold - 0.10,
            medusaThreshold + 0.12,
            medusaRadius) * medusa;
        vec3 medusaMuted = mix(
            vec3(medusaLight),
            color,
            0.27);
        vec3 medusaMineral = medusaMuted
            * mix(
                vec3(0.88, 0.90, 0.84),
                vec3(0.73, 0.67, 0.47),
                0.34)
            * mix(0.78, 1.03, smoother(medusaLight));
        color = mix(
            color,
            medusaMineral,
            medusaMineralMask * 0.78);

        float medusaField = medusaNoise(
            medusaPoint * 8.5
                + vec2(
                    medusaTime * 0.10,
                    -medusaTime * 0.07));
        float medusaFractureA = abs(sin(
            medusaPoint.x * 21.0
                + medusaPoint.y * 14.0
                + medusaField * 7.0
                + sin(medusaPoint.y * 31.0) * 0.42));
        float medusaFractureB = abs(sin(
            medusaPoint.y * 25.0
                - medusaPoint.x * 10.0
                + medusaField * 5.0));
        float medusaFracture = smoothstep(
            0.965,
            0.997,
            max(medusaFractureA, medusaFractureB));
        medusaFracture *= smoothstep(
                0.16,
                0.92,
                medusaRadius)
            * smoothstep(0.18, 0.72, medusaChannel)
            * medusa
            * (0.60 + medusaField * 0.40);
        color = mix(
            color,
            vec3(0.31, 0.56, 0.37) * 0.44,
            medusaFracture * 0.46);
        color += vec3(0.73, 0.67, 0.47)
            * medusaFracture
            * 0.10;

        float medusaSideDistance = abs(
            abs(medusaPoint.x)
                - (0.62 + 0.025 * sin(medusaTime * 0.9)));
        float medusaSnakePath = abs(
            medusaPoint.y
                - sin(
                    medusaPoint.x * 12.0
                        + medusaTime * 1.45) * 0.055
                - sin(
                    medusaPoint.x * 27.0
                        - medusaTime * 0.75) * 0.013);
        float medusaCoils = gaussian(
                medusaSideDistance,
                0.009)
            * gaussian(medusaSnakePath, 0.013)
            * (1.0 - smoothstep(
                0.79,
                1.05,
                abs(medusaPoint.x)));
        float medusaDetailEnvelope = medusa
            * (1.0 - smoother(medusaCancel) * 0.55);
        color += vec3(0.31, 0.56, 0.37)
            * medusaCoils
            * medusaDetailEnvelope
            * (0.09 + medusaChannel * 0.18);

        float medusaImpactRadius = medusaImpact * 1.16;
        float medusaShock = gaussian(
            medusaRadius - medusaImpactRadius,
            0.016 + medusaImpact * 0.010);
        float medusaInnerStone = 1.0 - smoothstep(
            medusaImpactRadius - 0.055,
            medusaImpactRadius + 0.035,
            medusaRadius);
        float medusaImpactEnvelope = smoother(
            1.0 - medusaImpact);
        vec3 medusaImpactGrade = vec3(medusaLight)
            * vec3(0.73, 0.67, 0.47)
            * 0.86;
        color = mix(
            color,
            medusaImpactGrade,
            medusaInnerStone
                * medusaImpactEnvelope
                * medusa
                * 0.46);
        color += mix(
                vec3(0.31, 0.56, 0.37),
                vec3(0.73, 0.67, 0.47),
                0.56)
            * medusaShock
            * medusaImpactEnvelope
            * medusa
            * 0.48;

        float medusaPupil = gaussian(medusaPoint.x, 0.0046)
            * (1.0 - smoothstep(
                0.045,
                0.23,
                abs(medusaPoint.y)));
        float medusaPupilEnvelope = medusa
            * smoothstep(0.42, 0.94, medusaChannel)
            * (1.0 - smoothstep(0.02, 0.32, medusaImpact));
        color += vec3(0.73, 0.67, 0.47)
            * medusaPupil
            * medusaPupilEnvelope
            * 0.20;

        float medusaVignette = smoothstep(
            0.38,
            1.00,
            medusaRadius);
        color *= 1.0 - medusaVignette * medusa * 0.25;
        color += vec3(0.31, 0.56, 0.37)
            * depthEdge
            * medusa
            * 0.045;
    }

    float grail = clamp(Relics.x, 0.0, 1.0);
    float grailRelease = clamp(Relics.y, 0.0, 1.0);
    float highlight = smoother(
        (luminance(color) - 0.34) / 0.52);
    float sacredReflection = smoothstep(
        0.62,
        0.97,
        sin(
            local.x * 8.0
                + sin(local.z * 11.0 - time * 0.45)))
        * volume;
    color += mix(
            vec3(0.29, 0.78, 0.91),
            vec3(1.00, 0.86, 0.49),
            0.62 + grailRelease * 0.18)
        * sacredReflection
        * highlight
        * grail
        * 0.060;

    // Sonar remains independent from darkness and temporal refraction.
    color = preserveSonar(
        color,
        undistortedSample,
        echoDarkening,
        worldSurface);

    fragColor = vec4(max(color, vec3(0.0)), 1.0);
}
