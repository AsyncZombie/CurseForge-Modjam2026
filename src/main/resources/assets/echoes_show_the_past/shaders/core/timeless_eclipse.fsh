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

float hash11(float p) {
    p = fract(p * 0.1031);
    p *= p + 33.33;
    p *= p + p;
    return fract(p);
}

void main() {
    vec2 p = texCoord0 * 2.0 - 1.0;
    float radius = length(p);
    if (radius > 0.68) {
        discard;
    }

    float angle = atan(p.y, p.x);
    float time = TextureMat[3][0];
    float instability = clamp(TextureMat[3][1], 0.0, 1.0);
    float shaderExposure = clamp(TextureMat[3][3], 0.45, 1.0);

    // The silhouette stays geometrically clean. Only the luminous solar edge breathes.
    float discRadius = 0.392;
    float disc = 1.0 - smoothstep(discRadius - 0.003, discRadius + 0.004, radius);
    float edgeDistance = abs(radius - discRadius);
    float hotRim = exp(-edgeDistance * 108.0)
            * smoothstep(discRadius - 0.006, discRadius + 0.014, radius);
    float photosphere = exp(-abs(radius - (discRadius + 0.016)) * 72.0)
            * smoothstep(discRadius, discRadius + 0.024, radius);

    // Sparse Baily-like beads make the ring feel solar without turning it into an icon.
    float beadNoise = hash11(floor((angle + 3.14159265) * 8.4));
    float beadBand = pow(max(sin(angle * 4.0 + 0.52), 0.0), 18.0)
            * mix(0.35, 1.0, beadNoise);
    float beads = beadBand * exp(-edgeDistance * 155.0)
            * (0.62 + instability * 0.18);
    float diamond = pow(max(cos(angle - 0.74), 0.0), 46.0)
            * exp(-edgeDistance * 118.0)
            * (0.78 + 0.07 * sin(time * 0.43));

    // A restrained gravitational arc belongs to the lens, not to the black disc.
    float lensArc = exp(-abs(radius - 0.505) * 76.0)
            * smoothstep(-0.92, 0.42, cos(angle + 0.26))
            * 0.16;

    vec3 deepDisc = vec3(0.00018, 0.00024, 0.00105);
    vec3 innerDisc = vec3(0.0022, 0.0020, 0.0048);
    vec3 ivory = vec3(1.0, 0.985, 0.91);
    vec3 royalGold = vec3(1.0, 0.53, 0.045) * ColorModulator.rgb;
    vec3 amber = vec3(0.92, 0.22, 0.008) * ColorModulator.rgb;
    vec3 discColor = mix(innerDisc, deepDisc, smoothstep(0.0, discRadius, radius));
    vec3 lightColor = mix(amber, royalGold, hotRim);
    lightColor = mix(lightColor, ivory, clamp(photosphere + diamond, 0.0, 1.0));
    vec3 color = mix(lightColor, discColor, disc);
    color += ivory * diamond * 0.55 + royalGold * (beads * 0.26 + lensArc);

    float lightAlpha = (hotRim * 0.82
            + photosphere * 0.48
            + beads * 0.34
            + diamond * 0.72
            + lensArc * 0.34) * shaderExposure;
    float alpha = max(disc * 0.998, lightAlpha);
    alpha *= vertexColor.a * ColorModulator.a;
    if (alpha <= 0.003) {
        discard;
    }
    fragColor = vec4(color * vertexColor.rgb, alpha);
}
