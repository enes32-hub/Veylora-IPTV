<p align="center">
  <picture>
    <source media="(prefers-color-scheme: light)" srcset="extras/logo_light.png">
    <img src="extras/logo_dark.png" alt="OwnTV libmpv" width="520">
  </picture>
</p>

<p align="center">
  <b>The mpv engine inside OwnTV</b><br>
  <sub>Newest mpv · newest FFmpeg · updated every month</sub>
</p>

<p align="center">
  <img alt="Platform" src="https://img.shields.io/badge/platform-Android%20library-3DDC84?logo=android&logoColor=white">
  <img alt="mpv" src="https://img.shields.io/badge/mpv-master%202a4eb8067-6D2A8C">
  <img alt="FFmpeg" src="https://img.shields.io/badge/FFmpeg-9.0.2-007808?logo=ffmpeg&logoColor=white">
  <img alt="minSdk" src="https://img.shields.io/badge/minSdk-26-3DDC84">
  <img alt="License" src="https://img.shields.io/badge/license-GPLv3%20binaries%20%C2%B7%20MIT%20wrapper-blue">
  <img alt="Built with the help of AI" src="https://img.shields.io/badge/built%20with-the%20help%20of%20AI-8A2BE2">
</p>

<p align="center">
  <a href="https://github.com/ahXN00/OwnTV_libmpv/actions/workflows/build.yaml">
    <img alt="Build" src="https://github.com/ahXN00/OwnTV_libmpv/actions/workflows/build.yaml/badge.svg">
  </a>
  <a href="https://github.com/ahXN00/OwnTV_libmpv/actions/workflows/publish.yaml">
    <img alt="Publish" src="https://github.com/ahXN00/OwnTV_libmpv/actions/workflows/publish.yaml/badge.svg">
  </a>
  <a href="https://github.com/ahXN00/OwnTV_libmpv/actions/workflows/monthly-update.yaml">
    <img alt="Monthly mpv update" src="https://github.com/ahXN00/OwnTV_libmpv/actions/workflows/monthly-update.yaml/badge.svg">
  </a>
</p>

---

