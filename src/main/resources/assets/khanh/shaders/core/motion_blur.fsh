#version 330
uniform sampler2D CurrentSampler;
uniform sampler2D HistorySampler;
layout(std140) uniform MotionBlur {
    vec4 Params;
};
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec3 current = texture(CurrentSampler, texCoord).rgb;
    vec3 history = texture(HistorySampler, texCoord).rgb;
    fragColor = vec4(mix(current, history, clamp(Params.x, 0.0, 0.72)), 1.0);
}
