#!/usr/bin/env python3
"""In-place legacy or encrypted validation upgrade with a live shell recorder.

Only an owned disposable ClipHistory emulator and its debuggable validation
package are accepted. Legacy text stays in memory; reports contain metadata.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import struct
import subprocess
import time
import xml.etree.ElementTree as ET


def main():
    p = argparse.ArgumentParser()
    p.add_argument('--adb', required=True)
    p.add_argument('--serial', required=True)
    p.add_argument('--apk', type=Path, required=True)
    p.add_argument('--test-apk', type=Path, required=True)
    p.add_argument('--output', type=Path, required=True)
    p.add_argument('--baseline', choices=['1.3.4', '1.4.0', '1.4.1'], default='1.3.4')
    args = p.parse_args()
    package = 'app.cliphistory.validation'
    configuration=(Path(__file__).resolve().parents[1]/'app/build.gradle.kts').read_text()
    version_code=int(re.search(r'versionCode\s*=\s*(\d+)',configuration)[1])
    version_name=re.search(r'versionName\s*=\s*"([^"]+)"',configuration)[1]+'-validation'

    def adb(*parts, binary=False):
        command = ['shell', shlex.join(parts[1:])] if parts and parts[0] == 'shell' else list(parts)
        r = subprocess.run([args.adb, '-s', args.serial, *command], capture_output=True, timeout=50)
        if r.returncode:
            raise RuntimeError(r.stderr.decode(errors='replace'))
        return r.stdout if binary else r.stdout.decode().strip()

    if not args.serial.startswith('emulator-') or adb('emu', 'avd', 'name').splitlines()[0] != 'ClipHistory13_API34':
        raise RuntimeError('Requires the owned disposable API 34 emulator')
    info = adb('shell', 'dumpsys', 'package', package)
    if 'versionName='+args.baseline+'-validation' not in info or 'DEBUGGABLE' not in info:
        raise RuntimeError('A fresh stopped legacy validation fixture must already be installed')
    uid = re.search(r'^package:' + re.escape(package) + r' uid:(\d+)$', adb('shell', 'pm', 'list', 'packages', '-U', package), re.M)[1]
    xml = '/sdcard/cliphistory-encrypted-upgrade.xml'

    def tree():
        adb('shell', 'uiautomator', 'dump', xml)
        return ET.fromstring(adb('shell', 'cat', xml))

    def find(root, label):
        return next((n for n in root.iter('node') if n.get('text') == label or n.get('content-desc') == label), None)

    def tap(node):
        x1, y1, x2, y2 = map(int, re.findall(r'\d+', node.get('bounds', '')))
        if x2 <= x1 or y2 <= y1:
            raise RuntimeError('Empty observed control')
        adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))

    def helper():
        for line in adb('shell', 'ps', '-A', '-o', 'PID,UID,NAME').splitlines():
            values = line.split()
            if len(values) == 3 and values[2] == package + ':clipboard':
                if values[1] != '2000':
                    raise RuntimeError('Recorder is not the supported non-root shell UID')
                return int(values[0])
        return None

    def legacy():
        states = []
        for name in ('history-a.bin', 'history-b.bin'):
            raw = adb('exec-out', 'run-as', package, 'cat', 'no_backup/' + name, binary=True)
            length = struct.unpack_from('>i', raw)[0]
            data = raw[4:4+length]
            body = data[:-32]
            if hashlib.sha256(body).digest() != data[-32:]:
                raise RuntimeError('Legacy fixture checksum failed')
            magic, version, generation, limit, next_id, paused, mode, count = struct.unpack_from('>iiqiq?Bi', body)
            if (magic, version) != (0x434c4831, 2):
                raise RuntimeError('Expected legacy v2 history')
            at = 34
            canonical = struct.pack('>iiqiq?Bi??i', magic, 3, 1, limit, next_id, paused, mode, count, False, True, 5)
            texts = []
            for _ in range(count):
                entry_id, timestamp, size = struct.unpack_from('>qqi', body, at)
                at += 20
                text = body[at:at+size]
                at += size
                canonical += struct.pack('>qqBi', entry_id, timestamp, 0, size) + text
                texts.append(text.decode())
            if at != len(body):
                raise RuntimeError('Legacy fixture has trailing data')
            states.append((generation, canonical, texts, count, limit, paused, mode))
        return max(states, key=lambda s: s[0])

    def instrument(mode, *extras):
        result=adb('shell','am','instrument','-w','-e','suite',mode,*extras,
            package+'.test/app.cliphistory.UiSmokeInstrumentation')
        if re.search(r'^FAIL|shortMsg=|INSTRUMENTATION_FAILED',result,re.M) or not re.search(r'^PASS \d+ encryption assertions',result,re.M):
            raise RuntimeError(result)
        return result

    encrypted_baseline=args.baseline!='1.3.4'
    if encrypted_baseline:
        # The new test APK stages harmless data using the installed old app's
        # own encryption classes. It never installs new production code here.
        adb('install','-r',str(args.test_apk))
        instrument('upgrade-fixture')

    def connect_old():
        adb('shell', 'am', 'start', '-W', '-n', package + '/app.cliphistory.ui.MainActivity')
        deadline = time.monotonic() + 45
        pressed = False
        while time.monotonic() < deadline:
            root = tree()
            permission = find(root, 'Allow all the time')
            if permission is not None:
                tap(permission)
            elif find(root,'Not now') is not None:
                tap(find(root,'Not now'))
            elif helper() and find(root, 'Listening') is not None:
                break
            else:
                button = find(root, 'Start recorder')
                if button is not None and not pressed:
                    tap(button)
                    pressed = True
        else:
            raise RuntimeError('Legacy recorder did not connect')
        return helper()

    def enable_automatic_recovery():
        # A reused fixture may have recovery disabled by an earlier settings
        # test. A live-upgrade recovery assertion requires it to be enabled.
        # The legacy synthetic copy producer may still be in the foreground.
        adb('shell', 'am', 'start', '-W', '-n', package + '/app.cliphistory.ui.MainActivity')
        menu = find(tree(), 'More options')
        if menu is None:
            raise RuntimeError('History menu was not visible before configuring recovery')
        tap(menu)
        tap(find(tree(), 'Settings'))
        for _ in range(8):
            root = tree()
            control = find(root, 'Automatic recovery')
            if control is not None and control.get('enabled') == 'true':
                if control.get('checked') != 'true':
                    tap(control)
                deadline = time.monotonic() + 8
                while time.monotonic() < deadline:
                    control = find(tree(), 'Automatic recovery')
                    if control is not None and control.get('checked') == 'true' and control.get('enabled') == 'true':
                        adb('shell', 'input', 'keyevent', 'KEYCODE_BACK')
                        return
                raise RuntimeError('Automatic recovery preference did not save')
            bounds = next(n.get('bounds') for n in root.iter('node') if n.get('scrollable') == 'true')
            x1, y1, x2, y2 = map(int, re.findall(r'\d+', bounds))
            adb('shell', 'input', 'swipe', str((x1+x2)//2), str(y2-100), str((x1+x2)//2), str(y1+100), '300')
        raise RuntimeError('Automatic recovery control was not found')

    try:
        old_pid = connect_old()
        # The encrypted baseline already has timed, indefinite and ordinary
        # entries staged through its own production codec and encryption.
        # Read it once at the upgrade boundary, preserving the live writer.
        canaries = [] if encrypted_baseline else ['Synthetic upgrade A\n  exact spaces  ', 'Synthetic upgrade B کوردی 中文 🙂']
        for text in canaries:
            adb('shell', 'am', 'start', '-W', '-n', package + '.test/app.cliphistory.SyntheticClipboardActivity',
                '-a', 'app.cliphistory.test.WRITE_SYNTHETIC', '--es', 'text', text)
            deadline = time.monotonic() + 12
            while time.monotonic() < deadline:
                if legacy()[2][0] == text:
                    break
                time.sleep(.1)
            else:
                raise RuntimeError('Old recorder missed a synthetic copy')
        if encrypted_baseline:
            fingerprint=instrument('stored-fingerprint')
            args.output.parent.mkdir(parents=True,exist_ok=True)
            args.output.with_suffix('.before.txt').write_text(fingerprint+'\n',encoding='utf-8')
            expected=re.search(r'^STORED_FINGERPRINT ([0-9a-f]{64})\s*$',fingerprint,re.M)[1]
            metadata=json.loads(re.search(r'^STORED_STATUS (.+)$',fingerprint,re.M)[1])
            before=(None,None,None,metadata['count'],metadata['limit'],metadata['paused'],metadata['duplicateMode'])
            wrapped_key_hash=hashlib.sha256(adb('exec-out','run-as',package,'cat','no_backup/history-key.bin',binary=True)).hexdigest()
            # Android records instrumentation as a user-requested process
            # stop. Explicitly restart the old app before the update so the
            # boundary exercises an enabled live recorder, not that stop.
            old_pid=connect_old()
        else:
            before = legacy()
            canonical = before[1] + hashlib.sha256(before[1]).digest()
            expected = hashlib.sha256(canonical).hexdigest()
        enable_automatic_recovery()
        if helper() != old_pid:
            raise RuntimeError('Old independent recorder changed before the upgrade')
        adb('install', '-r', str(args.apk))
        after_install_pid = helper()
        adb('install', '-r', str(args.test_apk))
        info = adb('shell', 'dumpsys', 'package', package)
        if 'versionName='+version_name not in info or f'versionCode={version_code} ' not in info:
            raise RuntimeError('Incorrect upgrade version')
        if 'uid:' + uid not in adb('shell', 'pm', 'list', 'packages', '-U', package):
            raise RuntimeError('In-place upgrade changed app UID')
        adb('shell', 'am', 'start', '-W', '-n', package + '/app.cliphistory.ui.MainActivity')
        deadline = time.monotonic() + 35
        while time.monotonic() < deadline:
            new_pid = helper()
            if new_pid and new_pid != old_pid:
                break
            time.sleep(.25)
        else:
            raise RuntimeError('New recorder did not replace the old writer')
        result = adb('shell', 'am', 'instrument', '-w', '-e', 'suite', 'stored-upgrade', '-e', 'expectedHash', expected,
                     package + '.test/app.cliphistory.UiSmokeInstrumentation')
        if 'FAIL' in result or 'INSTRUMENTATION_FAILED' in result or 'encryption assertions (stored-upgrade)' not in result:
            raise RuntimeError(result)
        key_preserved=None
        if encrypted_baseline:
            key_preserved=wrapped_key_hash==hashlib.sha256(adb('exec-out','run-as',package,'cat','no_backup/history-key.bin',binary=True)).hexdigest()
            if not key_preserved:raise RuntimeError('Encrypted upgrade replaced its wrapped data key')
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.with_suffix('.txt').write_text(result + '\n')
        args.output.write_text(json.dumps({'old_version': args.baseline+'-validation', 'new_version': version_name,
            'new_version_code':version_code,
            'old_helper_pid': old_pid, 'helper_pid_after_install': after_install_pid, 'new_helper_pid': new_pid,
            'helper_uid': 2000, 'app_uid_preserved': True, 'exact_committed_state_preserved': True,
            'automatic_recovery_enabled_before_upgrade': True,
            'both_slots_authenticated': True, 'private_modes': '0600', 'count': before[3], 'limit': before[4],
            'paused': before[5], 'duplicate_mode': before[6], 'wrapped_key_preserved':key_preserved,
            'sensitive_timed_and_indefinite_preserved':encrypted_baseline}, indent=2) + '\n')
        print('PASS live encrypted in-place upgrade: ' + str(args.output))
    finally:
        adb('shell', 'rm', '-f', xml)


if __name__ == '__main__':
    main()
