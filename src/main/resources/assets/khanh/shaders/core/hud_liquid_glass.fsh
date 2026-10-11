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
flat in float border;
out vec4 fragColor;

void main() {
    // Continuous rounded-rectangle distance: no Minecraft panel texture or stair-step mask.
    vec2 q = abs(localPos) - bounds + radius;
    float d = length(max(q, vec2(0.0))) + min(max(q.x, q.y), 0.0) - radius;
    float aa = max(fwidth(d) * 0.75, 0.001);
    float coverage = 1.0 - smoothstep(-aa, aa, d);
    if (coverage < 0.001) discard;

    // The rim bends the shared scene using the actual rounded edge's normal,
    // not a moving radial/noise distortion. Flat panel centres stay stable.
    vec2 outward = max(q, vec2(0.0));
    vec2 normal = length(outward) > 0.001 ? normalize(outward) :
        (q.x > q.y ? vec2(1.0, 0.0) : vec2(0.0, 1.0));
    normal *= sign(localPos);
    normal.y *= -1.0;
    vec2 pixel = 1.0 / max(ScreenSize, vec2(1.0));
    vec2 uv = gl_FragCoord.xy * pixel;
    float inside = max(-d, 0.0);
    float edge = 1.0 - smoothstep(0.0, min(8.0, min(bounds.x, bounds.y) * 0.75), inside);
    vec2 lens = normal * pixel * edge * edge * 3.5;
    vec2 refracted = clamp(uv + lens, pixel, vec2(1.0) - pixel);
    vec3 scene = texture(Sampler0, refracted).rgb * 0.46;
    scene += texture(Sampler0, clamp(refracted + pixel * vec2( 2.0,  1.0), pixel, vec2(1.0) - pixel)).rgb * 0.135;
    scene += texture(Sampler0, clamp(refracted + pixel * vec2(-2.0,  1.0), pixel, vec2(1.0) - pixel)).rgb * 0.135;
    scene += texture(Sampler0, clamp(refracted + pixel * vec2( 1.0, -2.0), pixel, vec2(1.0) - pixel)).rgb * 0.135;
    scene += texture(Sampler0, clamp(refracted + pixel * vec2(-1.0, -2.0), pixel, vec2(1.0) - pixel)).rgb * 0.135;

    // Warm-black film keeps the HUD creamy and readable while retaining the scene.
    float film = 0.28 + clamp(vertexColor.a, 0.0, 1.0) * 0.42;
    vec3 warmBlack = mix(vertexColor.rgb, vec3(0.040, 0.035, 0.030), 0.34);
    vec3 colour = mix(scene, warmBlack, film);
    float top = clamp(0.5 - localPos.y / max(bounds.y * 2.0, 1.0), 0.0, 1.0);
    colour += vec3(0.018, 0.015, 0.011) * top * top;
    float light = max(dot(normal, normalize(vec2(-0.45, 0.89))), 0.0);
    float rim = 1.0 - smoothstep(aa * 0.1, aa * 1.9, abs(d + aa * 0.35));
    float reflection = rim * border * (0.08 + light * 0.16);
    colour = mix(colour, vec3(0.965, 0.940, 0.890), reflection);
    colour += vec3(0.020, 0.016, 0.011) * edge * light;
    fragColor = vec4(colour, coverage * 0.96);
}
