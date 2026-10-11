// Custom Glint sparkles and colour. The wrap's TextureMat carries extra data in slots a 2D lookup
// never touches: [3].z time in seconds, [2].z sparkle strength (0 = none), [1].w sparkle density,
// [0].z [1].z [2].x tint colour, [2].y tint amount, [2].w brightness.

// Tint the wrap toward the chosen colour (keeping its light and dark), then set its brightness.
vec3 zc_grade(vec3 c, mat4 m) {
    vec3 tint = vec3(m[0].z, m[1].z, m[2].x);
    float luma = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(c, tint * (0.30 + 1.15 * luma), clamp(m[2].y, 0.0, 1.0));
    return clamp(c * max(m[2].w, 0.0), 0.0, 1.0);
}

float zc_hash(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

// One layer: a jittered star per grid cell, most cells lit, each twinkling on its own clock
// with a hot core and a four-point flare.
float zc_sparkleLayer(vec2 uv, float density, float t, float seed) {
    vec2 grid = uv * density;
    vec2 cell = floor(grid);
    vec2 f = fract(grid) - 0.5;
    float h = zc_hash(cell + seed);
    if (h < 0.06) return 0.0;
    vec2 jitter = (vec2(zc_hash(cell + seed + 7.1), zc_hash(cell + seed + 3.3)) - 0.5) * 0.55;
    vec2 d = f - jitter;
    float speed = 2.5 + 5.5 * zc_hash(cell + seed + 1.7);
    float twinkle = pow(max(0.0, sin(h * 6.2831 + t * speed)), 2.5);
    float core = exp(-dot(d, d) * 110.0);
    float flare = exp(-abs(d.x) * 40.0) * exp(-abs(d.y) * 4.5) + exp(-abs(d.y) * 40.0) * exp(-abs(d.x) * 4.5);
    return twinkle * (core * 2.6 + flare * 1.5);
}

// Glow to add on top of the lit colour: white-hot stars tinted by the wrap underneath.
vec3 zc_sparkle(vec2 itemUv, vec3 wrapColour, mat4 textureMat) {
    float amount = textureMat[2].z;
    if (amount <= 0.001) return vec3(0.0);
    float t = textureMat[3].z;
    itemUv *= max(textureMat[1].w, 0.05);
    float s = zc_sparkleLayer(itemUv, 4.0, t, 0.0)
        + zc_sparkleLayer(itemUv + 0.37, 7.0, t * 1.3, 11.0) * 0.95
        + zc_sparkleLayer(itemUv + 0.71, 12.0, t * 1.7, 23.0) * 0.8
        + zc_sparkleLayer(itemUv + 0.13, 20.0, t * 2.1, 37.0) * 0.65;
    vec3 tint = mix(vec3(1.0), normalize(wrapColour + 0.15) * 1.6, 0.35);
    return tint * s * amount;
}
