#!/usr/bin/env python3
"""Static delivery guards. This does NOT compile Android or inspect a built APK."""
from pathlib import Path
import re
import sys
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
MAIN = ROOT / "app/src/main"
ANDROID = "{http://schemas.android.com/apk/res/android}"
results: list[tuple[str, bool]] = []

def check(name: str, condition: bool) -> None:
    results.append((name, bool(condition)))
    print(f"{'PASS' if condition else 'FAIL'}  {name}")

def text(path: str) -> str:
    return (ROOT / path).read_text(encoding="utf-8")

manifest = ET.parse(MAIN / "AndroidManifest.xml").getroot()
app = manifest.find("application")
assert app is not None
permissions = {p.get(ANDROID + "name") for p in manifest.findall("uses-permission")}
check("source requests exactly the five reviewed permissions", permissions == {
    "moe.shizuku.manager.permission.API_V23", "android.permission.FOREGROUND_SERVICE",
    "android.permission.FOREGROUND_SERVICE_SPECIAL_USE", "android.permission.POST_NOTIFICATIONS",
    "android.permission.RECEIVE_BOOT_COMPLETED"})
check("source disables Android backup", app.get(ANDROID + "allowBackup") == "false" and app.get(ANDROID + "fullBackupContent") == "false")
check("source disables cleartext traffic", app.get(ANDROID + "usesCleartextTraffic") == "false")
providers = app.findall("provider")
check("Shizuku provider protected by system signature permission", len(providers) == 1 and providers[0].get(ANDROID + "permission") == "android.permission.INTERACT_ACROSS_USERS_FULL")
services = app.findall("service")
check("tile and private special-use recovery are the only Android services", len(services) == 2 and
      services[0].get(ANDROID + "permission") == "android.permission.BIND_QUICK_SETTINGS_TILE" and
      services[1].get(ANDROID + "exported") == "false" and services[1].get(ANDROID + "foregroundServiceType") == "specialUse")
receivers = app.findall("receiver")
check("only boot receiver is private and not direct-boot aware", len(receivers) == 1 and
      receivers[0].get(ANDROID + "exported") == "false" and receivers[0].get(ANDROID + "directBootAware") != "true")
activities=app.findall("activity")
check("only the launcher Activity is declared", len(activities)==1 and
      len(app.findall("activity/intent-filter/action"))==1)
for element in [app, *app.findall("activity"), *services, *receivers]:
    name = element.get(ANDROID + "name", "")
    if name.startswith("."):
        path = MAIN / "java/app/cliphistory" / (name[1:].replace(".", "/") + ".kt")
        check(f"manifest class exists: {name}", path.is_file())
for xml in sorted(MAIN.rglob("*.xml")):
    ET.parse(xml)
check("all source XML parses", True)

sources = list((MAIN / "java").rglob("*.kt"))
source = "\n".join(p.read_text(encoding="utf-8") for p in sources)
check("no unfinished production implementation markers", not re.search(r"\bTODO\s*\(|NotImplementedError|IMPLEMENT_ME|FIXME", source))
check("no runtime network client imports", not re.search(r"import\s+(java\.net|okhttp|io\.ktor|retrofit|com\.google\.firebase)\b", source))
check("no external process execution", not re.search(r"Runtime\.getRuntime\(\)\.exec|ProcessBuilder\(", source))
check("no Accessibility or keyboard components", "AccessibilityService" not in source and "InputMethodService" not in source)
check("no repeating clipboard polling mechanism", not re.search(r"Timer\(|scheduleAtFixedRate|while\s*\(true\)|Thread\.sleep", source))
privacy = text("app/src/main/java/app/cliphistory/ui/ScreenPrivacy.kt")
check("privacy defaults block screenshots with one conditional window policy", "FLAG_SECURE" in privacy and
      "clearFlags" in privacy and 'flag("allow_screenshots", false)' in text("app/src/main/java/app/cliphistory/ui/AppSettings.kt") and
      all("ScreenPrivacy.apply" in text("app/src/main/java/app/cliphistory/ui/" + name) for name in ["MainActivity.kt", "Ui.kt", "QuickCopyDialog.kt"]))
