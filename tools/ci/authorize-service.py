#!/usr/bin/env python3
"""Authorize the validation app with an already running Shizuku-compatible service.

Emulator-only CI helper. It reuses the native-UI mechanism of the existing drivers
(tools/test-encrypted-upgrade.py connect_old and tools/test-upgrade.py): open
ClipHistory validation, press its own Connect / Start recorder control, and press
the service manager's own "Allow all the time" button when the manager's real
prompt appears. Nothing is granted through pm, appops or the manager's private
configuration. Done means the recorder helper runs with the expected UID and the
app shows its acknowledged Listening state.
"""
import argparse
import json
import re
import shlex
import subprocess
import time
import xml.etree.ElementTree as ET
from pathlib import Path

PACKAGE = 'app.cliphistory.validation'
MAIN = PACKAGE + '/app.cliphistory.ui.MainActivity'
DUMP = '/data/local/tmp/cliphistory-ci-ui.xml'
ALLOW = ('Allow all the time', 'Allow')         # only inside the manager's own prompt
DISMISS = ('Explore the app', 'Continue', 'Not now')  # the app's optional first-run sheets
START = ('Start recorder', 'Connect', 'Reconnect')    # the app's own connection controls
READY = ('Listening', 'Recording')
UNSUPPORTED = ('Check your service setup',)  # a service mode the app cannot use
ROOT_REFUSED = ('Start your service without root',)  # the app's deliberate root (UID 0) refusal


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--manager-package', required=True)
    parser.add_argument('--expected-uid', type=int, choices=(0, 2000), default=2000)
    parser.add_argument('--timeout', type=int, default=120)
    parser.add_argument('--expect-root-refusal', action='store_true',
                        help='Root (UID 0) server: pass only if the app refuses it with its root message and '
                             'no recorder helper starts. Android clears the clipboard when root reads it.')
    parser.add_argument('--output', type=Path, required=True, help='JSON report path')
    args = parser.parse_args()
    if not re.fullmatch(r'emulator-\d+', args.serial):
        raise SystemExit('Physical devices are refused by this CI helper')
    if not re.fullmatch(r'[A-Za-z]\w*(?:\.[A-Za-z]\w*)+', args.manager_package):
        raise SystemExit('Invalid manager package name')

    def adb(*parts, timeout=45):
        # ADB joins shell arguments; quote them for Android's shell like the other drivers do.
        command = ['shell', shlex.join(parts[1:])] if parts and parts[0] == 'shell' else list(parts)
        result = subprocess.run([args.adb, '-s', args.serial, *command], capture_output=True, text=True,
                                encoding='utf-8', errors='replace', timeout=timeout)
        if result.returncode:
            raise RuntimeError(result.stderr.strip() or result.stdout.strip())
        return result.stdout.strip()

    def tree():
        adb('shell', 'rm', '-f', DUMP)  # never parse a stale hierarchy after a failed dump
        adb('shell', 'uiautomator', 'dump', DUMP)
        return ET.fromstring(adb('shell', 'cat', DUMP))

    def find(nodes, labels, package):
        for label in labels:  # labels are in priority order
            for node in nodes:
                values = {(node.get('text') or '').casefold(), (node.get('content-desc') or '').casefold()}
                if label.casefold() in values and node.get('package') == package:
                    return node
        return None

    def tap(node):
        values = list(map(int, re.findall(r'\d+', node.get('bounds', ''))))
        if len(values) != 4 or values[2] <= values[0] or values[3] <= values[1]:
            raise RuntimeError('Observed control has no usable bounds')
        adb('shell', 'input', 'tap', str((values[0] + values[2]) // 2), str((values[1] + values[3]) // 2))

    def helper_uid():
        for line in adb('shell', 'ps', '-A', '-o', 'PID,UID,NAME').splitlines()[1:]:
            values = line.split()
            if len(values) == 3 and values[2] == PACKAGE + ':clipboard':
                return int(values[1])
        return None

    started = time.monotonic()
    report = {'serial': args.serial, 'package': PACKAGE, 'manager_package': args.manager_package,
              'expected_helper_uid': args.expected_uid, 'prompt_answered': False, 'dismissed_dialogs': [],
              'events': [], 'started_utc': time.strftime('%Y-%m-%dT%H:%M:%SZ', time.gmtime())}

    def event(text):
        report['events'].append({'seconds': round(time.monotonic() - started, 2), 'event': text})
        print(text, flush=True)

    last_root = None
    try:
        adb('shell', 'am', 'start', '-W', '-n', MAIN)
        last_start = None
        unsupported_since = None
        refused_since = None
        while True:
            if time.monotonic() - started > args.timeout:
                raise RuntimeError('The validation app did not reach an authorized, listening recorder in time')
            try:
                root = tree()
            except Exception as error:  # uiautomator can fail transiently while windows change
                event('UI dump retry: ' + str(error)[:160])
                time.sleep(1)
                continue
            last_root = root
            nodes = list(root.iter('node'))
            anr = next((n for n in nodes if (n.get('text') or '').endswith("isn't responding")), None)
            if anr is not None:
                title = anr.get('text')
                if title.startswith(('ClipHistory', 'Shizuku', 'Shizako')):
                    raise RuntimeError('Product or service under test is not responding: ' + title)
                wait = next((n for n in nodes if n.get('text') == 'Wait'), None)
                if wait is None:
                    raise RuntimeError('Unhandled system dialog: ' + title)
                # Record unrelated emulator ANR dialogs; never disable ANR reporting to get a pass.
                report['dismissed_dialogs'].append(title)
                event('Pressed Wait on unrelated system dialog: ' + title)
                tap(wait)
                continue
            allow = find(nodes, ALLOW, args.manager_package)
            if allow is not None:
                event('Pressed the service manager prompt control: ' + allow.get('text'))
                tap(allow)
                report['prompt_answered'] = True
                time.sleep(0.5)
                continue
            uid = helper_uid()
            if args.expect_root_refusal:
                if uid is not None or find(nodes, READY, PACKAGE) is not None:
                    raise RuntimeError(f'A root server must be refused, but the recorder started (helper UID {uid})')
                if find(nodes, ROOT_REFUSED, PACKAGE) is not None:
                    refused_since = refused_since or time.monotonic()
                    if time.monotonic() - refused_since > 5:  # stays refused, no helper appears
                        event('The app refuses the root server with its root message; no recorder helper started')
                        report['root_refused'] = True
                        break
                    time.sleep(0.5)
                    continue
                refused_since = None
            if uid is not None and uid != args.expected_uid:
                raise RuntimeError(f'Recorder helper runs as UID {uid}, expected {args.expected_uid}')
            if uid is not None and find(nodes, READY, PACKAGE) is not None:
                event(f'Recorder helper runs as UID {uid} and the app reports it is listening')
                report['helper_uid'] = uid
                break
            if find(nodes, UNSUPPORTED, PACKAGE) is not None:
                unsupported_since = unsupported_since or time.monotonic()
                if time.monotonic() - unsupported_since > 20:
                    raise RuntimeError('The app keeps reporting "Check your service setup": it does not '
                                       'support this service mode (server UID expected %d)' % args.expected_uid)
            else:
                unsupported_since = None
            dismiss = find(nodes, DISMISS, PACKAGE)
            if dismiss is not None:
                event('Dismissed optional app sheet: ' + (dismiss.get('text') or dismiss.get('content-desc')))
                tap(dismiss)
                continue
            control = find(nodes, START, PACKAGE)
            if control is not None and (last_start is None or time.monotonic() - last_start > 12):
                event('Pressed app control: ' + (control.get('text') or control.get('content-desc')))
                tap(control)
                last_start = time.monotonic()
                continue
            if not any(n.get('package') in (PACKAGE, args.manager_package) for n in nodes):
                event('Validation app is not in front; opening it again')
                adb('shell', 'am', 'start', '-W', '-n', MAIN)
            time.sleep(0.4)
        adb('shell', 'input', 'keyevent', '3')
    except Exception as error:
        report['error'] = str(error)
        if last_root is not None:
            args.output.with_name(args.output.stem + '-last-ui.xml').write_text(
                ET.tostring(last_root, encoding='unicode') + '\n', encoding='utf-8')
        raise
    finally:
        report['elapsed_seconds'] = round(time.monotonic() - started, 2)
        args.output.parent.mkdir(parents=True, exist_ok=True)
        args.output.write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        try:
            adb('shell', 'rm', '-f', DUMP)
        except Exception:
            pass
    print(('PASS root server refused: ' if args.expect_root_refusal else 'PASS validation app authorized: ')
          + str(args.output), flush=True)


if __name__ == '__main__':
    main()
