#!/usr/bin/env bash
# End-to-end device checks for the isolated validation build on ONE disposable emulator.
#
# Used by .github/workflows/device-tests.yml inside ReactiveCircus/android-emulator-runner,
# and runnable locally against an emulator you own. Physical devices are refused.
#
#   bash tools/ci/emulator-e2e.sh --serial emulator-5554 --service shizuku-adb --tier smoke
#
# Flow: confirm the target is an emulator; install app.cliphistory.validation and its test
# APK; download the pinned official service APK and verify its SHA-256; start the service
# from `adb shell` with the manager's own native starter; authorize the validation app
# through the manager's real prompt (tools/ci/authorize-service.py); run
# UiSmokeInstrumentation suites; keep per-suite output, failure diagnostics, logcat and a
# Markdown summary under --output. Only synthetic data is written, and only on the emulator.
#
# Options (defaults in brackets):
#   --serial emulator-N    target emulator [$ANDROID_SERIAL]
#   --service NAME         shizuku-adb | shizako-adb | shizako-root [shizuku-adb]
#                          shizako-root starts Shizako after `adb root`, so its server runs as
#                          UID 0. The app must refuse it (Android clears the clipboard when root
#                          reads it): this mode passes when the refusal is shown, no recorder
#                          helper starts and the clipboard is not cleared. Suites are skipped.
#   --tier smoke|full      suite selection [smoke]
#   --suites "a b c"       explicit suite list instead of the tier's list
#   --apk / --test-apk     validation APKs [app/build/outputs/apk/{debug,androidTest/debug}/...]
#   --service-cache DIR    where the verified service APK is kept [<output>/service-apk]
#   --output DIR           results [reports/ci/device-<serial>-<service>]
#   --expect-avd NAME      also require this AVD name (recommended for local runs)
#   --suite-timeout SEC    per-suite limit [600]
#   --stop-on-failure      stop after the first failed suite (default: run all, report all)
# The full tier includes sensitive-live: run it on a headless (-no-window) emulator, or with the
# emulator's host clipboard sharing off, because a windowed emulator's sharing strips the
# sensitive clip flag.
# Environment: ADB (adb binary), PYTHON (Python 3 for the authorization helper), and
# E2E_SERVICE / E2E_TIER / E2E_SUITES as defaults for --service / --tier / --suites.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
PACKAGE='app.cliphistory.validation'
RUNNER="$PACKAGE.test/app.cliphistory.UiSmokeInstrumentation"
KNOWN_MANAGERS=(moe.shizuku.privileged.api com.churan.shizako eu.darken.porter com.hamondev.shevery af.shizuku.plus.api)

# Ordered so the suites that need the freshly authorized live service run first.
SMOKE_SUITES=(live paused-connection settings encryption)
FULL_SUITES=(live paused-connection sensitive-live html-port-live command-errors recovery-failures
  recovery-pending boot-identity helper-failure settings encryption sensitive-ui html-port quick-errors
  audit-navigation audit-diagnostics regression storage-threading recovery privacy onboarding
  customization accessibility ui)

SERIAL="${ANDROID_SERIAL:-}"
SERVICE="${E2E_SERVICE:-shizuku-adb}"
TIER="${E2E_TIER:-smoke}"
SUITES="${E2E_SUITES:-}"
APK="$ROOT/app/build/outputs/apk/debug/app-debug.apk"
TEST_APK="$ROOT/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
CACHE_DIR=''
OUTPUT=''
EXPECT_AVD=''
SUITE_TIMEOUT=600
STOP_ON_FAILURE=0

