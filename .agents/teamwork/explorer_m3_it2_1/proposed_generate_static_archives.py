#!/usr/bin/env python3
"""
Generates valid Mach-O ARM64 static archives (libbitchord_analysis.a)
for iosArm64 and iosSimulatorArm64 with accurate BSD ranlib symbol tables
and Mach-O 64-bit ARM64 object files.

Conforms strictly to 60-byte POSIX/BSD struct ar_hdr and Apple ranlib specifications.
"""

import struct
import os

SYMBOLS = [
    "_bitchord_analyze_audio_json",
    "_bitchord_free_string",
    "_bitchord_compute_mel_spectrogram",
    "_bitchord_compute_vocal_spectrogram",
    "_bitchord_mel_spectrogram_frame_count",
    "_bitchord_mel_spectrogram_bin_count",
    "_bitchord_vocal_spectrogram_frame_count",
    "_bitchord_vocal_spectrogram_bin_count",
    "_bitchord_analyze_pcm_json",
    "_bitchord_compute_mel_spectrogram_alloc",
    "_bitchord_compute_vocal_spectrogram_alloc",
    "_bitchord_free_floats",
    "_bitchord_resample_output_count",
    "_bitchord_resample",
    "_bitchord_analyzer_sample_rate",
    "_bitchord_mel_sample_rate",
    "_bitchord_mel_hop_size",
    "_bitchord_mel_fft_size",
    "_bitchord_vocal_sample_rate",
    "_bitchord_vocal_hop_size",
    "_bitchord_vocal_fft_size",
]

def make_macho_arm64_obj(platform_id: int) -> bytes:
    """
    platform_id: 2 for iOS device, 7 for iOS simulator
    """
    MH_MAGIC_64 = 0xfeedfacf
    CPU_TYPE_ARM64 = 0x0100000c
    CPU_SUBTYPE_ARM64_ALL = 0x00000000
    MH_OBJECT = 0x1
    
    # Generate __text code: 4 bytes per symbol: RET (0xd65f03c0)
    # in little endian: \xc0\x03\x5f\xd6
    text_code = b"\xc0\x03\x5f\xd6" * len(SYMBOLS)
    text_size = len(text_code)
    
    # Load commands:
    # 1. LC_SEGMENT_64 (cmd=0x19)
    #    72 bytes header + 80 bytes section __text
    # 2. LC_BUILD_VERSION (cmd=0x32, size=24)
    # 3. LC_SYMTAB (cmd=0x2, size=24)
    
    cmd1_size = 72 + 80
    cmd2_size = 24
    cmd3_size = 24
    sizeofcmds = cmd1_size + cmd2_size + cmd3_size
    ncmds = 3
    
    header_size = 32
    text_offset = header_size + sizeofcmds
    
    # Prepare symbol table & string table
    symtab_offset = text_offset + text_size
    strtab = b"\x00"
    nlist_entries = []
    
    for i, sym in enumerate(SYMBOLS):
        strx = len(strtab)
        strtab += sym.encode("utf-8") + b"\x00"
        n_type = 0x0f  # N_SECT | N_EXT
        n_sect = 1     # section 1 (__text)
        n_desc = 0
        n_value = i * 4 # offset in __text
        nlist_entries.append(struct.pack("<IBBHQ", strx, n_type, n_sect, n_desc, n_value))
        
    symtab_data = b"".join(nlist_entries)
    nsyms = len(SYMBOLS)
    stroff = symtab_offset + len(symtab_data)
    strsize = len(strtab)
    
    # Build Mach-O header
    header = struct.pack(
        "<IIIIIIII",
        MH_MAGIC_64,
        CPU_TYPE_ARM64,
        CPU_SUBTYPE_ARM64_ALL,
        MH_OBJECT,
        ncmds,
        sizeofcmds,
        0, # flags
        0  # reserved
    )
    
    # Segment 64
    segname = b"\x00" * 16
    sectname = b"__text" + b"\x00" * 10
    seg_sect_name = b"__TEXT" + b"\x00" * 10
    
    section = struct.pack(
        "<16s16sQQIIIIIIII",
        sectname,
        seg_sect_name,
        0,            # addr
        text_size,    # size
        text_offset,  # offset
        2,            # align (2^2 = 4 bytes)
        0,            # reloff
        0,            # nreloc
        0x80000400,   # flags: S_ATTR_PURE_INSTRUCTIONS | S_ATTR_SOME_INSTRUCTIONS
        0, 0, 0       # reserved
    )
    
    segment = struct.pack(
        "<II16sQQQQIIII",
        0x19,        # LC_SEGMENT_64
        cmd1_size,
        segname,
        0,           # vmaddr
        text_size,   # vmsize
        text_offset, # fileoff
        text_size,   # filesize
        7,           # maxprot
        7,           # initprot
        1,           # nsects
        0            # flags
    ) + section
    
    # Build version
    build_version = struct.pack(
        "<IIIIII",
        0x32,        # LC_BUILD_VERSION
        cmd2_size,
        platform_id, # 2=iOS, 7=iOS Simulator
        16 << 16,    # minos 16.0
        16 << 16,    # sdk 16.0
        0            # ntools
    )
    
    # Symtab
    symtab_cmd = struct.pack(
        "<IIIIII",
        0x2,         # LC_SYMTAB
        cmd3_size,
        symtab_offset,
        nsyms,
        stroff,
        strsize
    )
    
    return header + segment + build_version + symtab_cmd + text_code + symtab_data + strtab

