#!/usr/bin/env python3
"""Build MarOS with the Android SDK and a JDK. Never connects to or changes a TV."""
import argparse
import copy
import hashlib
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import zipfile

ROOT = Path(__file__).resolve().parent


def run(command):
    subprocess.run([str(x) for x in command], check=True)


def rank(path):
    return tuple(int(n) for n in re.findall(r'\d+', path.name))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT'))
    parser.add_argument('--java-home', default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--build-tools')
    parser.add_argument('--platform')
    parser.add_argument('--output', type=Path, default=ROOT / 'build/MarOS-debug.apk')
    parser.add_argument('--release', action='store_true')
    parser.add_argument('--keystore', type=Path)
    parser.add_argument('--alias', default='maros')
    parser.add_argument('--store-pass-env', default='MAROS_STORE_PASSWORD')
    parser.add_argument('--key-pass-env', default='MAROS_KEY_PASSWORD')
    args = parser.parse_args()
    sdk = Path(args.sdk).expanduser().resolve() if args.sdk else None
    if not sdk or not sdk.is_dir():
        parser.error('Set ANDROID_HOME or pass --sdk PATH.')
    suffix = '.exe' if os.name == 'nt' else ''
    java_home = Path(args.java_home).resolve() if args.java_home else None
    def jdk(name):
        found = java_home / 'bin' / (name + suffix) if java_home else shutil.which(name)
        if not found or not Path(found).is_file():
            parser.error('JDK tool missing: ' + name + '. Set JAVA_HOME or pass --java-home.')
        return str(found)
    java, javac, keytool = jdk('java'), jdk('javac'), jdk('keytool')
    choices = sorted((p for p in (sdk/'build-tools').iterdir() if re.fullmatch(r'\d+\.\d+\.\d+',p.name)),key=rank)
    tools = sdk/'build-tools'/args.build_tools if args.build_tools else choices[-1] if choices else None
    platforms = sorted((p for p in (sdk/'platforms').iterdir() if re.fullmatch(r'android-\d+(?:\.\d+)?',p.name)),key=rank)
    platform = sdk/'platforms'/('android-'+args.platform.removeprefix('android-')) if args.platform else platforms[-1] if platforms else None
    if not tools or not platform:
        parser.error('Install Android SDK Build-Tools and a platform.')
    aapt, align = tools/('aapt2'+suffix), tools/('zipalign'+suffix)
    android = platform/'android.jar'
    for file in [aapt,align,android,tools/'lib/d8.jar',tools/'lib/apksigner.jar']:
        if not file.is_file():parser.error('Missing Android SDK file: '+str(file))
    args.output = args.output.resolve()
    args.output.parent.mkdir(parents=True,exist_ok=True)
    if args.release:
        if not args.keystore or not args.keystore.is_file():parser.error('Release requires an existing private --keystore. No release key is generated implicitly.')
        if not os.environ.get(args.store_pass_env) or not os.environ.get(args.key_pass_env):parser.error('Set the two password environment variables before a release build.')
        keystore=args.keystore.resolve();password_args=['--ks-pass','env:'+args.store_pass_env,'--key-pass','env:'+args.key_pass_env]
    else:
        keystore=ROOT/'.local/debug.keystore';keystore.parent.mkdir(exist_ok=True)
        if not keystore.exists():run([keytool,'-genkeypair','-keystore',keystore,'-storepass','android','-keypass','android','-alias',args.alias,'-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=MarOS local debug','-noprompt'])
        password_args=['--ks-pass','pass:android','--key-pass','pass:android']
    with tempfile.TemporaryDirectory(prefix='maros-',dir=args.output.parent) as temp:
        stage=Path(temp)
        for name in ['gen','classes','dex']:(stage/name).mkdir()
        run([aapt,'compile','--dir',ROOT/'res','-o',stage/'res.zip'])
        run([aapt,'link','-o',stage/'base.apk','-I',android,'--manifest',ROOT/'AndroidManifest.xml','--java',stage/'gen','-A',ROOT/'assets','--min-sdk-version','30','--target-sdk-version','30',stage/'res.zip'])
        sources=sorted((ROOT/'src').rglob('*.java'))+sorted((stage/'gen').rglob('*.java'))
        run([javac,'-nowarn','-Xlint:-options','--release','11','-encoding','UTF-8','-classpath',android,'-d',stage/'classes',*sources])
        (stage/'dex-inputs.txt').write_text('\n'.join(p.as_posix() for p in sorted((stage/'classes').rglob('*.class'))),encoding='utf-8')
        run([java,'-cp',tools/'lib/d8.jar','com.android.tools.r8.D8','--min-api','30','--lib',android,'--output',stage/'dex','@'+str(stage/'dex-inputs.txt')])
        # Normalize ZIP names emitted by Windows aapt2 before signing.
        with zipfile.ZipFile(stage/'base.apk') as original,zipfile.ZipFile(stage/'unsigned.apk','w',zipfile.ZIP_DEFLATED) as target:
            seen=set()
            for entry in original.infolist():
                name=entry.filename.replace('\\','/')
                if name in seen:raise RuntimeError('Duplicate APK path: '+name)
                seen.add(name)
                normalized=copy.copy(entry);normalized.filename=name
                # Android 11+ requires resources.arsc to remain STORED and 4-byte aligned.
                # Preserve aapt2's compression decisions rather than recompressing all entries.
                if name=='resources.arsc':normalized.compress_type=zipfile.ZIP_STORED
                target.writestr(normalized,original.read(entry))
            for dex in sorted((stage/'dex').glob('classes*.dex')):target.write(dex,dex.name)
        run([align,'-f','4',stage/'unsigned.apk',stage/'aligned.apk'])
        run([java,'-jar',tools/'lib/apksigner.jar','sign','--ks',keystore,'--ks-key-alias',args.alias,*password_args,'--out',stage/'signed.apk',stage/'aligned.apk'])
        run([align,'-c','4',stage/'signed.apk'])
        run([java,'-jar',tools/'lib/apksigner.jar','verify','--verbose',stage/'signed.apk'])
        with zipfile.ZipFile(stage/'signed.apk') as apk:
            names=apk.namelist()
            if apk.getinfo('resources.arsc').compress_type!=zipfile.ZIP_STORED:raise RuntimeError('Android 11 requires uncompressed resources.arsc')
            if len(set(names))!=len(names) or any('\\' in n for n in names):raise RuntimeError('Invalid APK paths')
            photos=[n for n in names if re.fullmatch(r'assets/wallpapers/\d+\.jpg',n)]
            if len(photos)!=12:raise RuntimeError('Expected 12 bundled landscapes')
            for name in photos:
                data=apk.read(name)
                if not data.startswith(b'\xff\xd8\xff') or not data.endswith(b'\xff\xd9'):raise RuntimeError('Invalid JPEG: '+name)
        shutil.copy2(stage/'signed.apk',args.output)
    digest=hashlib.sha256(args.output.read_bytes()).hexdigest()
    args.output.with_suffix(args.output.suffix+'.sha256').write_text(digest+'  '+args.output.name+'\n',encoding='utf-8')
    print('Built and verified:',args.output.name,'('+str(args.output.stat().st_size)+' bytes)')
    print('SHA256:',digest)


if __name__=='__main__':main()
