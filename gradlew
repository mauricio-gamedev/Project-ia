#!/usr/bin/env sh
# Small, version-pinned Gradle bootstrapper. The distribution is downloaded from the official
# Gradle HTTPS endpoint only when absent, then cached under GRADLE_USER_HOME.
set -eu
GRADLE_VERSION="8.13"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-${HOME}/.gradle}"
DIST_ROOT="${GRADLE_USER_HOME}/wrapper/dists/nucleo-gradle-${GRADLE_VERSION}"
GRADLE_HOME="${DIST_ROOT}/gradle-${GRADLE_VERSION}"
GRADLE_BIN="${GRADLE_HOME}/bin/gradle"
EXPECTED_SHA256="20f1b1176237254a6fc204d8434196fa11a4cfb387567519c61556e8710aed78"

if [ ! -x "$GRADLE_BIN" ]; then
    mkdir -p "$DIST_ROOT"
    ZIP_FILE="${DIST_ROOT}/gradle-${GRADLE_VERSION}-bin.zip"
    if [ -f "$ZIP_FILE" ]; then
        if command -v sha256sum >/dev/null 2>&1; then
            ACTUAL_SHA256="$(sha256sum "$ZIP_FILE" | cut -d ' ' -f 1)"
        elif command -v shasum >/dev/null 2>&1; then
            ACTUAL_SHA256="$(shasum -a 256 "$ZIP_FILE" | cut -d ' ' -f 1)"
        else
            echo "Erro: sha256sum ou shasum é necessário para verificar a distribuição Gradle." >&2
            exit 1
        fi
        if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then rm -f "$ZIP_FILE"; fi
    fi
    if [ ! -f "$ZIP_FILE" ]; then
        if command -v curl >/dev/null 2>&1; then
            curl --fail --location --retry 3 --proto '=https' --tlsv1.2 \
                "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
                --output "$ZIP_FILE"
        elif command -v wget >/dev/null 2>&1; then
            wget "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -O "$ZIP_FILE"
        else
            echo "Erro: instale curl ou wget para baixar Gradle ${GRADLE_VERSION}." >&2
            exit 1
        fi
    fi
    if command -v sha256sum >/dev/null 2>&1; then
        ACTUAL_SHA256="$(sha256sum "$ZIP_FILE" | cut -d ' ' -f 1)"
    elif command -v shasum >/dev/null 2>&1; then
        ACTUAL_SHA256="$(shasum -a 256 "$ZIP_FILE" | cut -d ' ' -f 1)"
    else
        echo "Erro: sha256sum ou shasum é necessário para verificar a distribuição Gradle." >&2
        exit 1
    fi
    if [ "$ACTUAL_SHA256" != "$EXPECTED_SHA256" ]; then
        rm -f "$ZIP_FILE"
        echo "Erro: o SHA-256 da distribuição Gradle não corresponde ao valor oficial esperado." >&2
        exit 1
    fi
    if ! command -v unzip >/dev/null 2>&1; then
        echo "Erro: instale unzip para extrair a distribuição Gradle." >&2
        exit 1
    fi
    unzip -q -o "$ZIP_FILE" -d "$DIST_ROOT"
fi

exec "$GRADLE_BIN" "$@"
