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

float grain(vec2 p) {
    p = fract(p * vec2(443.897, 441.423));
    p += dot(p, p.yx + 19.19);
    return fract((p.x + p.y) * p.x);
}

vec4 over(vec4 top, vec4 base) {
    float a = top.a + base.a * (1.0 - top.a);
    if (a < 0.0001) return vec4(0.0);
    return vec4((top.rgb * top.a + base.rgb * base.a * (1.0 - top.a)) / a, a);
}

// param selects the surface: 1 glass, 2 accent fill, 3 knob, 4 inset track.
void main() {
    float d = roundedBox(localPos, bounds, radius);
    float aa = max(fwidth(d) * 0.7, 0.001);
    float coverage = 1.0 - smoothstep(-aa, aa, d);
    if (coverage < 0.001) discard;
    float pixel = max(fwidth(d), 0.0001);
    int mode = int(param + 0.5);
    float down = clamp(localPos.y / max(bounds.y, 1.0) * 0.5 + 0.5, 0.0, 1.0);
    float across = clamp(localPos.x / max(bounds.x, 1.0) * 0.5 + 0.5, 0.0, 1.0);
    float n = grain(gl_FragCoord.xy) - 0.5;
    vec4 colour;

    if (mode == 2) {
        vec3 tint = vertexColor.rgb;
        vec3 partner = mix(tint, vec3(1.0), 0.3);
        colour = vec4(mix(tint, partner, across), 1.0);
        float sheen = smoothstep(-aa, aa, roundedBox(localPos - vec2(0.0, pixel), bounds, radius));
        colour.rgb = mix(colour.rgb, vec3(1.0), sheen * 0.35);
    } else if (mode == 3) {
        vec3 tint = vertexColor.rgb;
        colour = vec4(mix(tint, tint * 0.86, down), 1.0);
    } else if (mode == 4) {
        colour = vec4(vec3(1.0), 0.05);
        float topShade = smoothstep(-aa, aa, roundedBox(localPos - vec2(0.0, pixel * 2.0), bounds, radius));
        float bottomLight = smoothstep(-aa, aa, roundedBox(localPos + vec2(0.0, pixel), bounds, radius));
        colour = over(vec4(vec3(0.0), 0.45 * topShade), colour);
        colour = over(vec4(vec3(1.0), 0.08 * bottomLight), colour);
    } else {
        // rgba(255,255,255,.06) film (stronger when tinted), 1px top-lit border,
        // inset 0 1px highlight: the shared glass recipe (no grain).
        vec3 tint = vertexColor.rgb;
        float saturation = max(max(tint.r, tint.g), tint.b) - min(min(tint.r, tint.g), tint.b);
        colour = vec4(tint, 0.06 + saturation * 0.16);
        float highlight = smoothstep(-aa, aa, roundedBox(localPos - vec2(0.0, pixel), bounds, radius));
        colour = over(vec4(vec3(1.0), 0.25 * highlight), colour);
        float ring = smoothstep(-pixel - aa, -pixel + aa, d);
        colour = over(vec4(vec3(1.0), ring * mix(0.40, 0.05, down)), colour);
    }
    // Keep Sampler0 referenced so the pipeline's declared sampler always binds.
    float bound = step(-1.0, texture(Sampler0, vec2(0.5)).a);
    fragColor = vec4(colour.rgb, colour.a * coverage * vertexColor.a * bound);
}
