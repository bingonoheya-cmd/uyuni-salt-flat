#version 150

// 塩原の水鏡（影MODなしの時）。
// Sampler0 = 水面を境に上下反転して描いた世界（画面と同じ解像度）
// 足元の波紋で水面が傾いた分だけ反射の向きを変え、見る角度で映り込みの強さを変える。

uniform sampler2D Sampler0;

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
uniform vec2 ScreenSize;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;

// x: 真下を見た時の反射率  y: 水平線付近の反射率  z: 揺れの強さ
uniform vec4 MirrorParams;
// カメラの位置（1024 で割った余り）。波紋ごとのいびつさを、場所に固定するため
uniform vec2 CameraXZ;

// 波紋: xy = 中心(カメラ相対のワールドXZ)  z = 経過秒  w = 強さ(0なら無効)
uniform vec4 Ripple0;
uniform vec4 Ripple1;
uniform vec4 Ripple2;
uniform vec4 Ripple3;
uniform vec4 Ripple4;
uniform vec4 Ripple5;
uniform vec4 Ripple6;
uniform vec4 Ripple7;
uniform vec4 Ripple8;
uniform vec4 Ripple9;
uniform vec4 Ripple10;
uniform vec4 Ripple11;
uniform vec4 Ripple12;
uniform vec4 Ripple13;
uniform vec4 Ripple14;
uniform vec4 Ripple15;

in vec3 surfacePos;

out vec4 fragColor;

// ---------------------------------------------------------------- 波紋（mirror_composite.fsh / mirror_surface.fsh 共通）
// 水深数cmの浅い水を足で踏んだ時の波:
//  ・波の塊は約 0.8 ブロック/秒で広がる（浅い水の波の速さ √(g×水深)）
//  ・波の山はそれより遅く動くので、新しい輪が先頭で生まれては後ろへ流れる（輪の本数が増えていく）
//  ・時間とともに輪の間隔が広がる
//  ・先頭のさらに外を、表面張力による細かいさざ波が速く走って、すぐ消える
//  ・足の形は丸くないので、輪は少しいびつ（波紋ごとに形が違う）
//  ・踏んだ瞬間は中心が盛り上がり、すぐ戻る
//  ・広がるほど、時間が経つほど弱まる（浅い水は底の摩擦で消えるのが早い）
//  ・遠くで細かすぎる波は、画面がチラつかないよう自動で消す
const float LIFE = 2.4;
const float TAU = 6.2831853;

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
}

// 1 つの波紋による水面の傾き (xy) と曲がり具合 (z: 負 = 山の頂上 = 光が集まる)
vec3 rippleShape(vec2 p, vec4 r, float footprint) {
    vec2 d = p - r.xy;
    float dist = length(d) + 0.0001;
    float age = r.z;

    vec2 cell = floor((r.xy + CameraXZ) * 64.0);
    float ang = atan(d.y, d.x);
    float wobble = (0.022 * sin(3.0 * ang + hash(cell) * TAU)
                  + 0.012 * sin(5.0 * ang + hash(cell + 17.0) * TAU)) * min(age * 2.0, 1.0);
    float fade = clamp((LIFE - age) / 0.4, 0.0, 1.0);

    float slope = 0.0;
    float curv = 0.0;

    // 主な輪（重力波）
    float k = 30.0 / (1.0 + age * 0.9);
    float front = 0.03 + age * 0.80 + wobble;
    float width = 0.07 + age * 0.20;
    float x = dist - front;
    float env = exp(-(x * x) / (width * width));
    float amp = r.w * 0.011 * exp(-age * 0.95) / sqrt(1.0 + front * 6.0);
    float aa = smoothstep(2.0 * footprint, 5.0 * footprint, TAU / k);
    float ph = k * (dist - 0.52 * age);
    slope += -amp * env * k * sin(ph) * aa;
    curv += -amp * env * k * k * cos(ph) * aa;

    // 表面張力のさざ波（先頭の外側を速く走る細かい波）
    float kc = 95.0;
    float fc = 0.05 + age * 1.25 + wobble;
    float xc = dist - fc;
    float envc = exp(-(xc * xc) / 0.004);
    float ampc = r.w * 0.0008 * exp(-age * 3.0) / sqrt(1.0 + fc * 6.0);
    float aac = smoothstep(2.0 * footprint, 5.0 * footprint, TAU / kc);
    float phc = kc * (dist - 0.9 * age);
    slope += -ampc * envc * kc * sin(phc) * aac;
    curv += -ampc * envc * kc * kc * cos(phc) * aac;

    // 踏んだ瞬間の中心の盛り上がり
    float b = max(0.0, 1.0 - age / 0.35);
    float sig = 0.012 + age * 0.05;
    float bump = r.w * 0.0143 * b * exp(-dist * dist / sig);
    slope += bump * (-2.0 * dist / sig);
    curv += bump * (4.0 * dist * dist / (sig * sig) - 2.0 / sig);

    return vec3(d / dist * slope, curv) * fade;
}

