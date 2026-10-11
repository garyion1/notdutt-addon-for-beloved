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
out vec2 localPos;
out vec4 vertexColor;
flat out vec2 bounds;
flat out float radius;
flat out float param;
// Same UV packing as gui_shape: integer magnitude = size, U fraction = corner
// radius, V fraction = a free per-shape parameter (mode, blur or scene flag).
void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec2 absoluteUV = abs(UV0);
    bounds = floor(absoluteUV) * 0.5;
    vec2 fraction = fract(absoluteUV);
    vec2 metadata = min(fraction, vec2(1.0) - fraction) * 1024.0;
    radius = clamp(metadata.x, 0.0, min(bounds.x, bounds.y));
    param = metadata.y;
    localPos = sign(UV0) * bounds;
    vertexColor = Color * ColorModulator;
}
