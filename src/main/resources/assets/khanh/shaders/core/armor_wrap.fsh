#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <khanh:wrap_sparkle.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
#ifdef PER_FACE_LIGHTING
in vec4 vertexPerFaceColorBack;
in vec4 vertexPerFaceColorFront;
#else
in vec4 vertexColor;
#endif
in vec4 lightMapColor;
in vec4 overlayColor;
in vec2 texCoord0;

out vec4 fragColor;

// Vanilla's armour fragment stage, with the armour pixels replaced by the flowing wrap PNG.
// Every 16 texels of the armour texture show one copy of the image.
void main() {
    vec4 base = texture(Sampler0, texCoord0);
#ifdef ALPHA_CUTOUT
    if (base.a < ALPHA_CUTOUT) {
        discard;
    }
#endif
    vec2 itemUv = texCoord0 * vec2(textureSize(Sampler0, 0)) / 16.0;
    vec2 wrapUv = (TextureMat * vec4(itemUv, 0.0, 1.0)).xy;
    vec4 wrap = texture(Sampler3, wrapUv);
    float luminance = dot(base.rgb, vec3(0.299, 0.587, 0.114));
    vec3 wrapped = zc_grade(clamp(wrap.rgb * (0.78 + 0.40 * luminance), 0.0, 1.0), TextureMat);
    vec4 color = vec4(mix(base.rgb, wrapped, wrap.a), base.a);
#ifdef PER_FACE_LIGHTING
    color *= (gl_FrontFacing ? vertexPerFaceColorFront : vertexPerFaceColorBack) * ColorModulator;
#else
    color *= vertexColor * ColorModulator;
#endif
#ifndef NO_OVERLAY
    color.rgb = mix(overlayColor.rgb, color.rgb, overlayColor.a);
#endif
    color *= lightMapColor;
    // Sparkles glow on their own: added after lighting so they shine even in shade.
    color.rgb += zc_sparkle(itemUv, wrap.rgb, TextureMat) * wrap.a;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
