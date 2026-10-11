#version 330
layout(std140) uniform DynamicTransforms {
    mat4 ModelViewMat;
    vec4 ColorModulator;
    vec3 ModelOffset;
    mat4 TextureMat;
};
layout(std140) uniform Projection { mat4 ProjMat; };
in vec3 Position;
in vec2 UV0;
in vec4 Color;
out vec3 direction;
void main() {
    vec4 projected = ProjMat * ModelViewMat * vec4(Position, 1.0);
    gl_Position = projected.xyww;
    direction = Position;
}
