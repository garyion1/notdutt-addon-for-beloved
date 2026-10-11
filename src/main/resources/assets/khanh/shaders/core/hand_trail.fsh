#version 330
uniform sampler2D HandSampler;
uniform sampler2D TrailSampler;
layout(std140) uniform HandEffects {
    vec4 OutlineColor;
    vec4 GlowColor;
    vec4 FillColor;
    vec4 Geometry;
    vec4 Style;
    vec4 Trail;
};
in vec2 texCoord;
out vec4 fragColor;
void main() {
    float current = texture(HandSampler, texCoord).a;
    vec2 previousUV = texCoord - vec2(0.0, Trail.z);
    float previous = previousUV.y < 0.0 ? 0.0 : texture(TrailSampler, previousUV).a * Trail.y;
    fragColor = vec4(GlowColor.rgb * max(current, previous), max(current, previous));
}
