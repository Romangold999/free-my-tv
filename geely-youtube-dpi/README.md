# Geely YouTube DPI

Standalone YouTube client for the Geely Atlas Pro head unit (GMC Custom, Android 5.0) and Android 14 phones.

This project lives in its own directory and branch inside the existing Free My TV repository. It does not replace or modify the launcher project.

## Targets

- Android 5.0 / API 21 head unit.
- Android 14 phone.
- ARMv7 (32-bit) and ARM64 builds, after verifying the head unit ABI.
- Large touch targets and a landscape-first in-car interface.
- Search, channel/video browsing and playback.
- An in-app DPI-bypass transport for the app's own network requests; no separate VPN app should be required.

## Network design

The DPI bypass must be integrated into this application's networking path. Merely bundling ByeDPIAndroid or starting its VPNService is not sufficient: the stock ByeDPIAndroid app redirects device traffic through Android VPNService, while this app needs a local proxy/transport that its own HTTP stack can use. The implementation must be verified against the upstream ByeDPI native API and Android 5 compatibility before claiming it works.

DPI bypass is not encryption, does not hide the user's IP address, and cannot guarantee access where the provider uses blocking methods beyond DPI.

## Compatibility baseline

NewPipe 0.28.8 is the initial reference client because it supports Android 5.0. Newer NewPipe releases require a newer Android version. Any NewPipe-derived code must retain its GPL-3.0-or-later licensing and required notices/source availability.

## Build and delivery

GitHub Actions will build APKs for the verified ABIs. The workflow must publish artifacts that can be downloaded directly to an Android phone. No local Android Studio setup should be required from the user.

## Development status

- [x] Dedicated branch created from the existing launcher repository.
- [ ] Inspect existing repository build conventions and keep this project isolated.
- [ ] Verify head-unit CPU ABI and Android 5 runtime constraints.
- [ ] Integrate a local ByeDPI-compatible proxy into the app's HTTP stack.
- [ ] Add the YouTube client UI and playback.
- [ ] Add reproducible GitHub Actions APK builds.
- [ ] Verify installation and startup on Android 5 and Android 14 devices.

Nothing in this directory is a tested APK yet. A successful CI build and real-device playback tests are required before calling it ready.
