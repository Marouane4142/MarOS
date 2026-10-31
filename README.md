<p align="center"><img src="docs/assets/maros-banner.svg" alt="MarOS — Your TV. Your space." width="960"></p>

<p align="center">
  <a href="https://github.com/Marouane4142/MarOS/releases"><img alt="Releases" src="https://img.shields.io/github/v/release/Marouane4142/MarOS?include_prereleases&color=87dcf5&label=release"></a>
  <a href="https://github.com/Marouane4142/MarOS/actions/workflows/ci.yml"><img alt="Build and checks" src="https://github.com/Marouane4142/MarOS/actions/workflows/ci.yml/badge.svg"></a>
  <a href="LICENSE"><img alt="MIT license" src="https://img.shields.io/badge/code-MIT-87dcf5"></a>
  <img alt="Android 11 and later" src="https://img.shields.io/badge/Android-11%2B-87dcf5">
  <img alt="Kotlin and Compose TV" src="https://img.shields.io/badge/Kotlin-Compose_TV-87dcf5">
</p>

**A personal home for Android TV and Google TV.** Native, remote-friendly, and free of advertising in the launcher. Your apps, your landscapes, your music — with a local phone companion.

[Français](docs/README.fr.md) · [Download](https://github.com/Marouane4142/MarOS/releases) · [Installation](docs/INSTALLATION.md) · [Contributing](CONTRIBUTING.md)

## Preview

<img src="docs/assets/home-v5.png" alt="MarOS 5 Compose home on a clean Android TV 11 emulator" width="960">

Actual MarOS 5 clean-install Android TV emulator capture. Available favorites depend on installed apps; no personal accounts or viewing data are shown. [Settings preview](docs/assets/studio-v5.png) · [Screenshot and backdrop credits](docs/assets/README.md).

## What you get

- **Your essentials:** ordered favorites, app collections, hidden apps, custom banners and a shortcut to the installed Play Store.
- **A quieter home:** Nature, Cinema, Aurora, Dune and Minimal presets, rotating landscapes, a personal album and ambient mode.
- **Real media controls:** artwork, track information, playback and seeking through compatible Android media sessions.
- **Recent viewing:** chronological, usable Watch Next entries and observed video sessions. No invented universal streaming history.
- **Your phone as a companion:** scan a QR code, send photos, edit favorites, prepare searches, keep shortcuts and export preferences. Room controls, media/volume, watchlist and validated personal ZIP backup are included. No phone app or central server required.
- **Useful extras:** opt-in city weather, a local agenda and timer. Optional Jellyfin, Home Assistant, Wake-on-LAN, camera and Ollama-compatible connectors.
- **Reversible installation tools:** explicit ADB device selection, a configuration snapshot and a journal of changes. Packages are disabled rather than uninstalled.

The Android account picker associates existing Google accounts with launcher profiles without a Cloud project. Calendars match the selected account after separate Android permission. File-scoped Drive backup is optional and needs your own TV OAuth client: [setup guide](docs/GOOGLE-SETUP.fr.md). Scenes, guest privacy and idle photo/series maintenance are integrated into the home, profile selector and content screens. Cast and remote voice input use the existing system Google apps. MarOS adds no voice recognition. See [features and limits](docs/VISION.fr.md).

## Versions

**5.0.0-beta.1** introduces Kotlin + Compose for TV HOME and MarOS Studio, reorderable shelves, local profiles, Google-linked profiles and a richer phone companion. The minimum remains Android 11/API 30; target SDK is 36. See [migration notes and limits](docs/MIGRATION-5.md). Public release assets remain separately published and signed.

### Original public beta

**v4.0.0-beta.1** is the first public MarOS release, derived from the private 4.0 iteration. The Android package is `io.github.marouane4142.maros`. Android **11 / API 30** is the minimum; that historical release targeted SDK 30.

The predecessor was exercised on a Xiaomi TV Box S 2nd generation running Android 11. This public package has its own release signature and is an early beta: other remotes, manufacturers and Android versions need testing. The interface is currently primarily **French**. See [validation and limitations](docs/TESTING.md).

An APK installs the launcher. It does **not** silently disable manufacturer applications, enable special access, or remove advertising inside third-party streaming apps.

## Install

Download the APK and `SHA256SUMS.txt` from [Releases](https://github.com/Marouane4142/MarOS/releases). Follow [the backup-first installation guide](docs/INSTALLATION.md) to install it, test it and select it as HOME. Manufacturer cleanup is optional and model-dependent.

This public package is separate from the earlier private `com.mihir.tvhome` installation. It does not overwrite that app or automatically migrate its data.

## Build from source

Requirements: Python 3.10+, **JDK 21**, Android SDK platform **37.0** and Build-Tools **36.0.0**. Set `JAVA_HOME` and `ANDROID_HOME`.

```sh
git clone https://github.com/Marouane4142/MarOS.git
cd MarOS
python launcher/build.py --sdk /path/to/Android/Sdk
```

On Windows, `python launcher/build.py --sdk "C:\path\to\Android\Sdk"` works too. Set `JAVA_HOME`, or pass `--java-home`. The result is `launcher/build/MarOS-debug.apk`, signed with an ignored local debug key. This command never connects to a TV.

The Python command drives the pinned Gradle wrapper, checks APK paths/assets and verifies the signature with SDK apksigner. `python launcher/build.py --local-release` produces an optimized R8 build signed with the local development key. Official releases use an external private key; see [releasing](docs/RELEASING.md). A local development APK cannot update an official release signed with another key.

```sh
python tools/check.py
```

Checks include Python/XML validation and the existing 17 chronology assertions. Run `sh gradlew :launcher:testDebugUnitTest :launcher:lintDebug` (Windows: `gradlew.bat`) for Kotlin tests and Android Lint. CI builds a debug artifact; official release APKs are separately signed.

## Phone companion and privacy

Open **Mon téléphone** on the TV. Scan its QR code or enter the displayed HTTP address and six-digit code. The session stays active for one hour across app changes and can be closed immediately on the TV.

The companion uses **HTTP on your local network**, not encrypted transport. Use a trusted LAN; do not forward port 8765 to the Internet. The APIs require pairing and validate request origins, formats and sizes.

Preferences, viewing observations and imported images remain in the app's private storage. Integration credentials use Android Keystore-backed encryption and are excluded from portable settings exports. Weather sends a user-chosen city and its coordinates to Open-Meteo; no weather location is preset and no forecast is requested before configuration. External services are contacted when you use their features. Read [privacy](PRIVACY.md) and [security](SECURITY.md).

## How it is organized

```text
launcher/src/io/github/marouane4142/maros/  Native TV app and local server
launcher/src/io/nayuki/qrcodegen/          Vendored QR generator
launcher/assets/companion.html            Phone interface
launcher/res/                             Android resources and vector icons
launcher/build.py                         Portable APK build
tools/tv-session.py                       Checked, journaled ADB operations
docs/                                    Installation, architecture and release notes
```

HOME and Studio use Kotlin/Compose for TV; the existing native Java editors and services remain interoperable. It does not need an AI model. Notification-listener access supports media sessions; accessibility access supports selected remote buttons. These optional permissions must be deliberately enabled. See [architecture](docs/ARCHITECTURE.md).

## Limitations

Streaming apps decide what metadata, history and deep links they expose. Netflix, Prime Video, TiviMate and others may not share complete or current viewing information. MarOS cannot bypass these restrictions. Chromecast, voice, DRM playback, casting routes and every remote revision are not guaranteed by this beta.

Jellyfin, Home Assistant, Wake-on-LAN, cameras and the optional assistant require your own compatible devices or servers. Their setup screens are not a claim of hardware certification. The local agenda does not synchronize an external calendar. MarOS is not affiliated with Google, Xiaomi or any streaming service.

## Credits and license

MarOS builds on **[mihirgupta0900/tv-home](https://github.com/mihirgupta0900/tv-home)**, at base commit `19803542cd335e0b2643dfa73c18c38051354ffa`. The upstream MIT notice is retained; MarOS contributions are also MIT. See [LICENSE](LICENSE) and [provenance](docs/upstream.json).

Third-party assets retain their own licenses: Lucide (ISC / applicable Feather MIT notices), Manrope (SIL OFL 1.1), Nayuki QR generator (MIT), and Wikimedia landscapes (their individual CC BY-SA licenses). See [third-party notices](THIRD_PARTY_NOTICES.md) and [wallpaper credits](launcher/WALLPAPER-CREDITS.md). The banner above is a brand illustration, not a device screenshot.

### Navigation refinement

The home now has an animated sliding menu focus, gently scaling app/episode cards and short view transitions. Frame values stay in draw/graphics phases; reduced motion remains available. A top-right avatar opens users and the guest session. Daily settings are limited to Appearance, Applications and Phone. Google calendars automatically match the selected profile after Android permission, with no calendar-selection settings page. Native account selection needs no Cloud client; optional Drive authorization needs its own TV OAuth client.
