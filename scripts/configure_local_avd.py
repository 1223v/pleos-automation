"""Inspect or configure only this workspace's existing PleosRoutine AVD.

Does not create/delete AVDs, wipe user data, start a browser, or restart a running test.
Use --apply to persist cold boot; --gpu host follows a verified host-GPU experiment.
"""
import argparse
import json
from pathlib import Path

root = Path(__file__).resolve().parent.parent
parser = argparse.ArgumentParser()
parser.add_argument('--apply', action='store_true')
parser.add_argument('--gpu', choices=['auto', 'host', 'software'])
args = parser.parse_args()
config = (root / '.tools/avd/PleosRoutine.avd/config.ini').resolve()
if not config.is_relative_to((root / '.tools/avd').resolve()) or not config.is_file():
    raise SystemExit('Expected workspace-local PleosRoutine config.ini is missing')
text = config.read_text(encoding='utf-8-sig')
lines = text.splitlines()
settings = dict(line.split('=', 1) for line in lines if '=' in line)
changes = {'fastboot.forceColdBoot': 'yes', 'fastboot.forceFastBoot': 'no', 'fastboot.forceChosenSnapshotBoot': 'no'}
if args.gpu:
    changes.update({'hw.gpu.enabled': 'yes', 'hw.gpu.mode': args.gpu})
print(json.dumps({'config': str(config), 'before': {key: settings.get(key) for key in changes}, 'after': changes, 'applied': args.apply}, indent=2))
if args.apply:
    backup = config.with_name('config.ini.before-routine-coldboot')
    if not backup.exists():
        backup.write_bytes(config.read_bytes())
    updated = [f'{key}={changes[key]}' if (key := line.split('=', 1)[0]) in changes else line for line in lines]
    updated.extend(f'{key}={value}' for key, value in changes.items() if key not in settings)
    config.write_text('\n'.join(updated) + '\n', encoding='utf-8')
