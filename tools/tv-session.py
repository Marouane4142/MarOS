#!/usr/bin/env python3
"""Checked, journaled Windows/native-ADB companion to tv.py. Never uninstalls.

Examples (global options precede the subcommand):
  python tools/tv-session.py --serial TV_IP:5555 backup --out C:/private/tv-backup
  python tools/tv-session.py --serial TV_IP:5555 disable --backup C:/private/tv-backup package.name
  python tools/tv-session.py --serial TV_IP:5555 install-new --backup C:/private/tv-backup launcher/build/tvhome.apk
  python tools/tv-session.py --serial TV_IP:5555 set-home --backup C:/private/tv-backup
  python tools/tv-session.py --serial TV_IP:5555 screenshot --out C:/private/home.png
  python tools/tv-session.py --serial TV_IP:5555 restore --backup C:/private/tv-backup

Backups contain private settings. They are configuration snapshots, not system
images or copies of private application data. New launcher rollback disables it;
its APK and data remain installed, as uninstalling is deliberately unsupported.
"""

import argparse
import datetime as dt
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tempfile
import uuid


PACKAGE = "io.github.marouane4142.maros"
HOME = PACKAGE + "/.MainActivity"
PKG_RE = re.compile(r"^[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+$")
COMP_RE = re.compile(r"^[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+$")
STATE_COMMAND = {0: "default-state", 1: "enable", 2: "disable", 3: "disable-user", 4: "disable-until-used"}
STATE_NAME = {0: "default", 1: "enabled", 2: "disabled", 3: "disabled-user", 4: "disabled-until-used"}


def now():
    return dt.datetime.now(dt.timezone.utc).isoformat()


def atomic_bytes(path, data):
    path = Path(path)
    path.parent.mkdir(parents=True, exist_ok=True)
    fd, name = tempfile.mkstemp(prefix=path.name + ".", suffix=".tmp", dir=str(path.parent))
    try:
        with os.fdopen(fd, "wb") as stream:
            stream.write(data)
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(name, path)
    finally:
        if os.path.exists(name):
            os.unlink(name)


def atomic_json(path, data):
    atomic_bytes(path, (json.dumps(data, indent=2, ensure_ascii=False) + "\n").encode("utf-8"))


def sdk_root():
    return Path(os.environ.get("ANDROID_SDK_ROOT") or os.environ.get("ANDROID_HOME") or
                str(Path(os.environ.get("LOCALAPPDATA", str(Path.home() / "AppData/Local"))) / "Android/Sdk"))


def parse_states(raw, user):
    """Read active package records only; ignore duplicate hidden-system records."""
    match = re.search(r"(?m)^Packages:\s*$", raw)
    if not match:
        raise RuntimeError("dumpsys package did not contain the Packages section")
    section = raw[match.end():]
    section = re.split(r"(?m)^Hidden system packages:\s*$", section, maxsplit=1)[0]
    states, package = {}, None
    for line in section.splitlines():
        start = re.match(r"^\s{2}Package \[([^\]]+)\]", line)
        if start:
            package = start.group(1)
            continue
        entry = re.match(r"^\s+User (\d+):\s+(.*)$", line)
        if package and entry and int(entry.group(1)) == user:
            enabled = re.search(r"(?:^|\s)enabled=(\d+)(?:\s|$)", entry.group(2))
            installed = re.search(r"(?:^|\s)installed=(true|false)(?:\s|$)", entry.group(2))
            if enabled and installed:
                value = int(enabled.group(1))
                if value not in STATE_COMMAND:
                    raise RuntimeError("Unsupported enabled state for " + package)
                states[package] = {"enabled": value, "installed": installed.group(1) == "true"}
    if not states:
        raise RuntimeError("Could not extract package enabled states for user " + str(user))
    return states


def parse_home(raw):
    components = [line.strip() for line in raw.splitlines() if COMP_RE.fullmatch(line.strip())]
    return components[-1] if components else None


