@echo off
setlocal EnableExtensions
cd /d "%~dp0"
set "HERE=%~dp0"
title ウユニ塩原Mod かんたんインストール

echo.
echo  ==============================================
echo     ウユニ塩原Mod  かんたんインストール
echo  ==============================================
echo.

if exist "gradlew.bat" goto :start
rem ------------------------------------------------------------ 0. 古い版のファイルを片付ける
rem 前の版を同じフォルダに上書き展開すると、新しい版で無くなったファイルが残ってしまい、
rem 組み立てに失敗することがあるので、使わなくなったファイルを先に消しておく
if exist "src\main\java\com\bingo\uyuni\mixin" rmdir /s /q "src\main\java\com\bingo\uyuni\mixin"
if exist "src\main\java\com\bingo\uyuni\client\MirrorRenderTypes.java" del /q "src\main\java\com\bingo\uyuni\client\MirrorRenderTypes.java"
if exist "src\main\java\com\bingo\uyuni\client\RemappedBufferSource.java" del /q "src\main\java\com\bingo\uyuni\client\RemappedBufferSource.java"
if exist "src\main\resources\uyuni.mixins.json" del /q "src\main\resources\uyuni.mixins.json"
if exist "src\main\resources\assets\uyuni\shaders\core\mirror_entity.json" del /q "src\main\resources\assets\uyuni\shaders\core\mirror_entity.json"
if exist "src\main\resources\assets\uyuni\shaders\core\mirror_entity.vsh" del /q "src\main\resources\assets\uyuni\shaders\core\mirror_entity.vsh"
if exist "src\main\resources\assets\uyuni\shaders\core\mirror_entity.fsh" del /q "src\main\resources\assets\uyuni\shaders\core\mirror_entity.fsh"
if exist "src\main\resources\assets\uyuni\shaders\core\mirror_entity_mask.json" del /q "src\main\resources\assets\uyuni\shaders\core\mirror_entity_mask.json"
if exist "src\main\resources\assets\uyuni\shaders\core\mirror_entity_mask.fsh" del /q "src\main\resources\assets\uyuni\shaders\core\mirror_entity_mask.fsh"
if exist "src\main\resources\assets\uyuni\shaders\core\mirror_entity_flag.json" del /q "src\main\resources\assets\uyuni\shaders\core\mirror_entity_flag.json"
if exist "src\main\resources\assets\uyuni\shaders\core\mirror_entity_flag.fsh" del /q "src\main\resources\assets\uyuni\shaders\core\mirror_entity_flag.fsh"
if exist "src\main\resources\assets\uyuni\shaders\core\mirror_plane.json" del /q "src\main\resources\assets\uyuni\shaders\core\mirror_plane.json"
if exist "src\main\resources\assets\uyuni\shaders\core\mirror_plane.vsh" del /q "src\main\resources\assets\uyuni\shaders\core\mirror_plane.vsh"
if exist "src\main\resources\assets\uyuni\shaders\core\mirror_plane.fsh" del /q "src\main\resources\assets\uyuni\shaders\core\mirror_plane.fsh"
if exist "src\main\resources\assets\uyuni\shaders\core\mirror_sky_probe.fsh" del /q "src\main\resources\assets\uyuni\shaders\core\mirror_sky_probe.fsh"
echo  [!] zip の中から直接開いているみたい。
echo      zip を右クリック →「すべて展開」してから、
echo      展開したフォルダの install.bat をダブルクリックしてね。
echo.
pause
exit /b 1

:start
rem ------------------------------------------------------------ 1. Java
echo  [1/3] Java を探しています...
set "JAVA_EXE="
call :try_java "%HERE%.jdk\bin\java.exe"
if defined JAVA_HOME call :try_java "%JAVA_HOME%\bin\java.exe"
for /f "delims=" %%p in ('where java 2^>nul') do call :try_java "%%p"
if not defined JAVA_EXE call :download_jdk
if not defined JAVA_EXE goto :fail_java

for %%i in ("%JAVA_EXE%") do set "JAVA_HOME=%%~dpi.."
for %%i in ("%JAVA_HOME%") do set "JAVA_HOME=%%~fi"
set "PATH=%JAVA_HOME%\bin;%PATH%"

rem ------------------------------------------------------------ 2. Build
echo.
echo  [2/3] Mod を組み立てています。
echo        初回は Minecraft と Forge のダウンロードが入るので 10分くらいかかるよ。
echo        英語がたくさん流れるけど、そのまま待っててね...
echo.
if exist "build\libs" del /q "build\libs\*.jar" >nul 2>&1
call gradlew.bat build --no-daemon
if errorlevel 1 goto :fail_build
set "JAR="
for %%f in ("build\libs\uyuni-*.jar") do set "JAR=%%~ff"
if not defined JAR goto :fail_build
copy /y "%JAR%" "%HERE%" >nul

rem ------------------------------------------------------------ 3. Install
echo.
echo  [3/3] どこに入れる？
echo.
echo     1 : いつものマイクラ（公式ランチャー）
echo     2 : 自分で mods フォルダを選ぶ（CurseForge / Prism など）
echo     3 : 入れない（jar を作るだけ）
echo.
choice /c 123 /n /m "  番号キーを押してね [1/2/3]: "
if errorlevel 3 goto :done_nocopy
if errorlevel 2 goto :pick
set "MODS=%APPDATA%\.minecraft\mods"
call :check_forge
goto :copy

