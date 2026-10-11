#version 330
uniform sampler2D Sampler0;
in vec2 localPos;
in vec4 vertexColor;
flat in vec2 bounds;
flat in float radius;
flat in float border;
out vec4 fragColor;
void main() {
    vec2 q = abs(localPos) - bounds + radius;
    float d = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float aa = max(fwidth(d) * 0.7, 0.001);
    float coverage = 1.0 - smoothstep(-aa, aa, d);
    if (border > 0.5) coverage -= 1.0 - smoothstep(-aa, aa, d + 1.0);
    fragColor = vertexColor * texture(Sampler0, vec2(0.5));
    fragColor.a *= clamp(coverage, 0.0, 1.0);
}
