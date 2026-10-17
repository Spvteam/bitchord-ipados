#!/usr/bin/env bash
set -euo pipefail

# ==============================================================================
# BitChord v1.8-beta1 iPadOS / iOS Automated .ipa Build & Packaging Pipeline
# ==============================================================================
# Stages:
# 1. Compiling Native C++17 DSP Analyzer (libbitchord_analysis.a)
# 2. Linking KMP SharedUI Static Framework (iosArm64)
# 3. Archiving Xcode Project (iosApp) for generic/platform=iOS
# 4. Packaging Payload/BitChord.app into BitChord-v1.8-beta1-iPadOS.ipa
# ==============================================================================

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD_DIR="${ROOT_DIR}/build"
PAYLOAD_DIR="${BUILD_DIR}/Payload"
APP_BUNDLE="${PAYLOAD_DIR}/BitChord.app"
IPA_OUTPUT="${BUILD_DIR}/BitChord-v1.8-beta1-iPadOS.ipa"
UNSIGNED_IPA_OUTPUT="${BUILD_DIR}/BitChord-iPadOS-unsigned.ipa"

echo "[Stage 1/4] Compiling Native C++17 DSP Analyzer (libbitchord_analysis.a)"
bash "${ROOT_DIR}/scripts/compile_dsp_ios.sh"

echo "[Stage 2/4] Linking KMP SharedUI Static Framework (iosArm64)"
chmod +x "${ROOT_DIR}/gradlew" || true
"${ROOT_DIR}/gradlew" :shared:compileKotlinIosArm64 :sharedUi:prepareComposeResourcesTaskForCommonMain :sharedUi:linkReleaseFrameworkIosArm64 --no-daemon

echo "[Stage 3/4] Archiving Xcode Project (iosApp) for generic/platform=iOS"
mkdir -p "${APP_BUNDLE}"
FRAMEWORK_DIR="${ROOT_DIR}/sharedUi/build/bin/iosArm64/releaseFramework"
if command -v xcrun >/dev/null 2>&1 && [ -d "${FRAMEWORK_DIR}/SharedUI.framework" ]; then
    IPHONEOS_SDK="$(xcrun --sdk iphoneos --show-sdk-path)"
    xcrun --sdk iphoneos swiftc \
        -target arm64-apple-ios16.0 \
        -sdk "${IPHONEOS_SDK}" \
        -O \
        -Xlinker -ObjC \
        -F "${FRAMEWORK_DIR}" \
        -framework SharedUI \
        -L "${ROOT_DIR}/native/libs/iosArm64" \
        -lbitchord_analysis \
        -lsqlite3 -lz -lc++ \
        -framework UIKit \
        -framework SwiftUI \
        -framework Foundation \
        -framework AVFoundation \
        -framework AudioToolbox \
        -framework Accelerate \
        -framework JavaScriptCore \
        -framework MediaPlayer \
        -framework Security \
        -framework WebKit \
        -framework Metal \
        -framework QuartzCore \
        -framework CoreGraphics \
        -framework CoreText \
        -parse-as-library \
        "${ROOT_DIR}/iosApp/Sources/iOSApp.swift" \
        -o "${APP_BUNDLE}/BitChord"
    chmod 755 "${APP_BUNDLE}/BitChord"
elif command -v xcodebuild >/dev/null 2>&1; then
    xcodebuild \
        -project "${ROOT_DIR}/iosApp/iosApp.xcodeproj" \
        -scheme "iosApp" \
        -configuration "Release" \
        -sdk "iphoneos" \
        -destination "generic/platform=iOS" \
        SYMROOT="${BUILD_DIR}/xcode" \
        CODE_SIGN_IDENTITY="" \
        CODE_SIGNING_REQUIRED="NO" \
        CODE_SIGNING_ALLOWED="NO" \
        clean build || true
fi

echo "[Stage 4/4] Packaging Payload/BitChord.app into BitChord-v1.8-beta1-iPadOS.ipa"
cp "${ROOT_DIR}/iosApp/Info.plist" "${APP_BUNDLE}/Info.plist"
cp "${ROOT_DIR}/iosApp/iosApp.entitlements" "${APP_BUNDLE}/iosApp.entitlements"

if command -v ldid >/dev/null 2>&1 && [ -f "${APP_BUNDLE}/BitChord" ]; then
    (cd "${BUILD_DIR}" && ldid -S"${ROOT_DIR}/iosApp/iosApp.entitlements" Payload/BitChord.app/BitChord)
fi

python3 "${ROOT_DIR}/scripts/package_ipa_bundle.py"
echo "Successfully generated sideload-ready IPA: ${IPA_OUTPUT}"
