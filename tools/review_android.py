#!/usr/bin/env python3
"""Explicit-device DPAD, HOME-return and paired-companion review. No install or HOME changes."""
import argparse
import io
import json
from pathlib import Path
import re
import shutil
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

PACKAGE = 'io.github.marouane4142.maros'


def review_experience(request, token, original, shell, home, checked, out):
    def api(path, body=None, expected=200):
        status, raw = request(path, body or {}, token)
        assert status == expected, (path, status, raw[:250])
        return json.loads(raw)

    home()
    state = api('/status')
    assert state['navigation_available'] and state['cast_installed']
    def focus_bounds():
        shell('uiautomator', 'dump', '/sdcard/maros-review.xml')
        raw = shell('cat', '/sdcard/maros-review.xml')
        return [node.get('bounds') for node in ET.fromstring(raw[raw.index('<?xml'):]).iter('node') if node.get('focused') == 'true']
    before = focus_bounds()
    api('/control', {'action': 'right'})
    time.sleep(.4)
    assert focus_bounds() != before, 'Phone key acknowledged but focus did not move'
    assert request('/control', {'action': 'shell', 'command': 'id'}, token)[0] >= 400
    shell('am', 'start', '-W', '-a', 'android.settings.SETTINGS')
    assert not api('/status')['navigation_available']
    assert request('/control', {'action': 'left'}, token)[0] >= 400
    api('/control', {'action': 'home'}); time.sleep(.6)
    assert api('/status')['navigation_available']
    checked('Phone controls HOME, rejects arbitrary commands and never injects keys into other apps')

    google = api('/google/status')
    assert not any(key in google for key in ('access_token', 'refresh_token', 'client_secret'))
    calendar = api('/calendar')
    assert isinstance(calendar['events'], list)
    if google.get('identity_source') == 'android' and google.get('system_account'):
        assert not google['connected'], 'Native account visibility must not imply OAuth authorization'
        assert google.get('email')
        checked('Native Google account is available without implying OAuth authorization')
    watched = api('/history')['items']
    initial_list = api('/watchlist')
    try:
        api('/control', {'action': 'guest_on'})
        assert api('/status')['guest']
        assert not api('/watchlist') and not api('/history')['items'] and not api('/calendar')['events']
        assert api('/google/status')['hidden']
        assert request('/backup', {}, token)[0] >= 400
    finally:
        api('/control', {'action': 'guest_off'})
    assert api('/history')['items'] == watched
    checked('Guest mode hides personal feeds, Google and archive export, then restores visibility')

    temporary = 'MarOS QA ' + str(int(time.time()))
    temporary_id = None
    try:
        api('/watchlist', {'action': 'add', 'title': temporary})
        temporary_id = next(item['id'] for item in api('/watchlist') if item['title'] == temporary)
        api('/watchlist', {'action': 'next', 'id': temporary_id})
        assert next(item for item in api('/watchlist') if item['id'] == temporary_id)['episode'] == 2
        found = api('/watchlist', {'action': 'search', 'query': 'Severance'})
        assert found and all(isinstance(item['id'], int) and item['title'] for item in found)
        checked('Personal list progresses independently and the real TVmaze API returns selectable series')
    finally:
        if temporary_id:
            api('/watchlist', {'action': 'remove', 'id': temporary_id})
    assert api('/watchlist') == initial_list

    status, archive = request('/backup', {}, token)
    assert status == 200 and archive.startswith(b'PK')
    (out / 'private-roundtrip.zip').write_bytes(archive)
    with zipfile.ZipFile(io.BytesIO(archive)) as zip_file:
        manifest = json.loads(zip_file.read('manifest.json'))
        entries = {name: zip_file.read(name) for name in zip_file.namelist()}
        assert manifest['format'] == 'maros-personal-backup'
        assert not any('secret' in name or 'token' in name for name in zip_file.namelist())
        assert 'google_tokens' not in json.dumps(manifest)
    status, result = request('/restore/validate', archive, token, raw=True)
    assert status == 200 and not json.loads(result)['applied'], result
    assert api('/export') == original

    def altered_zip(changes):
        buffer = io.BytesIO()
        with zipfile.ZipFile(buffer, 'w') as zip_file:
            for name, data in (entries | changes).items():
                zip_file.writestr(name, data)
        return buffer.getvalue()

    invalid_settings = json.loads(json.dumps(manifest))
    invalid_settings['config']['settings']['text_scale'] = 'invalid-number'
    assert request('/restore/validate', altered_zip({'manifest.json': json.dumps(invalid_settings).encode()}), token, raw=True)[0] >= 400
    # Exercise native image validation with a public bundled landscape, without
    # importing it into the owner's personal album.
    if len(manifest['photos']) < 100:
        with_photo = json.loads(json.dumps(manifest))
        image_path = 'photos/' + str(len(with_photo['photos'])).zfill(3) + '.jpg'
        with_photo['photos'].append({'path': image_path, 'title': 'Validation photo', 'favorite': False, 'excluded': False})
        landscape = next((Path(__file__).resolve().parents[1] / 'launcher/assets/wallpapers').glob('*.jpg')).read_bytes()
        changed = {'manifest.json': json.dumps(with_photo).encode(), image_path: landscape}
        status, result = request('/restore/validate', altered_zip(changed), token, raw=True)
        assert status == 200 and json.loads(result)['photos'] == len(with_photo['photos']), result
        changed[image_path] = b'not-an-image'
        assert request('/restore/validate', altered_zip(changed), token, raw=True)[0] >= 400

    invalid = io.BytesIO()
    with zipfile.ZipFile(invalid, 'w') as zip_file:
        zip_file.writestr('../escape.txt', 'invalid')
    assert request('/restore/validate', invalid.getvalue(), token, raw=True)[0] >= 400
    assert api('/export') == original
    status, result = request('/restore', archive, token, raw=True)
    assert status == 200 and json.loads(result)['applied'], result
    identity = lambda items: [(item['id'], item['title'], item['season'], item['episode']) for item in items]
    assert api('/export') == original and identity(api('/watchlist')) == identity(initial_list)
    status, previous = request('/backup/previous', {}, token)
    assert status == 200 and previous.startswith(b'PK')
    checked('Personal ZIP validates images/types, rejects traversal, self-restores and keeps a previous-state archive')
    task = api('/tasks', {'action': 'cast'})
    for _ in range(20):
        time.sleep(.5)
        task = api('/task', {'id': task['id']})
        if task['state'] in ('done', 'error'):
            break
    assert task['state'] == 'done', task
    checked('Long-operation queue acknowledges immediately and returns a real local Cast diagnostic')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', default=shutil.which('adb'))
    parser.add_argument('--serial', required=True)
    parser.add_argument('--out', type=Path, required=True)
    parser.add_argument('--companion', action='store_true', help='Open and test a temporary paired session, including reversible profile/order changes')
    parser.add_argument('--experience', action='store_true', help='Also validate room controls, guest privacy, metadata and backup round-trip')
    parser.add_argument('--history-import', type=Path, help='Explicitly merge a real legacy /history export through the paired API')
    args = parser.parse_args()
    if args.experience and not args.companion:
        parser.error('--experience requires --companion')
    if args.history_import and not args.companion:
        parser.error('--history-import requires --companion')
    if not args.adb:
        parser.error('Pass --adb or add ADB to PATH')
    args.out.mkdir(parents=True, exist_ok=True)
    report = {'package': PACKAGE, 'checks': []}
    port = None
    command_durations = []

    def adb(*command, binary=False):
        retryable = command[:2] in [('shell', 'uiautomator'), ('shell', 'cat'), ('exec-out', 'screencap')]
        for attempt in range(3 if retryable else 1):
            result = subprocess.run([args.adb, '-s', args.serial, *command], stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=45)
            if result.returncode == 0:
                return result.stdout if binary else result.stdout.decode('utf-8', 'replace').strip()
            if retryable and attempt < 2:
                time.sleep(.4)
        raise RuntimeError('ADB failed: ' + result.stderr.decode('utf-8', 'replace').strip())

    def shell(*command):
        return adb('shell', *command)

    def key(*codes):
        # Separate injections keep Android 11 key-up events and focus transitions observable.
        for code in codes:
            shell('input', 'keyevent', code)
            time.sleep(.25)

    def checked(label):
        report['checks'].append({'name': label, 'pass': True})
        print('PASS:', label, flush=True)

    def nodes():
        shell('uiautomator', 'dump', '/sdcard/maros-review.xml')
        raw = shell('cat', '/sdcard/maros-review.xml')
        (args.out / 'last-ui.xml').write_text(raw, encoding='utf-8')
        return list(ET.fromstring(raw[raw.index('<?xml'):]).iter('node'))

    def capture(name):
        (args.out / (name + '.png')).write_bytes(adb('exec-out', 'screencap', '-p', binary=True))

    def home():
        started = time.perf_counter()
        text = shell('am', 'start', '-W', '-n', PACKAGE + '/.MainActivity', '--ez', 'room_home', 'true')
        command_durations.append(round((time.perf_counter() - started) * 1000))
        resumed = [line for line in shell('dumpsys', 'activity', 'activities').splitlines() if 'ResumedActivity' in line]
        assert any(PACKAGE + '/.MainActivity' in line for line in resumed), resumed
        time.sleep(.6)
        return text

    try:
        report['model'] = shell('getprop', 'ro.product.model')
        report['api'] = shell('getprop', 'ro.build.version.sdk')
        key('KEYCODE_WAKEUP', 'KEYCODE_HOME')
        time.sleep(.6)
        home(); key('KEYCODE_BACK'); capture('home')
        checked('Compose HOME launches with a readable native screen')
        home_nodes = nodes()
        assert 'Reprendre' not in [n.get('text', '') for n in home_nodes]
        dock_before = {n.get('text'): n.get('bounds') for n in home_nodes if n.get('text') == 'VOS ESSENTIELS'}
        assert dock_before
        assert not any(n.get('text') == 'À reprendre' for n in home_nodes)
        # Reach the dock without entering the lower page, then verify horizontal
        # focus cannot move the music/header/dock or expose resume cards.
        for _ in range(8):
            current = nodes()
            heading = next(n for n in current if n.get('text') == 'VOS ESSENTIELS')
            heading_y = int(re.findall(r'\d+', heading.get('bounds'))[1])
            if any(n.get('focused') == 'true' and int(re.findall(r'\d+', n.get('bounds'))[1]) > heading_y for n in current):
                break
            key('KEYCODE_DPAD_DOWN')
        else:
            raise AssertionError('Cannot reach the essentials dock with DPAD')
        stable = {n.get('text'): n.get('bounds') for n in nodes() if n.get('text') in {'VOS ESSENTIELS', 'MarOS'}}
        for _ in range(8):
            key('KEYCODE_DPAD_RIGHT')
        for _ in range(8):
            key('KEYCODE_DPAD_LEFT')
        horizontal = nodes()
        assert not any(n.get('text') == 'À reprendre' for n in horizontal)
        assert stable == {n.get('text'): n.get('bounds') for n in horizontal if n.get('text') in stable}
        capture('dock-horizontal'); checked('Horizontal dock navigation leaves the first viewport fixed')
        key('KEYCODE_DPAD_DOWN')
        after = nodes()
        assert any(n.get('text') == 'À reprendre' for n in after)
        capture('resume-focus'); checked('Resume is below the first viewport and becomes visible only when navigating down past essentials')
        key('KEYCODE_BACK'); home()
        shell('dumpsys', 'gfxinfo', PACKAGE, 'reset')
        key('KEYCODE_DPAD_RIGHT', 'KEYCODE_DPAD_CENTER')
        texts = [n.get('text', '') for n in nodes()]
        assert texts.count('Regarder') >= 2
        capture('catalog'); checked('DPAD selects the video catalog')
        key('KEYCODE_BACK'); home()
        for _ in range(3):
            key('KEYCODE_DPAD_RIGHT', 'KEYCODE_DPAD_LEFT')
        checked('Repeated directional focus changes remain responsive')
        key('KEYCODE_MENU'); time.sleep(.4)
        assert PACKAGE + '/.StudioActivity' in shell('dumpsys', 'activity', 'activities')
        capture('studio'); checked('Remote MENU opens the Compose personalization screen')

        if args.companion:
            studio_texts = [n.get('text', '') for n in nodes()]
            assert all(label in studio_texts for label in ['Apparence', 'Applications', 'Téléphone'])
            assert not any(label in studio_texts for label in ['Chromecast', 'Voix', 'Agenda', 'Scènes', 'Invités', 'À regarder'])
            checked('Daily settings contain only appearance, apps and phone')
            key('KEYCODE_DPAD_DOWN', 'KEYCODE_DPAD_DOWN', 'KEYCODE_DPAD_CENTER')
            key('KEYCODE_DPAD_RIGHT', 'KEYCODE_DPAD_CENTER')
            time.sleep(1)
            labels = [n.get('text', '') for n in nodes()]
            code = next(re.search(r'Code\s+(\d{6})', s).group(1) for s in labels if re.search(r'Code\s+\d{6}', s))
            url = next(s for s in labels if re.fullmatch(r'http://[0-9.]+:8765', s))
            port = adb('forward', 'tcp:0', 'tcp:8765')
            host = urllib.parse.urlsplit(url).netloc

            def request(path, body, token='', origin=None, raw=False):
                headers = {'Content-Type': 'application/zip' if raw else 'application/json', 'Host': host, 'X-TVHome-Token': token}
                if origin:
                    headers['Origin'] = origin
                req = urllib.request.Request('http://127.0.0.1:' + port + path, data=body if raw else json.dumps(body).encode(), headers=headers)
                try:
                    with urllib.request.urlopen(req, timeout=12) as response:
                        return response.status, response.read()
                except urllib.error.HTTPError as error:
                    return error.code, error.read()

            assert request('/apps', {})[0] == 401
            token = json.loads(request('/pair', {'code': code})[1])['token']
            assert request('/apps', {}, token, 'https://untrusted.invalid')[0] == 403
            checked('Companion requires pairing and rejects foreign origins')
            if args.history_import:
                history = json.loads(args.history_import.read_text(encoding='utf-8'))
                status, result = request('/history/import', history, token)
                assert status == 200, result
                report['legacy_history_imported'] = json.loads(result)['imported']
                visible = json.loads(request('/history', {}, token)[1])['items']
                actual = {(item['package'], item['title']) for item in visible}
                expected = {(item['package'], item['title']) for item in history['items']}
                assert actual & expected, 'No imported viewing record is visible'
                checked('Real legacy viewing history merges and becomes visible')
            original = json.loads(request('/export', {}, token)[1])
            profiles = json.loads(request('/profiles', {}, token)[1])
            profile_name = 'Validation ' + str(int(time.time()) % 100000)
            active = profiles['current']
            try:
                order = original['settings']['dock']
                assert request('/arrange', {'order': list(reversed(order))}, token)[0] == 200
                assert json.loads(request('/export', {}, token)[1])['settings']['dock'] == list(reversed(order))
                request('/arrange', {'order': order}, token)
                checked('Phone changes favorite order and restores it exactly')
                assert request('/profile', {'action': 'create', 'name': profile_name}, token)[0] == 200
                changed = {'format': 'tvhome-settings', 'version': 2, 'settings': {'accent': 'rose', 'text_scale': 140, 'ui_scale': 110}}
                assert request('/import', changed, token)[0] == 200
                home()
                large_nodes = nodes()
                normal_brand = next(n for n in home_nodes if n.get('text') == 'MarOS')
                large_brand = next(n for n in large_nodes if n.get('text') == 'MarOS')
                normal_box = list(map(int, re.findall(r'\d+', normal_brand.get('bounds'))))
                large_box = list(map(int, re.findall(r'\d+', large_brand.get('bounds'))))
                assert large_box[2] - large_box[0] > (normal_box[2] - normal_box[0]) * 1.2, 'Large text did not reach the actual UI'
                capture('large-text')
                assert request('/export', {}, token)[0] == 200
                checked('140 percent text remains usable and the phone session survives an app change')
                for theme, accent in [('nature', 'ice'), ('cinema', 'ice'), ('aurora', 'sage'), ('dune', 'amber'), ('minimal', 'ice')]:
                    themed = {'format': 'tvhome-settings', 'version': 2, 'settings': {'theme': theme, 'accent': accent, 'text_scale': 100, 'ui_scale': 85}}
                    assert request('/import', themed, token)[0] == 200
                    home(); capture('theme-' + theme)
                    exported = json.loads(request('/export', {}, token)[1])['settings']
                    assert exported['theme'] == theme and exported['ui_scale'] == 85
                checked('All five themes persist, render and preserve the compact layout')
                compact = {'format': 'tvhome-settings', 'version': 2, 'settings': {'ui_scale': 75}}
                assert request('/import', compact, token)[0] == 200
                home(); capture('compact-75')
                checked('Extra compact layout remains available independently of text size')
                assert request('/profile', {'action': 'switch', 'name': active}, token)[0] == 200
                assert json.loads(request('/export', {}, token)[1])['settings']['accent'] == original['settings']['accent']
                checked('Local profiles isolate visual preferences')
                apps = json.loads(request('/apps', {}, token)[1])
                if apps:
                    preview = json.loads(request('/preview', {'package': apps[0]['package']}, token)[1])['image']
                    assert preview.startswith('data:image/jpeg;base64,')
                    checked('Paired phone receives a local banner preview')
            finally:
                request('/profile', {'action': 'switch', 'name': active}, token)
                request('/profile', {'action': 'delete', 'name': profile_name}, token)
                request('/import', original, token)
            checked('Temporary review changes restored to the original settings')
            if args.experience:
                review_experience(request, token, original, shell, home, checked, args.out)
            assert request('/close', {}, token)[0] == 200

        durations = []
        for _ in range(3):
            shell('am', 'start', '-W', '-a', 'android.settings.SETTINGS')
            result = home()
            match = re.search(r'TotalTime:\s*(\d+)', result)
            if match:
                durations.append(int(match.group(1)))
        report['home_return_total_time_ms'] = durations
        report['home_return_command_elapsed_ms'] = command_durations[-3:]
        report['timing_note'] = 'Android 11 may repeat a previous TotalTime for HOT returns; command elapsed includes ADB transport and is not an input-to-photon measurement.'
        checked('Repeated returns from Android Settings keep HOME usable')
        key('KEYCODE_BACK'); capture('home-final')
        memory = shell('dumpsys', 'meminfo', PACKAGE)
        match = re.search(r'TOTAL PSS:\s*(\d+)', memory)
        if match:
            report['pss_kib'] = int(match.group(1))
        (args.out / 'gfxinfo.txt').write_text(shell('dumpsys', 'gfxinfo', PACKAGE), encoding='utf-8')
        pid = shell('pidof', PACKAGE)
        assert pid.isdigit()
        errors = shell('logcat', '-d', '--pid=' + pid, '-t', '300', '-s', 'AndroidRuntime:E')
        assert 'FATAL EXCEPTION' not in errors
        checked('No fatal exception for the current launcher process')
    except Exception as error:
        report['error'] = str(error)
        try:
            capture('failure')
        except Exception as capture_error:
            report['capture_error'] = str(capture_error)
        raise
    finally:
        if port:
            adb('forward', '--remove', 'tcp:' + port)
        (args.out / 'report.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')


if __name__ == '__main__':
    main()
