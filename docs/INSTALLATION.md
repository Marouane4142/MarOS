# Installation and rollback

This guide is for Android TV / Google TV devices running Android 11 or later. The public beta uses `io.github.marouane4142.maros`, separate from the earlier private `com.mihir.tvhome` package. Do not reuse a private device backup from another device or user.

If migrating from the earlier private package, close its phone-companion session first: both packages use port 8765. Enable special access only for the version you intend to use. Keep the previous app and its data for rollback; the public APK does not import them automatically.

## 1. Prepare

Download the APK and checksum file from [Releases](https://github.com/Marouane4142/MarOS/releases). Verify the SHA-256 before installing. ADB Platform-Tools and Python 3.10+ are required for the journaled path; Android SDK Build-Tools are also needed to verify a first-install APK's package.

Enable developer options and USB debugging on the TV. Connect the computer and TV to a trusted local network. Authorize the computer on the TV when prompted. Use the actual endpoint shown by the TV; some newer devices require their own wireless-debugging pairing/port rather than port 5555.

The examples use `TV_IP:5555` as a placeholder, never an auto-selected device:

```sh
adb connect TV_IP:5555
adb -s TV_IP:5555 get-state
python tools/tv-session.py --serial TV_IP:5555 backup --out backup/before-maros
```

Keep the entire backup directory on your computer. It records device identity, current user, package states and configuration; it is not a system image or a copy of every application's private data. Never upload it to GitHub.

## 2. Install and inspect before changing HOME

```sh
python tools/tv-session.py --serial TV_IP:5555 install-new --backup backup/before-maros /path/to/MarOS-4.0.0-beta.1.apk
adb -s TV_IP:5555 shell am start -W -n io.github.marouane4142.maros/.MainActivity
python tools/tv-session.py --serial TV_IP:5555 screenshot --out shots/first-launch.png
```

The first-install helper refuses to overwrite an existing public MarOS package. If needed, provide `install-new ... --aapt /path/to/aapt2`. Check the UI and remote navigation before proceeding. Compilation with `launcher/build.py` does not perform these installation steps.

## 3. Select HOME

```sh
python tools/tv-session.py --serial TV_IP:5555 set-home --backup backup/before-maros
adb -s TV_IP:5555 shell input keyevent KEYCODE_HOME
```

Test Home again from **inside another application**. Keep ADB available until rollback has been checked. If Google TV retakes Home, inspect the installed stock components before disabling anything. On some Google TV devices the two relevant packages are `com.google.android.apps.tv.launcherx` and `com.google.android.tungsten.setupwraith`. Each optional change must use the captured backup and be applied individually, for example:

```sh
python tools/tv-session.py --serial TV_IP:5555 disable --backup backup/before-maros com.google.android.tungsten.setupwraith
```

Do not copy a manufacturer's entire package list to an unrelated TV. No package is uninstalled by this helper.

## 4. Optional features

- **Remote app buttons:** enable “Boutons de la télécommande MarOS” in Android accessibility settings, then calibrate only if the Xiaomi defaults do not match your remote. Confirm the physical buttons; injected ADB key events are not a substitute.
- **Music and video observations:** enable “Lecture en cours MarOS” in Android notification access. Compatible apps must publish usable media sessions.
- **Watch Next access:** where supported, `adb -s TV_IP:5555 shell pm grant io.github.marouane4142.maros android.permission.READ_TV_LISTINGS` grants the declared permission. A provider may still expose no usable data.
- **Weather:** choose a city in Météo. No city or forecast request is preset.
- **Phone:** open Mon téléphone and scan the current QR code. The HTTP address and code are generated from the device's local interface. The TV must be awake/reachable and the phone must be on the same non-isolated LAN.
- **Ambient photos:** use MarOS ambient mode or select the MarOS screensaver in Android's screensaver settings.

Access switches changed manually in Android settings are not automatically journaled by the CLI. They can be revoked there. Integrations requiring equipment are optional; do not paste server tokens into public issues.

## Updates

An official release can update a prior official release of the same package and signer:

```sh
adb -s TV_IP:5555 install -r /path/to/new-Maros-release.apk
```

`-r` preserves app data. Keep a portable preferences export before an update. Never solve a signing mismatch by clearing data or uninstalling a user's app. Local debug builds use a separate signer and should be tested on a separate development device/emulator.

## Rollback

```sh
python tools/tv-session.py --serial TV_IP:5555 restore --backup backup/before-maros
adb -s TV_IP:5555 shell input keyevent KEYCODE_HOME
```

The helper checks device/user identity, restores journaled package states and HOME, and disables a launcher it installed only after the captured HOME is available. It does not uninstall packages or clear their data. It only reverses recorded operations; manually changed permissions or settings need their own reversal. Keep the journal so an interrupted restore can be resumed.

Turn debugging off when it is no longer needed. This repository does not contain your backup or a universal factory-reset script.
