#version 330
uniform sampler2D SceneSampler;
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
const float TAU = 6.28318530718;
float handAlpha(vec2 uv) { return texture(HandSampler, clamp(uv, vec2(0.0), vec2(1.0))).a; }
vec3 rainbow(float t) { return 0.5 + 0.5 * cos(TAU * (t + vec3(0.0, 0.33, 0.67))); }
void main() {
    vec4 background = texture(SceneSampler, texCoord);
    vec4 hand = texture(HandSampler, texCoord);
    float a = clamp(hand.a, 0.0, 1.0);
    // Transparent target blending stores premultiplied colour at soft edges.
    vec3 base = hand.rgb / max(a, 0.0001);
    vec3 color = Style.z == 1.0 ? rainbow(Style.w * 0.16 + texCoord.y * 0.4) : FillColor.rgb;
    if (Style.z == 2.0) {
        vec2 stepSize = Geometry.xy * 8.0;
        color = (texture(SceneSampler, texCoord + vec2(stepSize.x, 0)).rgb
            + texture(SceneSampler, texCoord - vec2(stepSize.x, 0)).rgb
            + texture(SceneSampler, texCoord + vec2(0, stepSize.y)).rgb
            + texture(SceneSampler, texCoord - vec2(0, stepSize.y)).rgb) * 0.25;
        color = mix(color, FillColor.rgb, 0.22);
    } else if (Style.z == 3.0) {
        // Original screen-space reflection, masked to the held item only.
        // This is a stylised finish, not a world-space ray-traced mirror.
        vec2 reflectedUv = clamp(vec2(1.0 - texCoord.x, texCoord.y), Geometry.xy, vec2(1.0) - Geometry.xy);
        vec3 reflected = texture(SceneSampler, reflectedUv).rgb;
        float luminance = clamp(dot(base, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
        color = mix(reflected, FillColor.rgb, 0.16) * (0.65 + 0.35 * luminance);
    } else if (Style.z == 4.0) {
        // A still etched contour follows item luminance; no scrolling scanlines.
        float luminance = clamp(dot(base, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);
        float contours = luminance * 7.0;
        float distanceToLine = abs(fract(contours) - 0.5);
        float aa = max(fwidth(contours), 0.025);
        float line = 1.0 - smoothstep(0.04, 0.04 + aa, distanceToLine);
        color = mix(base, FillColor.rgb, 0.15 + line * 0.6);
    } else if (Trail.w > 0.5) {
        color *= 0.3 + 0.7 * clamp(dot(base, vec3(0.299, 0.587, 0.114)) * 1.8, 0.0, 1.0);
    }
    vec3 result = mix(background.rgb, mix(base, color, Style.x), a);
    float outer = 0.0, halo = 0.0;
    for (int i = 0; i < 8; ++i) {
        vec2 dir = vec2(cos(float(i) * TAU / 8.0), sin(float(i) * TAU / 8.0));
        if (Geometry.z > 0.0) outer = max(outer, handAlpha(texCoord + dir * Geometry.xy * Geometry.z));
        if (Geometry.w > 0.0) {
            halo += handAlpha(texCoord + dir * Geometry.xy * Geometry.w * 0.45) * 0.075;
            halo += handAlpha(texCoord + dir * Geometry.xy * Geometry.w) * 0.05;
        }
    }
    if (Trail.x > 0.0) {
        float ghost = texture(TrailSampler, texCoord).a * (1.0 - a) * Trail.x;
        result = mix(result, GlowColor.rgb, ghost * 0.6);
    }
    result += GlowColor.rgb * halo * (1.0 - a) * Style.y;
    result = mix(result, Style.z == 1.0 ? color : OutlineColor.rgb, clamp(outer - a, 0.0, 1.0));
    fragColor = vec4(result, background.a);
}
