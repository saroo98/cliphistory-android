#!/usr/bin/env python3
"""Signed update check on an owned disposable emulator, never a physical phone.

Uses root adbd only to seed/read a synthetic stopped app's private test fixture.
The recorder itself must run through non-root Shizuku as shell UID 2000.
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
    p=argparse.ArgumentParser()
    p.add_argument('--adb',required=True)
    p.add_argument('--serial',required=True)
    p.add_argument('--old-apk',type=Path,required=True)
    p.add_argument('--new-apk',type=Path,required=True)
    p.add_argument('--output',type=Path,required=True)
    args=p.parse_args()
    package='app.cliphistory'
    def adb(*parts,binary=False):
        command=['shell',shlex.join(parts[1:])] if parts and parts[0]=='shell' else list(parts)
        result=subprocess.run([args.adb,'-s',args.serial,*command],capture_output=True,timeout=45)
        if result.returncode:raise RuntimeError(result.stderr.decode(errors='replace'))
        return result.stdout if binary else result.stdout.decode().replace('\r\n','\n').strip()
    if not args.serial.startswith('emulator-') or not adb('emu','avd','name').splitlines()[0].startswith('ClipHistory13_'):
        raise RuntimeError('Signed upgrade fixtures require this task\'s disposable emulator')
    if adb('shell','pm','list','packages',package).splitlines().count('package:'+package):
        raise RuntimeError('Production-ID package already exists in this emulator; use a fresh fixture target')
    for path in (args.old_apk,args.new_apk):
        if not path.is_file():raise RuntimeError('Missing APK: '+str(path))
    adb('install',str(args.old_apk))
    old_info=adb('shell','dumpsys','package',package)
    old_version=re.search(r'versionName=(\S+)',old_info)[1]
    if old_version not in ('1.1.0','1.2.0'):raise RuntimeError('Unexpected old release version')
    adb('root');adb('wait-for-device')
    if adb('shell','id','-u')!='0':raise RuntimeError('Disposable emulator does not support fixture access')
    uid=int(re.search(r'^package:'+re.escape(package)+r' uid:(\d+)$',adb('shell','pm','list','packages','-U',package),re.M)[1])
    # Do not seed over a live independent writer.
    if any(line.strip().endswith(package+':clipboard') for line in adb('shell','ps','-A','-o','NAME').splitlines()):
        raise RuntimeError('Upgrade fixture has a live helper')
    adb('shell','am','force-stop',package)
    a='Synthetic upgrade A\n  exact spaces  '
    b='Synthetic upgrade B کوردی 中文 🙂'
    expected=[b,a]
    body=struct.pack('>iiqiq?i',0x434c4831,1,9,20,5,True,4)
    for entry_id,text in zip((4,3,2,1),(b,a,b,a)):
        encoded=text.encode();body+=struct.pack('>qqi',entry_id,entry_id,len(encoded))+encoded
    payload=body+hashlib.sha256(body).digest()
    frame=struct.pack('>i',len(payload))+payload
    temp=Path(args.output).with_suffix('.fixture.bin');temp.parent.mkdir(parents=True,exist_ok=True);temp.write_bytes(frame)
    adb('push',str(temp),'/data/local/tmp/cliphistory-upgrade-fixture.bin')
    directory='/data/user/0/'+package+'/no_backup'
    adb('shell','mkdir','-p',directory)
    adb('shell','chown',str(uid)+':'+str(uid),directory)
    adb('shell','chmod','700',directory)
    for name in ('history-a.bin','history-b.bin'):
        path=directory+'/'+name
        adb('shell','cp','/data/local/tmp/cliphistory-upgrade-fixture.bin',path)
        adb('shell','chown',str(uid)+':'+str(uid),path);adb('shell','chmod','600',path)
    adb('shell','restorecon','-R',directory)
    adb('shell','rm','/data/local/tmp/cliphistory-upgrade-fixture.bin')
    # Restarting adbd for fixture access can stop a shell-started Shizuku server.
    # Explicitly retain the recorder's supported non-root identity on this AVD.
    if adb('shell','su','2000','id','-u')!='2000':raise RuntimeError('Shell launch identity unavailable')
    shizuku_path=adb('shell','pm','path','moe.shizuku.privileged.api')
    if not re.fullmatch(r'package:/data/app/[^\n]+/moe\.shizuku\.privileged\.api-[^/]+/base\.apk',shizuku_path):
        raise RuntimeError('Unverified official Shizuku APK path')
    starter=shizuku_path.removeprefix('package:').removesuffix('/base.apk')+'/lib/x86_64/libshizuku.so'
    adb('shell','su','2000',starter)
    def tree():
        adb('shell','uiautomator','dump','/sdcard/cliphistory-upgrade.xml')
        return ET.fromstring(adb('shell','cat','/sdcard/cliphistory-upgrade.xml'))
    def find(root,label):
        return next((n for n in root.iter('node') if n.get('text')==label or n.get('content-desc')==label),None)
    def tap(node):
        values=list(map(int,re.findall(r'\d+',node.get('bounds',''))))
        if len(values)!=4:raise RuntimeError('UI control has no bounds')
        adb('shell','input','tap',str((values[0]+values[2])//2),str((values[1]+values[3])//2))
    def await_ui(predicate):
        end=time.monotonic()+25
        while time.monotonic()<end:
            root=tree()
            if predicate(root):return root
            time.sleep(.15)
        raise RuntimeError('Upgrade UI precondition not reached')
    adb('shell','am','start','-W','-n',package+'/app.cliphistory.ui.MainActivity')
    await_ui(lambda root:any(b in n.get('text','') for n in root.iter('node')))
    before={name:adb('exec-out','cat',directory+'/'+name,binary=True) for name in ('history-a.bin','history-b.bin')}
    if any(value!=frame for value in before.values()):raise RuntimeError('Old release changed offline fixture')
    adb('install','-r',str(args.new_apk))
    info=adb('shell','dumpsys','package',package)
    if 'versionName=1.3.0' not in info or 'versionCode=4 ' not in info:raise RuntimeError('Signed update version incorrect')
    new_uid=int(re.search(r'^package:'+re.escape(package)+r' uid:(\d+)$',adb('shell','pm','list','packages','-U',package),re.M)[1])
    if new_uid!=uid:raise RuntimeError('Update changed app UID')
    adb('shell','am','start','-W','-n',package+'/app.cliphistory.ui.MainActivity')
    root=await_ui(lambda root:find(root,'Continue') is not None or find(root,'More options') is not None)
    if find(root,'Continue') is not None:tap(find(root,'Continue'))
    root=await_ui(lambda root:find(root,'Connect') is not None)
    tap(find(root,'Connect'))
    end=time.monotonic()+25
    while time.monotonic()<end:
        root=tree()
        # ElementTree leaf nodes are false-y despite being present.
        permission=find(root,'Allow all the time')
        if permission is None:permission=find(root,'Allow')
        if permission is not None:tap(permission)
        # A successful attachment is evidenced by migrated private frames below.
        raw=adb('exec-out','cat',directory+'/history-a.bin',binary=True)
        if len(raw)>=12 and struct.unpack_from('>i',raw,8)[0]==2:break
        time.sleep(.15)
    def decode(raw):
        length=struct.unpack_from('>i',raw)[0];data=raw[4:4+length];body=data[:-32]
        if hashlib.sha256(body).digest()!=data[-32:]:raise RuntimeError('Migrated checksum invalid')
        magic,version,generation,limit,next_id,paused,mode,count=struct.unpack_from('>iiqiq?Bi',body)
        if (magic,version,limit,paused,mode,count)!=(0x434c4831,2,20,True,0,2):raise RuntimeError('Migration did not preserve pause/limit/policy/count')
        at=34;texts=[]
        for _ in range(count):
            entry_id,timestamp,size=struct.unpack_from('>qqi',body,at);at+=20;texts.append(body[at:at+size].decode());at+=size
        if at!=len(body) or texts!=expected:raise RuntimeError('Migration text/order changed')
        return {'format':version,'count':count,'limit':limit,'paused':paused,'mode':mode,'generation':generation}
    slots=[decode(adb('exec-out','cat',directory+'/'+name,binary=True)) for name in ('history-a.bin','history-b.bin')]
    processes=adb('shell','ps','-A','-o','UID,NAME').splitlines()
    if not any(line.split()==['2000',package+':clipboard'] for line in processes):raise RuntimeError('Migrating recorder did not run as non-root shell UID 2000')
    result={'old_version':old_version,'new_version':'1.3.0','serial':args.serial,'api':adb('shell','getprop','ro.build.version.sdk'),
        'same_uid':True,'signed_in_place_update':True,'old_offline_fixture_verified':True,'slots':slots,
        'new_apk_sha256':hashlib.sha256(args.new_apk.read_bytes()).hexdigest(),'fixture':'Synthetic v1 duplicate, Unicode and whitespace history; root adbd only for fixture access; shell UID 2000 recorder'}
    args.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8');print(json.dumps(result,indent=2))
    temp.unlink()


if __name__=='__main__':main()
