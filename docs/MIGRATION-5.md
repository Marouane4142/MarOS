# MarOS 5 · Kotlin and Compose for TV

The 5.0.0-beta.1 source moves HOME and the new Studio to Kotlin and Jetpack Compose for TV. The existing Java media, photo, integration and detailed settings components remain interoperable. This is an incremental migration, preserving the existing Android package and storage format.

## Interface and data

- A permanent full music panel on the right of HOME, including seeking, previous/next and volume controls. Continue-watching stays on HOME and in the history tool; there is no separate top-level resume tab. Essentials sit at the bottom of the first viewport, directly over the wallpaper. Continue-watching and the rest of the feed appear only when scrolling down; the essentials row is not a sticky overlay.
- App-local display scale defaults to 85%, with 75/100/110% choices independent of text size. Android output resolution and global density are unchanged.
- Glacier, Cinema, Minimal, Aurore and Dune palettes, centered icon controls and a top-left Android settings action.
- Xiaomi TV+ maps BUTTON_5 (scan 150 on the observed Xiaomi RC layout) to TiviMate; the other dedicated mappings remain compatible and individually learnable.
- One persistent HOME composition; returning from another app refreshes data without replacing the view tree.
- Lazy application shelves and grids, cancellable image coroutines, an 8 MiB app-art cache and separate image/config revisions.
- Compose TV Material components and TV focus behavior. Material 3 Expressive supplies motion specifications; the mobile Material theme is not mixed into the TV tree.
- Original MarOS colors, Manrope and local landscapes. No WorkTrack visual assets or palettes are used.
- Studio orders/hides viewing, recent-app, Jellyfin and quick-action shelves; favorites remain in the bottom dock. Local profiles store only the portable launcher preferences; streaming accounts and integration credentials are separate.
- One app catalog includes both LEANBACK_LAUNCHER and LAUNCHER entries, shared with the phone and tools.
- Detailed Java editors remain available for per-app banners, photo import, connections and backup. The photo editor pages twelve thumbnails at a time.

SharedPreferences remain the compatibility boundary for the Java services and phone server. Replacing the bounded 64-entry viewing store with Room or migrating all preferences to DataStore is not required for this release. ViewModel, StateFlow and coroutines own the new screen state.

## Connected features

- Paired phone: reorder favorites, assign collections, preview banners and manage local profiles. A paired client can end the sharing session. Existing photo, search and backup endpoints remain available.
- A total 20-second client deadline supplements socket read timeouts. Request sizes, Host/Origin and pairing limits remain enforced. Loopback clients are accepted for explicit ADB-forwarded validation; authentication is still required.
- Jellyfin: authenticated image requests remain scoped to the configured server/path, and the home shelf displays posters. Item links use the server web interface; universal native-client resume is not claimed.
- Cinema scene: apply the Cinema preset, optionally send a configured Home Assistant scene, then open a selected application.
- Wake-on-LAN: optionally wait up to a minute for the configured local Sunshine/GameStream HTTP port (47989), then open Moonlight. A response confirms that port is reachable, not that a game has launched.
- Remote photo import: manually read an HTTPS JSON array of `{ "url": "https://…", "title": "…" }` entries, at most thirty per list. Files become local copies subject to the existing 100-image limit, a 64 MiB album quota and a 64 MiB free-space reserve. This is an interchange format for HTTPS file servers; native SMB is not implemented. A separate Immich connector uses an encrypted API key, HTTPS metadata search filtered to an album, preview downloads and local deduplication. It imports at most thirty new photos per request across at most five metadata pages. The documented v2 search fields retained in the current v3 API are used; a configured live Immich server still needs validation.
- Calendar: manually import an HTTPS ICS feed, preserving manually entered events. Folded lines, UTC, local/TZID and all-day starts are supported. Recurring rules are excluded explicitly; the result reports this limitation.
- Version check: contact the public GitHub releases endpoint (including beta publications) only when requested. Installation continues through Android; no silent updater or new signing authority is introduced.

## Build

JDK 21, Android platform 37.0, Build-Tools 36.0.0 and Python 3.10+. Minimum Android API is 30, target API 36. The Gradle wrapper is pinned to 9.6.0; Android Gradle Plugin 9.4.1 supplies built-in Kotlin, with Kotlin/Compose compiler 2.4.21.

```sh
python tools/check.py
python launcher/build.py
sh gradlew :launcher:testDebugUnitTest :launcher:lintDebug
python launcher/build.py --local-release
```

On Windows, use `gradlew.bat` for direct Gradle tasks. `--local-release` explicitly signs an optimized R8 build with the ignored local development key. Official distribution requires `--release` and the external official keystore; the public certificate for the earlier official signer does not certify a local development build. AndroidX library Baseline Profiles are merged into release APKs. An app-specific generated profile is not included.

## Device migration and rollback