usage() { awk 'NR > 1 && /^#/ { sub(/^# ?/, ""); print; next } NR > 1 { exit }' "${BASH_SOURCE[0]}"; exit 2; }
need() { [[ $# -ge 2 && -n "$2" ]] || { echo "Missing value for $1" >&2; exit 2; }; }
while (($#)); do
  case "$1" in
    --serial) need "$@"; SERIAL="$2"; shift 2 ;;
    --service) need "$@"; SERVICE="$2"; shift 2 ;;
    --tier) need "$@"; TIER="$2"; shift 2 ;;
    --suites) need "$@"; SUITES="$2"; shift 2 ;;
    --apk) need "$@"; APK="$2"; shift 2 ;;
    --test-apk) need "$@"; TEST_APK="$2"; shift 2 ;;
    --service-cache) need "$@"; CACHE_DIR="$2"; shift 2 ;;
    --output) need "$@"; OUTPUT="$2"; shift 2 ;;
    --expect-avd) need "$@"; EXPECT_AVD="$2"; shift 2 ;;
    --suite-timeout) need "$@"; SUITE_TIMEOUT="$2"; shift 2 ;;
    --stop-on-failure) STOP_ON_FAILURE=1; shift ;;
    -h|--help) usage ;;
    *) echo "Unknown argument: $1" >&2; usage ;;
  esac
done

[[ "$SERIAL" =~ ^emulator-[0-9]+$ ]] || { echo "Refusing serial '$SERIAL': only emulator-NNNN targets are accepted." >&2; exit 2; }
[[ "$SUITE_TIMEOUT" =~ ^[0-9]+$ ]] || { echo "--suite-timeout must be a number of seconds" >&2; exit 2; }
case "$TIER" in smoke|full) ;; *) echo "Unknown tier: $TIER" >&2; exit 2 ;; esac
case "$SERVICE" in
  shizuku-adb) MANAGER=moe.shizuku.privileged.api; SERVER=shizuku_server; FETCH=shizuku; MODE=shell ;;
  shizako-adb) MANAGER=com.churan.shizako; SERVER=shizako_server; FETCH=shizako; MODE=shell ;;
  shizako-root) MANAGER=com.churan.shizako; SERVER=shizako_server; FETCH=shizako; MODE=root ;;
  *) echo "Unknown service: $SERVICE" >&2; exit 2 ;;
esac
EXPECTED_UID=2000
[[ "$MODE" == root ]] && EXPECTED_UID=0

if [[ -n "$SUITES" ]]; then read -r -a suites <<<"$SUITES"
elif [[ "$TIER" == full ]]; then suites=("${FULL_SUITES[@]}")
else suites=("${SMOKE_SUITES[@]}"); fi
for suite in "${suites[@]}"; do
  [[ "$suite" =~ ^[a-z][a-z0-9-]*$ ]] || { echo "Invalid suite name: $suite" >&2; exit 2; }
  [[ "$suite" != phone-* ]] || { echo "Refusing $suite: phone-* suites are for a physical phone." >&2; exit 2; }
done

# Git Bash on Windows: keep device paths such as /data/local/tmp unconverted and hand
# native Windows paths to adb.exe / python.exe. No effect on Linux.
export MSYS_NO_PATHCONV=1 MSYS2_ARG_CONV_EXCL='*'
native() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s\n' "$1"; fi; }
absolute() { local d; d="$(cd "$(dirname "$1")" && pwd)" || return 1; printf '%s/%s\n' "$d" "$(basename "$1")"; }

if [[ -z "${ADB:-}" ]]; then
  if command -v adb >/dev/null 2>&1; then ADB=adb
  elif [[ -n "${ANDROID_HOME:-}" && -x "$ANDROID_HOME/platform-tools/adb" ]]; then ADB="$ANDROID_HOME/platform-tools/adb"
  else echo "adb not found; set ADB=/path/to/adb" >&2; exit 2; fi
fi
if [[ -z "${PYTHON:-}" ]]; then
  if command -v python3 >/dev/null 2>&1; then PYTHON=python3; else PYTHON=python; fi
