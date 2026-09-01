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

// Matches philosophers_stone.fsh spectral accents.
const vec3 ECHO_COLD = vec3(0.350, 0.790, 0.920);
const vec3 MEMORY_WHITE = vec3(1.000, 0.965, 0.825);
const vec3 ALCHEMICAL_GOLD = vec3(1.000, 0.585, 0.145);
const vec3 STONE_HEART = vec3(0.720, 0.075, 0.235);

// Soft spectral ring / ribbon for Echo Altar orbit FX.
// UV: x along circumference/length, y across thickness (0 inner → 1 outer).
void main() {
    float along = texCoord0.x;
    float across = clamp(texCoord0.y, 0.0, 1.0);

    float ridge = 1.0 - abs(across * 2.0 - 1.0);
    float body = smoothstep(0.08, 0.42, ridge);
    float core = pow(max(ridge, 0.0), 2.4);
    float rim = smoothstep(0.55, 0.95, 1.0 - ridge);

    float flow = 0.5 + 0.5 * sin(along * 31.415926);
    float bevel = smoothstep(0.78, 0.98, flow) * body;
    float shade = mix(0.55, 1.0, core) * mix(0.82, 1.08, bevel);
    float depthShade = mix(0.62, 1.12, smoothstep(0.0, 1.0, across));
    float shape = max(body * 0.72, rim * 0.38) + core * 0.55 + bevel * 0.22;
    shape *= depthShade;

    vec3 tint = vertexColor.rgb * ColorModulator.rgb;
    // Pull vertex tints toward the Stone's alchemical grade.
    vec3 goldGrade = mix(tint, ALCHEMICAL_GOLD, 0.55);
    vec3 hot = mix(goldGrade, MEMORY_WHITE, clamp(core * 0.72 + bevel * 0.35, 0.0, 1.0));
    vec3 cool = mix(goldGrade, ECHO_COLD, 0.28) * 0.82;
    vec3 heart = mix(hot, STONE_HEART, rim * 0.18);
    vec3 spectral = mix(cool, heart, clamp(core + rim * 0.45, 0.0, 1.0)) * shade;

    float alpha = shape * vertexColor.a * ColorModulator.a;
    if (alpha <= 0.008) {
        discard;
    }
    fragColor = vec4(spectral, alpha);
}
