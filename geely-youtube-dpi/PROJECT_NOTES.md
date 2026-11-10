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
- Build a lightweight but genuinely usable YouTube-style client, not a proxy settings demo or a web shortcut.
- Responsive/adaptive UI: scale and rearrange controls, navigation, cards and player to the actual display size/density and orientation. Must work on a phone and a wide Geely head-unit/monitor; landscape-first with large touch targets in car mode, while remaining usable on smaller screens.
- Account flow: provide a safe sign-in / account-registration route for a Google/YouTube account, then expose account-dependent features such as subscriptions and library where technically supported. Investigate the supported official OAuth/browser handoff; never collect or store Google passwords in the app and never claim sync/login works until tested.
- Core lightweight YouTube sections: Home/recommendations, Search, video playback, channel pages, subscriptions, history/library, account/profile and fullscreen player. Keep visual hierarchy familiar but remove unnecessary heavy UI.
- Landscape interface with large, car-friendly controls.
- YouTube search, videos, channels, subscriptions and fullscreen playback.
- ByeDPI engine embedded in the app; local SOCKS5 proxy at 127.0.0.1:1080.
- YouTube networking must explicitly use that proxy; starting a proxy alone does NOT mean YouTube traffic is routed through it.
- Editable ByeDPI command line and multiple named profiles; profile changes must not require rebuilding.
- Build APK through GitHub Actions and give user a direct, usable download route.
- Do not require user to install Android Studio or do complicated local development steps.
- Work only on branch `geely-youtube-dpi`; do not change `main` or unrelated project files.

## ByeDPI profiles — important distinction

The command line supplied by the user is the **original baseline/reference profile**, not a permanently locked configuration. Keep an untouched copy so it can be restored and compared, but do NOT interpret this as a ban on changing ByeDPI arguments.

Original baseline string (retain exact order, repetitions and spelling in the reference copy):

```text
-Ku -a3 -An -Kt,h -n vk.com -d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -s30+s -d35+s -r1+s -S -Mh,d -As -Kt,h -n vk.com -d1 -d3+s -s6+s -d9+s -s12+s -d15+s -s20+s -d25+s -d30+s -d35+s -S -Mh,d
```

