#!/usr/bin/env python3
"""Offline source/distribution checks and behavioral chronology regression tests."""
import ast
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import xml.etree.ElementTree as ET

ROOT=Path(__file__).resolve().parents[1]


def main():
    excluded={'.git','.gradle','.kotlin','.local','build','dist','__pycache__','backup','backups','shots','outputs','work','.codex','.agents','.idea','.vscode'}
    files=[]
    for folder, directories, names in os.walk(ROOT):
        directories[:]=[d for d in directories if d not in excluded]
        files.extend(Path(folder)/name for name in names)
    for p in files:
        relative=p.relative_to(ROOT).as_posix()
        if p.suffix in {'.keystore','.jks','.p12','.pfx','.key','.apk','.aab','.zip'} or p.name.startswith('adbkey'):
            raise RuntimeError('Private/generated artifact in source: '+relative)
        if p.suffix=='.pem' and relative!='docs/release-signing-public.pem':raise RuntimeError('Unexpected certificate/key file: '+relative)
        if p.suffix=='.py':ast.parse(p.read_text(encoding='utf-8'),filename=relative)
        if p.suffix in {'.xml','.svg'}:ET.parse(p)
        if p.suffix in {'.py','.java','.kt','.kts','.md','.xml','.yml','.html','.txt','.json','.pem','.properties'}:
            text=p.read_text(encoding='utf-8')
            if re.search(r'-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----',text):raise RuntimeError('Private key content: '+relative)
            if re.search(r'\b(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,})',text):raise RuntimeError('Credential-shaped content: '+relative)
    for name in ['LICENSE','CONTRIBUTING.md','THIRD_PARTY_NOTICES.md','launcher/WALLPAPER-CREDITS.md']:
        if not (ROOT/name).is_file():raise RuntimeError('Missing distribution notice: '+name)
    if 'Copyright (c) 2026 Mihir Gupta' not in (ROOT/'LICENSE').read_text():raise RuntimeError('Upstream copyright must remain')
    jdk=Path(os.environ['JAVA_HOME'])/'bin' if os.environ.get('JAVA_HOME') else None
    suffix='.exe' if os.name=='nt' else ''
    java=str(jdk/('java'+suffix)) if jdk else shutil.which('java')
    javac=str(jdk/('javac'+suffix)) if jdk else shutil.which('javac')
    if not java or not javac:raise RuntimeError('JDK missing: set JAVA_HOME')
    with tempfile.TemporaryDirectory(prefix='maros-check-') as folder:
        subprocess.run([javac,'--release','11','-encoding','UTF-8','-d',folder,str(ROOT/'launcher/src/io/github/marouane4142/maros/RecentPolicy.java'),str(ROOT/'launcher/tests/RecentPolicyQA.java')],check=True)
        subprocess.run([java,'-cp',folder,'RecentPolicyQA'],check=True)
    print('PASS: Python/XML inputs, source distribution boundaries and chronology regression checks')


if __name__=='__main__':main()
