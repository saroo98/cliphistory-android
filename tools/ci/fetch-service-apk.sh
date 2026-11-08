#!/usr/bin/env bash
# Download a pinned, official service-manager APK for emulator tests and verify its SHA-256.
#
#   bash tools/ci/fetch-service-apk.sh <shizuku|shizako> <output-dir>
#
# Prints the verified APK path. A file already in <output-dir> is reused only if its
# hash matches the pin. Nothing is installed or executed here, and a mismatching
# download is deleted rather than used. The pins are the projects' own GitHub
# release assets; they match the copies used for the 1.4.5 local qualification.
set -euo pipefail

name="${1:-}"
out="${2:-}"
case "$name" in
  shizuku)
    # RikkaApps/Shizuku release v13.6.0
    file='shizuku-v13.6.0.r1086.2650830c-release.apk'
    url="https://github.com/RikkaApps/Shizuku/releases/download/v13.6.0/$file"
    sha256='6e273ab0e991c4e79bc8b1bbb9b9dd739ccac1a8712a541a214078886b7b790f'
    ;;
  shizako)
    # cr1437/Shizako release zako3.17 (upstream GitHub build, not the F-Droid zako3.17-f build)
    file='shizako-zako3.17-release.apk'
    url="https://github.com/cr1437/Shizako/releases/download/zako3.17/$file"
    sha256='07c0c96df59875d0061b6b6f51e987c059df2114915518f244fb275d7be0505e'
    ;;
  *)
    echo "usage: $0 <shizuku|shizako> <output-dir>" >&2
    exit 2
    ;;
esac
[[ -n "$out" ]] || { echo "usage: $0 <shizuku|shizako> <output-dir>" >&2; exit 2; }

checksum() {
  if command -v sha256sum >/dev/null 2>&1; then sha256sum "$1" | awk '{print $1}'
  else shasum -a 256 "$1" | awk '{print $1}'; fi
}
# Git Bash's curl is a native Windows program: give it a Windows path. No-op elsewhere.
native() { if command -v cygpath >/dev/null 2>&1; then cygpath -m "$1"; else printf '%s\n' "$1"; fi; }

mkdir -p "$out"
target="$out/$file"
if [[ -f "$target" && "$(checksum "$target")" == "$sha256" ]]; then
  echo "$target"
  exit 0
fi
rm -f "$target" "$target.part"
# GitHub's release downloads sometimes answer 5xx for a few minutes (a run on 8 Nov 2026 lost
# 15 jobs to one minute of HTTP 504). curl retries quickly; the outer loop waits longer
# between rounds, so a short outage costs time instead of the whole run.
downloaded=0
for wait in 0 15 30 60 120; do
  if ((wait)); then echo "Download of $file failed; retrying in ${wait}s" >&2; sleep "$wait"; fi
  if curl --fail --silent --show-error --location --proto '=https' --proto-redir '=https' --tlsv1.2 \
    --retry 3 --retry-delay 5 --connect-timeout 20 --max-time 300 -o "$(native "$target.part")" "$url"; then
    downloaded=1; break
  fi
done
((downloaded)) || { rm -f "$target.part"; echo "Could not download $file from $url" >&2; exit 1; }
actual="$(checksum "$target.part")"
if [[ "$actual" != "$sha256" ]]; then
  rm -f "$target.part"
  echo "SHA-256 mismatch for $file: expected $sha256, got $actual. Refusing to use it." >&2
  exit 1
fi
mv "$target.part" "$target"
echo "$target"
