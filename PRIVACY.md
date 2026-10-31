# Privacy

MarOS has no advertising SDK, analytics account or central MarOS backend. Some optional features contact external services; the app should not be described as entirely offline.

## Stored on the device

Preferences, selected apps, local viewing observations, agenda entries, timers, cached weather and shortcuts are kept in the application's private Android storage. Imported photos and banners are copied into that storage. Integration tokens are encrypted with an Android Keystore-backed AES-GCM key. Android and the device manufacturer still control the underlying operating system and other installed applications.

The portable JSON export contains an explicit list of display/app preferences and the chosen weather search text. It excludes photos, integration tokens, private agenda entries and server addresses. Treat exported preferences and device backups as personal files and do not attach them to public issues.

The separate personal ZIP archive includes local photos/banners, portable profiles/preferences, watchlist, local agenda and viewing observations. It excludes integration credentials, Google tokens and system account credentials. Validation precedes restoration; restoration merges records and keeps a private archive of the previous state. Archives are personal data. Drive upload happens only on request and sends this archive to the user's Google Drive.

## Optional access

- **Notification-listener access** allows Android media-session enumeration for playback controls and compatible video observations. The implementation does not read/store posted notification content.
- **Accessibility access** filters selected dedicated remote keys and optional calibration. It does not request retrieval of window content.
- Media-session artwork bitmaps may be retained privately for viewing cards, with a 12 MiB / 64-image / thirty-day limit. No screenshots of video playback are captured. Clearing local viewing history also removes these retained images.
- **TV listings** provide the usable Watch Next entries that providers choose to share.
- **Network** supports the local companion, weather and configured integrations.
- **System account selection** uses Android's account picker. Only a user-selected Google account is associated with a launcher profile. Its identity record stays in private storage and is excluded from portable exports; ordinary profile names remain part of profile backups. MarOS reads no system account passwords or tokens. Android account visibility is checked before reading calendars for a native association. Unlinking preserves the system account.
- **Calendar access**, requested explicitly from the profile screen, reads events from visible synchronized Google calendars matching the selected MarOS profile. Android expands recurring events. Guest mode hides these events, personal viewing feeds, Google identity and private images in MarOS.
- MarOS no longer exposes its own voice recognition or dictation. The Google voice key remains a firmware/system feature with its provider's policy.

These accesses are optional and should be enabled deliberately in Android settings. They can be revoked there. History from a streaming service is not made universally available by enabling them.

## Network activity

- Weather is inactive until a city/postal-code search is configured. The search is sent to Open-Meteo's geocoding endpoint, then coordinates are sent to its forecast endpoint. The provider also receives the network request metadata, such as the public IP address. No GPS permission is requested.
- The phone companion listens locally on port 8765 only during an explicit sharing session, normally one hour. HTTP is not encrypted. Pairing is required for its APIs; closing the session invalidates the token. Do not use untrusted networks or router port forwarding.
- Jellyfin, Home Assistant, the optional Ollama-compatible assistant, Wake-on-LAN and camera links use the user's own configured addresses. Requests may include the credentials required by those services.
- Immich requests use the configured HTTPS server and an API key encrypted with Android Keystore. Imports read the selected album; cached copies remain available offline. An optional six-hour idle/unmetered job can refresh it. This is disabled by default. The key and connection details are excluded from exports.
- Remote photo lists and ICS feeds are fetched only on explicit request from the configured HTTPS addresses. Imported events/images remain local; source URLs are excluded from portable exports.
- The version check contacts the public GitHub releases API on request.
- A separate, authenticated legacy-history import merges supported viewing titles, dates and positions into the local 30-day store. It accepts no launch URI or artwork URL. The portable visual-settings export does not include viewing history.
- Local profile snapshots contain portable launcher settings, separate from streaming accounts and encrypted credentials.
- Opening an app, web search or deep link sends the chosen content to that app/service under its own privacy practices.
- Optional TVmaze searches send the entered series name to TVmaze. Selecting a series stores its identifier; manual or opt-in background refresh sends that ID to retrieve metadata and upcoming episodes. This personal watchlist does not infer that a title was watched.
- Optional Drive authorization contacts Google's official OAuth and user-info endpoints over HTTPS. Credentials are entered on Google's page. MarOS requests identity and `drive.file`, stores tokens locally with Android Keystore and only lists its own backup archives. This authorization is separate from native account selection and is not needed for local profiles or Android calendars. The paired companion can show account status and selected calendar events but cannot retrieve or configure OAuth secrets. Revocation is available in the profile picker and the Google account controls.
- Cast diagnostics contact the existing receiver on the box. Actual casting, Play Store, system accounts and Google TV remote functionality remain system services with their providers' policies.

## Control and removal

Use the app's history/photo controls and **Effacer ma localisation** to remove the corresponding local items. Revoking special access stops the associated integrations. Android app-data controls remove the app's local data and may remove imported copies; original photo files on the phone remain separate. MarOS installation/restore tools do not uninstall packages or clear app data.

See the source for exact behavior and [SECURITY.md](SECURITY.md) for reporting a vulnerability. This document describes MarOS, not the privacy policies of Android, the TV manufacturer or third-party services.

Google identities and bounded private avatar images are associated with launcher profiles. OAuth tokens are encrypted separately per profile and excluded from archives. Guest selection hides the active identity and calendar. MarOS profile switching changes launcher preferences and calendar matching; it does not switch the accounts inside third-party streaming apps. Avatar images are downloaded only after explicit Google sign-in, over HTTPS from Google image hosts, and are never fetched during focus animation.
