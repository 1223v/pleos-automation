"""Install a workspace-local, checksum-verified Android/Pleos development SDK.

Does not modify the user's existing SDK, environment variables or AVDs.
Sources: Google repository2-1.xml; Pleos crp-sys-img.xml.
"""
from pathlib import Path, PurePosixPath
import argparse
import hashlib
import json
import shutil
import urllib.request
import xml.etree.ElementTree as ET
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
SDK = TOOLS / 'android-sdk'
DOWNLOADS = TOOLS / 'downloads'

def install(url, checksum, size, destination):
    destination = destination.resolve()
    assert destination.is_relative_to(SDK.resolve())
    marker = destination / '.routine-package.json'
    if marker.exists() and json.loads(marker.read_text())['checksum'] == checksum:
        print('Installed:', destination, flush=True)
        return
    archive = DOWNLOADS / url.rsplit('/', 1)[-1]
    DOWNLOADS.mkdir(parents=True, exist_ok=True)
    if not archive.exists() or archive.stat().st_size != int(size):
        print('Downloading:', url, 'bytes:', size, flush=True)
        with urllib.request.urlopen(url, timeout=90) as response, archive.open('wb') as output:
            shutil.copyfileobj(response, output, 1024 * 1024)
    digest = hashlib.file_digest(archive.open('rb'), 'sha1').hexdigest()
    if digest != checksum:
        raise RuntimeError(f'Checksum mismatch: {archive.name}')
    destination.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive) as package:
        files = [item for item in package.infolist() if not item.is_dir()]
        # Official archives have one top-level package directory.
        prefixes = {PurePosixPath(item.filename).parts[0] for item in files}
        strip = len(prefixes) == 1 and all(len(PurePosixPath(i.filename).parts) > 1 for i in files)
        for item in files:
            parts = PurePosixPath(item.filename).parts
            relative = Path(*parts[1:]) if strip else Path(*parts)
            target = (destination / relative).resolve()
            if not target.is_relative_to(destination):
                raise RuntimeError('Archive traversal rejected')
            target.parent.mkdir(parents=True, exist_ok=True)
            with package.open(item) as source, target.open('wb') as output:
                shutil.copyfileobj(source, output)
    marker.write_text(json.dumps({'url': url, 'checksum': checksum, 'bytes': int(size)}, indent=2))
    print('Verified and extracted:', destination, flush=True)

def google():
    xml = ET.fromstring(urllib.request.urlopen('https://dl.google.com/android/repository/repository2-1.xml').read())
    desired = {
        'platforms;android-35': ('platform-35_r02.zip', SDK / 'platforms/android-35'),
        'platforms;android-34': ('platform-34-ext7_r03.zip', SDK / 'platforms/android-34'),
        'build-tools;35.0.0': ('build-tools_r35_windows.zip', SDK / 'build-tools/35.0.0'),
        'cmdline-tools;latest': (None, SDK / 'cmdline-tools/latest'),
        'platform-tools': (None, SDK / 'platform-tools'),
        'emulator': ('emulator-windows_x64-16428233.zip', SDK / 'emulator'),
    }
    done = set()
    for package in xml.findall('remotePackage'):
        key = package.attrib.get('path')
        if key not in desired or key in done:
            continue
        name, destination = desired[key]
        for archive in package.findall('archives/archive'):
            complete = archive.find('complete')
            if archive.findtext('host-os') not in (None, 'windows'):
                continue
            if name and complete.findtext('url') != name:
                continue
            install('https://dl.google.com/android/repository/' + complete.findtext('url'),
                    complete.findtext('checksum'), complete.findtext('size'), destination)
            done.add(key)
            break
    if set(desired) != done:
        raise RuntimeError(f'Packages missing from official index: {set(desired)-done}')
    # Reuse the user's existing accepted license records; do not auto-accept new terms.
    accepted = Path('C:/AndroidSdk/licenses')
    if accepted.is_dir():
        shutil.copytree(accepted, SDK / 'licenses', dirs_exist_ok=True)
    local = ROOT / 'android/local.properties'
    local.parent.mkdir(parents=True, exist_ok=True)
    local.write_text('sdk.dir=' + SDK.as_posix() + '\n', encoding='utf-8')

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--pleos', action='store_true')
    args = parser.parse_args()
    if args.pleos:
        install('https://nexus-playground.pleos.ai/repository/raw-releases/connect-avd/2.x/crp_car_emulator.zip',
                'b4f858d5bcc6c71506d7edfc872bcb9e5a11acaa', 4087751964,
                SDK / 'system-images/android-34/android-automotive/x86_64')
    else:
        google()
