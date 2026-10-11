#version 330
uniform sampler2D SceneSampler;
in vec2 texCoord;
out vec4 fragColor;
void main() {
    vec2 pixel = 1.0 / vec2(textureSize(SceneSampler, 0));
    vec3 colour = texture(SceneSampler, texCoord).rgb * 0.36;
    colour += texture(SceneSampler, texCoord + pixel * vec2( 2.0,  2.0)).rgb * 0.16;
    colour += texture(SceneSampler, texCoord + pixel * vec2(-2.0,  2.0)).rgb * 0.16;
    colour += texture(SceneSampler, texCoord + pixel * vec2( 2.0, -2.0)).rgb * 0.16;
    colour += texture(SceneSampler, texCoord + pixel * vec2(-2.0, -2.0)).rgb * 0.16;
    fragColor = vec4(colour, 1.0);
}
