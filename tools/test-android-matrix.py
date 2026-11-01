#!/usr/bin/env python3
"""Run the existing acceptance suites on one explicitly owned disposable AVD."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import shlex
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--apk', type=Path, required=True)
    parser.add_argument('--test-apk', type=Path, required=True)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--suite', nargs='+', required=True)
    parser.add_argument('--theme', choices=['light', 'dark'], default='light')
    args = parser.parse_args()
    package = 'app.cliphistory.validation'

    def adb(*parts, timeout=50):
        command = ['shell', shlex.join(parts[1:])] if parts and parts[0] == 'shell' else list(parts)
        result = subprocess.run([args.adb, '-s', args.serial, *command], capture_output=True,
                                text=True, encoding='utf-8', errors='replace', timeout=timeout)
        if result.returncode:
            raise RuntimeError(result.stderr.strip() or result.stdout.strip())
        return result.stdout.strip()

    if not re.fullmatch(r'emulator-\d+', args.serial):
        raise RuntimeError('Physical devices are prohibited by this test driver')
    avd = adb('emu', 'avd', 'name').splitlines()[0]
    owned = {f'ClipHistory13_API{api}': api for api in (34, 35, 36, 37)}
    owned.update({f'ClipHistoryCompat_API{api}_20261031': api for api in (30, 31, 32, 35)})
    owned.update({'ClipHistoryExpanded_API33_20261031': 33, 'ClipHistoryExpanded_API361_20261031': 36})
    if avd not in owned:
        raise RuntimeError('This is not an owned ClipHistory disposable AVD')
    api = int(adb('shell', 'getprop', 'ro.build.version.sdk'))
    if owned[avd] != api:
        raise RuntimeError('AVD name and running API level disagree')
    args.output.mkdir(parents=True, exist_ok=True)
    report = {'serial': args.serial, 'avd': avd, 'api': api, 'theme': args.theme,
              'tile_page_limit': 4,
              'sdk_full': adb('shell', 'getprop', 'ro.build.version.sdk_full'),
              'fingerprint': adb('shell', 'getprop', 'ro.build.fingerprint'),
              'release': adb('shell', 'getprop', 'ro.build.version.release'),
              'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(),
              'test_apk_sha256': hashlib.sha256(args.test_apk.read_bytes()).hexdigest(), 'suites': []}
    for path in (args.apk, args.test_apk):
        if 'Success' not in adb('install', '-r', str(path.resolve())):
            raise RuntimeError('Validation APK installation did not report success')
    info = adb('shell', 'dumpsys', 'package', package)
    if 'DEBUGGABLE' not in info or 'versionName=1.4.4-validation' not in info:
        raise RuntimeError('Expected the isolated 1.4.4 validation package')
    report['shizuku_package'] = '\n'.join(line.strip() for line in
        adb('shell', 'dumpsys', 'package', 'moe.shizuku.privileged.api').splitlines()
        if re.search(r'versionName=|versionCode=', line))
    for suite in args.suite:
        started = time.monotonic()
        try:
            result = adb('shell', 'am', 'instrument', '-w', '-e', 'suite', suite, '-e', 'theme', args.theme,
                         '-e', 'tilePage', '4',
                         package + '.test/app.cliphistory.UiSmokeInstrumentation', timeout=240)
            (args.output / (suite + '.txt')).write_text(result + '\n', encoding='utf-8')
            failed = re.search(r'^FAIL(?:\s|:)|shortMsg=|Process crashed|INSTRUMENTATION_FAILED', result, re.M)
            summary = re.findall(r'^PASS(?:\s+\d+.*assertions.*|:\s+\d+.*assertions\..*)$', result, re.M)
            if failed or not summary:
                raise RuntimeError('Acceptance suite failed or never completed; inspect its saved log')
            row = {'suite': suite, 'passed': True, 'summary': summary[-1],
                   'seconds': round(time.monotonic() - started, 3)}
        except Exception as error:
            row = {'suite': suite, 'passed': False, 'error': str(error),
                   'seconds': round(time.monotonic() - started, 3)}
        report['suites'].append(row)
        (args.output / 'checks.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
        print(json.dumps(row), flush=True)
        if not row['passed']:
            raise RuntimeError(f'Stopped after {suite}; earlier successful evidence is preserved')


if __name__ == '__main__':
    main()
