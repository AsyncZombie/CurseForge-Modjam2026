#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>

uniform sampler2D Sampler0;

#ifdef DISSOLVE
uniform sampler2D DissolveMaskSampler;
#endif

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif

#ifndef EMISSIVE
in vec4 lightMapColor;
#endif

#ifndef NO_OVERLAY
in vec4 overlayColor;
#endif

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 textureColor = texture(Sampler0, texCoord0);
    float paintedCoverage = textureColor.a;

#ifdef GHOST_PLANE
    // Thin models use the same 0.5 cutout boundary as vanilla terrain. The
    // texture decides where geometry exists; surviving texels become full
    // material coverage so the block-condition opacity is applied only once.
    if (paintedCoverage < 0.5) {
        discard;
    }
    textureColor.a = 1.0;
#else
    vec4 silhouetteColor = textureLod(Sampler0, texCoord0, 2.0);
    float ghostCoverage = max(paintedCoverage, silhouetteColor.a * 0.22);
    if (ghostCoverage <= 0.004) {
        discard;
    }
    float paintedWeight = smoothstep(0.015, 0.20, paintedCoverage);
    textureColor.rgb = mix(silhouetteColor.rgb, textureColor.rgb, paintedWeight);
    textureColor.a = ghostCoverage;
#endif

#ifdef PER_FACE_LIGHTING
    vec4 faceVertexColor = gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack;
#else
    vec4 faceVertexColor = vertexColor;
#endif

#ifdef DISSOLVE
    if (faceVertexColor.a < texture(DissolveMaskSampler, texCoord0).a) {
        discard;
    }
    faceVertexColor.a = 1.0;
#endif

    textureColor *= faceVertexColor * ColorModulator;
#ifndef NO_OVERLAY
    textureColor.rgb = mix(overlayColor.rgb, textureColor.rgb, overlayColor.a);
#endif
#ifndef EMISSIVE
    textureColor *= lightMapColor;
#endif

    fragColor = apply_fog(
        textureColor,
        sphericalVertexDistance,
        cylindricalVertexDistance,
        FogEnvironmentalStart,
        FogEnvironmentalEnd,
        FogRenderDistanceStart,
        FogRenderDistanceEnd,
        FogColor);
}
