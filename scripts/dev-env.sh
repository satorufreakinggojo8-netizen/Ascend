#!/usr/bin/env bash
# Source this file before running Gradle in this repo:
#     source scripts/dev-env.sh
#
# It points JAVA_HOME / ANDROID_HOME at the project-local toolchain created by
# scripts/bootstrap-toolchain.sh when that toolchain exists, and otherwise
# leaves whatever the machine already has configured.
set -u

_cadence_repo_root() {
    local src="${BASH_SOURCE[0]}"
    local dir
    dir="$(cd "$(dirname "$src")" && pwd)"
    echo "$(cd "$dir/.." && pwd)"
}

REPO_ROOT="$(_cadence_repo_root)"
TOOLCHAIN="$REPO_ROOT/.toolchain"

_win_path() {
    if command -v cygpath >/dev/null 2>&1; then cygpath -w "$1"; else echo "$1"; fi
}

if [ -d "$TOOLCHAIN" ]; then
    JDK_DIR="$(ls -1d "$TOOLCHAIN"/jdk-* 2>/dev/null | head -n 1)"
    if [ -n "${JDK_DIR:-}" ] && [ -x "$JDK_DIR/bin/java" -o -x "$JDK_DIR/bin/java.exe" ]; then
        export JAVA_HOME="$(_win_path "$JDK_DIR")"
        export PATH="$JDK_DIR/bin:$PATH"
    fi

    if [ -d "$TOOLCHAIN/android-sdk" ]; then
        export ANDROID_HOME="$(_win_path "$TOOLCHAIN/android-sdk")"
        export ANDROID_SDK_ROOT="$ANDROID_HOME"
    fi

    GRADLE_DIR="$(ls -1d "$TOOLCHAIN"/gradle-* 2>/dev/null | head -n 1)"
    if [ -n "${GRADLE_DIR:-}" ]; then
        export PATH="$GRADLE_DIR/bin:$PATH"
    fi
fi

export REPO_ROOT
echo "dev-env: JAVA_HOME=${JAVA_HOME:-<unset>}"
echo "dev-env: ANDROID_HOME=${ANDROID_HOME:-<unset>}"
