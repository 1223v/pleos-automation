"""Install the pinned official Corretto JDK 17 locally, without global changes.

Release: https://github.com/corretto/corretto-17/releases/tag/17.0.21.0.11
Checksum: https://corretto.aws/downloads/latest_sha256/amazon-corretto-17-x64-windows-jdk.zip
The latest endpoint was checked on 2026-10-30; the version and digest below are pinned.
"""
from pathlib import Path
import shutil
import urllib.request
from bootstrap_node import extract_verified

ROOT = Path(__file__).resolve().parents[1]
VERSION = '17.0.21.0.11'
ARCHIVE = f'amazon-corretto-{VERSION}-windows-x64-jdk.zip'
SHA256 = '59d6514305f6fab3b18955fb17185562f0a0bbf20e1466eda33a5429f3b31618'
URL = f'https://corretto.aws/downloads/resources/{VERSION}/{ARCHIVE}'


def main():
    archive = ROOT / '.tools/downloads' / ARCHIVE
    archive.parent.mkdir(parents=True, exist_ok=True)
    if not archive.exists():
        with urllib.request.urlopen(URL, timeout=90) as response, archive.open('wb') as output:
            shutil.copyfileobj(response, output)
    destination = ROOT / '.tools' / f'corretto-{VERSION}'
    extract_verified(archive, SHA256, destination, prefix='jdk17.0.21_0')
    print(f'Verified SHA256 {SHA256}; installed {destination}', flush=True)


if __name__ == '__main__':
    main()