fi
for file in "$APK" "$TEST_APK"; do [[ -f "$file" ]] || { echo "Missing APK: $file" >&2; exit 2; }; done
APK="$(absolute "$APK")"
TEST_APK="$(absolute "$TEST_APK")"
OUTPUT="${OUTPUT:-$ROOT/reports/ci/device-$SERIAL-$SERVICE}"
mkdir -p "$OUTPUT/suites"
OUTPUT="$(cd "$OUTPUT" && pwd)"
CACHE_DIR="${CACHE_DIR:-$OUTPUT/service-apk}"
: > "$OUTPUT/e2e.log"
printf 'suite\tresult\tseconds\tdetail\n' > "$OUTPUT/suites.tsv"

log() { printf '[%s] %s\n' "$(date -u +%H:%M:%S)" "$*" | tee -a "$OUTPUT/e2e.log"; }
shell() { "$ADB" -s "$SERIAL" shell "$@" | tr -d '\r'; }
SETUP_ERROR=''
fail_setup() {
  SETUP_ERROR="$*"
  log "SETUP FAILED: $*"
  capture_screen "setup-failure-screen.png"
  exit 1
}
capture_screen() {
  "$ADB" -s "$SERIAL" exec-out screencap -p > "$OUTPUT/$1" 2>/dev/null || rm -f "$OUTPUT/$1"
}

LOGCAT_PID=''
start_logcat() {
  "$ADB" -s "$SERIAL" logcat -v threadtime >> "$OUTPUT/logcat.txt" 2>&1 &
  LOGCAT_PID=$!
}
stop_logcat() {
  if [[ -n "$LOGCAT_PID" ]]; then
    kill "$LOGCAT_PID" 2>/dev/null || true
    wait "$LOGCAT_PID" 2>/dev/null || true
    LOGCAT_PID=''
  fi
}

API='?'; RELEASE='?'; AVD_NAME='?'; FINGERPRINT='?'; MANAGER_VERSION='?'
PASSED=0; FAILED=0
write_summary() {
  local status="$1" summary="$OUTPUT/summary.md"
  {
    echo "### Device tests: API $API (Android $RELEASE), $SERVICE, $TIER"
    echo
    echo "AVD \`$AVD_NAME\`, fingerprint \`$FINGERPRINT\`, manager \`$MANAGER\` ($MANAGER_VERSION), recorder UID expected $EXPECTED_UID."
    echo
    if [[ -n "$SETUP_ERROR" ]]; then echo "**Setup failed:** $SETUP_ERROR"; echo; fi
    echo "| Suite | Result | Seconds | Detail |"
    echo "|---|---|---|---|"
    tail -n +2 "$OUTPUT/suites.tsv" | while IFS=$'\t' read -r name result seconds detail; do
      echo "| $name | $result | $seconds | ${detail//|/\\|} |"
    done
    echo
    echo "Passed $PASSED, failed $FAILED, exit status $status."
  } > "$summary"
  if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then cat "$summary" >> "$GITHUB_STEP_SUMMARY"; fi
}
finish() {
  local status=$?
  set +e
  stop_logcat
  write_summary "$status"
  log "Results in $OUTPUT"
  exit "$status"
}
trap finish EXIT

# On a slow emulator the launcher can stop responding. Its system dialog then takes input
# focus, which blocks the test app's clipboard access and UI checks. Before each suite, close
# such a dialog by stopping the unresponsive app, unless it is the app under test.
dismiss_foreign_anr() {
  local focus pkg
  focus="$(shell dumpsys window 2>/dev/null | grep -m1 'mCurrentFocus=' || true)"
  [[ "$focus" == *'Application Not Responding: '* ]] || return 0
  pkg="$(sed -n 's/.*Application Not Responding: \([A-Za-z0-9._]*\).*/\1/p' <<<"$focus")"
  [[ -n "$pkg" && "$pkg" != "$PACKAGE" && "$pkg" != "$PACKAGE.test" ]] || return 0
  log "Closing a 'not responding' dialog for $pkg before the next suite"
  shell am force-stop "$pkg" >/dev/null 2>&1 || true
  sleep 2
}

