#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <khanh:wrap_sparkle.glsl>

uniform sampler2D Sampler0;
uniform sampler2D Sampler3;

in float sphericalVertexDistance;
in float cylindricalVertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

// The held item keeps its silhouette (atlas alpha) but its pixels become the wrap PNG.
// Atlas UV * atlas size / 16 maps every 16x16 item sprite onto exactly one copy of the image.
void main() {
    vec4 base = texture(Sampler0, texCoord0);
    if (base.a < 0.1) {
        discard;
    }
    vec2 itemUv = texCoord0 * vec2(textureSize(Sampler0, 0)) / 16.0;
    // TextureMat carries the flow offset, so the PNG drifts slowly across the item.
    vec2 wrapUv = (TextureMat * vec4(itemUv, 0.0, 1.0)).xy;
    vec4 wrap = texture(Sampler3, wrapUv);
    // A touch of the item's own brightness keeps outlines and edges readable.
    float luminance = dot(base.rgb, vec3(0.299, 0.587, 0.114));
    vec3 wrapped = zc_grade(clamp(wrap.rgb * (0.78 + 0.40 * luminance), 0.0, 1.0), TextureMat);
    vec4 color = vec4(mix(base.rgb, wrapped, wrap.a), base.a) * vertexColor * ColorModulator;
    // Sparkles glow on their own: added after lighting so they shine even in shade.
    color.rgb += zc_sparkle(itemUv, wrap.rgb, TextureMat) * wrap.a;
    fragColor = apply_fog(color, sphericalVertexDistance, cylindricalVertexDistance, FogEnvironmentalStart, FogEnvironmentalEnd, FogRenderDistanceStart, FogRenderDistanceEnd, FogColor);
}
