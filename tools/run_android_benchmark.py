"""Measure an R8 release benchmark APK on an explicitly selected Android serial.

At least 30 force-stopped launches are required. Emulator results are labelled and
never presented as physical-device evidence. This runner does not provision or
silently select a device, change device thermal settings, or clear its log buffers.
"""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import math
import os
from pathlib import Path
import subprocess
import time
import uuid
import zipfile
from upstream import ROOT

PACKAGE = 'io.github.opencckmp.androidbenchmark'


def percentiles(samples):
    values = sorted(samples)
    return {'samples': len(values), 'p50': values[math.ceil(len(values) * .50) - 1],
            'p95': values[math.ceil(len(values) * .95) - 1], 'min': values[0], 'max': values[-1],
            'quantile': 'nearest-rank'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--serial', required=True, help='Explicit adb serial for the device you intend to measure')
    parser.add_argument('--iterations', type=int, default=30)
    parser.add_argument('--pause-seconds', type=float, default=2)
    parser.add_argument('--apk', type=Path, default=ROOT / 'benchmarks/android/build/outputs/apk/release/opencc-android-performance-release.apk')
    parser.add_argument('--sdk', type=Path, default=Path(os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT') or str(Path.home() / 'Library/Android/sdk')))
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.iterations < 30:
        parser.error('Use at least 30 fresh-process launches; shorter runs are not a benchmark result')
    if args.pause_seconds < 0:
        parser.error('--pause-seconds must be nonnegative')
    if not args.apk.is_file():
        parser.error('Build the standalone benchmarks/android R8 release APK first')
    with zipfile.ZipFile(args.apk) as apk:
        reference = json.loads(apk.read('assets/benchmark-reference.json'))
        for asset, field in [('benchmark-input.txt', 'input_sha256'), ('benchmark-expected.txt', 'expected_sha256')]:
            assert hashlib.sha256(apk.read('assets/' + asset)).hexdigest() == reference[field], asset
        assert 'assets/opencc/s2t.json' in apk.namelist(), 'APK must contain packaged s2t resources'
        assert 'assets/opencc/t2s.json' not in apk.namelist(), 'Benchmark intentionally ships one selected configuration'
    command = [str(args.sdk / 'platform-tools/adb'), '-s', args.serial]
    def adb(*arguments, check=True):
        return subprocess.run(command + list(arguments), check=check, capture_output=True, text=True)
    assert adb('get-state').stdout.strip() == 'device', 'Explicit serial must be connected and authorized'
    properties = ['ro.product.manufacturer', 'ro.product.model', 'ro.product.cpu.abi', 'ro.build.version.sdk',
                  'ro.build.fingerprint', 'ro.kernel.qemu', 'ro.boot.qemu', 'ro.hardware']
    device = {key: adb('shell', 'getprop', key).stdout.strip() for key in properties}
    emulator = (args.serial.startswith('emulator-') or device['ro.kernel.qemu'] == '1' or
                device['ro.boot.qemu'] == '1' or device['ro.hardware'] in ('goldfish', 'ranchu') or
                'emulator' in device['ro.product.model'].lower() or 'sdk_gphone' in device['ro.product.model'].lower())
    scope = 'emulator-performance-smoke' if emulator else 'explicit-device-performance'
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    output = args.output or ROOT / '.cache/benchmarks/android' / stamp / 'report.json'
    output.parent.mkdir(parents=True, exist_ok=True)
    report = {'schema_version': 1, 'created_utc': stamp, 'serial': args.serial, 'device': device,
              'emulator': emulator, 'scope': scope, 'native_reference': reference,
              'apk_sha256': hashlib.sha256(args.apk.read_bytes()).hexdigest(), 'apk_bytes': args.apk.stat().st_size,
              'pause_seconds': args.pause_seconds, 'requested_launches': args.iterations, 'launches': [],
              'measurement_notes': ['Fresh application process for each launch; OS file caches may remain warm.',
                                    'Warm p50/p95 use 300 conversions of the same 1000 UTF-16-unit corpus after 100 warmups.',
                                    'VmHWM is process-lifetime resident peak. Java/native heap values are snapshots without forced GC.',
                                    'Physical model claims require the recorded explicit serial and device metadata; emulator measurements have separate scope.']}
    adb('install', '-r', str(args.apk.resolve()))
    for iteration in range(args.iterations):
        adb('shell', 'am', 'force-stop', PACKAGE)
        assert not adb('shell', 'pidof', PACKAGE, check=False).stdout.strip(), 'Process must be absent before launch'
        token = uuid.uuid4().hex
        launch = adb('shell', 'am', 'start', '-W', '-n', PACKAGE + '/.MainActivity', '--es', 'run_token', token)
        deadline = time.monotonic() + 90
        result = None
        while time.monotonic() < deadline:
            log = adb('logcat', '-d', '-v', 'brief', 'OpenCCBenchmark:I', 'AndroidRuntime:E', '*:S').stdout
            if 'FAIL run_token=' + token in log:
                raise RuntimeError(f'Launch {iteration + 1}: benchmark assertion failed:\n{log[-8000:]}')
            for line in log.splitlines():
                marker = line.find('RESULT ')
                if marker < 0:
                    continue
                try:
                    candidate = json.loads(line[marker + len('RESULT '):])
                except json.JSONDecodeError:
                    continue
                if candidate.get('run_token') == token:
                    result = candidate
                    break
            if result is not None:
                break
            time.sleep(.25)
        if result is None:
            raise RuntimeError(f'Launch {iteration + 1}: complete benchmark JSON not observed; recent logs:\n{log[-8000:]}')
        assert result['output_matches_native'] and result['output_sha256'] == reference['expected_sha256']
        assert result['input_sha256'] == reference['input_sha256'] and result['input_utf16_units'] == 1000
        assert result['warm_1000']['samples'] >= 300 and result['warm_1000']['warmup_calls'] >= 100
        result['launch_command_output'] = launch.stdout
        result['launch_index'] = iteration + 1
        report['launches'].append(result)
        output.write_text(json.dumps(report, indent=2) + '\n')
        print(f'{scope} {iteration + 1}/{args.iterations}: first load+conversion {result["first_load_and_conversion_ns"] / 1e6:.3f} ms; warm p95 {result["warm_1000"]["p95_ns"] / 1e6:.3f} ms', flush=True)
        time.sleep(args.pause_seconds)
    report['summary'] = {
        'first_load_and_conversion_ns': percentiles([row['first_load_and_conversion_ns'] for row in report['launches']]),
        'first_load_ns': percentiles([row['first_load_ns'] for row in report['launches']]),
        'first_conversion_ns': percentiles([row['first_conversion_ns'] for row in report['launches']]),
        'per_launch_warm_p50_ns': percentiles([row['warm_1000']['p50_ns'] for row in report['launches']]),
        'per_launch_warm_p95_ns': percentiles([row['warm_1000']['p95_ns'] for row in report['launches']]),
        'vm_hwm_peak_bytes': max(row['memory_after']['vm_hwm_bytes'] for row in report['launches']),
        'java_heap_used_peak_snapshot_bytes': max(row['memory_after']['java_heap_used_bytes'] for row in report['launches'])}
    report['complete'] = True
    output.write_text(json.dumps(report, indent=2) + '\n')
    adb('shell', 'am', 'force-stop', PACKAGE)
    print(output)


if __name__ == '__main__':
    main()
