#!/usr/bin/env python3
"""External driver: never kills its instrumentation runner or a personal phone package."""
import argparse
import json
import re
import shlex
import subprocess
import time
import uuid
import hashlib
import struct
import xml.etree.ElementTree as ET
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--mode", choices=["start", "helper-death", "helper-death-off", "app-death", "recents", "task-stop", "force-stop", "boot", "boot-off", "idle"], required=True)
    parser.add_argument("--automatic",choices=["on","off"])
    parser.add_argument("--after-boot",choices=["on","off"])
    parser.add_argument("--pause",choices=["on","off"])
    parser.add_argument("--seconds", type=int, default=1800)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    package = "app.cliphistory.validation"

    def adb(*parts):
        # ADB joins shell arguments. Quote for Android's shell so canary spaces stay exact.
        command = ["shell",shlex.join(parts[1:])] if parts and parts[0]=="shell" else list(parts)
        completed = subprocess.run([args.adb, "-s", args.serial, *command], capture_output=True, text=True, timeout=45)
        if completed.returncode:
            raise RuntimeError("ADB failed: " + completed.stderr.strip())
        return completed.stdout.strip()

    if not args.serial.startswith("emulator-") or not adb("emu", "avd", "name").splitlines()[0].startswith("ClipHistory13_"):
        raise RuntimeError("Destructive lifecycle tests require this task's disposable ClipHistory13 emulator")
    identity = adb("shell", "pm", "list", "packages", "-U", package)
    match = re.search(r"^package:" + re.escape(package) + r" uid:(\d+)$", identity, re.M)
    if not match:
        raise RuntimeError("Exact isolated validation UID not found")
    owner_uid = int(match[1])

    def processes():
        found = {}
        for line in adb("shell", "ps", "-A", "-o", "PID,UID,NAME").splitlines()[1:]:
            values = line.split()
            if len(values) == 3 and values[2] in (package, package + ":clipboard"):
                pid, uid = map(int, values[:2])
                expected = 2000 if values[2].endswith(":clipboard") else owner_uid
                if uid != expected or pid <= 1:
                    raise RuntimeError("Helper/application identity mismatch")
                found[values[2]] = pid
        return found

    before = processes()
    started = time.monotonic()
    observations = {"mode": args.mode, "serial": args.serial, "api": adb("shell", "getprop", "ro.build.version.sdk"),
                    "before": before, "started_utc": time.strftime("%Y-%m-%dT%H:%M:%SZ", time.gmtime())}
    def ui_tree():
        adb("shell","uiautomator","dump","/sdcard/cliphistory-lifecycle.xml")
        return ET.fromstring(adb("shell","cat","/sdcard/cliphistory-lifecycle.xml"))

    def find_label(tree,label):
        return next((n for n in tree.iter("node") if label in (n.get("text"),n.get("content-desc"))),None)
    def find_task(tree):
        # Launcher can predict this app on Home; that shortcut is not a Recents task.
        return next((n for n in tree.iter("node") if n.get("content-desc")=="ClipHistory validation" and not n.get("text")),None)

    def tap(node):
        values=list(map(int,re.findall(r"\d+",node.get("bounds",""))))
        if len(values)!=4 or values[2]<=values[0] or values[3]<=values[1]:
            raise RuntimeError("Invalid observed recording control bounds")
        adb("shell","input","tap",str((values[0]+values[2])//2),str((values[1]+values[3])//2))

    def recording_options():
        tree=ET.fromstring(adb("shell","run-as",package,"cat","shared_prefs/recorder_recovery.xml"))
        return {n.get("name"):n.get("value") for n in tree}
    def snapshots():
        values=[]
        for slot in ("a","b"):
            raw = subprocess.check_output([args.adb,"-s",args.serial,"exec-out","run-as",package,"cat",f"no_backup/history-{slot}.bin"],timeout=15)
            if len(raw)<4:
                continue
            length=struct.unpack(">I",raw[:4])[0]
            payload=raw[4:4+length]
            if len(payload)>=66 and hashlib.sha256(payload[:-32]).digest()==payload[-32:]:values.append(payload)
        if not values:raise RuntimeError("No valid validation snapshot")
        return values
    def can_capture():
        canary = "Synthetic recovered " + args.mode + " " + uuid.uuid4().hex
        adb("shell", "am", "start", "-n", package + ".test/app.cliphistory.SyntheticClipboardActivity",
            "-a", "app.cliphistory.test.WRITE_SYNTHETIC", "--es", "text", canary)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            for payload in snapshots():
                if canary.encode() in payload[:-32]:
                    observations["captured_after_recovery"] = True
                    return
            time.sleep(0.2)
        raise RuntimeError("Recovered process did not capture the synthetic canary")
    if args.mode=="start":
        adb("shell","am","start","-W","-n",package+"/app.cliphistory.ui.MainActivity")
        deadline=time.monotonic()+40
        pressed=False
        while time.monotonic()<deadline:
            tree=ui_tree()
            labels={node.get("text"):node for node in tree.iter("node")}
            if "Pixel Launcher isn't responding" in labels and "Close app" in labels:
                tap(labels["Close app"])
                observations["dismissed_emulator_launcher_anr"]=True
                continue
            if "Listening" in labels or "Paused" in labels or "Recording paused" in labels:
                observations["explicit_start_connected"]=True
                break
            target=next((labels[label] for label in ("Continue","Not now","Start recorder","Reconnect","Connect") if label in labels),None)
            if target is not None and (target.get("text") in ("Continue","Not now") or not pressed):
                tap(target)
                pressed=target.get("text") not in ("Continue","Not now")
            time.sleep(0.2)
        else:
            raise RuntimeError("Explicit UI start did not connect; authorize Shizuku first")
        requested={"Automatic recovery":args.automatic,"Resume after reboot":args.after_boot,"Pause recording":args.pause}
        if any(requested.values()):
            tap(find_label(ui_tree(),"More options"));tap(find_label(ui_tree(),"Settings"))
            for label,value in requested.items():
                if value is None:
                    continue
                node=find_label(ui_tree(),label)
                if node is None or node.get("class")!="android.widget.Switch":
                    raise RuntimeError("Expected native setting is not visible: "+label)
                desired="true" if value=="on" else "false"
                if node.get("checked")!=desired:
                    tap(node)
                deadline=time.monotonic()+8
                while time.monotonic()<deadline:
                    node=find_label(ui_tree(),label)
                    if node is not None and node.get("enabled")=="true" and node.get("checked")==desired:
                        break
                else:
                    raise RuntimeError("Setting not acknowledged: "+label)
            observations["requested_settings"]={k:v for k,v in requested.items() if v is not None}
            adb("shell","input","keyevent","4")
        adb("shell","input","keyevent","3")
    elif args.mode in ("helper-death", "helper-death-off", "app-death"):
        if args.mode=="helper-death-off" and (recording_options().get("automatic","true")!="false" or recording_options().get("explicit_stop")=="true"):
            raise RuntimeError("Disable automatic recovery through start --automatic off first")
        name = package + ":clipboard" if args.mode == "helper-death" else package
        if args.mode=="helper-death-off":name=package+":clipboard"
        pid = before.get(name)
        if not pid:
            raise RuntimeError("Start and authorize the validation recorder before this test")
        if args.mode.startswith("helper-death"):
            adb("shell", "kill", "-9", str(pid))
        else:
            adb("shell", "run-as", package, "kill", "-9", str(pid))
        deadline = time.monotonic() + (20 if args.mode=="helper-death-off" else 45)
        while time.monotonic() < deadline:
            after = processes()
            if args.mode=="helper-death-off":
                if after.get(name):
                    raise RuntimeError("Disabled recovery restarted the helper")
                time.sleep(0.5);continue
            if after.get(name) and after[name] != pid and after.get(package + ":clipboard"):
                # A process existing is insufficient: its listener and descriptors must be ready.
                time.sleep(1)
                can_capture()
                observations["recovered"] = True
                break
            time.sleep(0.5)
        else:
            if args.mode=="helper-death-off":observations["disabled_recovery_did_not_restart"]=True
            else:raise RuntimeError("Enabled recovery did not restore the interrupted process")
    elif args.mode=="recents":
        helper=before.get(package+":clipboard")
        if not helper:raise RuntimeError("Validation helper must be active")
        adb("shell","am","start","-W","-n",package+"/app.cliphistory.ui.MainActivity")
        deadline=time.monotonic()+10
        while find_label(ui_tree(),"More options") is None:
            if time.monotonic()>deadline:raise RuntimeError("Validation activity did not become foreground before Recents")
        adb("shell","input","keyevent","3")
        deadline=time.monotonic()+10
        while True:
            tree=ui_tree()
            if tree.find("node").get("package")=="com.google.android.apps.nexuslauncher" and find_task(tree) is None:break
            if time.monotonic()>deadline:raise RuntimeError("Launcher did not return home before Recents")
        adb("shell","input","keyevent","187")
        deadline=time.monotonic()+10
        while find_task(ui_tree()) is None:
            if time.monotonic()>deadline:raise RuntimeError("Validation task is not visible in Recents")
        # Clear all is a native Recents dismissal, restricted to the disposable AVD.
        # Avoid swiping a clipped off-centre task thumbnail on Pixel Launcher.
        for _ in range(6):
            clear=find_label(ui_tree(),"Clear all")
            if clear is not None:break
            adb("shell","input","touchscreen","swipe","150","1000","1000","1000","500")
        else:raise RuntimeError("Native Recents Clear all control was not found")
        tap(clear)
        time.sleep(1)
        if find_task(ui_tree()) is not None:raise RuntimeError("Native Recents dismissal did not remove validation task")
        can_capture()
        observations["capture_continues_after_recents_dismissal"]=True
    elif args.mode in ("task-stop", "force-stop"):
        files_before = adb("shell", "run-as", package, "sha256sum", "no_backup/history-a.bin", "no_backup/history-b.bin")
        adb("shell", *( ["cmd", "activity", "stop-app", package] if args.mode == "task-stop" else ["am", "force-stop", package]))
        # An independent synthetic producer supplies the event that checks the helper stop gate.
        adb("shell", "am", "start", "-n", package + ".test/app.cliphistory.SyntheticClipboardActivity",
            "-a", "app.cliphistory.test.WRITE_SYNTHETIC", "--es", "text", "Synthetic lifecycle stop canary")
        time.sleep(3)
        files_after = adb("shell", "run-as", package, "sha256sum", "no_backup/history-a.bin", "no_backup/history-b.bin")
        if files_before != files_after or package + ":clipboard" in processes():
            raise RuntimeError("Deliberate stop did not prevent helper capture")
        observations["no_capture_after_user_stop"] = True
    elif args.mode in ("boot","boot-off"):
        opts=recording_options()
        if opts.get("explicit_stop")=="true" or opts.get("automatic","true")!="true" or opts.get("after_boot","true")!=("false" if args.mode=="boot-off" else "true"):
            raise RuntimeError("Boot-test settings precondition is not satisfied")
        newest=max(snapshots(),key=lambda p:struct.unpack(">q",p[8:16])[0])
        paused=newest[28]==1
        adb("reboot")
        deadline = time.monotonic() + 180
        while time.monotonic() < deadline:
            try:
                if adb("shell", "getprop", "sys.boot_completed") == "1":
                    break
            except (RuntimeError, subprocess.TimeoutExpired):
                pass
            time.sleep(2)
        else:
            raise RuntimeError("Disposable emulator did not finish rebooting")
        adb("shell", "input", "keyevent", "82")
        # Starting non-root Shizuku remains the documented manual prerequisite.
        base = adb("shell", "pm", "path", "moe.shizuku.privileged.api").removeprefix("package:").removesuffix("/base.apk")
        if not re.fullmatch(r"/data/app/~~[A-Za-z0-9_=-]+/moe\.shizuku\.privileged\.api-[A-Za-z0-9_=-]+", base):
            raise RuntimeError("Unexpected Shizuku APK location")
        abi = adb("shell", "getprop", "ro.product.cpu.abi")
        architecture = {"x86_64": "x86_64", "arm64-v8a": "arm64"}.get(abi)
        if not architecture:
            raise RuntimeError("Unsupported emulator ABI")
        adb("shell", base + "/lib/" + architecture + "/libshizuku.so")
        deadline = time.monotonic() + (20 if args.mode=="boot-off" else 45)
        while time.monotonic() < deadline:
            if args.mode=="boot-off":
                if package+":clipboard" in processes():raise RuntimeError("Disabled boot resumption started the helper")
                time.sleep(0.5);continue
            if package + ":clipboard" in processes():
                observations["reconnected_after_shizuku_start"] = True
                # Helper existence alone does not prove the restored recorder state.
                time.sleep(1)
                restored=max(snapshots(),key=lambda p:struct.unpack(">q",p[8:16])[0])
                if (restored[28]==1)!=paused:raise RuntimeError("Pause state changed across reboot")
                observations["pause_preserved"]=paused
                if paused:
                    before_hash=adb("shell","run-as",package,"sha256sum","no_backup/history-a.bin","no_backup/history-b.bin")
                    adb("shell","am","start","-n",package+".test/app.cliphistory.SyntheticClipboardActivity","-a","app.cliphistory.test.WRITE_SYNTHETIC","--es","text","Synthetic paused reboot "+uuid.uuid4().hex)
                    time.sleep(2)
                    if before_hash!=adb("shell","run-as",package,"sha256sum","no_backup/history-a.bin","no_backup/history-b.bin"):raise RuntimeError("Paused reboot captured text")
                    observations["no_capture_while_paused"]=True
                else:can_capture()
                break
            time.sleep(0.5)
        else:
            if args.mode=="boot-off":observations["disabled_boot_resumption_respected"]=True
            else:raise RuntimeError("Boot recovery did not reconnect after Shizuku started")
    else:
        if not 60 <= args.seconds <= 3600:
            raise RuntimeError("Idle duration must be between one minute and one hour")
        helper = before.get(package + ":clipboard")
        if not helper:
            raise RuntimeError("Validation helper is not active")
        observations["helper_stat_before"] = adb("shell", "cat", f"/proc/{helper}/stat")
        observations["memory_before"] = adb("shell", "dumpsys", "meminfo", package).split("App Summary", 1)[-1]
        time.sleep(args.seconds)
        if processes().get(package + ":clipboard") != helper:
            raise RuntimeError("Helper changed during idle measurement")
        observations["helper_stat_after"] = adb("shell", "cat", f"/proc/{helper}/stat")
        observations["memory_after"] = adb("shell", "dumpsys", "meminfo", package).split("App Summary", 1)[-1]
        observations["wakeup_sources"] = "Not measured by this driver; do not infer zero wakeups"
    observations.update(after=processes(), elapsed_seconds=round(time.monotonic()-started, 3))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(observations, indent=2) + "\n", encoding="utf-8")
    print("PASS " + args.mode + ": " + str(args.output))


if __name__ == "__main__":
    main()
