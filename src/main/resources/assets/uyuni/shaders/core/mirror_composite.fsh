#version 150

// 影MOD併用モードの水鏡の合成。
// Sampler0 = 影パックが完成させた絵のコピー
// Sampler1 = 空マップ（影パックの空を方向ごとに記録したもの）
// Sampler2 = mask（a=水面が見えている）
// Sampler3 = 水鏡に映った Mob の形（そこは影パックが描いた鏡像を残す）
// Sampler4 = 時刻ごとの空の色の表（mirror_sky_table。古くなった空マップの代わりに使う）

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;
uniform sampler2D Sampler3;
uniform sampler2D Sampler4;

uniform mat4 ProjView;
uniform mat4 InvProjView;
uniform float PlaneY;
// 目の位置（カメラ相対）。影パック使用中は画面の揺れのぶん原点からずれる
uniform vec3 Eye;
// x: 今の時刻（表の横のマス単位、小数あり）  y: 天球の角度(0〜1)  z: 星の明るさ  w: 未使用
uniform vec4 SkyState;
uniform vec2 ScreenSize;

// x: 真下の反射率  y: 水平線付近の反射率  z: 揺れの強さ
uniform vec4 MirrorParams;
// カメラの位置（1024 で割った余り）。波紋ごとのいびつさを、場所に固定するため
uniform vec2 CameraXZ;

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

in vec2 texCoord;

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

// ---------------------------------------------------------------- 今の時刻の空（表から）
const int TIME_BINS = 64;
const int ELEV_BINS = 8;

vec4 tableAt(int t, int e) {
    if (t < 0) t += TIME_BINS;
    if (t >= TIME_BINS) t -= TIME_BINS;
    e = clamp(e, 0, ELEV_BINS - 1);
    vec4 c = texelFetch(Sampler4, ivec2(t, e), 0);
    return vec4(c.rgb, step(0.35, c.a));
}

// 方向 dir の「今の時刻の空」。雲の細部はないが、明るさと色合いは今の空。a = 0 なら分からない
vec4 currentSky(vec3 dir) {
    float el = asin(clamp(dir.y, 0.0, 1.0)) / 1.5707963 * float(ELEV_BINS) - 0.5;
    float tt = SkyState.x - 0.5;
    int t0 = int(floor(tt));
    int e0 = int(floor(el));
    float ft = tt - floor(tt);
    float fe = clamp(el - floor(el), 0.0, 1.0);
    vec4 c00 = tableAt(t0, e0);
    vec4 c10 = tableAt(t0 + 1, e0);
    vec4 c01 = tableAt(t0, e0 + 1);
    vec4 c11 = tableAt(t0 + 1, e0 + 1);
    float w00 = (1.0 - ft) * (1.0 - fe) * c00.a;
    float w10 = ft * (1.0 - fe) * c10.a;
    float w01 = (1.0 - ft) * fe * c01.a;
    float w11 = ft * fe * c11.a;
    float wsum = w00 + w10 + w01 + w11;
    if (wsum < 0.001) return vec4(0.0);
    return vec4((c00.rgb * w00 + c10.rgb * w10 + c01.rgb * w01 + c11.rgb * w11) / wsum, 1.0);
}

float hash3(vec3 p) {
    p = fract(p * 0.1031);
    p += dot(p, p.zyx + 31.32);
    return fract((p.x + p.y) * p.z);
}

// 夜の星。空と一緒に（東から西へ）回る
vec3 stars(vec3 dir) {
    if (SkyState.z < 0.01) return vec3(0.0);
    float a = SkyState.y * TAU;
    float c = cos(a);
    float s = sin(a);
    vec3 d = vec3(c * dir.x - s * dir.y, s * dir.x + c * dir.y, dir.z);
    vec3 p = d * 110.0;
    vec3 cell = floor(p);
    float h = hash3(cell);
    if (h < 0.9) return vec3(0.0);
    vec3 off = vec3(hash3(cell + 17.1), hash3(cell + 41.7), hash3(cell + 73.3)) - 0.5;
    float dist = length(fract(p) - 0.5 - off * 0.5);
    float size = 0.07 + 0.08 * hash3(cell + 5.9);
    // 遠くて細かすぎる所はチラつくので弱める
    float px = length(fwidth(p));
    float b = smoothstep(size, size * 0.3, dist) * (h - 0.9) * 10.0 * smoothstep(0.9, 0.3, px);
    vec3 tint = mix(vec3(1.0, 0.92, 0.82), vec3(0.82, 0.9, 1.0), hash3(cell + 9.2));
    return tint * b * SkyState.z * 0.8;
}

