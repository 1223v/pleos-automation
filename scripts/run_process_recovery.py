"""Opt-in process death test on this workspace's official AVD; no vehicle writes."""
from pathlib import Path
import argparse
import subprocess
import time

ROOT = Path(__file__).resolve().parents[1]
ADB = ROOT / '.tools/android-sdk/platform-tools/adb.exe'
PREFIX = [str(ADB), '-s', 'emulator-5556']
RUNNER = 'com.pleos.routine.test/androidx.test.runner.AndroidJUnitRunner'
EVIDENCE = ROOT / 'evidence/W28'
FLAGS = subprocess.CREATE_NO_WINDOW if hasattr(subprocess, 'CREATE_NO_WINDOW') else 0


def adb(*args):
    return subprocess.run(PREFIX + list(args), check=True, capture_output=True,
                          text=True, encoding='utf-8', creationflags=FLAGS).stdout.strip()


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--evidence-dir', type=Path, default=EVIDENCE)
    evidence = parser.parse_args().evidence_dir
    fingerprint = adb('shell', 'getprop', 'ro.build.fingerprint')
    hardware = adb('shell', 'getprop', 'ro.hardware')
    if not fingerprint.startswith('Pleos/crp_avd/') or hardware != 'ranchu':
        raise SystemExit('Refusing to run outside the official Pleos AVD.')
    if adb('shell', 'am', 'get-current-user') != '10':
        raise SystemExit('Expected AVD user 10.')
    evidence.mkdir(parents=True, exist_ok=True)
    prepare = evidence / 'process-recovery-prepare-retest.txt'
    common = ['shell', 'am', 'instrument', '--user', '10', '-w', '-r', '-e', 'class']
    with prepare.open('w', encoding='utf-8') as output:
        process = subprocess.Popen(PREFIX + common + [
            'com.pleos.routine.RuntimeRecoveryTest#prepareRunningAudioForProcessTermination',
            '-e', 'recoveryPhase', 'prepare', RUNNER], stdout=output,
            stderr=subprocess.STDOUT, creationflags=FLAGS)
        deadline = time.monotonic() + 150
        while time.monotonic() < deadline:
            text = prepare.read_text(encoding='utf-8', errors='replace')
            if 'READY pid=' in text:
                print(next(line for line in text.splitlines() if 'READY pid=' in line), flush=True)
                adb('shell', 'am', 'force-stop', '--user', '10', 'com.pleos.routine')
                print('Stopped only com.pleos.routine after READY; prepare ends intentionally.', flush=True)
                process.wait(timeout=30)
                break
            if process.poll() is not None:
                raise SystemExit(f'Prepare ended before READY. Inspect {prepare}')
            time.sleep(0.25)
        else:
            # Only terminate the host observer; do not claim process death occurred.
            process.terminate()
            process.wait(timeout=10)
            raise SystemExit('READY was not observed; verification was not run.')
    verify = adb(*common,
        'com.pleos.routine.RuntimeRecoveryTest#newProcessRecoversUnknownWithoutReplaying',
        '-e', 'recoveryPhase', 'verify', RUNNER)
    (evidence / 'process-recovery-verify.txt').write_text(verify + '\n', encoding='utf-8')
    print(verify, flush=True)
    if 'PASS pid=' not in verify or 'OK (1 test)' not in verify:
        raise SystemExit('Recovery did not pass; inspect the verification log.')


if __name__ == '__main__':
    main()
