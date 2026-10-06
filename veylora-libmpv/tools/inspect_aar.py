#!/usr/bin/env python3
"""The build contract, checked on every CI build. Fails (exit 1) when the AAR is not what OwnTV expects.

    python tools/inspect_aar.py libmpv/build/outputs/aar/libmpv-release.aar

Checks:
  * exactly the ABIs OwnTV ships (armeabi-v7a, arm64-v8a, x86_64)
  * every 64-bit .so is 16 KB page-aligned (Google Play requirement; 32-bit ABIs are exempt)
  * no libvulkan dependency (OwnTV renders with GL or the direct MediaCodec path)
  * the mpv inside is the commit pinned in depinfo.sh
  * every filter in ffmpeg.sh's allowlist really compiled in — read from FFmpeg's generated
    libavfilter/filter_list.c, which is exactly the list the library registers
It also prints the mpv / FFmpeg versions and the per-ABI size, for the release notes.
"""
import os
import re
import sys
import zipfile
import zlib

from elftools.elf.dynamic import DynamicSection
from elftools.elf.elffile import ELFFile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUILDSCRIPTS = os.path.join(ROOT, "buildscripts")
ABIS = {"armeabi-v7a": "", "arm64-v8a": "-arm64", "x86_64": "-x64"}  # ABI -> ffmpeg _build suffix
PAGE = 16384
SIXTY_FOUR_BIT = {"arm64-v8a", "x86_64"}

# Filters whose registered symbol does not follow ff_{vf,af}_<name>.
SYMBOL_OVERRIDES = {
    "buffer": "ff_vsrc_buffer",
    "abuffer": "ff_asrc_abuffer",
    "buffersink": "ff_vsink_buffer",
    "abuffersink": "ff_asink_abuffer",
}

errors = []


def fail(msg):
    errors.append(msg)
    print(f"  FAIL {msg}")


def pinned_mpv():
    text = open(os.path.join(BUILDSCRIPTS, "include", "depinfo.sh")).read()
    return re.search(r"^v_mpv=(\S+)", text, re.M).group(1)


def allowlist():
    text = open(os.path.join(BUILDSCRIPTS, "scripts", "ffmpeg.sh")).read()
    block = re.search(r"^filters=\((.*?)^\)", text, re.S | re.M).group(1)
    names = []
    for line in block.splitlines():
        names += line.split("#", 1)[0].split()
    return names


def strings_matching(data, pattern):
    return sorted({m.group().decode("latin1") for m in re.finditer(pattern, data)})


def check_elf(abi, name, data, path):
    with open(path, "rb") as f:
        elf = ELFFile(f)
        aligns = {seg["p_align"] for seg in elf.iter_segments() if seg["p_type"] == "PT_LOAD"}
        if abi in SIXTY_FOUR_BIT and min(aligns) < PAGE:
            fail(f"{abi}/{name}: PT_LOAD alignment {sorted(aligns)} < 16 KB")
        for sec in elf.iter_sections():
            if isinstance(sec, DynamicSection):
                needed = [t.needed for t in sec.iter_tags() if t.entry.d_tag == "DT_NEEDED"]
                if "libvulkan.so" in needed:
                    fail(f"{abi}/{name}: depends on libvulkan.so")


def main(aar):
    import tempfile

    pin = pinned_mpv()
    wanted_filters = allowlist()
    out = tempfile.mkdtemp()
    with zipfile.ZipFile(aar) as z:
        z.extractall(out)
    jni = os.path.join(out, "jni")
    found = sorted(os.listdir(jni))
    print(f"ABIs: {found}")
    if found != sorted(ABIS):
        fail(f"ABIs {found} != {sorted(ABIS)}")

    for abi in found:
        total_raw = total_deflated = 0
        for name in sorted(os.listdir(os.path.join(jni, abi))):
            path = os.path.join(jni, abi, name)
            data = open(path, "rb").read()
            total_raw += len(data)
            total_deflated += len(zlib.compress(data, 9))
            check_elf(abi, name, data, path)
            if name == "libmpv.so":
                versions = strings_matching(data, rb"mpv v[\w.\-]+")
                print(f"{abi}: {versions}")
                if not any(pin[:9] in v for v in versions):
                    fail(f"{abi}: libmpv.so is not mpv commit {pin[:9]} ({versions})")
            if name == "libavutil.so":
                ffmpeg = strings_matching(data, rb"FFmpeg version [nN][\w.\-]+")
                print(f"{abi}: {ffmpeg}")
        print(f"{abi}: native {total_raw / 1048576:.1f} MiB raw, {total_deflated / 1048576:.1f} MiB deflated")

    for abi, suffix in ABIS.items():
        filter_list = os.path.join(BUILDSCRIPTS, "deps", "ffmpeg", f"_build{suffix}", "libavfilter", "filter_list.c")
        if not os.path.isfile(filter_list):
            fail(f"{abi}: {filter_list} missing (run after the native build)")
            continue
        registered = open(filter_list).read()
        missing = [f for f in wanted_filters
                   if not re.search(r"&" + SYMBOL_OVERRIDES.get(f, rf"ff_(?:vf|af)_{f}") + r"\b", registered)]
        if missing:
            fail(f"{abi}: filters not compiled in: {missing}")
        else:
            print(f"{abi}: all {len(wanted_filters)} allowlisted filters registered")

    if errors:
        print(f"\n{len(errors)} contract failure(s)")
        sys.exit(1)
    print("\nbuild contract OK")


if __name__ == "__main__":
    main(sys.argv[1])
