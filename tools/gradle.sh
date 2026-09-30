#!/usr/bin/env bash
# Text bootstrap, not the official Gradle wrapper JAR. Requires Java 17+, curl and unzip.
set -euo pipefail
root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
version=9.3.1
tools="$root/.tools"
zip="$tools/downloads/gradle-$version-bin.zip"
launcher="$tools/gradle-$version/bin/gradle"
checksum() { if command -v sha256sum >/dev/null; then sha256sum "$1" | awk '{print $1}'; else shasum -a 256 "$1" | awk '{print $1}'; fi; }
if [[ ! -x "$launcher" ]]; then
  command -v curl >/dev/null || { echo 'curl is required.' >&2; exit 1; }
  command -v unzip >/dev/null || { echo 'unzip is required.' >&2; exit 1; }
  mkdir -p "$tools/downloads"
  url="https://services.gradle.org/distributions/gradle-$version-bin.zip"
  curl --fail --location --proto '=https' --tlsv1.2 --connect-timeout 20 --max-time 120 "$url.sha256" -o "$zip.sha256"
  expected="$(tr -d '[:space:]' < "$zip.sha256")"
  [[ "$expected" =~ ^[a-fA-F0-9]{64}$ ]] || { echo 'Invalid Gradle checksum response.' >&2; exit 1; }
  if [[ ! -f "$zip" ]] || [[ "$(checksum "$zip")" != "$expected" ]]; then
    curl --fail --location --proto '=https' --tlsv1.2 --connect-timeout 20 --max-time 900 "$url" -o "$zip.part"
    [[ "$(checksum "$zip.part")" == "$expected" ]] || { echo 'Gradle checksum mismatch; not executing.' >&2; exit 1; }
    mv "$zip.part" "$zip"
  fi
  unzip -q -o "$zip" -d "$tools"
  chmod +x "$launcher"
fi
cd "$root"
exec "$launcher" "$@"
