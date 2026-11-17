#!/bin/bash
# ウユニ塩原Mod かんたんインストール（Mac / Linux 用）
set -u
cd "$(dirname "$0")" || exit 1
HERE="$(pwd)"

echo ""
echo " =============================================="
echo "    ウユニ塩原Mod  かんたんインストール"
echo " =============================================="
echo ""

pause_exit() { echo ""; read -r -p " Enter で閉じます" _; exit "$1"; }

ok_java() {
  local j="$1"
  [ -n "$j" ] && [ -x "$j" ] || return 1
  [ -x "$(dirname "$j")/javac" ] || return 1
  local v m
  v=$("$j" -version 2>&1 | awk -F'"' '/ version /{print $2; exit}')
  m=${v%%.*}
  [[ "$m" =~ ^[0-9]+$ ]] || return 1
  [ "$m" -ge 17 ] && [ "$m" -le 22 ]
}

# ------------------------------------------------------------ 1. Java
echo " [1/3] Java を探しています..."
JAVA=""
CANDIDATES=(
  "$HERE/.jdk/Contents/Home/bin/java"
  "$HERE/.jdk/bin/java"
  "${JAVA_HOME:-/nonexistent}/bin/java"
)
if [ -x /usr/libexec/java_home ]; then
  jh=$(/usr/libexec/java_home -v 17 2>/dev/null) && CANDIDATES+=("$jh/bin/java")
fi
CANDIDATES+=("$(command -v java 2>/dev/null)")
for c in "${CANDIDATES[@]}"; do
  if ok_java "$c"; then JAVA="$c"; break; fi
done

if [ -z "$JAVA" ]; then
  echo "       使える Java 17 が見つからなかったので、このフォルダに自動で用意するね（約190MB）..."
  case "$(uname -s)" in Darwin) aos=mac ;; *) aos=linux ;; esac
  case "$(uname -m)" in arm64|aarch64) aarch=aarch64 ;; *) aarch=x64 ;; esac
  url="https://api.adoptium.net/v3/binary/latest/17/ga/$aos/$aarch/jdk/hotspot/normal/eclipse"
  rm -rf .jdk .jdk-tmp jdk17.tar.gz
  if curl -fL --progress-bar -o jdk17.tar.gz "$url"; then
    mkdir .jdk-tmp && tar -xzf jdk17.tar.gz -C .jdk-tmp
    mv "$(find .jdk-tmp -mindepth 1 -maxdepth 1 -type d | head -n 1)" .jdk
    rm -rf .jdk-tmp jdk17.tar.gz
  fi
  for c in "$HERE/.jdk/Contents/Home/bin/java" "$HERE/.jdk/bin/java"; do
    if ok_java "$c"; then JAVA="$c"; break; fi
  done
fi

if [ -z "$JAVA" ]; then
  echo ""
  echo " [!] Java の準備に失敗しました。ネット接続を確認してもう一度試してね。"
  echo "     ダメなら https://adoptium.net/ から「Temurin 17」を入れてから試してね。"
  pause_exit 1
fi
JAVA_HOME="$(cd "$(dirname "$JAVA")/.." && pwd)"
export JAVA_HOME
export PATH="$JAVA_HOME/bin:$PATH"
echo "       Java: $("$JAVA" -version 2>&1 | head -n 1)"

# ------------------------------------------------------------ 2. Build
echo ""
echo " [2/3] Mod を組み立てています。初回は 10分くらいかかるので待っててね..."
echo ""
# 前の版を同じフォルダに上書き展開した時に残る、使わなくなったファイルを消す
rm -rf src/main/java/com/bingo/uyuni/mixin
rm -f "src/main/java/com/bingo/uyuni/client/MirrorRenderTypes.java"
rm -f "src/main/java/com/bingo/uyuni/client/RemappedBufferSource.java"
rm -f "src/main/resources/uyuni.mixins.json"
rm -f "src/main/resources/assets/uyuni/shaders/core/mirror_entity.json"
rm -f "src/main/resources/assets/uyuni/shaders/core/mirror_entity.vsh"
rm -f "src/main/resources/assets/uyuni/shaders/core/mirror_entity.fsh"
rm -f "src/main/resources/assets/uyuni/shaders/core/mirror_entity_mask.json"
rm -f "src/main/resources/assets/uyuni/shaders/core/mirror_entity_mask.fsh"
rm -f "src/main/resources/assets/uyuni/shaders/core/mirror_entity_flag.json"
rm -f "src/main/resources/assets/uyuni/shaders/core/mirror_entity_flag.fsh"
rm -f "src/main/resources/assets/uyuni/shaders/core/mirror_plane.json"
rm -f "src/main/resources/assets/uyuni/shaders/core/mirror_plane.vsh"
rm -f "src/main/resources/assets/uyuni/shaders/core/mirror_plane.fsh"
rm -f "src/main/resources/assets/uyuni/shaders/core/mirror_sky_probe.fsh"
chmod +x ./gradlew
rm -f build/libs/*.jar
if ! ./gradlew build --no-daemon; then
  echo ""
  echo " [!] 組み立てに失敗しました。上のエラー表示を Claude に見せてくれれば直すよ。"
  pause_exit 1
fi
JAR="$(ls build/libs/uyuni-*.jar 2>/dev/null | head -n 1)"
if [ -z "$JAR" ]; then
  echo " [!] jar が見つかりませんでした。"
  pause_exit 1
fi
cp -f "$JAR" "$HERE/"

# ------------------------------------------------------------ 3. Install
if [ "$(uname -s)" = "Darwin" ]; then
  MODS="$HOME/Library/Application Support/minecraft/mods"
else
  MODS="$HOME/.minecraft/mods"
fi
echo ""
echo " [3/3] いつものマイクラの mods フォルダに入れる？"
echo "       $MODS"
read -r -p " y = 入れる / n = 入れない(jar だけ作る) : " ans
if [[ "$ans" =~ ^[yY] ]]; then
  mkdir -p "$MODS"
  rm -f "$MODS"/uyuni-*.jar
  cp -f "$JAR" "$MODS/"
  echo ""
  echo " できあがり！ ランチャーで「forge 1.20.1」を選んで起動してね。"
  echo " 影MODを使うなら Oculus と Embeddium も同じ mods フォルダへ:"
  echo "   https://modrinth.com/mod/oculus"
  echo "   https://modrinth.com/mod/embeddium"
  if command -v open >/dev/null; then open "$MODS"; elif command -v xdg-open >/dev/null; then xdg-open "$MODS" >/dev/null 2>&1; fi
else
  echo ""
  echo " このフォルダに $(basename "$JAR") を置いておいたよ。好きな mods フォルダに入れてね。"
fi
pause_exit 0