vec3 totalRipple(vec2 p, float footprint) {
    vec4 ripples[16] = vec4[16](Ripple0, Ripple1, Ripple2, Ripple3, Ripple4, Ripple5, Ripple6, Ripple7,
                                Ripple8, Ripple9, Ripple10, Ripple11, Ripple12, Ripple13, Ripple14, Ripple15);
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 16; i++) {
        if (ripples[i].w > 0.0) sum += rippleShape(p, ripples[i], footprint);
    }
    return sum;
}

void main() {
    vec3 view = normalize(surfacePos);

    vec3 rip = totalRipple(surfacePos.xz, length(fwidth(surfacePos.xz))) * MirrorParams.z;
    vec2 g = rip.xy;
    float focus = -rip.z;
    vec3 n = normalize(vec3(-g.x, 1.0, -g.y));
    vec3 r = reflect(view, n);

    // 反射した視線を、上下反転した世界の中の「読む位置」に直す。
    // 傾きがなければ mirrored == view となり、ちょうどこの画素を読む。
    vec3 mirrored = vec3(r.x, -r.y, r.z);
    mat4 pv = ProjMat * ModelViewMat;
    vec4 c1 = pv * vec4(surfacePos + mirrored * 6.0, 1.0);
    vec4 c2 = pv * vec4(surfacePos + view * 6.0, 1.0);
    vec2 uv = gl_FragCoord.xy / ScreenSize + (c1.xy / c1.w - c2.xy / c2.w) * 0.5;
    uv = clamp(uv, vec2(0.0015), vec2(0.9985));

    vec3 refl = texture(Sampler0, uv).rgb;

    // フレネル: 遠く(視線が水面すれすれ)ほど鏡に、真下ほど塩が透ける
    float grazing = 1.0 - clamp(dot(-view, n), 0.0, 1.0);
    float alpha = mix(MirrorParams.x, MirrorParams.y, grazing * grazing);

    // 水越しに透ける塩の上の、波の山の下に集まる光（コースティクス）。
    // 下の塩は後ろに透けて見えている分 (1 - alpha) なので、その分だけ明るさを足す
    float caustic = clamp(focus * 0.04, -0.35, 0.6);
    refl += vec3(0.85) * caustic * (1.0 - alpha) / max(alpha, 0.05);

    // バニラの地形と同じ円柱フォグ
    float fogDist = max(length(surfacePos.xz), abs(surfacePos.y));
    float fog = fogDist < FogEnd ? smoothstep(FogStart, FogEnd, fogDist) : 1.0;
    vec3 color = mix(refl, FogColor.rgb, fog * FogColor.a);
    alpha = mix(alpha, 1.0, fog * FogColor.a);

    fragColor = vec4(color, alpha);
}