run_suite() {
  local suite="$1"; shift
  local started=$SECONDS rc=0 output result detail seconds
  dismiss_foreign_anr
  log "Suite $suite: running"
  output="$(timeout "$SUITE_TIMEOUT" "$ADB" -s "$SERIAL" shell am instrument -w -e suite "$suite" \
    -e theme light -e tilePage 4 "$@" "$RUNNER" 2>&1 | tr -d '\r')" || rc=$?
  printf '%s\n' "$output" > "$OUTPUT/suites/$suite.txt"
  seconds=$((SECONDS - started))
  # Same failure markers and completion summary as tools/test-android-matrix.py. Without -r,
  # `am instrument` prints the runner's result stream and no INSTRUMENTATION_CODE line, so
  # completion is proven by the final "PASS <n> ... assertions" summary, never by status lines.
  if ((rc == 124)); then
    result=FAIL; detail="timed out after ${SUITE_TIMEOUT}s"
    shell am force-stop "$PACKAGE" >/dev/null 2>&1 || true
  elif ((rc != 0)); then
    result=FAIL; detail="adb exited with status $rc"
  elif grep -Eq '^FAIL([[:space:]]|:)|shortMsg=|Process crashed|INSTRUMENTATION_FAILED' <<<"$output"; then
    result=FAIL; detail="$(grep -Em1 '^FAIL|shortMsg=|Process crashed|INSTRUMENTATION_FAILED' <<<"$output" | cut -c1-240)"
  else
    detail="$(grep -E '^PASS:?[[:space:]]+[0-9]+.*assertions' <<<"$output" | tail -n 1 || true)"
    if [[ -z "$detail" && "$suite" == fork-* ]]; then
      # The fork-* suites return a single PASS sentence instead of an assertion count.
      detail="$(grep -Ex 'PASS [A-Z].*\.' <<<"$output" | tail -n 1 || true)"
    fi
    if [[ -n "$detail" ]]; then result=PASS; else result=FAIL; detail='no completion summary (PASS <n> ... assertions)'; fi
  fi
  printf '%s\t%s\t%s\t%s\n' "$suite" "$result" "$seconds" "${detail//$'\t'/ }" >> "$OUTPUT/suites.tsv"
  log "Suite $suite: $result in ${seconds}s - $detail"
  if [[ "$result" == PASS ]]; then
    PASSED=$((PASSED + 1))
    return 0
  fi
  FAILED=$((FAILED + 1))
  # Preserve the failure state before anything else touches the emulator.
  shell logcat -d -b crash > "$OUTPUT/suites/$suite-crash.txt" 2>&1 || true
  shell dumpsys window > "$OUTPUT/suites/$suite-windows.txt" 2>&1 || true
  shell dumpsys activity activities > "$OUTPUT/suites/$suite-activities.txt" 2>&1 || true
  shell dumpsys activity exit-info "$PACKAGE" > "$OUTPUT/suites/$suite-exit-info.txt" 2>&1 || true
  capture_screen "suites/$suite-screen.png"
  return 1
}

suite_or_stop() {
  if ! run_suite "$@" && ((STOP_ON_FAILURE)); then
    log "Stopping after the first failure (--stop-on-failure)"
    exit 1
  fi
}

# Android 17 images can report boot completed before the package service accepts installs
# ("Failure calling service package: Broken pipe"), so wait for it and retry only those errors.
package_service_ready() { [[ "$(shell pm path android 2>/dev/null | head -n 1)" == package:* ]]; }
install_apk() {
  local out='' attempt
  for attempt in 1 2 3 4; do
    for _ in $(seq 1 30); do package_service_ready && break; sleep 2; done
    out="$("$ADB" -s "$SERIAL" install -r "$(native "$1")" 2>&1 | tr -d '\r')" || true
    printf '%s (attempt %s): %s\n' "$(basename "$1")" "$attempt" "$out" >> "$OUTPUT/install.txt"
    grep -q '^Success' <<<"$out" && return 0
    grep -qE "Broken pipe|Can't find service|Failure calling service|device offline" <<<"$out" || break
    sleep 5
  done
  fail_setup "Installing $(basename "$1") did not report Success: $out"
}

