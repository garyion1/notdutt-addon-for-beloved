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
out vec4 fragColor;
void main() {
    vec2 q = abs(localPos) - bounds + radius;
    float d = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float aa = max(fwidth(d) * 0.7, 0.001);
    float coverage = 1.0 - smoothstep(-aa, aa, d);
    if (coverage < 0.001) discard;

    // Physical framebuffer coordinates remain correct through GUI transforms,
    // scale changes and dropdown/column layouts. No animated UV distortion.
    vec2 pixel = 1.0 / max(ScreenSize, vec2(1.0));
    vec2 uv = gl_FragCoord.xy * pixel;
    float edge = 1.0 - smoothstep(0.0, min(12.0, min(bounds.x, bounds.y) * 0.85), max(-d, 0.0));
    vec2 radial = vec2(localPos.x, -localPos.y) / max(bounds, vec2(1.0));
    vec2 direction = radial / max(length(radial), 0.001);
    vec2 refracted = clamp(uv + direction * pixel * edge * edge * 2.2, pixel, vec2(1.0) - pixel);
    vec3 background = texture(Sampler0, refracted).rgb;
    float film = 0.16 + clamp(vertexColor.a, 0.0, 1.0) * 0.26;
    vec3 colour = mix(background, vertexColor.rgb, film);
    float rim = 1.0 - smoothstep(0.0, aa * 2.4, abs(d));
    float light = max(dot(direction, normalize(vec2(-0.5, 0.85))), 0.0);
    colour = mix(colour, vec3(0.81, 0.84, 0.91), rim * (0.045 + light * 0.080));
    colour += vec3(0.009, 0.011, 0.016) * edge * light;
    fragColor = vec4(colour, coverage * (0.80 + rim * 0.04));
}
