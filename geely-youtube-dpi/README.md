# Geely YouTube DPI

A separate Android YouTube-style client prototype for the Geely Atlas Pro head unit (reported Android 5.0/API 21) and Android 14 phones. This project is isolated in `geely-youtube-dpi/` on branch `geely-youtube-dpi`; it does not replace the existing launcher.

## Current features in source

- Home/trending feed, YouTube search results and channel pages via NewPipe Extractor v0.26.3.
- Local channel subscriptions and local watch history (not Google-account synced).
- Embedded ExoPlayer 2.19.1 player with DASH, HLS and progressive/muxed playback paths.
- Embedded ByeDPI native SOCKS5 listener at `127.0.0.1:1080`, managed by a foreground service.
- Extractor, thumbnail and player media transports configured to use the SOCKS5 proxy without direct fallback.
- Editable named ByeDPI profiles; the original user-provided command remains the default reference profile.
- Responsive navigation and thumbnail sizing for compact phones and wide displays.
- In-app open-source license viewer with bundled GPL-3.0, ByeDPI MIT and third-party notices.

## Compatibility and build checks

GitHub Actions builds a debug APK with:
- package `com.romgold.geelyyoutube`, version `0.2.0` / versionCode 2;
- minSdk 21, targetSdk 28;
- native `armeabi-v7a` and `arm64-v8a` libraries;
- JNI export checks for both native ABIs;
- a guard comparing the exact original ByeDPI command against the in-app default and profile file.

Latest completed build: [Actions run #95](https://github.com/Romangold999/free-my-tv/actions/runs/42915218790), commit `8bfec6278b675480480006a2187471d66f3c2169`, successful. Artifact ID `13809027487`; SHA-256 `adae51e1ce797d5edd6461df3f4c1d0b08e83716c13a8f4aa777111ef1e69cf7`.

A subsequent workflow change adds checks for all three license files inside the APK. That new check must pass in its own Actions run before it is considered verified.

## Important limitations

- Google OAuth / YouTube sign-in is not implemented. The app never asks for or stores Google passwords.
- Subscriptions and history are stored locally only.
- A successful CI build does not prove installation or runtime compatibility.
- The local SOCKS5 handshake confirms the listener responds; it does not prove that YouTube is reachable through the user's network.
- No real-device testing has yet been completed on either Android 14 or the Geely Android 5.0 head unit.
- Captions, quality selection, pagination, live-stream edge cases, codecs, sleep/resume and long-running stability remain unverified.

## Release policy

Do not call this a finished app or distribute an intermediate APK as final until actual tests pass on both target devices: installation/launch, ARMv7 native loading, ByeDPI start/stop and profile switching, search, thumbnails, video and audio playback, fullscreen, sleep/resume and stability. The user explicitly requested only the fully finished APK.

## Open-source components

- NewPipe Extractor v0.26.3 — GPL-3.0-or-later.
- ByeDPIAndroid JNI/native bridge (pinned upstream commit) — GPL-3.0-or-later.
- hufrea/byedpi native component — MIT.

License texts and third-party notices are bundled in the app and shown from the Profile screen.
