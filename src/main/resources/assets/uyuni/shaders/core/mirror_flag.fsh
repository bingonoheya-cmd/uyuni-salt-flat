#version 150

// 鏡像がある画素に 1 を書く。テクスチャが分かっている時は、透明な部分（Mob の毛の隙間など）を抜く

uniform sampler2D Sampler0;
uniform float UseTexture;

in vec2 texCoord0;

out vec4 fragColor;

void main() {
    if (UseTexture > 0.5 && texture(Sampler0, texCoord0).a < 0.1) discard;
    fragColor = vec4(1.0);
}
