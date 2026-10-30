# Changelog

## 4.0.0-beta.1 — 2026-10-30

First public MarOS beta, derived from the private 4.0 iteration of tv-home.

### Included

- MarOS identity, Manrope typography and 40 consistent native vector controls.
- Favorites, collections, custom app banners, configurable appearance and ambient photos.
- Compatible Android media-session controls and chronological viewing entries.
- Paired local phone companion with one-hour sessions, favorite editing and photo/text import.
- Local agenda, timer, configurable city weather and optional connected-device integrations.
- Standalone Android package `io.github.marouane4142.maros`, versionCode 40000.
- Portable build command, checked ADB operations, MIT license, contribution and privacy documentation.

### Publication changes

- Private snapshots, local machine paths, personal settings and development signing material excluded.
- Weather has no default city; it requires the user's choice before requesting a forecast.
- Wake-on-LAN requires the user's own broadcast address.
- Official APK signed with a new private release key. Public certificate supplied for verification.

### Known limitations

The interface is primarily French. Minimum and target SDK are 30. The predecessor was exercised on Xiaomi TV Box S 2nd generation / Android 11; broad public device compatibility is not established. Viewing history and media controls depend on the source applications. Optional hardware integrations require separate end-to-end validation.
