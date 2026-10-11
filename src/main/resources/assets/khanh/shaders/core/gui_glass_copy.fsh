#version 330
uniform sampler2D SceneSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    // World RGB can be valid while its alpha is zero. Keep the shared scene opaque.
    fragColor = vec4(texture(SceneSampler, texCoord).rgb, 1.0);
}