def concrete_home(component):
    return bool(component and COMP_RE.fullmatch(component) and not component.startswith("android/"))


class ADB:
    def __init__(self, path, serial):
        self.path, self.serial = str(path), serial

    def run(self, args, binary=False, timeout=120):
        result = subprocess.run([self.path, "-s", self.serial, *args], stdout=subprocess.PIPE,
                                stderr=subprocess.PIPE, timeout=timeout)
        if result.returncode:
            raise RuntimeError("adb failed (%s): %s" % (result.returncode, result.stderr.decode("utf-8", "replace").strip()))
        return result.stdout if binary else result.stdout.decode("utf-8", "replace").replace("\r\n", "\n")

    def shell(self, command, timeout=120):
        # A sentinel also detects older shell transports that lose the exit status.
        marker = "__TVSESSION_" + uuid.uuid4().hex + "__"
        wrapped = command + "\n__tv_rc=$?\nprintf '\\n" + marker + ":%s\\n' \"$__tv_rc\"\nexit \"$__tv_rc\""
        result = subprocess.run([self.path, "-s", self.serial, "shell", "sh", "-c", shlex.quote(wrapped)],
                                stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=timeout)
        output = result.stdout.decode("utf-8", "replace").replace("\r\n", "\n")
        match = re.search(r"\n" + re.escape(marker) + r":(\d+)\s*$", output)
        if not match:
            raise RuntimeError("No checked shell result for %r: %s" % (command, (output + result.stderr.decode("utf-8", "replace"))[-1600:]))
        body, rc = output[:match.start()], int(match.group(1))
        if rc or result.returncode:
            raise RuntimeError("TV shell failed (%s/%s), %r: %s" % (rc, result.returncode, command, body[-1600:]))
        return body

    def prop(self, key):
        return self.shell("getprop " + shlex.quote(key)).strip()

    def identity(self):
        return {"device_serial": self.prop("ro.serialno") or self.prop("ro.boot.serialno"),
                "fingerprint": self.prop("ro.build.fingerprint"),
                "model": self.prop("ro.product.model"), "sdk": self.prop("ro.build.version.sdk"),
                "transport_serial": self.serial,
                "current_user": int(self.shell("am get-current-user").strip())}

    def states(self, user):
        return parse_states(self.shell("dumpsys package"), user)

    def home(self, user):
        return parse_home(self.shell("cmd package resolve-activity --brief --user %s -a android.intent.action.MAIN -c android.intent.category.HOME" % user))

    def screenshot(self, destination):
        data = self.run(["exec-out", "screencap", "-p"], binary=True)
        if not data.startswith(b"\x89PNG\r\n\x1a\n"):
            raise RuntimeError("TV did not return a PNG screenshot")
        atomic_bytes(destination, data)


def backup(adb, destination):
    folder = Path(destination).resolve()
    if folder.exists() and any(folder.iterdir()):
        raise RuntimeError("Backup directory must be absent or empty; existing snapshots are never overwritten")
    folder.mkdir(parents=True, exist_ok=True)
    identity = adb.identity()
    if not identity["device_serial"] or not identity["fingerprint"]:
        raise RuntimeError("Stable device serial and build fingerprint are required")
    user = identity["current_user"]
    raw = {"getprop.txt": "getprop", "dumpsys-package.txt": "dumpsys package",
           "settings-global.txt": "settings list global", "settings-secure.txt": "settings --user %s list secure" % user,
           "settings-system.txt": "settings --user %s list system" % user,
           "packages-installed.txt": "pm list packages --user %s" % user,
           "packages-enabled.txt": "pm list packages -e --user %s" % user,
           "packages-disabled.txt": "pm list packages -d --user %s" % user,
           "home-resolved.txt": "cmd package resolve-activity --brief --user %s -a android.intent.action.MAIN -c android.intent.category.HOME" % user,
           "home-candidates.txt": "cmd package query-activities --brief --components --user %s -a android.intent.action.MAIN -c android.intent.category.HOME" % user}
    captured = {}
    for name, command in raw.items():
        captured[name] = adb.shell(command)
        atomic_bytes(folder / "raw" / name, captured[name].encode("utf-8"))
    states = parse_states(captured["dumpsys-package.txt"], user)
    installed = set(re.findall(r"(?m)^package:([^\s]+)$", captured["packages-installed.txt"]))
    missing = sorted(installed - set(states))
    if missing:
        raise RuntimeError("Incomplete package-state backup: " + ", ".join(missing))
    adb.screenshot(folder / "before.png")
    snapshot = {"schema": 1, "created_at": now(), "identity": identity, "current_user": user,
                "home": parse_home(captured["home-resolved.txt"]), "packages": states,
                "limitations": "Configuration snapshot only; private app data and system image are not copied. No packages will be uninstalled."}
    atomic_json(folder / "journal.json", {"schema": 1, "entries": []})
    # Snapshot is the final commit marker: mutations refuse an incomplete backup.
    atomic_json(folder / "snapshot.json", snapshot)
    print("Backup complete: " + str(folder))


