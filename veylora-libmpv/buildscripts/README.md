# Building

The native build runs on **Linux** (or macOS). In this repository it runs in GitHub Actions
(`.github/workflows/build.yaml`); the steps below are the same by hand.

## Download dependencies

`download.sh` installs the Android SDK and NDK and downloads every source at the version pinned in
`include/depinfo.sh` (mpv at a fixed commit, everything else at a tag).

On Debian/Ubuntu or RHEL/Fedora it also installs the system packages it needs.

```shell
./download.sh
```

If you already have the Android SDK, symlink `sdk/android-sdk-linux` to your SDK root before running
it; the script still installs the SDK packages it needs.

## Patching

```shell
./patch.sh
```

Applies OwnTV's patches from `patches/<dependency>/` (currently one, on mpv — see its file header).
It resets each patched dependency to a clean state first, so it is safe to run again.

## Build

```shell
./build.sh
```

Builds every dependency and then the AAR, for `armeabi-v7a`, `arm64-v8a` and `x86_64`.
Add `--clean` to rebuild from scratch.

One architecture only:

```shell
./build.sh --arch arm64 mpv
./build.sh -n
```

One component only (after a change to it), then the AAR:

```shell
./build.sh -n ffmpeg        # add --clean for a clean rebuild, --arch for one architecture
./build.sh -n
```

The AAR lands in `../libmpv/build/outputs/aar/libmpv-release.aar`. Check it against the build contract:

```shell
python3 ../tools/inspect_aar.py ../libmpv/build/outputs/aar/libmpv-release.aar
```

## Logs on a device

The library logs to logcat under the tag `mpv`, at the level the app asks for with mpv's `msg-level`:

```shell
adb logcat -s mpv
```
