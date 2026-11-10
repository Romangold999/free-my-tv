# Integration findings (2026-11-10)

This file records source-level facts verified against the pinned upstream revisions. It is not a claim that the application or APK is complete.

## ByeDPI Android JNI contract

Pinned upstream: `dovecoteescapee/ByeDPIAndroid` at `43db0955de687d43f9473374dc7c5548eaa55359`.

Relevant files:

- `app/src/main/java/io/github/dovecoteescapee/byedpi/core/ByeDpiProxy.kt`
- `app/src/main/java/io/github/dovecoteescapee/byedpi/core/ByeDpiProxyPreferences.kt`
- `app/src/main/cpp/native-lib.c`

Verified behavior:

1. `ByeDpiProxy` loads native library `byedpi`.
2. For a command-line profile, `ByeDpiProxyCmdPreferences(cmd)` creates argv as `arrayOf("ciadpi") + shellSplit(cmd)`.
3. JNI method `jniCreateSocketWithCommandLine(args)` calls native `parse_args(argc, argv)`, then creates the listening socket using `params.laddr`.
4. `jniStartProxy(fd)` starts the native proxy loop; `jniStopProxy(fd)` stops it.
5. The normal upstream command-line profile path and the native proxy are separate from the app's VPN/TUN service. This supports an in-process local SOCKS proxy design, but does not by itself prove that our fork builds or routes all client traffic correctly.

Important implementation requirements:

- Do not call the blocking native proxy loop on Android's main thread.
- Keep the socket fd lifecycle paired: create -> start -> stop; surface native parse/start errors in the UI.
- Ensure each profile restart stops and joins the previous proxy job before starting a new one.
- The exact user-provided profile remains stored byte-for-byte in `profiles/user-supplied-youtube.txt`; do not normalize it.
- Verify the native default listen address/port from the actual command-line profile and explicitly configure/confirm the local SOCKS endpoint before wiring clients to it.
- Review argument parsing and memory cleanup before vendoring: JNI allocates argv strings, so cleanup/error paths need scrutiny in the fork.

## NewPipe 0.28.8 network paths

Pinned upstream: `TeamNewPipe/NewPipe` tag `v0.28.8`.

Verified files:

- `app/src/main/java/org/schabi/newpipe/DownloaderImpl.java`
- `app/src/main/java/org/schabi/newpipe/player/datasource/YoutubeHttpDataSource.java`
- `app/src/main/java/org/schabi/newpipe/player/helper/PlayerDataSource.java`

Findings:

- `DownloaderImpl` owns an OkHttpClient built from an injected `OkHttpClient.Builder`; this is the natural point to set a SOCKS proxy for extractor/API/page requests.
- `YoutubeHttpDataSource` opens video connections using `URL.openConnection()` and therefore does not inherit the Downloader's OkHttp proxy configuration.
- The video DataSource must be separately patched to open each initial and redirected URL through the local SOCKS proxy. Redirect handling must preserve proxy use.
- Other image/thumbnail clients must be inventoried before claiming all app traffic is proxied.

## Next implementation sequence

1. Import the pinned NewPipe source into this project while retaining GPL-3.0-or-later notices and source-offer obligations.
2. Vendor only the required ByeDPI native source/JNI bridge and its exact submodule dependencies; inspect all license files and build scripts first.
3. Add a lifecycle-managed in-app proxy controller and editable named profile storage.
4. Inject the SOCKS proxy into Downloader's OkHttp builder and NewPipe's YouTube playback DataSource, then audit image and auxiliary HTTP clients.
5. Build ARMv7 and ARM64 variants in GitHub Actions; verify Android 5/API 21 compatibility and run playback tests on the actual head unit.

No APK is considered ready until CI succeeds and real-device playback is verified.


## Implementation checkpoint — 2026-11-11

The source-level integration described above has now been implemented in the separate app:

- Dependency: NewPipe Extractor v0.26.3 (the version shipped with NewPipe app v0.28.8), not the full NewPipe UI.
- Extractor downloader: custom `HttpURLConnection` transport through SOCKS5 `127.0.0.1:1080`.
- Thumbnails: dedicated HTTP connections through the same SOCKS5 proxy.
- Playback: ExoPlayer 2.19.1 with OkHttp SOCKS transport; DASH manifest playback is preferred when provided by NewPipe Extractor. The fallback uses muxed streams or a separate video/audio pair.
- Native proxy lifecycle: JNI listener runs on a worker thread owned by a foreground Android Service. Startup probes the local SOCKS listener; stop/restart handles the specific worker instance; profile changes restart the listener with the new argument string.
- UI: current sections are Home/trending, Search, Channels, local Subscriptions, local History/Library and Profile/ByeDPI settings. Compact navigation scrolls horizontally on narrow screens.
- CI run #73 passed for commit `f0d2ce627e268825b358e26877fc2a549ebf5dff`, including APK packaging checks for `armeabi-v7a`, `arm64-v8a`, minSdk 21 and targetSdk 28.

Still not proven:
- A local TCP connect to the SOCKS listener proves only that the local listener accepts a connection. It does not prove that YouTube is reachable through the selected ByeDPI strategy.
- No physical Android 5.0 or Android 14 device has yet been used to validate installation, native loading, network access, playback, fullscreen, sleep/resume or long-running stability.
- The current subscriptions and history are local-only. Google OAuth and account synchronization are not implemented.
- DASH/progressive playback paths compile but have not yet been validated against real YouTube media on the target devices.
