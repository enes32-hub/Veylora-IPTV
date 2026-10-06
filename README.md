# Veylora IPTV — 1.0.0

Independent Android TV IPTV player derived from OwnTV. This repository contains
source code and build resources only, not APKs, AABs, IPTV accounts or API credentials.
The player does not supply channels or grant rights to any content.

## Source layout

```text
  veylora-tv             Modified Android TV application
  veylora-core        Modified core and playback modules
  veylora-libmpv      Available upstream native wrapper/build sources
```
Keep these directories side by side; the application uses a Gradle composite build.

## Build
Use JDK 17, Android SDK 37 and the included Gradle wrapper. Configure ANDROID_HOME
or your own local.properties (never commit it).
From veylora-tv:

```sh
  ./gradlew :app:assembleStandardDebug
  ./gradlew :app:assembleStandardRelease :app:bundleStandardRelease
  ./gradlew :app:testStandardDebugUnitTest :veylora-core:core:testDebugUnitTest
```

On Windows use gradlew.bat. Dependencies require Internet access.
Optional personal TMDB access is supplied at build time using
  -Pveylora.tmdbTokenFile=/private/path/token.txt
No token is included here. Compiling a token into an APK does not keep it secret.
Release signing uses KEYSTORE_FILE, KEYSTORE_PASSWORD, KEY_ALIAS and KEY_PASSWORD.
An optional external properties file can be selected with VEYLORA_SIGNING_PROPERTIES.
Never commit signing keys or passwords. Unsigned artifacts are not store-ready.

## Provenance and licenses
Upstream: https://github.com/ahXN00/OwnTV
Core: https://github.com/ahXN00/OwnTV_Core
Native wrapper: https://github.com/ahXN00/OwnTV_libmpv
Existing copyright and license notices are retained in each source tree.
Module README and CHANGELOG files describe upstream history, not this fork's release
features or publication instructions. Follow this root document to build this fork.
The application and core are distributed under GPLv3; dependencies retain their
respective licenses. This fork is not an official OwnTV release.
Changes include Veylora branding, discovery/filter controls, multilingual metadata
handling, navigation, playback controls and crash fixes. This is the modified
working-tree snapshot, not an unmodified upstream release.

The build resolves tv.own.owntv:libmpv:2026.10.0 from upstream Maven. The included
native build recipe is not a claim that every transitive native source required
for public binary distribution is already bundled. Verify corresponding sources
and notices for the exact distributed binaries before distributing a release.
TMDB usage remains subject to TMDB terms and attribution requirements.

## Release status
Source publication only. Production signing, release-device testing, privacy/store
declarations and final dependency/license review remain separate release steps.
