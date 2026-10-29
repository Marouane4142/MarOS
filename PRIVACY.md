# Privacy

MarOS has no advertising SDK, analytics account or central MarOS backend. Some optional features contact external services; the app should not be described as entirely offline.

## Stored on the device

Preferences, selected apps, local viewing observations, agenda entries, timers, cached weather and shortcuts are kept in the application's private Android storage. Imported photos and banners are copied into that storage. Integration tokens are encrypted with an Android Keystore-backed AES-GCM key. Android and the device manufacturer still control the underlying operating system and other installed applications.

The portable JSON export contains an explicit list of display/app preferences and the chosen weather search text. It excludes photos, integration tokens, private agenda entries and server addresses. Treat exported preferences and device backups as personal files and do not attach them to public issues.

## Optional access

- **Notification-listener access** allows Android media-session enumeration for playback controls and compatible video observations. The implementation does not read/store posted notification content.
- **Accessibility access** filters selected dedicated remote keys and optional calibration. It does not request retrieval of window content.
- **TV listings** provide the usable Watch Next entries that providers choose to share.
- **Network** supports the local companion, weather and configured integrations.

These accesses are optional and should be enabled deliberately in Android settings. They can be revoked there. History from a streaming service is not made universally available by enabling them.

## Network activity

- Weather is inactive until a city/postal-code search is configured. The search is sent to Open-Meteo's geocoding endpoint, then coordinates are sent to its forecast endpoint. The provider also receives the network request metadata, such as the public IP address. No GPS permission is requested.
- The phone companion listens locally on port 8765 only during an explicit sharing session, normally one hour. HTTP is not encrypted. Pairing is required for its APIs; closing the session invalidates the token. Do not use untrusted networks or router port forwarding.
- Jellyfin, Home Assistant, the optional Ollama-compatible assistant, Wake-on-LAN and camera links use the user's own configured addresses. Requests may include the credentials required by those services.
- Opening an app, web search or deep link sends the chosen content to that app/service under its own privacy practices.

## Control and removal

Use the app's history/photo controls and **Effacer ma localisation** to remove the corresponding local items. Revoking special access stops the associated integrations. Android app-data controls remove the app's local data and may remove imported copies; original photo files on the phone remain separate. MarOS installation/restore tools do not uninstall packages or clear app data.

See the source for exact behavior and [SECURITY.md](SECURITY.md) for reporting a vulnerability. This document describes MarOS, not the privacy policies of Android, the TV manufacturer or third-party services.
