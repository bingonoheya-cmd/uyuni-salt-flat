#version 150

// 画面全体を覆う四角形（Position は -1〜1 の画面座標）

in vec3 Position;

out vec2 texCoord;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
    texCoord = Position.xy * 0.5 + 0.5;
}
