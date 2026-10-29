# Security policy

## Supported development line

The latest public beta is the active development version. Earlier private builds are outside this repository's release support. This project has not undergone an independent security audit.

## Report privately

Use [GitHub private vulnerability reporting](https://github.com/Marouane4142/MarOS/security/advisories/new) when it is enabled. Do not include working credentials, private photos, a device backup or pairing tokens in a public issue. If the private reporting form is unavailable, open a minimal public issue requesting a private contact route without disclosing exploit details or secrets.

Include affected version, relevant Android version, reproduction conditions and impact. The project does not promise a fixed response time or a bug bounty.

## Intended boundaries

The companion is a temporary **trusted-LAN HTTP** service. It is not an Internet-facing server, and pairing does not encrypt network traffic. Its endpoints validate pairing tokens, Host/Origin, input formats and bounded upload sizes. Do not forward its port to the Internet.

Only deliberately configured integrations should receive their credentials. Keep signing keys and Android/ADB credentials private. Official release APKs use the public certificate in [docs/release-signing-public.pem](docs/release-signing-public.pem); that certificate contains no private key. A matching checksum verifies bytes, while signature continuity is needed for Android updates.

Manufacturer package cleanup is optional and device-specific. Take a backup and keep a working HOME path before changing defaults. Never propose uninstalling packages or clearing user data as a routine troubleshooting step.