check("tile uses immutable PendingIntent and unlock", "FLAG_IMMUTABLE" in text("app/src/main/java/app/cliphistory/ui/ClipboardTileService.kt") and "unlockAndRun" in source)
check("normal app uses private no-backup files", "noBackupFilesDir" in text("app/src/main/java/app/cliphistory/client/PrivateHistory.kt"))
check("shell does not truncate or chmod private files", not re.search(r"ftruncate|Os\.chmod|setLength\(", "\n".join(p.read_text(encoding="utf-8") for p in (MAIN / "java/app/cliphistory/daemon").glob("*.kt"))))
bridge = text("app/src/main/java/app/cliphistory/daemon/PlatformClipboardBridge.kt")
check("system callback UID and identity are checked", "getCallingUid() != 1000" in bridge and "clearCallingIdentity" in bridge and "restoreCallingIdentity" in bridge)
check("sensitive flag handled before text extraction", bridge.index("if (sensitive)") < bridge.index("val raw"))
check("clipboard source is not coerced through content providers", "coerceToText(" not in bridge and "getItemAt(0).text" in bridge)
daemon = text("app/src/main/java/app/cliphistory/daemon/ClipboardUserService.kt")
aidl = text("app/src/main/aidl/app/cliphistory/ipc/IClipboardDaemon.aidl")
methods = re.findall(r"\b(?:Bundle|String|void)\s+(\w+)\([^;]*?\)\s*=\s*\d+\s*;", aidl)
check("all AIDL entrypoints implemented", all(re.search(r"override fun " + name + r"\(", daemon) for name in methods) and len(methods) == 17)
for name in methods:
    if name == "destroy":
        continue
    start = daemon.index("override fun " + name + "(")
    body = daemon[start:]
    next_method = body.find("override fun ", len("override fun "))
    if next_method >= 0:
        body = body[:next_method]
    check(f"owner-UID guard on {name}", "requireOwner()" in body)
check("Shizuku destroy transaction matches documented constant", "destroy() = 16777114" in aidl)
check("helper watches Shizuku server rather than UI lifetime", "IBinder shizukuServer" in aidl and "server.linkToDeath" in daemon and "leaseAlive=false" in daemon)
check("replacement/unlinked private data is handled", "attachedFileIds!=incomingIds" in daemon and "st_nlink" in text("app/src/main/java/app/cliphistory/daemon/FdAccess.kt"))
check("daemon uses bounded queue", "ArrayBlockingQueue<Runnable>(256)" in daemon)
check("owner stop gates clipboard reads and serial commits", "if(!canRead())return" in bridge and
      "if(!ownerStop.check())" in daemon and "FLAG_STOPPED" in text("app/src/main/java/app/cliphistory/daemon/OwnerStopGuard.kt") and
      "REASON_USER_REQUESTED" in text("app/src/main/java/app/cliphistory/daemon/OwnerStopGuard.kt"))
check("recovery retries are bounded and user-stop gated", "longArrayOf(1000,3000,10_000)" in source and
      "mayRecover(options,boot" in source and re.search(r'putBoolean\("explicit_stop",\s*true\)', source) is not None)
check("quick tile uses the platform QS dialog without a task launch or overlay permission",
      "showDialog(dialog)" in source and "QuickCopyActivity" not in source and
      "android.service.quicksettings.ACTIVE_TILE" not in text("app/src/main/AndroidManifest.xml") and
      "SYSTEM_ALERT_WINDOW" not in permissions)
check("IPC history is paged", "count in 1..40" in daemon and "SearchPreview.snippet" in daemon and "maxLength:Int=180" in text("app/src/main/java/app/cliphistory/core/SearchPreview.kt"))
check("self-test uses tested probe tracker", "ProbeTracker()" in daemon and "probe.match" in daemon)
build = text("app/build.gradle.kts")
check("compile and target API 37", "compileSdk = 37" in build and "targetSdk = 37" in build)
check("release is not marked debuggable", "isDebuggable = false" in build)
check("build pins Shizuku dependencies", build.count(":13.1.5") == 2)
check("Windows script does not silently accept SDK licences", "--licenses" in text("tools/build-windows.ps1") and not re.search(r"yes\s*\||'y'\s*\|", text("tools/build-windows.ps1")))
check("Windows build gates actual APK signature and permissions", "apksigner.bat" in text("tools/build-windows.ps1") and "Unexpected permission in built APK" in text("tools/build-windows.ps1"))
for path in ("README.md", "START_HERE.md", "BUILDING.md", "DEVICE_TESTS.md", "SECURITY.md", "VERIFICATION.md", "LICENSE", "THIRD_PARTY_NOTICES.md", "docs/SOURCES.md", "BUILD_WINDOWS.cmd", "gradlew", "gradlew.bat"):
    check(f"delivery file: {path}", (ROOT / path).is_file())
if "--distribution" in sys.argv:
    check("no signing material distributed", not (ROOT / ".signing").exists())
check("APK includes full runtime notices", (MAIN / "assets/licenses/NOTICES.txt").is_file() and "Permission is hereby granted" in text("app/src/main/assets/licenses/NOTICES.txt") and "END OF TERMS AND CONDITIONS" in text("app/src/main/assets/licenses/NOTICES.txt"))
check("official Gradle wrapper is checksum pinned", "distributionSha256Sum=" in text("gradle/wrapper/gradle-wrapper.properties") and (ROOT / "gradle/wrapper/gradle-wrapper.jar").is_file())
check("F-Droid build metadata is supplied", (ROOT / "fdroid/metadata/app.cliphistory.yml").is_file())
failed = [name for name, ok in results if not ok]
print(f"RESULT {len(results)-len(failed)}/{len(results)} static source checks passed")
print("Android build, merged-manifest, APK and physical-device tests are NOT implied by these checks.")
sys.exit(1 if failed else 0)
