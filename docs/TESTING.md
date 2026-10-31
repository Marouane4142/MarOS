# Validation and known limits

## Automated checks

`python tools/check.py` validates Python/XML inputs, distribution boundaries and the existing 17 JVM chronology assertions. `python launcher/build.py` compiles all sources and checks APK signing, alignment, ZIP path normalization and twelve landscape JPEGs. CI repeats the checks and build using a clean runner.

These checks do not replace a physical TV test. MarOS 5 targets API 36; Android 11/API 30 remains the minimum. A broad compatibility claim for newer Android versions is not established by compilation against a newer platform.

## MarOS 5 checks

Run `sh gradlew :launcher:testDebugUnitTest :launcher:lintDebug` (Windows: `gradlew.bat`). `python launcher/build.py --local-release` verifies the optimized signed APK and merged library profile. `tools/review_android.py --serial EXPLICIT_DEVICE --out shots/review --companion` checks the absence of a separate Reprendre tab, the essentials at the bottom of the first viewport and resume below the fold, DPAD navigation, Studio, paired API rejection, reversible profile/favorite edits, five themes, compact/large-text rendering and HOME returns. The explicit `--history-import FILE` option merges a real legacy history export through the paired API; it is a migration operation and is not undone with temporary visual settings. Wake-up is explicit because a running Android Dream can otherwise mask the activity. Screenshots and reports stay ignored.

The optional `--experience` flag, together with `--companion`, checks owner phone commands, refusal to control third-party windows, guest visibility, real TVmaze searches and a temporary watchlist title. It downloads a private ZIP, validates it without applying, rejects a traversal archive, self-restores the same data and checks the previous-state archive. It requires a network-connected TV; a mock browser check is not a substitute for this device test. Treat the generated ZIP and screenshots as private files.

Google OAuth and Drive require a real owner-configured client and explicit sign-in; follow [Google setup](GOOGLE-SETUP.fr.md). Test Cast playback from a real sender and distinguish receiver discovery from successful media loading. Frame timing must be measured with the same warm navigation sequence and device refresh rate; compilation does not establish 60 FPS.

## Development history

The private predecessor was exercised on Xiaomi TV Box S 2nd generation / Android 11: native UI, real Spotify sessions, manufacturer-specific physical remote keys, HOME returns from apps, image imports, ambient mode and the local companion. Those private records are not bundled in the public repository because they contain device/user state.

The public beta has a new package and signer, generic app defaults, opt-in city weather and a portable build. It is a distinct install. The initial release notes state what was tested for this exact public snapshot; tests of the predecessor are not presented as exhaustive certification of this package.

## Version 4 public beta smoke result

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

## Version 5 Xiaomi validation

The optimized Compose release variant passed 23 device checks on Xiaomi TV Box S 2nd generation / Android 11, including native Google account status without OAuth, guest privacy, phone controls, profiles, backup validation and restore. The native account-picker flow and separate Calendar permission were also exercised. Seven Kotlin tests, 17 chronology assertions and Android Lint passed. See the [sanitized report](qa/maros5-xiaomi.json) and [release notes](releases/5.0.0-beta.1.md). The official APK keeps the public release certificate and is compared against the validated local build for identical non-signing payload.

The clean Android TV 11 emulator also passed **17 checks**, including phone pairing, profile isolation, all themes, large text and repeated HOME returns. [Successful CI run](https://github.com/Marouane4142/MarOS/actions/runs/41185055771) · [recorded checks](qa/maros5-android-tv-smoke.json). The public README previews come from that clean installation, without personal account data.
