"""Package project sources and evidence, deliberately excluding private signing keys."""
from pathlib import Path
import hashlib
import shutil
import zipfile
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parent.parent
out = root / 'delivery'
out.mkdir(exist_ok=True)
apk = out / 'TodayEat-v1.0.0.apk'
shutil.copy2(root / 'app/build/outputs/apk/release/app-release.apk', apk)
tests = list((root / 'app/build/test-results').rglob('TEST-*.xml'))
assert tests, 'Missing test evidence'
total = 0
for report in tests:
    suite = ET.parse(report).getroot()
    assert int(suite.attrib.get('failures', 0)) == 0, report
    assert int(suite.attrib.get('errors', 0)) == 0, report
    total += int(suite.attrib['tests'])
    (out / 'test-results').mkdir(exist_ok=True)
    suite.attrib.pop('hostname', None)
    ET.ElementTree(suite).write(out / 'test-results' / report.name, encoding='utf-8', xml_declaration=True)
assert total == 19, total
lint = root / 'app/build/reports/lint-results-debug.xml'
assert len(ET.parse(lint).getroot().findall('issue')) == 0, 'Lint issues remain'
shutil.copy2(lint, out / 'test-results' / 'lint-results-debug.xml')
shutil.copytree(root / 'app/build/screenshots', out / 'screenshots', dirs_exist_ok=True)

files = [root / n for n in ['README.md', '.gitignore', 'settings.gradle', 'build.gradle', 'gradle.properties', 'gradlew', 'gradlew.bat', 'app/build.gradle']]
for folder in ['gradle', 'app/src', 'scripts']:
    files += [f for f in (root / folder).rglob('*') if f.is_file() and '__pycache__' not in f.parts]
files += [f for f in out.rglob('*') if f.is_file() and (f.suffix == '.md' or 'screenshots' in f.parts or 'test-results' in f.parts or f.name == 'apk-verification.txt')]
archive = out / 'TodayEat-source.zip'
with zipfile.ZipFile(archive, 'w', zipfile.ZIP_DEFLATED) as z:
    for f in sorted(files):
        z.write(f, Path('TodayEat') / f.relative_to(root))
with zipfile.ZipFile(archive) as z:
    assert z.testzip() is None
    assert not any('.signing' in n or 'signing.properties' in n or '.tools/' in n for n in z.namelist())
with (out / 'SHA256SUMS.txt').open('w', encoding='utf-8') as f:
    for item in [apk, archive]:
        digest = hashlib.sha256(item.read_bytes()).hexdigest()
        f.write(f'{digest}  {item.name}\n')
print(f'Packaged APK ({apk.stat().st_size} bytes), {len(files)} source files; {total} tests passed, no lint issues.')