Rules for future work:
- The user explicitly allows and expects tuning/updating ByeDPI arguments when the current profile stops working or performance/blocking conditions change.
- Keep the original baseline intact as a rollback/reference copy; create separate editable working profiles (named and described) for experiments and production use.
- Do not silently overwrite the baseline, remove duplicates, reorder options, or alter user-supplied text during copying/migration. Any intentional tuning belongs in a separate working profile and must be explained.
- Record each tested change: profile name, exact arguments, date, target/service, test environment, observed result (works/doesn't work/partial), and previous profile to roll back to.
- Allow editing profiles in the app without rebuilding the APK. Switching profiles should restart/reconfigure the proxy safely.
- Never assume a profile remains effective forever; retest and update it when the user reports failure or conditions change.

The profile file `geely-youtube-dpi/profiles/user-supplied-youtube.txt` was corrected to match the Kotlin default in commit `a26dabd51c20dd99b41f34ed5b8759103fc0f4bd`; treat that as the baseline copy unless the user explicitly chooses otherwise.

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

## Latest build result — current branch status

- Run `42875010424` for commit `328a9c1ffe74bed0e4daae4f9fc30815475f551a` completed with failure at Kotlin compilation. Exact error: `MainActivity.kt:43` passed a `String` to `SearchInfo.getInfo`, which expects a `SearchQueryHandler`.
- The immediately preceding run `42874953459` for commit `84733986e7df58028dd50f90736a8ccab2cf844a` succeeded, including the SOCKS-routed extractor downloader. This success predates the YouTube search UI commit and does not validate the current HEAD.
- Fix in progress: create the handler with `ServiceList.YouTube.searchQHFactory.fromQuery(query)` and pass it to `SearchInfo.getInfo`. Rebuild required before treating search integration as compile-verified.

## Adaptive UI and account requirements (added 2026-11-10)

- The interface must be responsive to display dimensions/density, not hard-coded for one screen. Use resource/layout breakpoints or measured available width to adapt navigation, content columns, cards and player sizing.
- Support phone portrait/landscape and the Geely head unit / wide monitor. Car mode is landscape-first, high contrast, large tap targets, readable text at dashboard distance; phone layout should not waste space.
- Target a lightweight YouTube-like experience with familiar sections: Home, Search, Subscriptions, Library/History, Channel, Account/Profile and full-screen playback.
- Add Google/YouTube account sign-in and account creation/registration entry points only through a safe supported flow (official OAuth/browser handoff or other documented supported method). Never request/store Google passwords directly. NewPipe Extractor search alone does not provide account registration, authenticated subscriptions, or account sync. Verify feasibility and test authentication before marking it complete.
- Keep core browsing/playback usable without sign-in where possible; clearly distinguish local features from account-synced features.

## Latest build result — verified success (historical)

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


## Device compatibility and real-device testing (mandatory)

- Geely head unit runs Android 5.0 (API 21). The app's current `minSdk = 21` is intentional and must remain at 21 unless a documented blocker is found. Do not accidentally raise minSdk through a library or AndroidX dependency.
- Android 5.0 compatibility is a release gate, not an assumption: validate APK install/launch, native ByeDPI ABI (`armeabi-v7a` is required for many older head units), proxy start/stop, search, video playback and audio/video decoding on the actual Geely unit. Compile success alone is insufficient.
- User's phone is Android 14 (Unihertz Tank 2). Every debug/release candidate must also be installable and testable on this phone before considering the build ready. Keep both `armeabi-v7a` and `arm64-v8a` native ABIs in the APK.
- Keep target SDK / permissions / foreground-service behavior compatible with both Android 5 and Android 14. Avoid APIs unavailable on API 21 unless guarded by runtime version checks. Check NewPipe Extractor, desugaring and any future player dependencies for minSdk/API requirements before adding them.
- Phone test path should be simple: provide a directly downloadable debug APK from GitHub Actions artifact/release; user installs it on Android 14 and reports results. Do not require Android Studio, adb, or manual builds from the user.
- Maintain a device test checklist and record actual outcomes separately for (A) Android 14 phone and (B) Geely Android 5.0 head unit: install/launch, adaptive layout, account flow, search, ByeDPI profile switching, stream playback, audio, fullscreen, sleep/resume, and stability. Never mark a device test passed until the user confirms it or verified test evidence exists.
- Current Gradle config inspected 2026-11-10: `minSdk = 21`, `targetSdk = 28`, ABI filters `armeabi-v7a` + `arm64-v8a`. This is a compatibility starting point, not proof of runtime support.


## Verified checkpoint — 2026-11-10 18:39 UTC

The previous "Latest build result" sections above are historical and must not be treated as current. The current branch head at this checkpoint is commit `dcc7ada053c5743625788d76f2fdcf97c2a0fdaa`.

Latest Actions run:
- Run ID: `42875981439`
- Branch: `geely-youtube-dpi`
- Commit: `dcc7ada053c5743625788d76f2fdcf97c2a0fdaa`
- Conclusion: `success`
- Build debug APK: success
- Artifact upload: success
- Artifact name: `geely-youtube-debug-apk`
- Artifact ID: `13792975484`
- Size: 3,771,079 bytes
- SHA-256: `22f7ba43f976e7966aa0c5bfff600d78906da2c7141c4f472f5fffd3591cd313`
- Run page: https://github.com/Romangold999/free-my-tv/actions/runs/42875981439

**Product status, stated plainly:** this is not a finished YouTube application. It is a technical prototype with a rudimentary settings/search screen, embedded ByeDPI controls, and NewPipe Extractor search. Selecting a result only displays its URL; no video player is integrated. No home/recommendation feed, thumbnail grid, channel pages, subscriptions, history/library, account sign-in, or responsive production UI exists yet. No real-device runtime test has been performed.

Do not present the current artifact as a finished app or imply it is ready for daily use. It may be used only as an engineering smoke-test APK if explicitly useful.

## Delivery milestones and honest release gates

Do not promise a calendar delivery date before the major unknowns are resolved. Deliver in verified milestones:
1. **Playable alpha:** video stream extraction plus an embedded player; verify the video/audio requests use the local SOCKS proxy with no silent direct-network fallback; verify on Android 14 first.
2. **Usable YouTube-style client:** responsive layouts for phone and wide head unit; home/search/results with thumbnails; video/channel pages; local history/library; robust ByeDPI foreground lifecycle and editable profiles.
3. **Account-dependent features and head-unit release:** validate safe official Google sign-in/registration route and whether subscriptions/account sync are technically supported; test on actual Android 5.0 head unit, including ARMv7 native library, playback/audio/fullscreen/sleep-resume. Only then call it ready.

Current immediate next task: implement and compile the embedded playback path, beginning with a supported player dependency that preserves minSdk 21, and route all media requests through the SOCKS5 proxy. Keep the original ByeDPI command line untouched as the baseline profile.


## Playback integration attempt — 2026-11-10

- Added ExoPlayer 2.19.1 core/UI and OkHttp extension; dependencies chosen to retain Android API 21 compatibility.
- Added `VideoPlayerActivity` with an explicit SOCKS5 proxy client targeting `127.0.0.1:1080`, with connection retry fallback disabled.
- Search-result tap now requests `StreamInfo` from NewPipe Extractor, selects a muxed (audio+video) stream, then launches the embedded player.
- This is an initial progressive/muxed playback path only. Separate DASH audio/video streams, adaptive quality, captions, live playback, thumbnails, and foreground-service proxy lifecycle are not implemented.
- **Not yet verified:** compilation of this exact commit, extractor stream availability, real network routing end-to-end, video/audio playback, Android 14 runtime, or Android 5.0 runtime. CI must pass before calling this a playable alpha.


## Verified build checkpoint — 2026-11-10 18:48 UTC

- Branch head build commit: `1faf237ad7fb9120301e777ad120401d8d759076`.
- GitHub Actions run `42878378593` completed successfully; `Build debug APK` and artifact upload both passed.
- Artifact: `geely-youtube-debug-apk`, ID `13792368592`, size 7,722,298 bytes.
- Build configuration checked: minSdk 21, targetSdk 28, ABI filters include `armeabi-v7a` and `arm64-v8a`.
- This confirms compilation/package generation only; it does not prove device install, native ABI loading, extractor success, media routing or playback.
- The app manifest was then updated to stop forcing landscape orientation on both activities, allowing phone rotation and responsive UI work. Version bumped to 0.2.0 / versionCode 2. A fresh CI build is required for those changes.
- Current feature status remains a prototype: search results plus a first progressive/muxed-stream player path. Home feed, thumbnails, channels, subscriptions, history/library, account flow, DASH separate audio/video, foreground service and device tests remain outstanding.