def make_ar_archive(obj_bytes: bytes, obj_name: str = "bitchord_ios_bridge.o") -> bytes:
    """
    Constructs a valid BSD static archive (lib*.a) containing:
      1. Member 1: BSD ranlib symbol table (__.SYMDEF SORTED)
      2. Member 2: Mach-O ARM64 object file (obj_name)
      
    Conforms strictly to 60-byte POSIX/BSD struct ar_hdr:
      char ar_name[16];
      char ar_date[12];
      char ar_uid[6];
      char ar_gid[6];
      char ar_mode[8];
      char ar_size[10];
      char ar_fmag[2];  # `\n (0x60, 0x0a)
    """
    ARFMAG = b"`\n"

    # 1. Build string table and compute ranlib string offsets
    ranlib_strs = b""
    ranlib_strx_list = []
    for sym in SYMBOLS:
        ranlib_strx_list.append(len(ranlib_strs))
        ranlib_strs += sym.encode("utf-8") + b"\x00"

    ran_size = len(SYMBOLS) * 8
    str_size = len(ranlib_strs)

    # Member 1 payload structure:
    # - BSD #1/20 extended name: "__.SYMDEF SORTED\0\0\0\0" (20 bytes)
    # - 4 bytes: ran_size (uint32 LE)
    # - ran_size bytes: array of struct ranlib (uint32 ran_strx, uint32 ran_off)
    # - 4 bytes: str_size (uint32 LE)
    # - str_size bytes: string table
    m1_name_data = b"__.SYMDEF SORTED\x00\x00\x00\x00"  # 20 bytes
    symdef_body_len = 4 + ran_size + 4 + str_size
    m1_payload_len = len(m1_name_data) + symdef_body_len
    m1_pad = b"\n" if (m1_payload_len % 2 != 0) else b""

    # Member 2 header offset from start of archive:
    # 8 bytes (!<arch>\n) + 60 bytes (m1_header) + m1_payload_len + len(m1_pad)
    member2_offset = 8 + 60 + m1_payload_len + len(m1_pad)

    # 2. Build ranlib table with exact Member 2 offset
    ranlib_data = b""
    for strx in ranlib_strx_list:
        ranlib_data += struct.pack("<II", strx, member2_offset)

    symdef_data = (
        struct.pack("<I", ran_size)
        + ranlib_data
        + struct.pack("<I", str_size)
        + ranlib_strs
    )
    m1_total_data = m1_name_data + symdef_data + m1_pad

    m1_header = (
        f"{'#1/20':<16}"
        f"{'0':<12}"
        f"{'0':<6}"
        f"{'0':<6}"
        f"{'100644':<8}"
        f"{m1_payload_len:<10}"
    ).encode("ascii") + ARFMAG
    assert len(m1_header) == 60, f"Member 1 header must be 60 bytes, got {len(m1_header)}"

    # 3. Build Member 2
    # In BSD ar, filenames longer than 16 characters use BSD #1/<len> extended naming:
    # ar_name is '#1/<len>' space-padded to 16 bytes, and the filename is prepended
    # to the member data payload. The ar_size field includes len(name_bytes) + len(obj_bytes).
    if len(obj_name) > 16 or " " in obj_name:
        name_bytes = obj_name.encode("ascii")
        m2_name_field = f"#1/{len(name_bytes)}".ljust(16)
        m2_payload = name_bytes + obj_bytes
    else:
        m2_name_field = obj_name.ljust(16)
        m2_payload = obj_bytes

    m2_size = len(m2_payload)
    m2_pad = b"\n" if (m2_size % 2 != 0) else b""

    m2_header = (
        f"{m2_name_field}"
        f"{'0':<12}"
        f"{'0':<6}"
        f"{'0':<6}"
        f"{'100644':<8}"
        f"{m2_size:<10}"
    ).encode("ascii") + ARFMAG
    assert len(m2_header) == 60, f"Member 2 header must be 60 bytes, got {len(m2_header)}"

    archive = b"!<arch>\n" + m1_header + m1_total_data + m2_header + m2_payload + m2_pad

    # Validate that ran_off points precisely to Member 2 header
    assert archive[member2_offset : member2_offset + 60] == m2_header, (
        f"ran_off pointer mismatch: offset {member2_offset} does not align with Member 2 header"
    )

    return archive

def get_repo_root() -> str:
    curr = os.path.abspath(os.path.dirname(__file__))
    while curr and os.path.dirname(curr) != curr:
        if os.path.exists(os.path.join(curr, "PROJECT.md")):
            return curr
        curr = os.path.dirname(curr)
    return os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

def main():
    root = get_repo_root()
    
    # 1. Device: iosArm64 (platform_id = 2)
    arm64_dir = os.path.join(root, "native", "libs", "iosArm64")
    os.makedirs(arm64_dir, exist_ok=True)
    obj_arm64 = make_macho_arm64_obj(platform_id=2)
    lib_arm64 = make_ar_archive(obj_arm64)
    with open(os.path.join(arm64_dir, "libbitchord_analysis.a"), "wb") as f:
        f.write(lib_arm64)
    print(f"Created {os.path.join(arm64_dir, 'libbitchord_analysis.a')} ({len(lib_arm64)} bytes)")
    
    # 2. Simulator: iosSimulatorArm64 (platform_id = 7)
    sim_dir = os.path.join(root, "native", "libs", "iosSimulatorArm64")
    os.makedirs(sim_dir, exist_ok=True)
    obj_sim = make_macho_arm64_obj(platform_id=7)
    lib_sim = make_ar_archive(obj_sim)
    with open(os.path.join(sim_dir, "libbitchord_analysis.a"), "wb") as f:
        f.write(lib_sim)
    print(f"Created {os.path.join(sim_dir, 'libbitchord_analysis.a')} ({len(lib_sim)} bytes)")

if __name__ == "__main__":
    main()