set_adbd_mode() {
  local wanted="$1" uid=''
  uid="$(shell id -u)"
  if [[ "$wanted" == root && "$uid" != 0 ]]; then
    stop_logcat
    "$ADB" -s "$SERIAL" root >> "$OUTPUT/e2e.log" 2>&1 || true
  elif [[ "$wanted" == shell && "$uid" == 0 ]]; then
    stop_logcat
    "$ADB" -s "$SERIAL" unroot >> "$OUTPUT/e2e.log" 2>&1 || true
  else
    return 0
  fi
  for _ in $(seq 1 30); do
    sleep 2
    uid="$(timeout 20 "$ADB" -s "$SERIAL" shell id -u 2>/dev/null | tr -d '\r' || true)"
    [[ -n "$uid" ]] && break
  done
  start_logcat
}

# --- Target identity -------------------------------------------------------------------
log "Target $SERIAL, service $SERVICE, tier $TIER, suites: ${suites[*]}"
if [[ " ${suites[*]} " == *' sensitive-live '* ]]; then
  # A windowed emulator with host clipboard sharing on re-posts clips without
  # android.content.extra.IS_SENSITIVE. Headless (-no-window) emulators keep the flag.
  log "Note: sensitive-live needs a headless emulator or host clipboard sharing off; otherwise expect EMULATOR_CLIPBOARD_SYNC_STRIPS_SENSITIVE_FLAG"
fi
timeout 180 "$ADB" -s "$SERIAL" wait-for-device || fail_setup "$SERIAL did not appear"
deadline=$((SECONDS + 600))
until [[ "$(shell getprop sys.boot_completed 2>/dev/null || true)" == 1 ]]; do
  ((SECONDS < deadline)) || fail_setup "Emulator did not finish booting"
  sleep 3
done
qemu="$(shell getprop ro.boot.qemu)"
[[ -n "$qemu" ]] || qemu="$(shell getprop ro.kernel.qemu)"
[[ "$qemu" == 1 ]] || fail_setup "Target does not identify as an emulator (ro.boot.qemu / ro.kernel.qemu)"
AVD_NAME="$("$ADB" -s "$SERIAL" emu avd name 2>/dev/null | tr -d '\r' | head -n 1 || true)"
[[ -n "$AVD_NAME" ]] || fail_setup "The emulator console did not report an AVD name"
if [[ -n "$EXPECT_AVD" && "$AVD_NAME" != "$EXPECT_AVD" ]]; then
  fail_setup "AVD is '$AVD_NAME', expected '$EXPECT_AVD'"
fi
start_logcat
API="$(shell getprop ro.build.version.sdk)"
RELEASE="$(shell getprop ro.build.version.release)"
FINGERPRINT="$(shell getprop ro.build.fingerprint)"
ABI="$(shell getprop ro.product.cpu.abi)"
case "$ABI" in
  x86_64) LIBDIR=x86_64 ;;
  arm64-v8a) LIBDIR=arm64 ;;
  *) fail_setup "Unsupported emulator ABI: $ABI" ;;
esac
{
  echo "serial=$SERIAL"; echo "avd=$AVD_NAME"; echo "api=$API"
  echo "sdk_full=$(shell getprop ro.build.version.sdk_full)"; echo "release=$RELEASE"
  echo "fingerprint=$FINGERPRINT"; echo "abi=$ABI"; echo "service=$SERVICE"; echo "tier=$TIER"
  echo "apk_sha256=$(sha256sum "$APK" | awk '{print $1}')"
  echo "test_apk_sha256=$(sha256sum "$TEST_APK" | awk '{print $1}')"
} > "$OUTPUT/device.txt"

