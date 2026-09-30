#!/usr/bin/env python3
"""Package reviewed source and a verified APK without keys, caches or device data."""
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_DEFLATED
import hashlib
import io
import re
import subprocess

root=Path(__file__).resolve().parents[1]
version=re.search(r'versionName\s*=\s*"([^"]+)"',(root/'app/build.gradle.kts').read_text(encoding='utf-8')).group(1)
apk=root/f'ClipHistory-{version}.apk'
assert apk.is_file() and Path(str(apk)+'.sha256').is_file(), 'Verify and checksum the release APK first'
assert Path(str(apk)+'.sha256').read_text(encoding='ascii').split()[0] == hashlib.sha256(apk.read_bytes()).hexdigest(), 'APK checksum does not match'
archive=root.parent/f'ClipHistory-{version}-complete.zip'
files=[p for p in root.iterdir() if p.is_file() and (p.suffix=='.md' or p.name in {'.gitignore','.gitattributes','LICENSE','BUILD_WINDOWS.cmd','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat',apk.name,apk.name+'.sha256'})]
files.append(root/'app/build.gradle.kts')
for folder in ('app/src','gradle','LICENSES','tools','docs','fastlane','fdroid/metadata','fdroid/submission'):
    files.extend(p for p in (root/folder).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
files=sorted(set(files))
canonical=None
if (root/'.git').exists():
    assert subprocess.run(['git','diff','--quiet','HEAD','--'],cwd=root).returncode == 0, 'Commit reviewed source changes before packaging'
    # Git blobs retain canonical line endings, including the runtime licence asset.
    canonical=ZipFile(io.BytesIO(subprocess.check_output(['git','archive','--format=zip','HEAD'],cwd=root)))
for path in files:
    relative=path.relative_to(root)
    assert not set(relative.parts)&{'.signing','.tools','.gradle','.kotlin','.git','build'}
    assert path.suffix.lower() not in {'.jks','.keystore','.pem','.key'}
    assert path.name not in {'password.txt','local.properties'}
with ZipFile(archive,'w',ZIP_DEFLATED) as z:
    for path in files:
        relative=path.relative_to(root).as_posix()
        data=path.read_bytes() if canonical is None or path == apk or path.name == apk.name+'.sha256' else canonical.read(relative)
        if canonical is None and path.suffix in {'.kt','.java','.kts','.xml','.aidl','.md','.properties','.txt','.py','.yml','.sh'}:
            data=data.replace(b'\r\n',b'\n')
        entry=ZipInfo('ClipHistory/'+relative, date_time=(1980,1,1,0,0,0))
        entry.compress_type=ZIP_DEFLATED
        entry.external_attr=(0o100755 if path.name == 'gradlew' or path.suffix == '.sh' else 0o100644)<<16
        z.writestr(entry,data)
if canonical is not None:canonical.close()
with ZipFile(archive) as z:
    assert z.testzip() is None
    assert z.read('ClipHistory/'+apk.name)==apk.read_bytes()
digest=hashlib.sha256(archive.read_bytes()).hexdigest()
Path(str(archive)+'.sha256').write_text(f'{digest}  {archive.name}\n',encoding='ascii')
print(f'Verified {len(files)} files: {archive}\nSHA-256 {digest}')
