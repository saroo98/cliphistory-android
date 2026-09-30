"""Package the 1.1.0 source, signed APK and selected evidence without local keys/caches."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib

root = Path(__file__).resolve().parents[1]
apk = root / "ClipHistory-1.1.0.apk"
archive = root.parent / "ClipHistory-1.1.0-complete.zip"
assert apk.is_file(), "Build and verify the signed release before packaging"
files = [p for p in root.iterdir() if p.is_file() and (
    p.suffix == ".md" or p.name in {
        ".gitignore", ".gitattributes", "LICENSE", "BUILD_WINDOWS.cmd",
        "build.gradle.kts", "settings.gradle.kts", "gradle.properties",
        "gradlew", "gradlew.bat", "apk-permissions.txt",
        apk.name, apk.name + ".sha256",
    }
)]
files.append(root / "app/build.gradle.kts")
for folder in ("app/src", "tools", "docs", "reports/screenshots", "reports/screenshots-large-font"):
    files.extend(p for p in (root / folder).rglob("*") if p.is_file() and "__pycache__" not in p.parts)
for pattern in (
    "redesign-final-build.txt", "redesign-lint-release.txt", "redesign-signature.txt",
    "redesign-permissions.txt", "redesign-badging.txt", "TEST-*.xml",
    "ui-instrumentation.txt", "ui-large-font.txt", "release-*.txt", "release-*.xml",
    "redesign-source-review.txt",
):
    files.extend((root / "reports").glob(pattern))
files = sorted(set(files))
for path in files:
    relative = path.relative_to(root)
    assert not set(relative.parts) & {".signing", ".tools", ".gradle", ".kotlin", "build"}
    assert path.suffix.lower() not in {".jks", ".keystore", ".pem", ".key"}
    assert path.name not in {"password.txt", "local.properties"}
with ZipFile(archive, "w", ZIP_DEFLATED) as output:
    for path in files:
        output.write(path, Path("ClipHistory") / path.relative_to(root))
with ZipFile(archive) as output:
    assert output.testzip() is None
    assert output.read("ClipHistory/" + apk.name) == apk.read_bytes()
digest = hashlib.sha256(archive.read_bytes()).hexdigest()
archive.with_suffix(archive.suffix + ".sha256").write_text(f"{digest}  {archive.name}\n", encoding="ascii")
print(f"Verified {len(files)} files: {archive}")
print(f"SHA-256 {digest}")
