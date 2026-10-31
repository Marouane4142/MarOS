#!/usr/bin/env python3
"""Portable Gradle front end. Builds and verifies an APK; never connects to a device."""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parent


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--release', action='store_true')
    parser.add_argument('--local-release', action='store_true', help='Optimized build signed with the local development key')
    parser.add_argument('--keystore', type=Path)
    parser.add_argument('--alias', default='maros')
    parser.add_argument('--store-pass-env', default='MAROS_STORE_PASSWORD')
    parser.add_argument('--key-pass-env', default='MAROS_KEY_PASSWORD')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    env = os.environ.copy()
    env.pop('DEBUG', None)
    if args.sdk:
        env['ANDROID_HOME'] = str(Path(args.sdk).resolve())
    if args.java_home:
        env['JAVA_HOME'] = str(Path(args.java_home).resolve())
    if not env.get('JAVA_HOME'):
        parser.error('Set JAVA_HOME to JDK 21 or pass --java-home.')
    java = Path(env['JAVA_HOME']) / 'bin' / ('java.exe' if os.name == 'nt' else 'java')
    if not java.is_file():
        parser.error('JDK executable missing.')
    if args.release and not args.local_release:
        if args.keystore:
            env['MAROS_KEYSTORE'] = str(args.keystore.resolve())
        if not env.get('MAROS_KEYSTORE') or not Path(env['MAROS_KEYSTORE']).is_file():
            parser.error('A release requires an existing --keystore / MAROS_KEYSTORE, or explicit --local-release.')
        env['MAROS_KEY_ALIAS'] = args.alias
        for target, source in [('MAROS_STORE_PASSWORD', args.store_pass_env), ('MAROS_KEY_PASSWORD', args.key_pass_env)]:
            if not os.environ.get(source):
                parser.error('Missing signing password environment variable: ' + source)
            env[target] = os.environ[source]
    variant = 'release' if args.release or args.local_release else 'debug'
    wrapper = ROOT.parent / ('gradlew.bat' if os.name == 'nt' else 'gradlew')
    command = ([str(wrapper)] if os.name == 'nt' else ['sh', str(wrapper)]) + [':launcher:assemble' + variant.title(), '--console=plain']
    if args.local_release:
        command.append('-Pmaros.localRelease=true')
    subprocess.run(command, cwd=ROOT.parent, env=env, check=True)
    source = ROOT / 'build/outputs/apk' / variant / ('launcher-' + variant + '.apk')
    if not source.is_file():
        raise RuntimeError('Signed APK missing: ' + str(source))
    output = (args.output or ROOT / ('build/MarOS-' + variant + '.apk')).resolve()
    output.parent.mkdir(parents=True, exist_ok=True)
    if output != source.resolve():
        shutil.copy2(source, output)
    with zipfile.ZipFile(output) as apk:
        names = apk.namelist()
        if len(names) != len(set(names)) or any('\\' in n for n in names):
            raise RuntimeError('Invalid ZIP paths')
        photos = [n for n in names if n.startswith('assets/wallpapers/') and n.endswith('.jpg')]
        if len(photos) != 12:
            raise RuntimeError('Expected twelve bundled landscapes')
        if variant == 'release' and 'assets/dexopt/baseline.prof' not in names:
            raise RuntimeError('Merged AndroidX Baseline Profile missing')
    sdk = Path(env.get('ANDROID_HOME', ''))
    signer = sdk / 'build-tools/36.0.0/lib/apksigner.jar'
    if signer.is_file():
        subprocess.run([str(java), '-jar', str(signer), 'verify', '--verbose', str(output)], env=env, check=True)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    output.with_suffix('.apk.sha256').write_text(digest + '  ' + output.name + '\n', encoding='utf-8')
    print('Built and verified:', output)
    print('SHA256:', digest)


if __name__ == '__main__':
    main()
