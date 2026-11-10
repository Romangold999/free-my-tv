# Geely YouTube DPI

Standalone YouTube client for the Geely Atlas Pro head unit (GMC Custom, Android 5.0) and Android 14 phones.

This project lives in its own directory and branch inside the existing Free My TV repository. It does not replace or modify the launcher project.

## Targets

- Android 5.0 / API 21 head unit.
- Android 14 phone.
- ARMv7 (32-bit) and ARM64 builds, after verifying the head-unit ABI.
- Large touch targets and a landscape-first in-car interface.
- Search, channel/video browsing and playback.
- An in-app DPI-bypass transport for the app's own network requests; no separate VPN app should be required.

## User-supplied YouTube strategy

The exact command line supplied by the user as currently working is saved in `profiles/user-supplied-youtube.txt`. Preserve it byte-for-byte as the initial default profile. Do not silently normalize, remove repeated flags, reorder arguments, or replace the `vk.com` host selector. The user reports that strategies sometimes become stale, so the app must expose an editable command-line field and allow saving multiple named profiles. A profile change must restart/reconfigure the local proxy without requiring an APK rebuild.

The profile is user-tested on the user's network, not independently validated on the Geely head unit or all carriers. Treat it as a starting preset, not a universal guarantee. Record the date/device/network when the user later confirms a profile works or stops working.

## Network design

The upstream ByeDPIAndroid source exposes a native ByeDPI SOCKS proxy through JNI (ByeDpiProxy and jniCreateSocket / jniStartProxy). Its proxy defaults to a local listener at 127.0.0.1:1080. This gives us a viable integration route without running the upstream app's device-wide Android VPNService: embed the byedpi native component, start its local SOCKS listener inside this app, and configure the YouTube client's HTTP stack to use that SOCKS proxy.

The separate hev-socks5-tunnel component is used by the upstream app for VPN/TUN traffic redirection; it should not be needed if we proxy only this app's own HTTP connections. This must be confirmed with a working native build and a network test. All HTTP clients used by the extractor, images and playback must be checked; configuring only one OkHttp instance would leave other requests outside the proxy.

DPI bypass is not encryption, does not hide the user's IP address, and cannot guarantee access where the provider uses blocking methods beyond DPI.

## Compatibility baseline

- Uses NewPipe Extractor v0.26.3, the extractor release shipped with NewPipe v0.28.8. NewPipe v0.29.0 raised the full app's minSdk to 23, so do not upgrade blindly when preserving Android 5 support.
- This project uses the extractor library, not the full NewPipe app UI.
- NewPipe Extractor and the embedded ByeDPI Android bridge are GPL-3.0-or-later; the native ByeDPI submodule is MIT. Preserve license texts, notices and source availability when distributing builds.

## Current implementation

- Native ByeDPI listener is embedded through JNI and managed by an Android foreground service.
- The exact user baseline remains available as an editable named profile. The service restarts the proxy when a different profile is started.
- Extractor requests, thumbnail requests and ExoPlayer media requests are explicitly routed through SOCKS5 at 127.0.0.1:1080; there is no direct fallback in these paths.
- The proxy controller probes the local SOCKS listener before reporting startup success.
- The app currently provides Home/trending, Search, channel pages, local subscriptions, local watch history/library, profile settings and fullscreen playback.
- Playback prefers a DASH manifest when available, otherwise uses a muxed stream or attempts a separate video/audio pair.
- The interface adapts navigation and thumbnail dimensions to compact phone and wider display sizes.

## Build and delivery

GitHub Actions builds a debug APK for ARMv7 and ARM64. CI now verifies that both `libbyedpi.so` binaries are packaged and that the manifest retains minSdk 21 and targetSdk 28. No local Android Studio setup is required from the user.

Latest successful CI build: run #73, commit `f0d2ce627e268825b358e26877fc2a549ebf5dff`. This verifies compilation and APK packaging only, not runtime behavior on either target device.

## Development status

- [x] Dedicated branch created from the existing launcher repository.
- [x] Pinned ByeDPI JNI/native dependency and retained license files.
- [x] Editable named ByeDPI profiles with restart-on-switch behavior.
- [x] SOCKS-routed NewPipe Extractor, thumbnails and ExoPlayer media transport.
- [x] Home/trending, search, channels, local subscriptions and local history.
- [x] DASH playback path and progressive fallback.
- [x] Responsive navigation and thumbnail sizing.
- [x] Reproducible GitHub Actions build with ARMv7/ARM64 and APK metadata checks.
- [ ] Safe Google OAuth / account-synced subscriptions and history.
- [ ] Verify the head-unit ABI and run on the actual Android 5.0 Geely head unit.
- [ ] Install and test on the actual Android 14 phone.
- [ ] Validate ByeDPI network effectiveness, long-running service stability, sleep/resume, search and audio/video playback on both devices.
- [ ] Validate codec/format coverage, live streams, captions and quality selection.

A green CI build is not a finished APK. Do not mark this project ready until the real-device checks above have been completed.
