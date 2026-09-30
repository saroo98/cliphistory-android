#!/usr/bin/env python3
"""Package reviewed source and a verified APK without keys, caches or device data."""
from pathlib import Path
from zipfile import ZipFile, ZIP_DEFLATED
import hashlib
import re

root=Path(__file__).resolve().parents[1]
version=re.search(r'versionName\s*=\s*"([^"]+)"',(root/'app/build.gradle.kts').read_text(encoding='utf-8')).group(1)
apk=root/f'ClipHistory-{version}.apk'
assert apk.is_file() and Path(str(apk)+'.sha256').is_file(), 'Verify and checksum the release APK first'
archive=root.parent/f'ClipHistory-{version}-complete.zip'
files=[p for p in root.iterdir() if p.is_file() and (p.suffix=='.md' or p.name in {'.gitignore','.gitattributes','LICENSE','BUILD_WINDOWS.cmd','build.gradle.kts','settings.gradle.kts','gradle.properties','gradlew','gradlew.bat',apk.name,apk.name+'.sha256'})]
files.append(root/'app/build.gradle.kts')
for folder in ('app/src','gradle','LICENSES','tools','docs','fastlane','fdroid/metadata'):
    files.extend(p for p in (root/folder).rglob('*') if p.is_file() and '__pycache__' not in p.parts)
files=sorted(set(files))
for path in files:
    relative=path.relative_to(root)
    assert not set(relative.parts)&{'.signing','.tools','.gradle','.kotlin','.git','build'}
    assert path.suffix.lower() not in {'.jks','.keystore','.pem','.key'}
    assert path.name not in {'password.txt','local.properties'}
with ZipFile(archive,'w',ZIP_DEFLATED) as z:
    for path in files:z.write(path,Path('ClipHistory')/path.relative_to(root))
with ZipFile(archive) as z:
    assert z.testzip() is None
    assert z.read('ClipHistory/'+apk.name)==apk.read_bytes()
digest=hashlib.sha256(archive.read_bytes()).hexdigest()
Path(str(archive)+'.sha256').write_text(f'{digest}  {archive.name}\n',encoding='ascii')
print(f'Verified {len(files)} files: {archive}\nSHA-256 {digest}')
