# Publishing a release

Official APKs must use the same private release signer for update continuity. The public certificate is in `release-signing-public.pem`; it is safe to distribute and contains no private key. Do not commit, upload as a release asset, or include in source archives a keystore, password file, ADB key or TV backup.

The first public version is `4.0.0-beta.1`, versionCode `40000`, package `io.github.marouane4142.maros`. It is separate from the earlier private package. Raise versionCode monotonically for each future public APK. Keep versionName, CHANGELOG, build User-Agent and release notes consistent.

## Before tagging

1. Review the source diff, permissions, network behavior and third-party notices.
2. Run `python tools/check.py`, build the APK and validate the changed behavior on an appropriate test device. Record limitations honestly in release notes.
3. Confirm no personal location, machine path, credentials, signed private archive or user data is tracked.
4. Update CHANGELOG. Public binary assets belong in Releases rather than the source tree.

## Signed build

Keep the release keystore outside the repository. Provide its passwords as environment variables without placing their values in shell history, logs or workflow files. The build reads them by name:

```sh
python launcher/build.py --release \
  --keystore /private/path/maros-release.jks --alias maros-release \
  --output dist/MarOS-VERSION.apk
```

The defaults read `MAROS_STORE_PASSWORD` and `MAROS_KEY_PASSWORD`. The script verifies signing and alignment and writes a SHA-256 file. Development CI uses a different debug key, not the official release key. No official signing secret is stored in this repository's workflows.

Inspect the Android package/version and certificate using SDK `aapt2 dump badging` and `apksigner verify --print-certs`. Prepare `SHA256SUMS.txt` for the exact upload assets. Keep the private key and its recovery information in a separate secure backup: losing it prevents ordinary same-package updates.

## GitHub release

Create a tag at the reviewed commit and push it without force. Authenticate `gh` using the maintainer account. Use an actual file for multiline release notes:

```sh
git tag -a vVERSION -m "MarOS VERSION"
git push origin vVERSION
gh release create vVERSION dist/MarOS-VERSION.apk dist/SHA256SUMS.txt \
  --verify-tag --prerelease --title "MarOS VERSION" --notes-file docs/releases/VERSION.md
```

Use `--prerelease` for beta builds; do not represent broad hardware compatibility or untested connectors as stable. Verify the published metadata, attached files and their hashes after upload. GitHub supplies source archives from the tag. A future stable release can omit the prerelease flag after its support claims are validated.