The former private package `com.mihir.tvhome` is a separate application. Export its portable preferences through its paired companion before switching HOME. Import that file into the new package and verify favorites. Android isolates each package's private files: portable exports do not transfer personal photos, banners, service credentials or viewing observations. A paired legacy `/history` diagnostic export can separately import up to 64 supported, dated observations through `/history/import`; imported rows contain no launch URI or artwork and reopen the source application. Existing records are merged by date, with the same retention limit. Keep the old app and its data available until these have been handled separately.

`tools/tv-session.py` captures the selected device/user identity, original HOME and package/settings state. `special-access --media --remote --ambient --replace-package OLD_PACKAGE` journals each supported access/old-screensaver substitution. Media listener grants use `cmd notification allow_listener`/`disallow_listener`; writing Secure settings alone is insufficient because NotificationManager can restore its stored grants after updates. Other applications' services are retained. `restore` reinstates the captured HOME and settings before disabling a newly installed public package. It does not erase app data.

```sh
python tools/tv-session.py --serial TV_IP:5555 backup --out backups/before
# Install and explicitly launch/test the public package first.
python tools/review_android.py --serial TV_IP:5555 --out shots/review --companion
python tools/tv-session.py --serial TV_IP:5555 set-home --backup backups/before
```

## Validation boundaries

Run the original 17 chronology assertions, Kotlin policy/calendar tests, Android Lint and APK archive/signature verification. `review_android.py` uses an explicitly selected device, real DPAD events, native screenshots, paired API rejection/order/profile checks and repeated returns from Android Settings. Temporary profile/order changes are restored. It does not install the APK or select HOME automatically.

Xiaomi Android 11 validation does not certify Android 12–17 behavior, every manufacturer key revision, DRM playback, voice/casting or user-specific external servers. A native browser automation host was unavailable during the initial migration; the companion protocol and JavaScript syntax were verified, but the new phone layout still requires a mobile-browser visual review.

## Design references

The UI/UX Pro Max skill by Next Level Builder was consulted for visible focus, icon semantics, contrast, spacing and reduced motion: https://github.com/nextlevelbuilder/ui-ux-pro-max-skill. Its generic design-system search returned web marketing layouts, which were not adopted for this TV launcher. Layout and navigation decisions follow Android TV guidance: https://developer.android.com/design/ui/tv/guides/styles/layouts and https://developer.android.com/design/ui/tv/guides/foundations/design-for-tv. No third-party skill runtime is bundled into the APK.

## Episode images and dock update

Compose episode cards use landscape artwork with separate titles and remaining time. Android TV thumbnails are preferred over posters. READ_TV_LISTINGS is a runtime permission and is requested contextually; the device migration tool can journal its grant with `special-access --listings`. Without it, media-session access alone is insufficient to recover the TV provider's metadata.

An artwork-only join can fill empty images from permitted Watch Next / preview rows when the matching image is unambiguous. Those rows never create watched entries or alter viewing dates. Images directly provided as media-session bitmaps are retained locally, at most 640 px, 64 files / 12 MiB, with the same thirty-day retention and a free-space reserve. File names are hashes; the image reader accepts only this private artwork namespace. It never captures playback frames or protected surfaces.

Some legacy exports contain no image and cannot be reconstructed from the available providers. These cards explicitly show “Image non partagée”. Images are not guessed from unrelated episodes. The implemented salon features and configuration limits are listed in [VISION.fr.md](VISION.fr.md). Google configuration is documented in [GOOGLE-SETUP.fr.md](GOOGLE-SETUP.fr.md).


## Fixed viewport and salon features

The first page has no vertical scroll container. LEFT/RIGHT changes only dock focus, with horizontal scrolling at the viewport edges. DOWN from the dock switches to the lower feed; UP/Back returns to the selected dock item. A draw-phase focus outline avoids image recomposition and default focus/ripple animations. A separate offscreen layer caches the wallpaper and gradients; this trades one bounded screen-size graphics buffer for fewer draw commands. The music panel is centered in the space above the dock. Recently opened apps are no longer collected or displayed.

The clock is minute-aligned. Media listeners detach when hidden, paused sessions use sparse fallback checks, and the full home model no longer reloads provider data on every playback-state callback. Appearance changes apply before slower provider reads. Optional maintenance uses JobScheduler with idle/unmetered constraints, not an always-running polling service.

Local phone controls, automatic matching of Android Google calendars to the active profile, guest display privacy, TVmaze watchlist/details, personal ZIP backups and optional Google device-code/Drive flows are implemented. External service credentials and an OAuth client remain owner configuration. Google sign-in is cancelled when leaving its page/activity. The system Cast receiver and Google TV remote remain authoritative for their system functions. MarOS has no microphone, voice recognizer or dictation interface; the remote voice key is handled by the existing system Google app.