// ---------------------------------------------------------------- 空マップ
// 方向 dir の空の色。a = 0 ならまだ分からない（その時は水面を描き換えない）
vec4 skyColor(vec3 dir) {
    dir.y = max(dir.y, 0.003);
    dir = normalize(dir);
    vec2 s = dir.xz / (1.0 + dir.y);
    vec4 mapped = texture(Sampler1, s * 0.5 + 0.5);
    float known = step(0.25, mapped.a);

    // 空マップの古さ。見たばかりの空はそのまま、見ていない間に古くなった空は「今の時刻の空」へ置き換える
    // （ずっと下を向いていても、夜なのに昼の青空が映る、ということがないように）
    float fresh = mapped.a > 0.55 ? clamp((mapped.a - 0.6) / 0.4, 0.0, 1.0) : 0.0;
    vec4 now = currentSky(dir);
    if (now.a > 0.5) {
        float keep = known * smoothstep(0.0, 0.6, fresh);
        mapped.rgb = mix(now.rgb + stars(dir), mapped.rgb, keep);
        known = 1.0;
    }

    // その方向の空が今の画面に写っていれば、完成した絵から直接読む（雲の動きや太陽まで正確）。
    // 画面のふちに近いほど空マップへなめらかに切り替える。
    vec4 clip = ProjView * vec4(dir, 0.0);
    if (clip.w > 0.0) {
        vec2 ndc = clip.xy / clip.w;
        float edge = max(abs(ndc.x), abs(ndc.y));
        if (edge < 0.99) {
            vec2 suv = ndc * 0.5 + 0.5;
            if (texture(Sampler2, suv).b > 0.5) {
                float w = known > 0.5 ? smoothstep(0.99, 0.80, edge) : 1.0;
                return vec4(mix(mapped.rgb, texture(Sampler0, suv).rgb, w), 1.0);
            }
        }
    }
    return vec4(mapped.rgb, known);
}

// 周りも水面かどうか（手や Mob のふちに反射がはみ出さないよう、少し内側だけ使う）
float waterMask(vec2 uv) {
    vec2 px = 2.0 / ScreenSize;
    float m = texture(Sampler2, uv).a;
    m = min(m, texture(Sampler2, uv + vec2(px.x, 0.0)).a);
    m = min(m, texture(Sampler2, uv - vec2(px.x, 0.0)).a);
    m = min(m, texture(Sampler2, uv + vec2(0.0, px.y)).a);
    m = min(m, texture(Sampler2, uv - vec2(0.0, px.y)).a);
    return m;
}

void main() {
    vec2 uv = texCoord;
    if (waterMask(uv) < 0.5) discard;

    // この画素に映っている水面上の点（カメラ相対）と視線
    vec4 farPoint = InvProjView * vec4(uv * 2.0 - 1.0, 1.0, 1.0);
    vec3 view = normalize(farPoint.xyz / farPoint.w - Eye);
    if (view.y > -0.0001) discard;
    vec3 p = Eye + view * ((PlaneY - Eye.y) / view.y);

    // 波紋で傾いた水面の向きと、光の集まり具合（コースティクス）
    vec3 rip = totalRipple(p.xz, length(fwidth(p.xz))) * MirrorParams.z;
    vec2 g = rip.xy;
    float focus = -rip.z;
    vec3 n = normalize(vec3(-g.x, 1.0, -g.y));
    vec3 r = reflect(view, n);

    float cosView = clamp(dot(-view, n), 0.0, 1.0);
    float grazing = 1.0 - cosView;
    float strength = mix(MirrorParams.x, MirrorParams.y, grazing * grazing);

    vec3 color;
    if (texture(Sampler3, uv).a > 0.5) {
        // 影パックが描いた Mob の鏡像。水面の傾きの分だけずらして読む（揺れる）
        vec3 mirrored = vec3(r.x, -r.y, r.z);
        vec4 c0 = ProjView * vec4(p, 1.0);
        vec4 c1 = ProjView * vec4(p + mirrored * 6.0, 1.0);
        vec4 c2 = ProjView * vec4(p + view * 6.0, 1.0);
        vec2 shift = (c1.xy / c1.w - c2.xy / c2.w) * 0.5;
        vec2 suv = clamp(uv + shift, vec2(0.0015), vec2(0.9985));
        if (texture(Sampler3, suv).a < 0.5 || texture(Sampler2, suv).a < 0.5) suv = uv;
        color = texture(Sampler0, suv).rgb;
    } else {
        // 水越しに見える塩: 波で屈折して少しゆがみ、波の山の下に光が集まって明るい筋ができる
        vec4 b0 = ProjView * vec4(p, 1.0);
        vec4 b1 = ProjView * vec4(p - vec3(g.x, 0.0, g.y) * 0.25, 1.0);
        vec2 buv = clamp(uv + (b1.xy / b1.w - b0.xy / b0.w) * 0.5, vec2(0.0015), vec2(0.9985));
        if (texture(Sampler2, buv).a < 0.5 || texture(Sampler3, buv).a > 0.5) buv = uv;
        vec3 base = texture(Sampler0, buv).rgb;
        base *= 1.0 + clamp(focus * 0.04, -0.35, 0.6);

        // 空の映り込み（まだ見たことのない方向の空は、影パックの水のまま）
        vec4 sky = skyColor(r);
        color = mix(base, sky.rgb, strength * sky.a);
    }
    fragColor = vec4(color, 1.0);
}