OwnTV libmpv is the **mpv playback engine** of [OwnTV](https://github.com/ahXN00/OwnTV), the
open-source IPTV **player** for Android TV and phones: [mpv](https://mpv.io/) and
[FFmpeg](https://ffmpeg.org/) compiled for Android, with the small Kotlin/JNI wrapper the apps talk to.
It is built from [jarnedemeulemeester/libmpv-android](https://github.com/jarnedemeulemeester/libmpv-android)
and published as `tv.own.owntv:libmpv`. [OwnTV Core](https://github.com/ahXN00/OwnTV_Core)'s
`:player-core` uses it; both apps get it from there and never name it themselves.

> **Keeping it current is a monthly job — [UPDATING.md](UPDATING.md) is the procedure.**

> ⚠️ OwnTV provides **no** channels, playlists, subscriptions, streams or media content. This is a
> player engine; the sources are the user's own.

---

## 💬 Community

Questions, ideas, bug reports — or just want to follow along? **Join the OwnTV Telegram group:**

### 👉 [t.me/owntvplayer](https://t.me/owntvplayer)

Scan to join from your phone:

<a href="https://t.me/owntvplayer"><img src="extras/qr-codes/telegram_qr_code.jpg" alt="Scan to join the OwnTV Telegram group" width="170"></a>

---

## ✨ Why OwnTV builds its own

| What OwnTV needs | Upstream — jarnedemeulemeester/libmpv-android 1.0.0 | **Ours — OwnTV libmpv** |
|---|---|---|
| **The newest mpv.** mpv makes no bug-fix releases; every fix after a release is only on its main branch | 0.41.0 (December 2025) | **newest main branch, checked and updated every month** |
| **Interlaced TV channels without comb lines** (mpv's automatic deinterlacing) | ❌ does not work — the needed FFmpeg filter is missing | ✅ works |
| **Night mode and volume levelling on the mpv player** | ❌ plays silence — FFmpeg filters are missing | ✅ works |
| **Recovery from a frozen stream** — OwnTV starts a fresh player while the frozen one is still shutting down | ✅ | ✅ (same code as upstream) |
| **Newest FFmpeg** (the part that opens and decodes the streams) | 8.1 | **9.0.2** |
| **Rebuildable exactly** — every ingredient pinned to a fixed version | ✅ | ✅ |
| **Download size** (arm64, compressed) | 11.0 MB | 11.3 MB |
| **How often it gets updated** | whenever its one maintainer releases (5 times in 27 months) | **every month** ([UPDATING.md](UPDATING.md)) |

## 🔢 Versions compared

What official mpv offers, what the upstream library we forked ships, and what this build ships.
The "ours" column changes with every release — update it in the same commit as `depinfo.sh`.

| | Official mpv | Upstream — jarnedemeulemeester/libmpv-android 1.0.0 | **Ours — OwnTV libmpv** |
|---|---|---|---|
| mpv | newest release **0.41.0** (2025-12-21); no point releases, fixes land on master only | 0.41.0 | **master `2a4eb8067`** (0.41.0 + 1,072 commits, 2026-09-23), one carried patch |
| FFmpeg | — (newest FFmpeg release: 9.0.2) | 8.1 | **9.0.2** |
| FFmpeg filters | — | none | **29, allowlisted** (deinterlace, rotation, audio dynamics, EQ + plumbing) |
| FFmpeg muxers / encoders | — | none / none | mpegts, matroska / none |
| libplacebo · libass · dav1d | — | 7.360.1 · 0.17.4 · 1.5.3 | 7.360.1 · 0.17.5 · 1.5.4 |
| Several instances at once | — | yes | yes (same JNI) |
| ABIs | — | armeabi-v7a, arm64-v8a, x86, x86_64 | armeabi-v7a, arm64-v8a, x86_64 |
| Published as | — | `dev.jdtech.mpv:libmpv` (Maven Central) | `tv.own.owntv:libmpv` (OwnTV Maven, no login) |
| How often it moves | a release every 6–9 months | when its maintainer tags (5 releases in 27 months) | **monthly** ([UPDATING.md](UPDATING.md)) |

## 📦 What's inside

Every version is pinned in [`buildscripts/include/depinfo.sh`](buildscripts/include/depinfo.sh).

- **mpv** — a pinned commit on `master` (`v_mpv`), built with Lua, libass and libplacebo; no libcurl,
  no Vulkan. OwnTV carries one patch on top ([`buildscripts/patches/mpv/`](buildscripts/patches/mpv)).
- **FFmpeg** — a release tag (`v_ffmpeg`), `--enable-gpl --enable-version3`, MediaCodec + JNI,
  mbedTLS, dav1d, libxml2 (DASH). **No encoders.** Muxers: `mpegts` and `matroska` only, so mpv's
  `stream-record` can write what it is already playing. Filters: the allowlist in
  [`buildscripts/scripts/ffmpeg.sh`](buildscripts/scripts/ffmpeg.sh), each commented with who needs it.
- **ABIs** — `armeabi-v7a`, `arm64-v8a`, `x86_64` (no 32-bit x86, which neither app ships). 64-bit
  libraries are 16 KB page-aligned.
- **minSdk** 26. **Java package** `dev.jdtech.mpv`, kept from upstream on purpose.

### Differences from upstream, in full

| File | Change |
|---|---|
| `buildscripts/include/depinfo.sh` | `v_mpv` is a master commit, not a release |
| `buildscripts/include/download-deps.sh` | mpv cloned blobless + checkout of the pinned commit |
| `buildscripts/scripts/ffmpeg.sh` | filter allowlist, `mpegts`/`matroska` muxers |
| `buildscripts/build.sh` | no 32-bit x86 |
| `buildscripts/patches/mpv/0001-revert-hls-manifest-through-stream.patch` | undoes mpv `13a4bfbc1`: with it, IPTV panels answer 403 on every HLS segment |
| `libmpv/src/main/cpp/main.cpp` | mpv's log follows the app's `msg-level` (upstream always asked for verbose) |
| `libmpv/src/main/cpp/event.cpp`, `jni_utils.*`, `MPVLib.kt` | end of file reaches the app with its reason and error code (`EventObserver.endFile`, no-op by default) |
| `libmpv/build.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml` | publishes `tv.own.owntv:libmpv` to OwnTV's Maven repository instead of Maven Central; `abiFilters` |
| `renovate.json` | the mpv rule removed (mpv is bumped by the monthly workflow) |
| `tools/inspect_aar.py` | new — the build contract |
| Upstream Maven publishing | Not included in this fork; publishing into the upstream repository is not part of this source release. |
| `tools/render_logo.py`, `extras/` | new — this README's logo |
| `.github/workflows/*` | `build`, `publish`, `monthly-update` (upstream's `publish.yaml` removed) |

Keep this table true: it is what makes an `upstream` merge reviewable.

## ✅ The build contract

Every CI build ends with `tools/inspect_aar.py`, which fails the build unless:

- the AAR holds exactly the three ABIs, every 64-bit `.so` is 16 KB-aligned, nothing links `libvulkan`;
- the mpv inside is the commit pinned in `depinfo.sh`;
- every allowlisted filter is really compiled in (read from FFmpeg's generated `filter_list.c`).

It also prints the mpv/FFmpeg versions and the per-ABI size; the publish workflow puts that report in
the GitHub Release notes.

## 🛠️ Building

The native build needs Linux (or macOS); it runs in GitHub Actions. On a Linux machine:

```sh
cd buildscripts
./download.sh        # SDK, NDK and all pinned sources
./patch.sh           # applies buildscripts/patches/
./build.sh           # all ABIs, then the AAR
python3 ../tools/inspect_aar.py ../libmpv/build/outputs/aar/libmpv-release.aar
```

Single component or ABI: `./build.sh -n ffmpeg`, `./build.sh --arch arm64 mpv`, then `./build.sh -n`.

## 🚀 Releasing

Versions are date-based: **`YYYY.MM.N`** (`2026.09.0`, then `2026.10.0`; `N` counts extra releases in
the same month). Push the tag `vYYYY.MM.N` on `main`:

1. `publish.yaml` checks the tag format,
2. runs the full build and contract check (the same `build.yaml` as every push),
3. publishes `tv.own.owntv:libmpv:YYYY.MM.N` to OwnTV's Maven repository (the `gh-pages` branch of
   OwnTV_Core, written with the `CORE_BUMP_TOKEN` secret; the newest 12 versions are kept),
4. creates the GitHub Release with the AAR and the contract report,
5. opens a **"Pin libmpv YYYY.MM.N" pull request on OwnTV_Core**. It never merges itself — a new
   engine waits for the device test.

A published version is immutable. Re-running on a tag that already published stops with "already
published"; fix forward with the next `N`.

## 📥 Consuming it

Public, **no account or token needed**:

```kotlin
// settings.gradle.kts
maven {
    url = uri("https://ahxn00.github.io/OwnTV_Core/maven")
    content { includeGroup("tv.own.owntv") }
}

// gradle/libs.versions.toml
libmpv = { group = "tv.own.owntv", name = "libmpv", version.ref = "libmpv" }
```

## 🤝 Contributing

Contributions, bug reports and ideas are welcome. Two things to know before opening a pull request:

- **Never pin a floating source.** Every dependency is a tag or a commit in `depinfo.sh`; a build must
  be reproducible from the tag alone.
- **The JNI stays per-instance.** OwnTV's recovery creates a new mpv while a frozen one is still being
  destroyed; a single global instance aborts the app there.

## 🙏 Credits

This library is a thin layer over other people's excellent work. Thank you to all of them.

- [**mpv**](https://mpv.io/) ([mpv-player/mpv](https://github.com/mpv-player/mpv)) — the player itself.
- [**jarnedemeulemeester/libmpv-android**](https://github.com/jarnedemeulemeester/libmpv-android) — the
  Android library this repository is forked from: its build scripts and its multi-instance JNI wrapper.
- [**mpv-android**](https://github.com/mpv-android/mpv-android) — the original Android build scripts
  and JNI code both of the above grew from.
- [**FFmpeg**](https://ffmpeg.org/) — demuxing, decoding and filtering.

### 🧩 Built with

[libass](https://github.com/libass/libass) ·
[libplacebo](https://code.videolan.org/videolan/libplacebo) ·
[dav1d](https://code.videolan.org/videolan/dav1d) ·
[Mbed TLS](https://github.com/Mbed-TLS/mbedtls) ·
[FreeType](https://freetype.org/) ·
[HarfBuzz](https://github.com/harfbuzz/harfbuzz) ·
[FriBidi](https://github.com/fribidi/fribidi) ·
[Fontconfig](https://gitlab.freedesktop.org/fontconfig/fontconfig) ·
[libxml2](https://gitlab.gnome.org/GNOME/libxml2) ·
[libunibreak](https://github.com/adah1972/libunibreak) ·
[Lua](https://www.lua.org/) — see each project for its own license.

The logo's mpv-style mark is OwnTV's own flat drawing in mpv's colours; mpv's own logo belongs to the
mpv project.

### 🎨 Brand

The lowercase **owntv** wordmark was designed for OwnTV by [@m3th0d93](https://github.com/m3th0d93)
in [issue #227](https://github.com/ahXN00/OwnTV/issues/227).

## ⚖️ Legal

OwnTV libmpv is media **player** infrastructure only. It ships with no channels, playlists,
subscriptions or content, and does not endorse or facilitate access to unauthorized streams. Users
of the apps built on it are solely responsible for the sources they add.

## 📄 License

The wrapper code (Kotlin, JNI, build scripts) is **MIT** — see [LICENSE](LICENSE), kept from upstream.

The **binaries are GPLv3 as a whole**: FFmpeg is configured with `--enable-gpl --enable-version3` and
mpv is built with its GPL parts. Anything shipping this AAR ships GPLv3 code and owes its users the
corresponding source: this repository at the release tag plus the pinned upstream sources it names.

---

<sub>OwnTV is an open-source, player-only project, built with the help of AI.</sub>
