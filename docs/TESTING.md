# Validation and known limits

## Automated checks

`python tools/check.py` validates Python/XML inputs, distribution boundaries and the existing 17 JVM chronology assertions. `python launcher/build.py` compiles all sources and checks APK signing, alignment, ZIP path normalization and twelve landscape JPEGs. CI repeats the checks and build using a clean runner.

These checks do not replace a physical TV test. The build intentionally targets API 30; Android 11 is the minimum. A broad compatibility claim for newer Android versions is not established by compilation against a newer platform.

## Development history

The private predecessor was exercised on Xiaomi TV Box S 2nd generation / Android 11: native UI, real Spotify sessions, manufacturer-specific physical remote keys, HOME returns from apps, image imports, ambient mode and the local companion. Those private records are not bundled in the public repository because they contain device/user state.

The public beta has a new package and signer, generic app defaults, opt-in city weather and a portable build. It is a distinct install. The initial release notes state what was tested for this exact public snapshot; tests of the predecessor are not presented as exhaustive certification of this package.

## Public beta smoke result

The public app installed and ran on the Android TV 11 / API 30 emulator using a 1920×1080 display at 320 dpi. All eight recorded checks passed: native launch, settings overview, local page serving, rejection without a token, code pairing, session continuity in another app, availability back on MarOS and absence of a MarOS AndroidRuntime fatal exception.

Evidence: [successful Actions run](https://github.com/Marouane4142/MarOS/actions/runs/41031959796), [recorded checks](qa/android-tv-smoke.json), [device metrics](qa/device.txt) and the actual captures under `docs/assets/`. The tests use an isolated emulator and do not change a physical TV. CI uses a debug signer; official APK signatures are separately verified.

## Device test checklist

- First launch and Back/DPAD focus, Home from inside another app, large text, reduced animations.
- Fresh favorites, app install/remove refresh, Play Store presence/absence, custom banner reset.
- Photo import, exclusion, favorite selection, ambient entry and immediate return.
- Compatible media metadata, playback/pause, remote-output behavior and supported seeking.
- Empty/unavailable Watch Next, chronological entries, duplicate and future-date filtering.
- Dedicated physical remote keys; ADB-injected key events are not equivalent.
- Phone pairing, mobile layout, authorization rejection, cross-activity session, manual close and expiration.
- City selection, no request before selection, offline/error state and clearing the chosen location.
- Timer and agenda; only test optional hardware integrations with the required equipment.
- Identity/journal checks and restoration of the captured HOME before disabling a newly installed launcher.

Record the exact device/API, build/tag, steps, result and remaining limitations. Redact private data from reports. Netflix and other DRM playback may prevent screenshots; a black frame alone proves neither success nor failure.

## Unvalidated integrations

The first beta does not certify Chromecast/voice flows, every remote firmware, all streaming histories, Jellyfin installations, Home Assistant entities, Wake-on-LAN BIOS/network settings, cameras or an external model server. The app must not invent successful device state or universal resume support.
