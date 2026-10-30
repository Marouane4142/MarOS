#!/usr/bin/env python3
"""Opt-in smoke check for an isolated emulator only. Never selects a physical TV."""
import argparse
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE='io.github.marouane4142.maros'


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--apk',type=Path,required=True)
    parser.add_argument('--out',type=Path,required=True)
    args=parser.parse_args()
    if not re.fullmatch(r'emulator-\d+',args.serial):parser.error('Only an explicitly named emulator is allowed.')
    adb=shutil.which('adb')
    if not adb:parser.error('adb is missing')
    args.out.mkdir(parents=True,exist_ok=True)
    report={'checks':[],'device':'Android emulator','package':PACKAGE}
    def call(*command,binary=False,input=None,timeout=60):
        r=subprocess.run([adb,'-s',args.serial,*command],input=input,stdout=subprocess.PIPE,stderr=subprocess.PIPE,timeout=timeout)
        if r.returncode:
            details=r.stderr.decode('utf-8','replace')
            if command[0]=='install':details+='\n'+r.stdout.decode('utf-8','replace')
            raise RuntimeError('ADB '+command[0]+' failed: '+details[-2500:])
        return r.stdout if binary else r.stdout.decode('utf-8','replace').replace('\r\n','\n')
    def shell(command):return call('shell',command)
    def capture(name):(args.out/(name+'.png')).write_bytes(call('exec-out','screencap','-p',binary=True))
    def nodes():
        shell('uiautomator dump /sdcard/maros-smoke.xml')
        raw=shell('cat /sdcard/maros-smoke.xml')
        (args.out/'last-ui.xml').write_text(raw,encoding='utf-8')
        return list(ET.fromstring(raw[raw.index('<?xml'):]).iter('node'))
    def click(label):
        for node in sorted(nodes(),key=lambda n:n.get('clickable')!='true'):
            if label.casefold() in (node.get('text','')+' '+node.get('content-desc','')).casefold():
                box=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
                if len(box)==4 and box[2]>box[0] and box[3]>box[1]:shell('input tap %d %d'%((box[0]+box[2])//2,(box[1]+box[3])//2));time.sleep(.5);return
        raise RuntimeError('Visible control missing: '+label)
    def checked(name):report['checks'].append({'name':name,'pass':True});print('PASS:',name,flush=True)
    try:
        (args.out/'device.txt').write_text(shell('getprop ro.build.version.sdk')+shell('wm size')+shell('wm density'),encoding='utf-8')
        call('install','-r',str(args.apk.resolve()),timeout=180)
        shell('am start -W -n '+PACKAGE+'/.MainActivity');time.sleep(2)
        resumed=[line for line in shell('dumpsys activity activities').splitlines() if 'ResumedActivity' in line]
        assert any(PACKAGE in line for line in resumed),resumed
        capture('home');checked('Native HOME activity launched')
        click('Réglages MarOS');capture('settings');checked('Settings overview opened')
        click('Connecter mon téléphone');time.sleep(1)
        labels=[n.get('text','') for n in nodes()]
        code=next(re.search(r'Code\s+(\d{6})',s).group(1) for s in labels if re.search(r'Code\s+\d{6}',s))
        url=next(s for s in labels if re.fullmatch(r'http://[0-9.]+:8765',s));host=url.removeprefix('http://');address=host.split(':')[0]
        def request(path,body=None,token=''):
            raw=b'' if body is None else json.dumps(body).encode()
            header=('GET' if body is None else 'POST')+' '+path+' HTTP/1.1\r\nHost: '+host+'\r\nConnection: close\r\n'
            if body is not None:header+='Content-Type: application/json\r\nContent-Length: '+str(len(raw))+'\r\nX-TVHome-Token: '+token+'\r\n'
            response=call('shell','toybox nc -w 8 '+address+' 8765',binary=True,input=header.encode()+b'\r\n'+raw,timeout=15)
            status=int(response.split(b' ',2)[1]);return status,response.split(b'\r\n\r\n',1)[1]
        assert request('/')[0]==200;checked('Local companion serves its page')
        assert request('/apps',{})[0]==401;checked('Companion rejects an unauthenticated API request')
        status,body=request('/pair',{'code':code});assert status==200;token=json.loads(body)['token'];checked('Visible code pairs a client')
        assert request('/export',{},token)[0]==200
        shell('am start -W -a android.settings.SETTINGS');assert request('/export',{},token)[0]==200;checked('Same paired session survives another app')
        shell('am start -W -n '+PACKAGE+'/.MainActivity')
        assert request('/apps',{},token)[0]==200;checked('Companion remains available on native home')
        fatal=shell('logcat -d -s AndroidRuntime:E')
        (args.out/'android-runtime.txt').write_text(fatal,encoding='utf-8')
        assert 'Process: '+PACKAGE not in fatal;checked('No MarOS AndroidRuntime fatal exception')
    except Exception as error:
        report['error']=str(error)
        try:capture('failure');(args.out/'android-runtime.txt').write_text(shell('logcat -d -s AndroidRuntime:E'),encoding='utf-8')
        except Exception:pass
        raise
    finally:
        (args.out/'report.json').write_text(json.dumps(report,indent=2),encoding='utf-8')
        try:shell('am force-stop '+PACKAGE)
        except Exception:pass


if __name__=='__main__':main()
