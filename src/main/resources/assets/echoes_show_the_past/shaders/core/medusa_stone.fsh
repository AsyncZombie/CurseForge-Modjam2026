#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D StoneSampler;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif
in vec4 lightMapColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 source = texture(Sampler0, texCoord0);
#ifdef PER_FACE_LIGHTING
    vec4 faceVertexColor = gl_FrontFacing
        ? vertexPerFaceColorFront
        : vertexPerFaceColorBack;
#else
    vec4 faceVertexColor = vertexColor;
#endif
    if (source.a < 0.08 || faceVertexColor.a <= 0.002) {
        discard;
    }

    // Entity UVs are authored in texture pixels. Converting every sixteen
    // source pixels into one stone tile keeps a consistent texel density on
    // heads, limbs, bodies and equipment instead of recolouring mob pixels.
    vec2 sourceSize = vec2(textureSize(Sampler0, 0));
    vec2 stoneUv = fract(texCoord0 * sourceSize / 16.0);
    vec3 stone = texture(StoneSampler, stoneUv).rgb;
    float mineral = dot(stone, vec3(0.2126, 0.7152, 0.0722));
    // Keep Minecraft's stone material genuinely neutral. The former
    // limestone/moss recolour was the source of the green statues even after
    // the stone sampler itself had been fixed.
    vec3 carvedStone = mix(stone, vec3(mineral), 0.28);
    carvedStone *= faceVertexColor.rgb * max(lightMapColor.rgb, vec3(0.025));
    vec4 result = vec4(
        carvedStone,
        source.a * faceVertexColor.a * ColorModulator.a);
    fragColor = apply_fog(
        result,
        sphericalVertexDistance,
        cylindricalVertexDistance,
        FogEnvironmentalStart,
        FogEnvironmentalEnd,
        FogRenderDistanceStart,
        FogRenderDistanceEnd,
        FogColor);
}
