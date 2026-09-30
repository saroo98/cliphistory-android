#!/usr/bin/env python3
"""Native physical-device timing, scoped to the isolated synthetic validation app.

Records private local video, app frame stats and compositor traces. No clipboard
writes, production app actions, notification exports or public uploads.
"""
import argparse
import json
from pathlib import Path
import re
import shlex
import subprocess
import time
import uuid
import xml.etree.ElementTree as ET


def main():
    p=argparse.ArgumentParser()
    p.add_argument('--adb',required=True)
    p.add_argument('--serial',required=True)
    p.add_argument('--output',type=Path,required=True)
    p.add_argument('--smoke',action='store_true',help='Two immediate trials; never substitutes for the timed test')
    args=p.parse_args();package='app.cliphistory.validation'
    def adb(*parts,binary=False):
        command=['shell',shlex.join(parts[1:])] if parts and parts[0]=='shell' else list(parts)
        r=subprocess.run([args.adb,'-s',args.serial,*command],capture_output=True,timeout=40)
        if parts==('shell','pidof','screenrecord') and r.returncode==1:return ''
        if r.returncode:raise RuntimeError(r.stderr.decode(errors='replace'))
        return r.stdout if binary else r.stdout.decode(errors='replace').replace('\r\n','\n').strip()
    info=adb('shell','dumpsys','package',package)
    if 'DEBUGGABLE' not in info or 'versionName=1.3.0-validation' not in info:
        raise RuntimeError('Requires this task\'s isolated debuggable validation APK')
    if any(line.strip().endswith(package+':clipboard') for line in adb('shell','ps','-A','-o','NAME').splitlines()):
        raise RuntimeError('Stop the validation recorder before an offline synthetic check')
    args.output.mkdir(parents=True,exist_ok=False)
    ident=uuid.uuid4().hex[:12];device='/sdcard/Download/cliphistory-validation-'+ident
    adb('shell','mkdir',device)
    xml='/sdcard/cliphistory-validation-timing-'+ident+'.xml'
    local=args.output/'config.pbtxt'
    # Isolate frame timelines from verbose ftrace traffic so startup frames and
    # touch timestamps survive the entire bounded capture.
    local.write_text('buffers { size_kb: 4096 fill_policy: RING_BUFFER }\n'
        'buffers { size_kb: 32768 fill_policy: RING_BUFFER }\nduration_ms: 8000\n'
        'data_sources { config { name: "android.surfaceflinger.frametimeline" target_buffer: 0 } }\n'
        'data_sources { config { name: "linux.ftrace" target_buffer: 1 ftrace_config { atrace_categories: "gfx" atrace_categories: "view" atrace_categories: "input" atrace_categories: "wm" atrace_apps: "app.cliphistory.validation" atrace_apps: "com.android.systemui" } } }\n')
    def tree():
        adb('shell','uiautomator','dump',xml)
        return ET.fromstring(adb('shell','cat',xml))
    def find(root,label):
        return next((n for n in root.iter('node') if n.get('text')==label or n.get('content-desc')==label or
                     n.get('content-desc','').startswith(label+', ')),None)
    def bounds(node):
        values=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
        if len(values)!=4:raise RuntimeError('No observed UI bounds')
        return values
    def tap(node):
        x1,y1,x2,y2=bounds(node);adb('shell','input','tap',str((x1+x2)//2),str((y1+y2)//2))
    def await_tree(label):
        end=time.monotonic()+15
        while time.monotonic()<end:
            root=tree();n=find(root,label)
            if n is not None:return root,n
        raise RuntimeError('Missing UI control: '+label)
    def capture_setting(value):
        adb('shell','cmd','statusbar','collapse')
        adb('shell','am','start','-W','-n',package+'/app.cliphistory.ui.MainActivity')
        for _ in range(4):
            root=tree()
            if find(root,'More options') is not None:break
            if not any(n.get('package')==package for n in root.iter('node')):raise RuntimeError('Validation app is not foreground')
            adb('shell','input','keyevent','KEYCODE_BACK')
        root,n=await_tree('More options')
        if find(root,'Synthetic newest text') is None:raise RuntimeError('Expected synthetic history is not visible')
        tap(n);tap(await_tree('Settings')[1])
        for _ in range(6):
            root=tree();n=find(root,'Allow screenshots')
            if n is not None:
                original=n.get('checked')=='true'
                if original!=value:tap(n)
                return original
            scroll=next((n for n in root.iter('node') if n.get('scrollable')=='true' and n.get('package')==package),None)
            if scroll is None:raise RuntimeError('Settings scroll view unavailable')
            x1,y1,x2,y2=bounds(scroll)
            adb('shell','input','swipe',str((x1+x2)//2),str(y2-120),str((x1+x2)//2),str(y1+120),'450')
        raise RuntimeError('Screenshot control not reached')
    original=capture_setting(True)
    producer=package+'.test/app.cliphistory.SyntheticClipboardActivity'
    adb('shell','am','start','-W','-n',producer)
    intervals=[0,0] if args.smoke else [60]*10+[120]*2
    trials=[];started=time.monotonic()
    try:
        for number,wait in enumerate(intervals,1):
            print(f'Trial {number}/{len(intervals)}: idle wait {wait} seconds',flush=True)
            end=time.monotonic()+wait
            while time.monotonic()<end:time.sleep(min(1,end-time.monotonic()))
            adb('shell','cmd','statusbar','expand-settings')
            root=tree();tile=find(root,'Clipboard validation')
            if tile is None:
                pager=next((n for n in root.iter('node') if n.get('resource-id')=='com.android.systemui:id/qs_pager'),None)
                if pager is None:raise RuntimeError('Observed QS pager unavailable')
                x1,y1,x2,y2=bounds(pager)
                adb('shell','input','swipe',str(x2-80),str((y1+y2)//2),str(x1+80),str((y1+y2)//2),'500')
                root,tile=await_tree('Clipboard validation')
            if 'Quick copy' not in tile.get('content-desc',''):raise RuntimeError('Validation tile is not configured for quick copy')
            if adb('shell','pidof','screenrecord'):raise RuntimeError('Another screen recording is active')
            adb('shell','dumpsys','gfxinfo',package,'reset')
            video=f'{device}/trial-{number:02d}.mp4';trace=f'/data/misc/perfetto-traces/cliphistory-validation-{ident}-trial-{number:02d}.perfetto'
            recording=subprocess.Popen([args.adb,'-s',args.serial,'shell',shlex.join(['screenrecord','--time-limit','6','--bit-rate','8000000','--size','672x1496',video])],stdout=subprocess.PIPE,stderr=subprocess.PIPE)
            deadline=time.monotonic()+5
            while not adb('shell','pidof','screenrecord'):
                if time.monotonic()>deadline:raise RuntimeError('Recorder did not start')
            trace_start=subprocess.run([args.adb,'-s',args.serial,'shell',shlex.join(['perfetto','--txt','-c','-','-o',trace,'--background-wait'])],
                input=local.read_bytes(),capture_output=True,timeout=40)
            if trace_start.returncode:raise RuntimeError(trace_start.stderr.decode(errors='replace'))
            marker=f'{ident}_trial_{number}'
            adb('shell','log','-t','ClipHistoryFrameTest',marker)
            tap_time=time.monotonic();tap(tile)
            root,close=await_tree('Close quick copy')
            labels=[n.get('text','') for n in root.iter('node') if n.get('package')==package]
            if not all(value in labels for value in ('Synthetic newest text','Synthetic third text')):
                raise RuntimeError('Three-entry panel did not load')
            accessible_ms=(time.monotonic()-tap_time)*1000
            recording.communicate(timeout=15)
            if recording.returncode:raise RuntimeError('Video recording failed')
            root,close=await_tree('Close quick copy')
            text_nodes=[n for n in root.iter('node') if n.get('package')==package]
            # Only store owned controls and numeric timings, never the QS XML.
            panel=[bounds(n) for n in text_nodes if n.get('class')=='android.widget.ScrollView']
            stats=adb('shell','dumpsys','gfxinfo',package,'framestats')
            (args.output/f'trial-{number:02d}-frames.txt').write_text(stats)
            logs=adb('logcat','-d','-v','epoch','-s','ClipHistoryFrameTest:I','ClipHistoryTileTrace:D','*:S')
            (args.output/f'trial-{number:02d}-events.txt').write_text(logs)
            tap(close);adb('shell','cmd','statusbar','collapse')
            if find(tree(),'Close quick copy') is not None:raise RuntimeError('Panel did not close')
            time.sleep(1) # Complete this bounded trace before pulling it.
            for remote in (video,trace):
                destination=args.output/Path(remote).name;adb('pull',remote,str(destination))
                if not destination.is_file() or destination.stat().st_size<1000:raise RuntimeError('Missing frame evidence')
                adb('shell','rm',remote)
            trials.append({'trial':number,'wait_seconds':wait,'native_tap':True,'accessibility_dump_available_ms':accessible_ms,
                'panel_stable_for_video':True,'closed_with_one_tap':True,'panel_bounds':panel,'marker':marker})
            report={'smoke':args.smoke,'api':adb('shell','getprop','ro.build.version.sdk'),'elapsed_seconds':time.monotonic()-started,
                'requested_idle_seconds':sum(intervals),'trials':trials,'privacy':'Raw videos/traces remain private local test evidence; no QS XML saved or published'}
            (args.output/'results.json').write_text(json.dumps(report,indent=2)+'\n')
            print(f'Trial {number} passed: panel loaded, remained visible and closed; frames/video/trace saved',flush=True)
        print('PASS all requested native timing trials',flush=True)
    finally:
        adb('shell','cmd','statusbar','collapse')
        capture_setting(original)
        adb('shell','rm',xml)


if __name__=='__main__':main()
