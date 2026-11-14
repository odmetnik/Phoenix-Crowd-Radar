#!/usr/bin/env python3
from pathlib import Path
import re, subprocess
root=Path(__file__).resolve().parents[1]
build=(root/'app/build.gradle.kts').read_text()
version=re.search(r'versionName\s*=\s*"([^"]+)"',build).group(1)
help_path='app/src/main/assets/help.txt'
help_text=(root/help_path).read_text()
assert help_text.splitlines()[0]==f'VERSION: {version}', 'Help version must match APK version'
for section in ['BLE','GPS','UWB','WI-FI','PRECIZNOST','PRIVATNOST','PROMJENE']:
    assert section in help_text.upper(),f'Missing Help topic: {section}'
try:
    result=subprocess.run(['git','diff','--name-only','HEAD^','HEAD'],cwd=root,text=True,capture_output=True,check=True)
except subprocess.CalledProcessError:
    changed=[]
else:
    changed=result.stdout.splitlines()
if any((p.startswith('app/src/main/') and p!=help_path) or p=='app/build.gradle.kts' for p in changed):
    assert help_path in changed,'App source/resources/build changed: update Help in the same commit'
print(f'PASS: Help {version}, required topics and code-change policy')