:pick
set "MODS="
echo  フォルダを選ぶ画面を開くよ（見当たらなければタスクバーを見てね）
for /f "usebackq delims=" %%d in (`powershell -NoProfile -STA -Command "Add-Type -AssemblyName System.Windows.Forms; $f = New-Object System.Windows.Forms.FolderBrowserDialog; $f.Description = 'mods フォルダを選んでね'; $o = New-Object System.Windows.Forms.Form -Property @{TopMost = $true}; if ($f.ShowDialog($o) -eq 'OK') { $f.SelectedPath }"`) do set "MODS=%%d"
if not defined MODS goto :done_nocopy

:copy
if not exist "%MODS%" mkdir "%MODS%"
del /q "%MODS%\uyuni-*.jar" >nul 2>&1
copy /y "%JAR%" "%MODS%\" >nul
if errorlevel 1 goto :fail_copy
echo.
echo  ==============================================
echo   できあがり！ mods フォルダに入れました。
echo  ==============================================
echo.
echo   ランチャーで「forge 1.20.1」を選んで起動してね。
echo   ゲーム内で「塩の手鏡」をクラフトして右クリックすると塩原へ行けるよ。
echo     ガラス板 / ネザークォーツ / ガラス板
echo     ネザークォーツ / エンダーパール / ネザークォーツ
echo     ガラス板 / ネザークォーツ / ガラス板
echo.
echo   影MODを使うなら、同じ mods フォルダにこの2つも入れてね:
echo     Oculus     https://modrinth.com/mod/oculus
echo     Embeddium  https://modrinth.com/mod/embeddium
echo.
start "" "%MODS%"
goto :end

:done_nocopy
echo.
echo  このフォルダに uyuni-1.0.0.jar を置いておいたよ。
echo  好きな mods フォルダにドラッグして入れてね。
start "" "%HERE%"
goto :end

:fail_java
echo.
echo  [!] Java の準備に失敗しました。
echo      ネットにつながっているか確認して、もう一度 install.bat を開いてね。
echo      それでもダメなら https://adoptium.net/ から「Temurin 17」を入れてから試してね。
goto :end_err

:fail_build
echo.
echo  [!] 組み立てに失敗しました。
echo      上のエラー表示（FAILED や error: のあたり）をコピーして Claude に見せてくれれば直すよ。
goto :end_err

:fail_copy
echo.
echo  [!] mods フォルダへのコピーに失敗しました。
echo      マイクラを閉じてから、もう一度試してね。
echo      jar はこのフォルダにもあるので、手で入れても OK。
goto :end_err

:end_err
echo.
pause
exit /b 1

:end
echo.
pause
exit /b 0

rem ============================================================ subroutines

:try_java
if defined JAVA_EXE exit /b 0
if not exist "%~1" exit /b 0
if not exist "%~dp1javac.exe" exit /b 0
set "JV="
set "JM="
"%~1" -version 2> "%TEMP%\uyuni_java_ver.txt"
for /f "tokens=3" %%v in ('findstr /i /c:" version " "%TEMP%\uyuni_java_ver.txt"') do set "JV=%%~v"
if not defined JV exit /b 0
for /f "delims=." %%m in ("%JV%") do set "JM=%%m"
if not defined JM exit /b 0
if %JM% LSS 17 exit /b 0
if %JM% GTR 22 exit /b 0
set "JAVA_EXE=%~1"
echo        見つかったよ: Java %JV%
exit /b 0

:download_jdk
echo        使える Java 17 が見つからなかったので、このフォルダに自動で用意するね（約190MB）...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$ErrorActionPreference = 'Stop'; $ProgressPreference = 'SilentlyContinue'; [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; Invoke-WebRequest -UseBasicParsing -Uri 'https://api.adoptium.net/v3/binary/latest/17/ga/windows/x64/jdk/hotspot/normal/eclipse' -OutFile 'jdk17.zip'; if (Test-Path '.jdk-tmp') { Remove-Item '.jdk-tmp' -Recurse -Force }; Expand-Archive -Path 'jdk17.zip' -DestinationPath '.jdk-tmp' -Force; if (Test-Path '.jdk') { Remove-Item '.jdk' -Recurse -Force }; $d = Get-ChildItem '.jdk-tmp' -Directory | Select-Object -First 1; Move-Item $d.FullName '.jdk'; Remove-Item '.jdk-tmp', 'jdk17.zip' -Recurse -Force"
if errorlevel 1 exit /b 0
call :try_java "%HERE%.jdk\bin\java.exe"
exit /b 0

:check_forge
dir /b /ad "%APPDATA%\.minecraft\versions\1.20.1-forge-*" >nul 2>&1
if not errorlevel 1 exit /b 0
echo.
echo  [!] Forge 1.20.1 がまだ入っていないみたい。
echo      いまブラウザで Forge のページを開くので「Installer」をダウンロードして、
echo      開いたら「Install client」のまま OK を押してね。
echo      終わったらこの画面に戻って、何かキーを押してね。
start "" "https://files.minecraftforge.net/net/minecraftforge/forge/index_1.20.1.html"
pause
exit /b 0
