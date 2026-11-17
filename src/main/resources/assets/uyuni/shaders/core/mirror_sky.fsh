#version 150

// 空マップの更新（影MOD併用モード）。
// 空マップは「上半分の空」を立体射影で 1 枚の正方形に広げたもの。
//   方向 D → 位置 s = D.xz / (1 + D.y)   （真上が中心、地平線が円のふち）
//
// a の意味:  0.6〜1.0 = 実際に見た空（1.0 = 今見た。見ていない間に少しずつ 0.6 まで古くなる）
//            0.5 = まわりの見た空から推測した空
//            0.3 = 古くなった空（時刻や天気が変わった。今の空で塗り替わるまでの間だけ使う）
//            0   = まだ分からない（水面には使わない）
//
// ・空の色はすべて「影パックが実際に描いた空」から取る（バニラの空の色は使わない）
// ・見えている方向は毎フレーム書き足す（止まっている時は強く、動いている時は弱く。
//   動いている最中の絵は影パックのモーションブラー/TAA で星や雲が流れているので、焼き付けないため）
//
// Sampler0 = 影パックが完成させた絵   Sampler1 = 前のフレームの空マップ   Sampler2 = mask (b=空が見えている)

uniform sampler2D Sampler0;
uniform sampler2D Sampler1;
uniform sampler2D Sampler2;

uniform mat4 ProjView;
// x: 0=初期化 1=更新   y: 覚えている所を新しい空で上書きする割合（カメラが止まっているほど 1）
// z: 見えていない所を、まわりの空へなじませる割合（古い雲の継ぎ目を残さない）
// w: 1 = 時刻や天気が大きく変わった（覚えている空をすべて「古い空」にする）
uniform vec4 SkyParams;
// 1 / 画面の解像度
uniform vec2 ScreenTexel;
// x: このフレームで「見た空」が 1 段古くなる確率   y: 乱数の種
uniform vec2 SkyDecay;
// 1 / 空マップの解像度
uniform vec2 SkyTexel;

in vec2 texCoord;

out vec4 fragColor;

// 画面のこの点が「くっきり空だけ」か。
// 動く Mob（空クジラなど）は影パックの TAA でまわりに数ピクセルにじむので、
// Mob・ブロックのふちから 4 ピクセル以内は空マップに覚えない（残像が焼き付かないように）
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

float hash(vec2 p) {
    p = fract(p * vec2(0.1031, 0.1030));
    p += dot(p, p.yx + 33.33);
    return fract((p.x + p.y) * p.x);
}

void main() {
    if (SkyParams.x < 0.5) {
        fragColor = vec4(0.0);
        return;
    }

    vec2 s = texCoord * 2.0 - 1.0;
    float r2 = dot(s, s);
    vec4 previous = texture(Sampler1, texCoord);
    // 時刻や天気が大きく変わった: 覚えている空は「古い空」に。見えている所から順に今の空で塗り替わる
    if (SkyParams.w > 0.5 && previous.a > 0.4) previous.a = 0.3;

    // ---- いま画面に写っている空なら覚える
    if (r2 <= 1.0) {
        vec3 dir = vec3(2.0 * s.x, 1.0 - r2, 2.0 * s.y) / (1.0 + r2);
        vec4 clip = ProjView * vec4(dir, 0.0);
        if (clip.w > 0.0) {
            vec2 ndc = clip.xy / clip.w;
            float edge = max(abs(ndc.x), abs(ndc.y));
            if (edge < 0.985) {
                vec2 suv = ndc * 0.5 + 0.5;
                if (clearSky(suv)) {
                    vec3 seen = texture(Sampler0, suv).rgb;
                    // 画面のふちほど弱く書き足す。雲は動くので、別の時に覚えた所との境目が
                    // くっきりした線（パネル状の継ぎ目）にならないよう、なめらかにつなぐ
                    float feather = smoothstep(0.985, 0.70, edge);
                    float w = previous.a > 0.55 ? SkyParams.y * feather : 1.0;
                    fragColor = vec4(mix(previous.rgb, seen, w), 1.0);
                    return;
                }
            }
        }
    }

    // ---- まわりの「見た空」から推測（少し離れた 4 方向を見て、速く広がるように）
    vec2 offs = SkyTexel * 3.0;
    vec4 n0 = texture(Sampler1, texCoord + vec2(offs.x, 0.0));
    vec4 n1 = texture(Sampler1, texCoord - vec2(offs.x, 0.0));
    vec4 n2 = texture(Sampler1, texCoord + vec2(0.0, offs.y));
    vec4 n3 = texture(Sampler1, texCoord - vec2(0.0, offs.y));
    // 「見た空」「推測した空」だけを広げる（古い空・分からない所は広げない）
    float w0 = step(0.4, n0.a) * n0.a;
    float w1 = step(0.4, n1.a) * n1.a;
    float w2 = step(0.4, n2.a) * n2.a;
    float w3 = step(0.4, n3.a) * n3.a;
    float wsum = w0 + w1 + w2 + w3;

    if (previous.a > 0.55) {
        // 前に見た空: 見ていない間に少しずつ古くなる（8bit なので、確率で 1 段ずつ下げる）。
        // 古くなった所は、水面では「いまの時刻の空」（mirror_sky_table）に置き換わっていく
        if (hash(texCoord * 1024.0 + SkyDecay.y) < SkyDecay.x) {
            previous.a = max(0.6, previous.a - 1.0 / 255.0);
        }
        // 少しずつまわりになじませる（古い雲のくっきりした継ぎ目を残さない）
        if (wsum > 0.0) {
            vec3 estimate = (n0.rgb * w0 + n1.rgb * w1 + n2.rgb * w2 + n3.rgb * w3) / wsum;
            previous.rgb = mix(previous.rgb, estimate, SkyParams.z);
        }
        fragColor = previous;
        return;
    }

    if (wsum <= 0.0) {
        fragColor = previous;
        return;
    }
    if (previous.a > 0.2 && previous.a < 0.4) {
        // 古い空: となりまで今の空が来たら、すぐ塗り替える（地平線から内側へ、波のように広がる）
        vec3 fresh = (n0.rgb * w0 + n1.rgb * w1 + n2.rgb * w2 + n3.rgb * w3) / wsum;
        fragColor = vec4(fresh, 0.5);
        return;
    }
    vec3 estimate = (n0.rgb * w0 + n1.rgb * w1 + n2.rgb * w2 + n3.rgb * w3) / wsum;
    float blend = previous.a > 0.25 ? 0.25 : 1.0;
    fragColor = vec4(mix(previous.rgb, estimate, blend), 0.5);
}
