#!/bin/bash
# 云端会话开始时准备编译环境：Android SDK、Maven 镜像、local.properties，并预先下载编译依赖。
# 只在 Claude Code 云端会话里运行；可以重复运行，已经准备好的步骤会跳过。
set -euo pipefail

if [ "${CLAUDE_CODE_REMOTE:-}" != "true" ]; then
  exit 0
fi

PROJECT_DIR="${CLAUDE_PROJECT_DIR:-$(cd "$(dirname "$0")/../.." && pwd)}"
SDK_DIR="${ANDROID_HOME:-$HOME/android-sdk}"
CMDLINE_TOOLS_URL="https://dl.google.com/android/repository/commandlinetools-linux-16111833_latest.zip"
# compileSdk 37 要的平台；AGP 9.4 默认用 build-tools 36，也一起装上，编译时就不用再下载
SDK_PACKAGES=("platforms;android-37.0" "build-tools;37.0.0" "build-tools;36.0.0")

log() { echo "[session-start] $*" >&2; }

# 1. Android SDK 命令行工具
SDKMANAGER="$SDK_DIR/cmdline-tools/latest/bin/sdkmanager"
if [ ! -x "$SDKMANAGER" ]; then
  log "下载 Android 命令行工具"
  tmp=$(mktemp -d)
  curl -fsSL --retry 3 -o "$tmp/cmdline-tools.zip" "$CMDLINE_TOOLS_URL"
  # 容器里不一定有 unzip；Python 解压不保留可执行权限，后面补上
  python3 -I -c 'import sys, zipfile; zipfile.ZipFile(sys.argv[1]).extractall(sys.argv[2])' \
    "$tmp/cmdline-tools.zip" "$tmp/unzipped"
  mkdir -p "$SDK_DIR/cmdline-tools"
  rm -rf "$SDK_DIR/cmdline-tools/latest"
  mv "$tmp/unzipped/cmdline-tools" "$SDK_DIR/cmdline-tools/latest"
  chmod +x "$SDK_DIR/cmdline-tools/latest/bin/"*
  rm -rf "$tmp"
fi

# 2. 平台和 build-tools
missing=()
for package in "${SDK_PACKAGES[@]}"; do
  [ -d "$SDK_DIR/${package//;//}" ] || missing+=("$package")
done
if [ ${#missing[@]} -gt 0 ]; then
  log "安装 ${missing[*]}"
  sdk_log=$(mktemp)
  yes | "$SDKMANAGER" --sdk_root="$SDK_DIR" --licenses > "$sdk_log" 2>&1 || true
  if ! "$SDKMANAGER" --sdk_root="$SDK_DIR" "${missing[@]}" >> "$sdk_log" 2>&1; then
    tail -20 "$sdk_log" >&2
    log "安装失败"
    exit 1
  fi
  rm -f "$sdk_log"
fi

# 3. local.properties 告诉 Gradle SDK 在哪（这个文件不进仓库）
if ! grep -qs '^sdk\.dir=' "$PROJECT_DIR/local.properties"; then
  echo "sdk.dir=$SDK_DIR" >> "$PROJECT_DIR/local.properties"
fi

# 4. 经这里的代理直接访问 Maven Central 会返回 429，改用 Google 的 Maven Central 镜像（只改本机，不改仓库）
mkdir -p "$HOME/.gradle/init.d"
cat > "$HOME/.gradle/init.d/central-mirror.gradle" <<'EOF'
// 由 .claude/hooks/session-start.sh 生成：把 mavenCentral() 指到 Google 的镜像。
def mirror = 'https://maven-central.storage-download.googleapis.com/maven2/'
def redirect = { repos ->
    repos.configureEach { repo ->
        if (repo instanceof MavenArtifactRepository && repo.url.toString().startsWith('https://repo.maven.apache.org')) {
            repo.url = mirror
        }
    }
}
beforeSettings { settings ->
    redirect(settings.pluginManagement.repositories)
    redirect(settings.dependencyResolutionManagement.repositories)
    redirect(settings.buildscript.repositories)
}
allprojects { project ->
    redirect(project.buildscript.repositories)
    redirect(project.repositories)
}
EOF

if [ -n "${CLAUDE_ENV_FILE:-}" ] && ! grep -qs '^export ANDROID_HOME=' "$CLAUDE_ENV_FILE"; then
  echo "export ANDROID_HOME=\"$SDK_DIR\"" >> "$CLAUDE_ENV_FILE"
fi

# 5. 预先下载 Gradle 和编译依赖。每个工作目录只做一次；失败也不影响会话，之后编译时会再下载
marker="$PROJECT_DIR/.gradle/session-start-warmed"
if [ ! -f "$marker" ]; then
  log "预先下载编译依赖"
  gradle_log=$(mktemp)
  if (cd "$PROJECT_DIR" && ./gradlew --no-daemon :app:compileDebugUnitTestKotlin) > "$gradle_log" 2>&1; then
    mkdir -p "$(dirname "$marker")" && touch "$marker"
    rm -f "$gradle_log"
  else
    log "预先下载失败，不影响使用，编译时会重新下载（日志：$gradle_log）"
  fi
fi

log "编译环境已就绪"
