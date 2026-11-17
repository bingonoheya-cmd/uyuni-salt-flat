#version 150

// 影MOD併用モード: 影パックが描き終えた直後の「奥行き」から、
//   a = 水面が見えている画素   b = 空が見えている画素
// を求める。Mob・パーティクル・ブロック・一人称の手など、手前にあるものは奥行きで自然に除かれる。
//
// Sampler0 = Minecraft の奥行き（Oculus はこれを影パックの depthtex0 として使うので、水面・Mob・手が全部入っている）
//
// 水面かどうかは「高さ」と「面の向き」で決める:
//  - 高さ: 影パックによっては水面そのものを波で上下させる（頂点を動かす）。
//          以前は ±6cm しか許していなかったので、波の山と谷が水面と認められず、白い筋になっていた。
//          塩の板(水面の 7.6cm 下)から水面の 25cm 上までを許す。
//  - 向き: 水面に立つ Mob の足元は、高さだけでは水面と見分けられない。
//          水面（波があっても）は上を向いた面、Mob の脚は横を向いた面なので、面の向きで見分ける。
//          面の向きは隣の画素の位置から求める。物のふちで隣が別の物にまたがらないよう、近い方の隣を使う。

uniform sampler2D Sampler0;

uniform mat4 InvProjView;   // 画面 → カメラ相対の世界座標
uniform vec3 Eye;           // 目の位置（カメラ相対。影パック使用中は画面の揺れのぶんずれる）
uniform float PlaneY;       // 水面の高さ（カメラ相対）
uniform float Radius;       // 水面として扱う最大距離（描画距離）
uniform vec2 ScreenSize;

in vec2 texCoord;

out vec4 fragColor;

vec3 unproject(vec2 uv, float depth) {
    vec4 p = InvProjView * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

vec3 positionAt(vec2 uv) {
    return unproject(uv, texture(Sampler0, uv).r);
}

void main() {
    float depth = texture(Sampler0, texCoord).r;
    vec3 dir = normalize(unproject(texCoord, 1.0) - Eye);

    // 空: 何も描かれていない（奥行きが一番奥のまま）か、ずっと遠く
    vec3 hit = unproject(texCoord, depth);
    float hitDist = length(hit - Eye);
    float sky = (depth >= 0.999999 || hitDist > 1024.0) ? 1.0 : 0.0;

    float water = 0.0;
    if (sky < 0.5 && dir.y < -0.0001) {
        float planeDist = (PlaneY - Eye.y) / dir.y;
        bool inBand = hit.y > PlaneY - 0.12 && hit.y < PlaneY + 0.25;
        if (planeDist < Radius && inBand) {
            // 面の向き（近い方の隣の画素から）
            vec2 px = 1.0 / ScreenSize;
            vec3 l = positionAt(texCoord - vec2(px.x, 0.0));
            vec3 r = positionAt(texCoord + vec2(px.x, 0.0));
            vec3 d = positionAt(texCoord - vec2(0.0, px.y));
            vec3 u = positionAt(texCoord + vec2(0.0, px.y));
            vec3 dx = distance(r, hit) < distance(l, hit) ? r - hit : hit - l;
            vec3 dy = distance(u, hit) < distance(d, hit) ? u - hit : hit - d;
            vec3 n = normalize(cross(dx, dy));
            // 遠く（24 ブロック以上）は Mob の足元も数画素なので、向きは見ない（細かい波で向きが乱れるため）
            bool facesUp = abs(n.y) > 0.7 || planeDist > 24.0;
            if (facesUp) water = 1.0;
        }
    }

    fragColor = vec4(0.0, 0.0, sky, water);
}
