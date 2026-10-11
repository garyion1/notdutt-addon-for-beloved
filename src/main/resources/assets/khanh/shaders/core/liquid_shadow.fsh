#version 330
uniform sampler2D Sampler0;
in vec2 localPos;
in vec4 vertexColor;
flat in vec2 bounds;
flat in float radius;
flat in float param;
out vec4 fragColor;

float roundedBox(vec2 p, vec2 b, float r) {
    vec2 q = abs(p) - b + r;
    return length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - r;
}

// The quad is the panel grown by `param` on every side; fade across that margin.
void main() {
    float blur = max(param, 1.0);
    vec2 inner = max(bounds - vec2(blur), vec2(1.0));
    float d = roundedBox(localPos, inner, min(radius, min(inner.x, inner.y)));
    float a = 1.0 - smoothstep(-blur, blur, d);
    a = a * a * (3.0 - 2.0 * a);
    if (a < 0.002) discard;
    float bound = step(-1.0, texture(Sampler0, vec2(0.5)).a);
    fragColor = vec4(vertexColor.rgb, vertexColor.a * a * bound);
}
