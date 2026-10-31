# Changelog

## 5.0.0-beta.1 — 2026-10-31

- Native Android TV account picker associates existing Google accounts with launcher profiles, reuses matching profiles and keeps Calendar permission separate. No Cloud project, Google auth SDK or system tokens are needed for this path. Drive is an optional backup action; unlinking preserves the system account.
- Removed the MarOS microphone, voice-command parser and search/recommendation dictation; the system Google voice key remains unchanged.

- Sliding top-menu focus, 150 ms card focus animation and 190 ms view entrances, with graphics-phase state reads and reduced-motion support.
- Top-right Google identity/avatar, account-scoped encrypted tokens and guest selection in the profile picker.
- Three daily settings categories; Cast/voice/calendar/watchlist/scenes/guest settings tabs removed.
- Automatic visible Google calendar matching by active profile account, after Android permission; no manual calendar selection.

- Kotlin/Compose for TV HOME and Studio, preserving the MarOS visual identity.
- Full right-hand music player, bottom essentials dock, HOME viewing shelf and separately imported legacy viewing observations.
- Landscape episode cards, TV-listings permission, unambiguous provider artwork joins and bounded retention of shared session artwork.
- Compact 85% home scale, independent text sizing, five dark palettes and top-left Android settings.
- Centered icon controls, Xiaomi TV+ to TiviMate, and persistent Android media-listener grants.
- Lazy app shelves/grids, retained HOME composition, cancellable images and targeted caches.
- Fixed first viewport, vertically centered music, edge-only horizontal scrolling and no recently opened apps shelf.
- Local profiles, reorderable/hidden lower home sections and consistent sideloaded app discovery.
- Phone favorite order, collections, profiles, banner previews and explicit disconnect.
- Cinema scenes, PC readiness check, manual Immich/HTTPS photo-list and ICS imports, storage diagnostics and release lookup.
- Mon salon: Google-linked Android calendars, guest privacy, TVmaze watchlist/details, scenes and native quick settings.
- Paired phone directional HOME/media/volume controls, asynchronous task results and personal ZIP validation/merge with a previous-state archive.
- Optional Google device-code sign-in and file-scoped Drive backup, with a project setup guide; existing system Cast and Google services retained.
- Opt-in idle/unmetered six-hour Immich and series maintenance, minute-aligned home clock and lifecycle-aware media listeners.
- Scoped Jellyfin artwork authentication and a home library shelf.
- Gradle/AGP build, R8/resource shrinking, library Baseline Profiles and API 36 target with Android 11 minimum.
- Kotlin tests, Android Lint, explicit-device DPAD/paired API review and journaled optional-access migration.
- Detailed Java editors/services remain compatible. See [scope and limitations](docs/MIGRATION-5.md).

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