class Session:
    def __init__(self, adb, folder):
        self.adb, self.folder = adb, Path(folder).resolve()
        self.snapshot = json.loads((self.folder / "snapshot.json").read_text(encoding="utf-8"))
        self.journal = json.loads((self.folder / "journal.json").read_text(encoding="utf-8"))
        if self.snapshot.get("schema") != 1 or self.journal.get("schema") != 1:
            raise RuntimeError("Unsupported backup or journal schema")
        original, actual = self.snapshot["identity"], adb.identity()
        for field in ("device_serial", "fingerprint", "model", "sdk", "current_user"):
            if not original.get(field) or actual[field] != original[field]:
                # User zero is valid, hence the separate presence check below.
                if field == "current_user" and field in original and actual[field] == original[field]:
                    continue
                raise RuntimeError("Device identity/current-user mismatch for " + field)
        self.user = self.snapshot["current_user"]
        if self.user != actual["current_user"]:
            raise RuntimeError("Snapshot user mismatch")

    def write(self):
        atomic_json(self.folder / "journal.json", self.journal)

    def prepare(self, kind, **fields):
        entry = {"id": uuid.uuid4().hex, "kind": kind, "status": "prepared", "prepared_at": now(), **fields}
        self.journal["entries"].append(entry)
        self.write()
        return entry

    def finish(self, entry, status="applied"):
        entry["status"], entry[status + "_at"] = status, now()
        self.write()

    def set_setting(self, namespace, key, value):
        if namespace != "secure" or key not in {"enabled_notification_listeners", "enabled_accessibility_services", "accessibility_enabled", "screensaver_components"}:
            raise RuntimeError("Unsupported special-access setting")
        prefix = "settings --user %s " % self.user
        values = dict(line.split("=", 1) for line in self.adb.shell(prefix + "list " + namespace).splitlines() if "=" in line)
        if values.get(key) == value:
            return
        entry = self.prepare("setting", namespace=namespace, key=key, old_present=key in values, old_value=values.get(key, ""), target_value=value)
        self.adb.shell(prefix + "put %s %s %s" % (namespace, key, shlex.quote(value)))
        if self.adb.shell(prefix + "get %s %s" % (namespace, key)).strip() != value:
            raise RuntimeError("Special-access readback failed")
        self.finish(entry)

    def notification_listener(self, component, enabled):
        if not re.fullmatch(r"[A-Za-z0-9_.]+/[A-Za-z0-9_.]+", component):
            raise RuntimeError("Invalid listener component")
        raw = self.adb.shell("settings --user %s get secure enabled_notification_listeners" % self.user).strip()
        old = component in raw.split(":")
        if old == enabled:
            return
        entry = self.prepare("notification_listener", component=component, old_enabled=old, target_enabled=enabled)
        self.set_notification_listener(component, enabled)
        self.finish(entry)

    def set_notification_listener(self, component, enabled):
        # Settings.Secure alone is overwritten by NotificationManager's stored grants.
        command = "allow_listener" if enabled else "disallow_listener"
        self.adb.shell("cmd notification %s %s %s" % (command, shlex.quote(component), self.user))
        raw = self.adb.shell("settings --user %s get secure enabled_notification_listeners" % self.user).strip()
        if (component in raw.split(":")) != enabled:
            raise RuntimeError("Notification listener grant readback failed")

    def special_access(self, media, remote, replace, ambient=False, listings=False):
        self.package_state(PACKAGE)
        if listings:
            permission = "android.permission.READ_TV_LISTINGS"
            dump = self.adb.shell("dumpsys package " + PACKAGE)
            if not re.search(re.escape(permission) + r": granted=true", dump):
                entry = self.prepare("runtime_permission", package=PACKAGE, permission=permission, old_granted=False)
                self.adb.shell("pm grant --user %s %s %s" % (self.user, PACKAGE, permission))
                if not re.search(re.escape(permission) + r": granted=true", self.adb.shell("dumpsys package " + PACKAGE)):
                    raise RuntimeError("TV listings permission grant failed")
                self.finish(entry)
        if replace and not re.fullmatch(r"[A-Za-z0-9_]+(?:\.[A-Za-z0-9_]+)+", replace):
            raise RuntimeError("Invalid replacement package")
        if media:
            self.notification_listener(PACKAGE + "/" + PACKAGE + ".MediaListener", True)
            if replace:
                raw = self.adb.shell("settings --user %s get secure enabled_notification_listeners" % self.user).strip()
                for component in raw.split(":"):
                    if component.startswith(replace + "/"):
                        self.notification_listener(component, False)
        for enabled, key, service in [(remote, "enabled_accessibility_services", "RemoteButtonService")]:
            if not enabled:
                continue
            raw = self.adb.shell("settings --user %s get secure %s" % (self.user, key)).strip()
            components = [c for c in raw.split(":") if c and c != "null" and not (replace and c.startswith(replace + "/"))]
            component = PACKAGE + "/" + PACKAGE + "." + service
            if component not in components:
                components.append(component)
            self.set_setting("secure", key, ":".join(components))
        if remote:
            self.set_setting("secure", "accessibility_enabled", "1")
        if ambient and replace:
            raw = self.adb.shell("settings --user %s get secure screensaver_components" % self.user).strip()
            components = raw.split(":")
            replacement = PACKAGE + "/" + PACKAGE + ".AmbientDreamService"
            selected = [replacement if item.startswith(replace + "/") else item for item in components]
            if selected != components:
                self.set_setting("secure", "screensaver_components", ":".join(selected))
        print("Requested special access applied and journaled; unrelated services preserved.")

    def package_state(self, package, required=True):
        state = self.adb.states(self.user).get(package)
        if state is None or not state["installed"]:
            if required:
                raise RuntimeError("Package is not installed for current user: " + package)
            return None
        return state["enabled"]

    def change_enabled(self, package, value):
        output = self.adb.shell("pm %s --user %s %s" % (STATE_COMMAND[value], self.user, shlex.quote(package)))
        actual = self.package_state(package)
        if actual != value:
            raise RuntimeError("Enabled-state readback failed for %s: %r; output: %s" % (package, actual, output))

    def disable(self, package):
        if not PKG_RE.fullmatch(package):
            raise RuntimeError("Invalid package name")
        if package not in self.snapshot["packages"]:
            raise RuntimeError("Package was not captured in the backup")
        old = self.package_state(package)
        if old == 3:
            print(package + " is already disabled-user; no change")
            return
        entry = self.prepare("disable", package=package, old_enabled=old, target_enabled=3)
        self.change_enabled(package, 3)
        self.finish(entry)
        print(package + ": " + STATE_NAME[old] + " -> disabled-user")

    def install_new(self, apk, aapt=None):
        if not concrete_home(self.snapshot.get("home")):
            raise RuntimeError("No concrete original HOME was captured; cannot guarantee a safe launcher rollback")
        if PACKAGE in self.snapshot["packages"] or self.package_state(PACKAGE, required=False) is not None:
            raise RuntimeError("Refusing to overwrite a pre-existing " + PACKAGE)
        apk = Path(apk).resolve(strict=True)
        if aapt:
            aapt = Path(aapt)
        else:
            root = Path(self.adb.path).resolve().parent.parent / "build-tools"
            candidates = list(root.glob("*/aapt2.exe")) + list(root.glob("*/aapt.exe")) + list(root.glob("*/aapt2"))
            if not candidates:
                raise RuntimeError("Cannot verify APK package; provide --aapt PATH")
            aapt = sorted(candidates, key=lambda p: tuple(int(x) for x in re.findall(r"\d+", p.parent.name)))[-1]
        check = subprocess.run([str(aapt), "dump", "badging", str(apk)], stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=60)
        if check.returncode:
            raise RuntimeError("aapt could not verify the APK")
        match = re.search(r"(?m)^package: name='([^']+)'", check.stdout.decode("utf-8", "replace"))
        if not match or match.group(1) != PACKAGE:
            raise RuntimeError("APK is not " + PACKAGE)
        entry = self.prepare("install_new", package=PACKAGE, apk=str(apk), original_home=self.snapshot["home"])
        output = self.adb.run(["install", "--user", str(self.user), str(apk)], timeout=180)
        if not re.search(r"(?m)^Success\s*$", output) or self.package_state(PACKAGE, required=False) is None:
            raise RuntimeError("Installation was not verified: " + output)
        self.finish(entry)
        print("Installed " + PACKAGE + "; HOME has not been changed. Validate by launching explicitly before set-home.")

    def set_home(self):
        if not concrete_home(self.snapshot.get("home")):
            raise RuntimeError("No concrete original HOME was captured; refusing to change the default")
        if self.package_state(PACKAGE) not in (0, 1):
            raise RuntimeError("Custom launcher must be enabled before setting HOME")
        current = self.adb.home(self.user)
        if current == HOME:
            print("Custom launcher is already HOME; no change")
            return
        entry = self.prepare("set_home", previous_home=current, target_home=HOME)
        self.set_home_checked(HOME)
        self.finish(entry)
        print("HOME now resolves to " + HOME)

    def set_home_checked(self, component):
        if not concrete_home(component):
            raise RuntimeError("Cannot restore an unresolved HOME component")
        self.adb.shell("cmd package set-home-activity --user %s %s" % (self.user, shlex.quote(component)))
        actual = self.adb.home(self.user)
        # Android may expand .Activity in the readback.
        def expanded(value):
            if not value:
                return value
            package, activity = value.split("/", 1)
            return package + "/" + (package + activity if activity.startswith(".") else activity)
        if expanded(actual) != expanded(component):
            raise RuntimeError("HOME readback failed: " + str(actual))

    def restore(self):
        entries = [e for e in reversed(self.journal["entries"]) if e["status"] != "restored"]
        if not entries:
            print("No journaled changes to restore; TV unchanged")
            return
        # Restore stock/package availability first, while custom HOME remains usable.
        for entry in entries:
            if entry["kind"] == "disable":
                self.change_enabled(entry["package"], entry["old_enabled"])
                self.finish(entry, "restored")
        # V2 special access is a secure setting. Restore presence and empty values
        # exactly before disabling the app that supplied these optional services.
        setting_entries = [e for e in entries if e["kind"] == "setting"]
        # Android changes accessibility_enabled as a side effect of changing the
        # service list. Restore that master switch last, after its dependencies.
        setting_entries.sort(key=lambda e: e["key"] == "accessibility_enabled")
        for entry in setting_entries:
            if entry["kind"] == "setting":
                namespace, key = entry["namespace"], entry["key"]
                if namespace not in ("secure", "system", "global") or not re.fullmatch(r"[A-Za-z0-9_]+", key):
                    raise RuntimeError("Invalid journaled setting")
                prefix = "settings --user %s " % self.user
                if entry["old_present"]:
                    command = prefix + "put %s %s %s" % (namespace, key, shlex.quote(entry["old_value"]))
                else:
                    command = prefix + "delete %s %s" % (namespace, key)
                self.adb.shell(command)
                observed = self.adb.shell(prefix + "list " + namespace)
                values = dict(line.split("=", 1) for line in observed.splitlines() if "=" in line)
                if (key in values) != entry["old_present"] or entry["old_present"] and values[key] != entry["old_value"]:
                    raise RuntimeError("Setting restore readback failed for " + key)
                self.finish(entry, "restored")
        for entry in entries:
            if entry["kind"] == "notification_listener":
                self.set_notification_listener(entry["component"], entry["old_enabled"])
                self.finish(entry, "restored")
        for entry in entries:
            if entry["kind"] == "runtime_permission":
                command = "grant" if entry["old_granted"] else "revoke"
                self.adb.shell("pm %s --user %s %s %s" % (command, self.user, shlex.quote(entry["package"]), shlex.quote(entry["permission"])))
                self.finish(entry, "restored")
        for entry in entries:
            if entry["kind"] == "set_home":
                self.set_home_checked(self.snapshot["home"])
                self.finish(entry, "restored")
        # Disable the newly installed launcher only after original HOME is available.
        for entry in entries:
            if entry["kind"] == "install_new":
                if self.package_state(PACKAGE, required=False) is not None:
                    original_home = self.snapshot.get("home")
                    if not concrete_home(original_home):
                        raise RuntimeError("Cannot safely disable custom launcher without an original HOME")
                    if self.adb.home(self.user) != original_home:
                        self.set_home_checked(original_home)
                    self.change_enabled(PACKAGE, 3)
                self.finish(entry, "restored")
        unknown = [e["kind"] for e in entries if e["status"] != "restored"]
        if unknown:
            raise RuntimeError("Unsupported journal entries remain: " + repr(unknown))
        print("Journaled changes restored; nothing was uninstalled.")


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--adb", default=str(sdk_root() / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb")))
    parser.add_argument("--serial", required=True, help="Explicit ADB transport serial; no auto-selection")
    subs = parser.add_subparsers(dest="command", required=True)
    take = subs.add_parser("backup")
    take.add_argument("--out", required=True)
    shot = subs.add_parser("screenshot")
    shot.add_argument("--out", required=True)
    for command in ("disable", "install-new", "set-home", "restore", "special-access"):
        child = subs.add_parser(command)
        child.add_argument("--backup", required=True, help="Directory created by backup")
        if command == "disable":
            child.add_argument("package")
        if command == "install-new":
            child.add_argument("apk")
            child.add_argument("--aapt")
        if command == "special-access":
            child.add_argument("--media", action="store_true")
            child.add_argument("--remote", action="store_true")
            child.add_argument("--replace-package")
            child.add_argument("--ambient", action="store_true")
            child.add_argument("--listings", action="store_true", help="Grant the normal TV-listings runtime permission")
    args = parser.parse_args()
    adb = ADB(args.adb, args.serial)
    if args.command == "backup":
        backup(adb, args.out)
    elif args.command == "screenshot":
        adb.screenshot(args.out)
        print("Screenshot saved: " + str(Path(args.out).resolve()))
    else:
        session = Session(adb, args.backup)
        if args.command == "disable":
            session.disable(args.package)
        elif args.command == "install-new":
            session.install_new(args.apk, args.aapt)
        elif args.command == "set-home":
            session.set_home()
        elif args.command == "special-access":
            session.special_access(args.media, args.remote, args.replace_package, args.ambient, args.listings)
        elif args.command == "restore":
            session.restore()


if __name__ == "__main__":
    try:
        main()
    except (RuntimeError, OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print("ERROR: " + str(error), file=sys.stderr)
        sys.exit(1)
