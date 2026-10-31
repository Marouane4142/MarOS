#!/usr/bin/env python3
"""Install and review only an explicitly selected isolated emulator."""
import argparse
from pathlib import Path
import re
import shutil
import subprocess
import sys
from review_android import main as review


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--out', type=Path, required=True)
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        parser.error('Only an explicitly named emulator is allowed.')
    adb = shutil.which('adb')
    if not adb:
        parser.error('ADB missing')
    args.out.mkdir(parents=True, exist_ok=True)
    metrics = [subprocess.check_output([adb, '-s', args.serial, 'shell', *command]).decode('utf-8', 'replace') for command in [('getprop', 'ro.build.version.sdk'), ('wm', 'size'), ('wm', 'density')]]
    (args.out / 'device.txt').write_text(''.join(metrics), encoding='utf-8')
    subprocess.run([adb, '-s', args.serial, 'install', '-r', str(args.apk.resolve())], check=True, timeout=180)
    original = sys.argv
    try:
        sys.argv = [original[0], '--adb', adb, '--serial', args.serial, '--out', str(args.out), '--companion']
        review()
    finally:
        sys.argv = original
        subprocess.run([adb, '-s', args.serial, 'shell', 'am', 'force-stop', 'io.github.marouane4142.maros'], check=False, timeout=20)


if __name__ == '__main__':
    main()
