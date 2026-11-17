#version 150

// 鏡像の形を描く。Position は ShaderReflection が影パックに渡したのと同じ「カメラ空間」の位置

in vec3 Position;
in vec2 UV0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out vec2 texCoord0;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    texCoord0 = UV0;
}
