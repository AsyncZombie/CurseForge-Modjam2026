#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;
uniform sampler2D CrackSampler;

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
    vec2 sourceSize = vec2(textureSize(Sampler0, 0));
    vec2 crackUv = texCoord0 * sourceSize / 16.0;
    vec4 crack = texture(CrackSampler, crackUv);
    float fissure = crack.a * smoothstep(0.10, 0.78, 1.0 - dot(
        crack.rgb,
        vec3(0.2126, 0.7152, 0.0722)));
    if (fissure < 0.025) {
        discard;
    }
    vec3 color = vec3(0.10, 0.13, 0.09)
        * max(lightMapColor.rgb, vec3(0.08))
        * faceVertexColor.rgb;
    vec4 result = vec4(
        color,
        fissure * faceVertexColor.a * ColorModulator.a);
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
