#version 330
layout(std140) uniform Globals {
    ivec3 CameraBlockPos;
    vec3 CameraOffset;
    vec2 ScreenSize;
    float GlintAlpha;
    float GameTime;
    int MenuBlurRadius;
    int UseRgss;
};
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

// Wide golden-angle disc over the quarter-resolution scene: heavy frosted blur.
vec3 frostSample(vec2 uv) {
    vec2 texel = 1.0 / vec2(textureSize(Sampler0, 0));
    vec3 sum = texture(Sampler0, uv).rgb;
    const int TAPS = 12;
    for (int i = 0; i < TAPS; i++) {
        float angle = float(i) * 2.399963;
        float reach = sqrt((float(i) + 0.5) / float(TAPS)) * 7.0;
        sum += texture(Sampler0, uv + vec2(cos(angle), sin(angle)) * reach * texel).rgb;
    }
    return sum / float(TAPS + 1);
}

vec3 orb(vec2 p, vec2 centre, float size, vec3 colour) {
    vec2 d = p - centre;
    return colour * exp(-dot(d, d) / (size * size));
}

// The frosted world (or plain ink without one) plus three slow grey light orbs, all in
// screen space so every panel samples one continuous field.
void main() {
    float d = roundedBox(localPos, bounds, radius);
    float aa = max(fwidth(d) * 0.7, 0.001);
    float coverage = 1.0 - smoothstep(-aa, aa, d);
    if (coverage < 0.001) discard;

    vec2 screen = max(ScreenSize, vec2(1.0));
    vec2 uv = gl_FragCoord.xy / screen;
    float aspect = screen.x / screen.y;
    vec2 p = vec2(uv.x * aspect, 1.0 - uv.y);

    // param: 1 = no world bound, 2 = full-screen dimmed world, 3 = frosted panel body.
    bool frosted = param > 2.5;
    vec3 ink = frosted ? vec3(0.014, 0.014, 0.016) : vec3(0.008, 0.008, 0.009);
    vec3 base = ink;
    if (param > 1.5) {
        base = frosted ? mix(frostSample(uv), ink, 0.72) : mix(texture(Sampler0, uv).rgb, ink, 0.84);
    }

    // Time arrives packed in the vertex colour's RGB: a 24-bit phase of a 600 s loop.
    vec3 packedTime = floor(vertexColor.rgb * 255.0 + 0.5);
    float t = (packedTime.r * 65536.0 + packedTime.g * 256.0 + packedTime.b) / 16777215.0 * 600.0;
    const float TAU = 6.2831853;
    vec2 violet = vec2(0.12 * aspect, 0.04) + vec2(sin(TAU * t / 40.0), cos(TAU * t / 50.0)) * vec2(0.09, 0.07);
    vec2 cyan = vec2(aspect - 0.06, 0.40) + vec2(sin(TAU * t / 60.0 + 1.7), cos(TAU * t / 40.0 + 0.6)) * vec2(0.10, 0.06);
    vec2 pink = vec2(0.40 * aspect, 1.04) + vec2(cos(TAU * t / 75.0 + 2.4), sin(TAU * t / 50.0 + 1.1)) * vec2(0.08, 0.08);
    // Black theme: the orbs are soft neutral light, never colour.
    vec3 glow = orb(p, violet, 0.62, vec3(0.105))
        + orb(p, cyan, 0.52, vec3(0.070))
        + orb(p, pink, 0.50, vec3(0.055));
    glow *= frosted ? 0.0 : 1.0;
    vec3 colour = vec3(1.0) - (vec3(1.0) - base) * (vec3(1.0) - clamp(glow, 0.0, 1.0));

    if (!frosted) {
        float vignette = smoothstep(1.25, 0.35, length((uv - 0.5) * vec2(aspect, 1.0)));
        colour *= mix(0.72, 1.0, vignette);
    }
    fragColor = vec4(colour, coverage * vertexColor.a);
}
