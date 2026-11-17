#version 150

in vec3 Position;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

// カメラ相対の位置（水面上の点）
out vec3 surfacePos;

void main() {
    surfacePos = Position;
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
}
