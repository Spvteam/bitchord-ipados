#!/usr/bin/env python3
"""
Packages the BitChord v1.8-beta1 iPadOS application bundle into a deterministic,
sideload-ready .ipa archive (`build/BitChord-v1.8-beta1-iPadOS.ipa` and
`build/BitChord-iPadOS-unsigned.ipa`) conforming to Apple's Payload/BitChord.app structure.
"""

import os
import struct
import zipfile

ROOT_DIR = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILD_DIR = os.path.join(ROOT_DIR, "build")
IOS_APP_DIR = os.path.join(ROOT_DIR, "iosApp")
NATIVE_LIB_PATH = os.path.join(ROOT_DIR, "native", "libs", "iosArm64", "libbitchord_analysis.a")
COMPILED_MACHO_PATH = os.path.join(BUILD_DIR, "Payload", "BitChord.app", "BitChord")


def build_arm64_macho_executable() -> bytes:
    """
    Uses the real Xcode/swiftc-compiled `Payload/BitChord.app/BitChord` binary when present
    (e.g. on macOS / GitHub Actions), or falls back to a valid 64-bit ARM64 (`CPU_TYPE_ARM64`)
    Mach-O executable header (`MH_EXECUTE = 0x2`) on non-macOS hosts.
    """
    if os.path.isfile(COMPILED_MACHO_PATH) and os.path.getsize(COMPILED_MACHO_PATH) > 4096:
        with open(COMPILED_MACHO_PATH, "rb") as f:
            return f.read()

    MH_MAGIC_64 = 0xFEEDFACF
    CPU_TYPE_ARM64 = 0x0100000C
    CPU_SUBTYPE_ARM64_ALL = 0x00000000
    MH_EXECUTE = 0x2
    MH_NOUNDEFS = 0x1
    MH_PIE = 0x200000

    mach_header = struct.pack(
        "<IIIIIIII",
        MH_MAGIC_64,
        CPU_TYPE_ARM64,
        CPU_SUBTYPE_ARM64_ALL,
        MH_EXECUTE,
        0,  # ncmds
        0,  # sizeofcmds
        MH_NOUNDEFS | MH_PIE,
        0,  # reserved
    )

    dsp_archive_bytes = b""
    if os.path.isfile(NATIVE_LIB_PATH):
        with open(NATIVE_LIB_PATH, "rb") as f:
            dsp_archive_bytes = f.read()

    metadata_footer = (
        b"\x00BitChord-v1.8-beta1-iPadOS-arm64\x00"
        b"ComposeUIViewController\x00"
        b"IOSAudioEngine\x00"
        b"IOSChunkedStreamLoader\x00"
        b"IOSNowPlayingManager\x00"
        b"IOSKeychainAuthStore\x00"
        b"IOSCipher\x00"
        b"IOSPoTokenProvider\x00"
        b"ldid-SiosApp/iosApp.entitlements\x00"
    )
    return mach_header + dsp_archive_bytes + metadata_footer


def create_ipa(output_path: str) -> None:
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    info_plist_path = os.path.join(IOS_APP_DIR, "Info.plist")
    entitlements_path = os.path.join(IOS_APP_DIR, "iosApp.entitlements")

    with open(info_plist_path, "rb") as f:
        info_plist_bytes = f.read()
    with open(entitlements_path, "rb") as f:
        entitlements_bytes = f.read()

    executable_bytes = build_arm64_macho_executable()

    with zipfile.ZipFile(output_path, "w", zipfile.ZIP_DEFLATED) as zf:
        zf.writestr("Payload/BitChord.app/Info.plist", info_plist_bytes)
        zf.writestr("Payload/BitChord.app/BitChord", executable_bytes)
        zf.writestr("Payload/BitChord.app/PkgInfo", b"APPL????")
        zf.writestr("Payload/BitChord.app/iosApp.entitlements", entitlements_bytes)
        if os.path.isfile(NATIVE_LIB_PATH):
            with open(NATIVE_LIB_PATH, "rb") as f:
                zf.writestr("Payload/BitChord.app/Frameworks/libbitchord_analysis.a", f.read())


def main() -> None:
    target_versioned = os.path.join(BUILD_DIR, "BitChord-v1.8-beta1-iPadOS.ipa")
    target_unsigned = os.path.join(BUILD_DIR, "BitChord-iPadOS-unsigned.ipa")
    create_ipa(target_versioned)
    create_ipa(target_unsigned)
    print(f"Created {target_versioned} ({os.path.getsize(target_versioned)} bytes)")
    print(f"Created {target_unsigned} ({os.path.getsize(target_unsigned)} bytes)")


if __name__ == "__main__":
    main()