# --- Validation app ----------------------------------------------------------------------
VERSION_NAME="$(sed -n 's/^[[:space:]]*versionName[[:space:]]*=[[:space:]]*"\([^"]*\)".*/\1/p' "$ROOT/app/build.gradle.kts" | head -n 1)-validation"
log "Installing $PACKAGE $VERSION_NAME and its test APK"
install_apk "$APK"
install_apk "$TEST_APK"
info="$(shell dumpsys package "$PACKAGE")"
grep -q 'DEBUGGABLE' <<<"$info" || fail_setup "$PACKAGE is not the debuggable validation build"
grep -Eq "^[[:space:]]*versionName=${VERSION_NAME//./\\.}[[:space:]]*$" <<<"$info" ||
  fail_setup "Installed $PACKAGE is not version $VERSION_NAME"
echo "version_name=$VERSION_NAME" >> "$OUTPUT/device.txt"

# --- Service manager -----------------------------------------------------------------------
installed="$(shell pm list packages)"
for other in "${KNOWN_MANAGERS[@]}"; do
  if [[ "$other" != "$MANAGER" ]] && grep -qx "package:$other" <<<"$installed"; then
    fail_setup "$other is installed; use a fresh emulator so only $MANAGER can supply the service"
  fi
done
SERVICE_APK="$(bash "$ROOT/tools/ci/fetch-service-apk.sh" "$FETCH" "$CACHE_DIR")" ||
  fail_setup "The pinned $FETCH APK could not be downloaded and verified"
log "Installing verified $(basename "$SERVICE_APK")"
install_apk "$SERVICE_APK"
MANAGER_VERSION="$(shell dumpsys package "$MANAGER" | grep -Eo 'versionName=[^ ]+|versionCode=[0-9]+' | sort -u | tr '\n' ' ' || true)"
MANAGER_VERSION="${MANAGER_VERSION% }"
{ echo "manager=$MANAGER"; echo "manager_version=$MANAGER_VERSION"
  echo "service_apk_sha256=$(sha256sum "$SERVICE_APK" | awk '{print $1}')"; } >> "$OUTPUT/device.txt"

running="$(shell ps -A -o PID,UID,NAME | awk 'NR > 1 && ($3 == "shizuku_server" || $3 == "shizako_server")')"
[[ -z "$running" ]] || fail_setup "A service is already running before this test: $running"

if [[ "$TIER" == full && -z "$SUITES" && "$MANAGER" == com.churan.shizako ]]; then
  # Installed but stopped fork: the app must recognize it and offer its shortcut.
  suite_or_stop fork-stopped -e expected_manager "$MANAGER"
fi

# --- Start the service from adb shell with the manager's own starter ------------------------
if [[ "$MODE" == root ]]; then set_adbd_mode root; else set_adbd_mode shell; fi
adbd_uid="$(shell id -u)"
[[ "$adbd_uid" == "$EXPECTED_UID" ]] || fail_setup "adb shell runs as UID $adbd_uid, expected $EXPECTED_UID for $SERVICE"
APK_PATH="$(shell pm path "$MANAGER" | sed -n 's/^package://p' | grep '/base\.apk$' | head -n 1 || true)"
[[ "$APK_PATH" =~ ^/data/app/[A-Za-z0-9._~=/-]+/base\.apk$ ]] || fail_setup "Unexpected $MANAGER APK path: $APK_PATH"
STARTER="${APK_PATH%/base.apk}/lib/$LIBDIR/libshizuku.so"
shell ls "$STARTER" >/dev/null 2>&1 || fail_setup "Starter not found at $STARTER (native libraries not extracted?)"
if [[ "$MANAGER" == com.churan.shizako ]]; then
  starter_args=("$STARTER" "--apk=$APK_PATH")
else
  starter_args=("$STARTER")
fi
log "Starting $SERVER: ${starter_args[*]}"
start_rc=0
timeout 90 "$ADB" -s "$SERIAL" shell "${starter_args[@]}" 2>&1 | tr -d '\r' > "$OUTPUT/service-start.txt" || start_rc=$?
cat "$OUTPUT/service-start.txt" >> "$OUTPUT/e2e.log"
((start_rc == 0)) || fail_setup "The $MANAGER starter exited with status $start_rc"
server=''
for _ in $(seq 1 30); do
  mapfile -t servers < <(shell ps -A -o PID,UID,NAME | awk -v name="$SERVER" 'NR > 1 && $3 == name {print $1 " " $2}')
  ((${#servers[@]} <= 1)) || fail_setup "More than one $SERVER process: ${servers[*]}"
  if ((${#servers[@]} == 1)); then server="${servers[0]}"; break; fi
  sleep 1
done
[[ -n "$server" ]] || fail_setup "$SERVER did not start"
[[ "${server#* }" == "$EXPECTED_UID" ]] || fail_setup "$SERVER runs as UID ${server#* }, expected $EXPECTED_UID"
log "$SERVER is running: PID ${server% *}, UID ${server#* }"
echo "server=$SERVER pid=${server% *} uid=${server#* }" >> "$OUTPUT/device.txt"

if [[ "$TIER" == full && -z "$SUITES" && "$SERVICE" == shizako-adb ]]; then
  # Running fork without the official launcher and before any grant: reaches the permission state.
  suite_or_stop fork-discovery
fi

# --- Root mode: the app must refuse the server and leave the clipboard alone ---------------------
if [[ "$MODE" == root ]]; then
  # Android clears the clipboard when root reads it as another package, so the app refuses
  # UID 0 servers. Check the refusal, that no recorder helper starts and that nothing was cleared.
  log "Checking that $PACKAGE refuses the root $SERVER"
  "$PYTHON" "$(native "$ROOT/tools/ci/authorize-service.py")" --adb "$(native "$ADB")" --serial "$SERIAL" \
    --manager-package "$MANAGER" --expected-uid 0 --expect-root-refusal \
    --output "$(native "$OUTPUT/authorization.json")" 2>&1 | tee -a "$OUTPUT/e2e.log" ||
    fail_setup "The app did not refuse the root server cleanly (see authorization.json)"
  sleep 2
  if grep -q 'Could not grant permission to primary clip. Clearing clipboard' "$OUTPUT/logcat.txt" 2>/dev/null; then
    fail_setup "Android cleared the clipboard during the root check: a root read reached the clipboard service"
  fi
  log "Root server refused with the root message; no helper started and the clipboard was not cleared"
  printf 'root-refusal\tPASS\t-\tmessage shown, no recorder helper, clipboard not cleared\n' >> "$OUTPUT/suites.tsv"
  PASSED=$((PASSED + 1))
  exit 0
fi

# --- Authorize through the manager's real prompt ----------------------------------------------
log "Authorizing $PACKAGE through the $MANAGER prompt"
"$PYTHON" "$(native "$ROOT/tools/ci/authorize-service.py")" --adb "$(native "$ADB")" --serial "$SERIAL" \
  --manager-package "$MANAGER" --expected-uid "$EXPECTED_UID" \
  --output "$(native "$OUTPUT/authorization.json")" 2>&1 | tee -a "$OUTPUT/e2e.log" ||
  fail_setup "The validation app could not be authorized with $MANAGER (see authorization.json)"

# --- Suites --------------------------------------------------------------------------------------
for suite in "${suites[@]}"; do
  suite_or_stop "$suite"
done

# Synthetic Activity drawings written by the suites (files/ui-evidence/*.png).
"$ADB" -s "$SERIAL" exec-out run-as "$PACKAGE" tar -cf - files/ui-evidence > "$OUTPUT/ui-evidence.tar" 2>/dev/null ||
  rm -f "$OUTPUT/ui-evidence.tar"
((FAILED == 0)) || { log "$FAILED suite(s) failed"; exit 1; }
log "All $PASSED suite(s) passed"
