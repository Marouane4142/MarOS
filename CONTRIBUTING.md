# Contributing to MarOS

Thanks for helping make Android TV calmer and more useful. This is an early public beta, with a primarily French interface and a small native Java codebase.

## Start here

- For a small bug fix, open a focused pull request with a clear before/after description.
- Discuss a major feature, broad refactor or new server integration in an issue first.
- Check existing issues. Describe the Android version, TV/box model, remote and exact reproduction steps.
- Redact account names, local addresses, viewing history, photos, pairing codes and credentials from screenshots and logs. Never attach a device backup or a keystore.

Use [the bug report template](https://github.com/Marouane4142/MarOS/issues/new?template=bug_report.yml) for reproducible problems. Report security issues privately as explained in [SECURITY.md](SECURITY.md).

## Development setup

Install Python 3.10+, a JDK 11+ and the Android SDK. Set `ANDROID_HOME` and `JAVA_HOME`, then run:

```sh
python tools/check.py
python launcher/build.py
```

Use a test device or emulator. Building creates a local debug key in `launcher/.local/`; it is ignored by Git. An official release uses a different certificate. Do not replace a real user's installation or change HOME while testing without an explicit backup and consent.

The build script only builds. Installation and optional manufacturer cleanup are separate operations documented in [INSTALLATION.md](docs/INSTALLATION.md).

## Changes that fit the project

- Keep the TV UI native and lightweight. Avoid a large runtime dependency for a small feature.
- Support DPAD focus, Back, long press and readable text from a sofa. Keep focus visible.
- Use the shared MarOSUI components, native vector icons and existing spacing and color roles.
- Keep common tasks simple and place optional equipment behind progressive disclosure.
- Respect reduced animation settings and ensure large text remains usable.
- Put blocking network/file work off the main thread. Bound response and image sizes.
- Request only the permissions a feature needs. Keep integrations opt-in and local credentials out of settings exports.
- Do not add telemetry, advertising, automatic package removal or hidden background services.
- Never invent viewing history or claim a service supports a deep link without evidence.

## Validation

Run the checks and APK build for code changes. Add or adjust a focused test when behavior needs regression coverage; avoid tests that merely repeat implementation details. The chronology test already covers real continuation entries, invalid dates, sorting and duplicates.

For UI or device behavior, report what you actually tested: device/API, navigation path, result and a redacted screenshot when helpful. If a feature needs Jellyfin, Home Assistant, a PC or another device, distinguish a validated end-to-end test from a connector that only compiles.

For companion changes, check pairing, rejection without a token, session continuity across activities, closure, input limits and mobile layout. Test with a trusted isolated development network; do not expose the service to the Internet.

## Pull requests

Keep one coherent purpose per PR. Explain the concrete problem and resulting behavior, list meaningful validation and mention remaining limitations. Update user-facing documentation when permissions, network traffic, installation or configuration changes.

Follow the existing code where you touch it, and prefer readable methods when adding new code. Large formatting-only rewrites make review harder. Never commit `.local`, build artifacts, APKs, backups, keys, tokens or private exports.

Contributions are submitted under the project's MIT license. Preserve upstream and third-party notices. By submitting, you confirm that you have the right to contribute the material; do not copy proprietary code or unlicensed brand artwork.
