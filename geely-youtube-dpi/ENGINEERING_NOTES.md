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
