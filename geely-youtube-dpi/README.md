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

NewPipe 0.28.8 is the initial reference client because it declares minSdk 21 (Android 5.0). NewPipe 0.29.0 raised minSdk to 23 (Android 6). Any NewPipe-derived code must retain its GPL-3.0-or-later licensing and required notices/source availability. ByeDPIAndroid is also GPL-3.0; the exact native ByeDPI submodule license and notices must be retained when vendored.

## Build and delivery

GitHub Actions will build APKs for the verified ABIs. The workflow must publish artifacts that can be downloaded directly to an Android phone. No local Android Studio setup should be required from the user.

## Development status

- [x] Dedicated branch created from the existing launcher repository.
- [x] Confirmed upstream ByeDPIAndroid has a JNI-accessible local SOCKS proxy path, separate from its VPN/TUN redirection path.
- [x] Saved the exact user-supplied YouTube command as the initial editable profile.
- [ ] Import/pin the Android 5-compatible NewPipe 0.28.8 source and preserve licensing.
- [ ] Vendor the minimal ByeDPI native source and JNI bridge; inspect native submodule license and build flags.
- [ ] Route every relevant NewPipe HTTP client through the local SOCKS proxy.
- [ ] Verify head-unit CPU ABI and Android 5 runtime constraints.
- [ ] Add large-touch, landscape-first in-car UI and profile editor.
- [ ] Add reproducible GitHub Actions APK builds.
- [ ] Verify installation, search and playback on Android 5 and Android 14 devices.

Nothing in this directory is a tested APK yet. A successful CI build and real-device playback tests are required before calling it ready.
