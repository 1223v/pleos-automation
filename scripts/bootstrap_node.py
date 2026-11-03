"""Install the official, SHA-256 verified Windows x64 Node runtime locally.

No global installation or environment/PATH changes.
Release: https://nodejs.org/en/blog/release/v24.22.1
"""
from pathlib import Path, PurePosixPath
import hashlib
import shutil
import stat
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
VERSION = '24.22.1'
PREFIX = f'node-v{VERSION}-win-x64'
SHA256 = 'd2046d28c2c3dcd211ac5145c0bf30e2047ef9dfb306693c8c6e2eccd8b95fa5'
URL = f'https://nodejs.org/dist/v{VERSION}/{PREFIX}.zip'


def extract_verified(archive, checksum, destination, prefix=PREFIX):
    archive, destination = Path(archive), Path(destination).resolve()
    with archive.open('rb') as stream:
        if hashlib.file_digest(stream, 'sha256').hexdigest() != checksum:
            raise ValueError('Runtime archive checksum mismatch')
    with zipfile.ZipFile(archive) as package:
        members = []
        for item in package.infolist():
            parts = PurePosixPath(item.filename).parts
            if ('\\' in item.filename or ':' in item.filename or not parts or
                    parts[0] != prefix or '..' in parts or
                    stat.S_ISLNK(item.external_attr >> 16)):
                raise ValueError('Unsafe archive entry')
            if item.is_dir():
                continue
            if len(parts) < 2:
                raise ValueError('Invalid archive root')
            target = destination.joinpath(*parts[1:]).resolve()
            if not target.is_relative_to(destination):
                raise ValueError('Archive traversal rejected')
            members.append((item, target))
        for item, target in members:
            target.parent.mkdir(parents=True, exist_ok=True)
            with package.open(item) as source, target.open('wb') as output:
                shutil.copyfileobj(source, output)


def main():
    archive = ROOT / '.tools/downloads' / f'{PREFIX}.zip'
    archive.parent.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        print(f'Downloading official runtime: {URL}', flush=True)
        with urllib.request.urlopen(URL, timeout=90) as response, archive.open('wb') as output:
            shutil.copyfileobj(response, output)
    destination = ROOT / '.tools' / PREFIX
    extract_verified(archive, SHA256, destination)
    print(f'Verified SHA256 {SHA256}; installed {destination}', flush=True)


if __name__ == '__main__':
    main()
