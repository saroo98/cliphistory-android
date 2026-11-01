#!/usr/bin/env python3
"""Matched cold first-draw measurements on this task's disposable validation emulator."""
import argparse
import json
import math
from pathlib import Path
import re
import subprocess
import time


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--adb',required=True)
    parser.add_argument('--serial',required=True)
    parser.add_argument('--expected-version',required=True)
    parser.add_argument('--output',type=Path,required=True)
    args=parser.parse_args();package='app.cliphistory.validation'
    def adb(*parts):
        result=subprocess.run([args.adb,'-s',args.serial,*parts],capture_output=True,text=True,timeout=35)
        if result.returncode:raise RuntimeError(result.stderr.strip())
        return result.stdout
    if not args.serial.startswith('emulator-') or not adb('emu','avd','name').splitlines()[0].startswith('ClipHistory13_'):
        raise RuntimeError('Cold force-stop measurements require the owned disposable emulator')
    if re.search(r'versionName=(\S+)',adb('shell','dumpsys','package',package))[1]!=args.expected_version:
        raise RuntimeError('Measured APK version differs from the requested version')
    if package+':clipboard' in adb('shell','ps','-A','-o','NAME'):
        raise RuntimeError('Stop the isolated recorder before an offline launch comparison')
    trials=[]
    for index in range(10):
        adb('shell','cmd','statusbar','collapse')
        adb('shell','am','force-stop',package)
        result=adb('shell','am','start','-W','-a','app.cliphistory.OPEN_HISTORY','-n',package+'/app.cliphistory.ui.MainActivity')
        if 'LaunchState: COLD' not in result or 'Status: ok' not in result:raise RuntimeError('Cold launch was not reported by Android')
        total=int(re.search(r'TotalTime: (\d+)',result)[1])
        fixture_xml='/sdcard/cliphistory-cold-validation.xml'
        adb('shell','uiautomator','dump',fixture_xml)
        ui=adb('shell','cat',fixture_xml)
        adb('shell','rm',fixture_xml)
        if 'Synthetic newest text' not in ui:raise RuntimeError('Expected matched synthetic history was not rendered')
        trials.append({'trial':index+1,'android_first_draw_ms':total,'matched_history_visible':True})
        print(f'Cold trial {index+1}: first draw {total} ms; history visible',flush=True)
        time.sleep(.3)
    values=sorted(trial['android_first_draw_ms'] for trial in trials)
    report={'version':args.expected_version,'api':adb('shell','getprop','ro.build.version.sdk').strip(),
            'trials':trials,'p50_ms':values[4],'p95_ms':values[math.ceil(len(values)*.95)-1],
            'scope':'Android am-start cold first draw. History presence verified separately; its async loaded-frame latency is not measured by this metric.'}
    args.output.parent.mkdir(parents=True,exist_ok=True);args.output.write_text(json.dumps(report,indent=2)+'\n')
    print(f'PASS 10 cold launches: p50={report["p50_ms"]} ms, p95={report["p95_ms"]} ms',flush=True)


if __name__=='__main__':main()
