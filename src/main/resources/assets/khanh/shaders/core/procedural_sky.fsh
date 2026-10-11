#version 330
layout(std140) uniform SkyEffects { vec4 TintTime; vec4 Settings; };
in vec3 direction;
out vec4 fragColor;
float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453123);
}
float noise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash(i), hash(i + vec2(1.0, 0.0)), u.x),
        mix(hash(i + vec2(0.0, 1.0)), hash(i + vec2(1.0, 1.0)), u.x), u.y);
}
void main() {
    vec3 p = normalize(direction) * Settings.y;
    float t = TintTime.w * Settings.x * 0.22;
    float a = sin(p.x * 2.7 + t + sin(p.z * 2.1 - t));
    float b = cos(p.z * 3.1 - t * 0.8 + sin(p.y * 2.5 + t));
    float c = sin(p.y * 2.9 + p.x * 1.7 + t * 0.6);
    float water = 0.5 + 0.5 * sin(a + b + c);
    float veins = pow(clamp(1.0 - abs(a + b * 0.7 + c * 0.6), 0.0, 1.0), 9.0);
    if (Settings.w > 1.5) {
        vec2 q = vec2(atan(p.z, p.x) * 1.8, p.y * 0.35);
        float bands = 0.0;
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float bend = noise(vec2(q.x * 1.5 + t + fi * 3.1, q.y + t * 0.5));
            float center = 0.08 + fi * 0.12 + bend * 0.14;
            float delta = (normalize(direction).y - center) / 0.075;
            bands += exp(-delta * delta);
        }
        float shimmer = 0.65 + 0.35 * sin(t * 2.0 + q.x * 4.0);
        float glow = bands * shimmer * clamp(Settings.z, 0.0, 1.5);
        vec3 night = vec3(0.006, 0.012, 0.035);
        vec3 aurora = mix(TintTime.rgb, vec3(0.65, 0.40, 0.98),
            clamp(normalize(direction).y * 0.8, 0.0, 0.6));
        fragColor = vec4(clamp(night + aurora * glow, 0.0, 1.0), 1.0);
        return;
    }
    float pattern = mix(water, veins, Settings.w);
    float intensity = clamp(Settings.z, 0.0, 1.5);
    vec3 base = TintTime.rgb * (0.12 + pattern * intensity);
    vec3 glow = mix(TintTime.rgb, vec3(1.0), 0.65) * veins * intensity * 0.45;
    fragColor = vec4(clamp(base + glow, 0.0, 1.0), 1.0);
}
