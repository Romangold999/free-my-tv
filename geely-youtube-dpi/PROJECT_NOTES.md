# PROJECT_NOTES.md — checkpoint for new chat branches

Last updated: 2026-11-10 (UTC)
Repository: https://github.com/Romangold999/free-my-tv
Working branch: `geely-youtube-dpi`
Project directory: `geely-youtube-dpi/`

## Purpose

Build a separate Android application for YouTube on:
- Geely Atlas Pro 2023 head unit, reported as GMC Custom Android 5.0 (API 21).
- User's Android 14 phone.

Required product direction:
- Landscape interface with large, car-friendly controls.
- YouTube search, videos, channels, subscriptions and fullscreen playback.
- ByeDPI engine embedded in the app; local SOCKS5 proxy at 127.0.0.1:1080.
- YouTube networking must explicitly use that proxy; starting a proxy alone does NOT mean YouTube traffic is routed through it.
- Editable ByeDPI command line and multiple named profiles; profile changes must not require rebuilding.
- Build APK through GitHub Actions and give user a direct, usable download route.
- Do not require user to install Android Studio or do complicated local development steps.
- Work only on branch `geely-youtube-dpi`; do not change `main` or unrelated project files.

## User's exact ByeDPI profile

Preserve this exact string, including repeated options and their order. Do not normalize, deduplicate, or rewrite it:

```text
-Ku -a3 -An -Kt,h -n vk.com -d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -r1+s -S -Mh,d -As -Kt,h -n vk.com -d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -S -Mh,d
```

The profile file `geely-youtube-dpi/profiles/user-supplied-youtube.txt` was corrected to match the Kotlin default in commit `a26dabd51c20dd99b41f34ed5b8759103fc0f4bd`.

## Current implementation

- Android app scaffold and landscape dark UI exist in `geely-youtube-dpi/app/`.
- Named ByeDPI profiles and editable arguments are stored in SharedPreferences.
- JNI wrapper exists at:
  `app/src/main/java/io/github/dovecoteescapee/byedpi/core/ByeDpiProxy.kt`
- Native CMake integration exists at:
  `app/src/main/cpp/CMakeLists.txt`
- Upstream ByeDPI Android native wrapper is pinned to:
  `dovecoteescapee/ByeDPIAndroid` commit
  `43db0955de687d43f9473374dc7c5548eaa55359`
  and uses recursive submodule `https://github.com/hufrea/byedpi.git`.
- CI fetches that pinned upstream source and builds the native library for `armeabi-v7a` and `arm64-v8a`.
- Native JNI interface expected by the C code:
  `jniCreateSocketWithCommandLine(args)`, `jniStartProxy(fd)`, `jniStopProxy(fd)`.
- UI currently has start/stop ByeDPI controls and reports local SOCKS5 endpoint 127.0.0.1:1080.
- Current proxy thread is daemon/background-thread based; a robust Android foreground-service/process-lifecycle implementation is still needed for reliable long-running use.
- ByeDPIAndroid is GPL-3.0-or-later; core hufrea/byedpi is MIT. License texts are in `geely-youtube-dpi/licenses/`.

## Latest build result — verified success

GitHub Actions workflow:
`.github/workflows/geely-youtube-android.yml`

Latest verified run:
- Run ID: `42873503890`
- Job ID: `130030598420`
- Branch: `geely-youtube-dpi`
- Commit: `6754b8d2beda2ce7d787a2c3552633fad39843d7`
- Status/conclusion: `completed / success`
- Android SDK/NDK/CMake installation: success
- Pinned ByeDPI native dependency fetch: success
- Gradle wrapper generation: success
- `Build debug APK`: success
- `upload-artifact`: success

Artifact:
- Name: `geely-youtube-debug-apk`
- Artifact ID: `13790014141`
- Size: 888,320 bytes
- SHA-256: `3a0c6e1499f21e9d1c530f65deb74dbcf1cca0e209bcc5e1d5de67cbc5cfb870`
- Expires: 2026-11-24T18:18:34Z
- Run page: https://github.com/Romangold999/free-my-tv/actions/runs/42873503890
- Actions page: https://github.com/Romangold999/free-my-tv/actions

Important: This proves the current debug APK compiles and was uploaded as an Actions artifact. It does NOT yet prove installation/runtime stability, actual YouTube playback, or that YouTube traffic is routed through ByeDPI.

## Next engineering tasks (priority order)

1. Inspect build artifact contents and exact APK path/name; provide a simple user download link via the Actions artifact route (artifact downloads may require GitHub login).
2. Inspect app manifest, package/application ID, minSdk and ABI packaging; confirm compatibility with Android 5.0/API 21 and Android 14.
3. Add reliable foreground service/lifecycle handling for ByeDPI, including notification and clean stop/restart behavior.
4. Integrate a YouTube client/player. NewPipe v0.28.8 was identified as a possible Android 5.0-compatible baseline (minSdk 21, GPL-3.0-or-later), but is NOT integrated yet.
5. Audit every relevant network path (extractor/OkHttp, video HLS/DASH/progressive, captions, thumbnails/images, live streams) and configure SOCKS proxy consistently. Do not claim proxy coverage until verified.
6. Replace current placeholder YouTube action with working search, channel/video browsing and playback.
7. Add tests or runtime verification, then produce a fresh APK artifact. Do not call an APK fully working until real runtime/playback checks pass.

## CI history / fixes

- Earlier run `42873392762` failed because `android-actions/setup-android@v3` tried to install deprecated SDK package `tools`.
- Commit `c2318b78f3ad0c992c98296701e07df313a914a3` removed that action.
- Commit `6754b8d2beda2ce7d787a2c3552633fad39843d7` resolves sdkmanager from the runner SDK and installs pinned Android platform/build-tools/NDK/CMake. This run succeeded.

## Working rules / communication

- User works from an Android phone and wants us to do repository work directly where possible.
- Give direct, copyable links and concrete results. Avoid vague progress updates and instructions that require searching through UI screens.
- Be candid about limitations. Never invent a download URL or claim success before verifying the GitHub run/artifact.
- Keep all code changes scoped to `geely-youtube-dpi/` plus the root workflow file when needed.
- Before changing an existing file, fetch its current contents/SHA from the target branch; write only to `geely-youtube-dpi`.
- In a new chat, read this file first, then check the current branch HEAD and latest Actions run/artifacts because this checkpoint can become stale.
