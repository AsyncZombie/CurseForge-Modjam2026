#version 330

layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};

uniform sampler2D Sampler0;

in vec2 texCoord0;
in vec4 vertexColor;

out vec4 fragColor;

void main() {
    vec4 sigil = texture(Sampler0, texCoord0);
    float alpha = sigil.a * vertexColor.a * ColorModulator.a;
    if (sigil.a <= 0.025 || alpha <= 0.008) {
        discard;
    }

    float luminance = dot(sigil.rgb, vec3(0.2126, 0.7152, 0.0722));
    float whiteHeat = smoothstep(0.68, 0.98, luminance);
    float amberDepth = smoothstep(0.08, 0.58, luminance);
    vec3 deepAmber = vec3(0.62, 0.17, 0.0);
    vec3 raGold = vec3(1.0, 0.52, 0.0);
    vec3 sunWhite = vec3(1.0, 0.97, 0.78);
    vec3 metal = mix(deepAmber, raGold, amberDepth);
    metal = mix(metal, sunWhite, whiteHeat * 0.88);
    metal *= vertexColor.rgb * ColorModulator.rgb;

    // Preserve a dense carved core while additive passes receive a controlled rim.
    float carvedCore = smoothstep(0.16, 0.82, sigil.a);
    alpha *= mix(0.68, 1.0, carvedCore);
    fragColor = vec4(metal, alpha);
}
