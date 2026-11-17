#version 150

// 「時刻ごとの空の色」の表（影MOD併用モード）。
//   横 64 マス = 1 日の時刻（天球の角度）   縦 8 マス = 地平線(下)〜真上(上) の高さ
//
// ずっと下を向いていると、水面に映すべき真上の空は画面に写らないので、影パックの「今の空」は読めない。
// そこで、空が画面に写っている時に「この時刻の、この高さの空はこんな色」を覚えておき、
// 古くなった空マップの代わりに使う（雲の細部はないが、時刻・明るさ・色合いは今の空になる）。
//
// a の意味: 1.0 = 実際に見て覚えた   0.5 = 前後の時刻から推測   0.4 = となりの高さから推測   0 = 分からない
//
// Sampler0 = 影パックが完成させた絵   Sampler1 = 前のフレームの表   Sampler2 = mask (b=空が見えている)

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ProjView;
uniform vec2 ScreenTexel;
// x: 今の時刻のマス(0〜63)   y: 未使用   z: 0=初期化 1=更新   w: 覚える速さ
uniform vec4 TableParams;

out vec4 fragColor;

const int TIME_BINS = 64;
const int ELEV_BINS = 8;
const float TAU = 6.2831853;
const float HALF_PI = 1.5707963;

// 画面のこの点が「くっきり空だけ」か（Mob・ブロックのふちから 4 ピクセル以内は使わない）
bool clearSky(vec2 suv) {
    if (texture(Sampler2, suv).b < 0.5) return false;
    for (int i = 0; i < 8; i++) {
        float ang = float(i) * 0.785398;
        vec2 o = vec2(cos(ang), sin(ang)) * ScreenTexel * 4.0;
        if (texture(Sampler2, suv + o).b < 0.5) return false;
        if (texture(Sampler2, suv + o * 0.5).b < 0.5) return false;
    }
    return true;
}

// バニラの空の明るさ（昼 1、夜 0）を元にした、時刻による空の明るさの目安
float daylight(float celestial) {
    float f = clamp(cos(celestial * TAU) * 2.0 + 0.5, 0.0, 1.0);
    return 0.06 + 0.94 * f;
}

float binTime(int t) {
    return (float(t) + 0.5) / float(TIME_BINS);
}

int wrapBin(int t) {
    if (t < 0) return t + TIME_BINS;
    if (t >= TIME_BINS) return t - TIME_BINS;
    return t;
}

void main() {
    ivec2 cell = ivec2(gl_FragCoord.xy);
    if (TableParams.z < 0.5) {
        fragColor = vec4(0.0);
        return;
    }
    vec4 prev = texelFetch(Sampler1, cell, 0);
    int t = cell.x;
    int e = cell.y;

    // ---- 今の時刻のマス: 画面に写っている空から覚える
    if (t == int(TableParams.x + 0.5)) {
        vec3 sum = vec3(0.0);
        float n = 0.0;
        for (int k = 0; k < 3; k++) {
            float el = (float(e) + 0.2 + 0.3 * float(k)) / float(ELEV_BINS) * HALF_PI;
            for (int i = 0; i < 24; i++) {
                float az = (float(i) + 0.5) / 24.0 * TAU;
                vec3 dir = vec3(cos(el) * cos(az), sin(el), cos(el) * sin(az));
                vec4 clip = ProjView * vec4(dir, 0.0);
                if (clip.w <= 0.0) continue;
                vec2 ndc = clip.xy / clip.w;
                if (max(abs(ndc.x), abs(ndc.y)) > 0.97) continue;
                vec2 suv = ndc * 0.5 + 0.5;
                if (!clearSky(suv)) continue;
                vec3 c = texture(Sampler0, suv).rgb;
                if (max(c.r, max(c.g, c.b)) > 0.97) continue;   // 太陽そのもの・まぶしい光は平均に入れない
                sum += c;
                n += 1.0;
            }
        }
        if (n >= 2.0) {
            float rate = prev.a > 0.75 ? TableParams.w : 1.0;
            fragColor = vec4(mix(prev.rgb, sum / n, rate), 1.0);
            return;
        }
    }

    // ---- 実際に見て覚えたマスはそのまま
    if (prev.a > 0.75) {
        fragColor = prev;
        return;
    }

    // ---- まだ見ていない時刻: 前後でいちばん近い「見た時刻」から、昼夜の明るさの違いを掛けて推測
    float here = daylight(binTime(t));
    vec3 acc = vec3(0.0);
    float wsum = 0.0;
    bool foundL = false;
    bool foundR = false;
    for (int d = 1; d <= TIME_BINS / 2; d++) {
        if (!foundL) {
            int tl = wrapBin(t - d);
            vec4 c = texelFetch(Sampler1, ivec2(tl, e), 0);
            if (c.a > 0.75) {
                float ratio = clamp(here / daylight(binTime(tl)), 0.05, 20.0);
                acc += clamp(c.rgb * ratio, 0.0, 1.0) / float(d);
                wsum += 1.0 / float(d);
                foundL = true;
            }
        }
        if (!foundR) {
            int tr = wrapBin(t + d);
            vec4 c = texelFetch(Sampler1, ivec2(tr, e), 0);
            if (c.a > 0.75) {
                float ratio = clamp(here / daylight(binTime(tr)), 0.05, 20.0);
                acc += clamp(c.rgb * ratio, 0.0, 1.0) / float(d);
                wsum += 1.0 / float(d);
                foundR = true;
            }
        }
        if (foundL && foundR) break;
    }
    if (wsum > 0.0) {
        fragColor = vec4(acc / wsum, 0.5);
        return;
    }

    // ---- この高さはどの時刻でも見たことがない: となりの高さから借りる（1 フレームに 1 マスずつ広がる）
    vec4 below = e > 0 ? texelFetch(Sampler1, ivec2(t, e - 1), 0) : vec4(0.0);
    vec4 above = e < ELEV_BINS - 1 ? texelFetch(Sampler1, ivec2(t, e + 1), 0) : vec4(0.0);
    if (below.a > 0.35) {
        fragColor = vec4(below.rgb, 0.4);
    } else if (above.a > 0.35) {
        fragColor = vec4(above.rgb, 0.4);
    } else {
        fragColor = vec4(0.0);
    }
}
