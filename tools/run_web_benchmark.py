#!/usr/bin/env python3
"""Isolated published-artifact Wasm benchmark: fresh browser processes and warmed conversion calls."""
import argparse
import gzip
import hashlib
import http.server
import json
import math
import os
from pathlib import Path
import platform
import subprocess
import threading
from run_benchmarks import UNIT
from upstream import ROOT, LOCK
from measure_web import CompressedApp, distribution_files, validate_resources


def percentile(values, quantile):
    values = sorted(values)
    return values[max(0, math.ceil(len(values) * quantile) - 1)]


def summary(values):
    return {'samples': len(values), 'min_ms': min(values), 'p50_ms': percentile(values, .5),
            'p95_ms': percentile(values, .95), 'max_ms': max(values)}


def prepare_corpus():
    resources = ROOT / 'benchmarks/web/src/wasmJsMain/resources'
    resources.mkdir(parents=True, exist_ok=True)
    text = (UNIT * (1000 // len(UNIT) + 1))[:1000]
    input_file = resources / 'benchmark-input.txt'; input_file.write_text(text)
    expected = resources / 'benchmark-expected.txt'
    subprocess.run([str(ROOT / '.cache/oracle/src/tools/opencc'), '-c', str(ROOT / 'opencc-data/build/generated/resources/opencc/s2t.json'),
                    '--include-tofu-risk-dictionaries', '-i', str(input_file), '-o', str(expected)], check=True)
    reference = {'upstream': LOCK, 'config': 's2t', 'include_tofu_risk_dictionaries': True, 'input_characters': 1000,
                 'input_sha256': hashlib.sha256(input_file.read_bytes()).hexdigest(), 'expected_sha256': hashlib.sha256(expected.read_bytes()).hexdigest()}
    (resources / 'benchmark-reference.json').write_text(json.dumps(reference, indent=2) + '\n')
    return reference


def harness_identity():
    paths = [ROOT / 'tools/run_web_benchmark.py', ROOT / 'tools/web_benchmark_probe.mjs', ROOT / 'tools/measure_web.py', ROOT / 'tools/run_benchmarks.py']
    paths += [ROOT / 'benchmarks/web' / name for name in ['build.gradle.kts', 'settings.gradle.kts', 'gradle.properties']]
    paths += sorted(path for path in (ROOT / 'benchmarks/web/src').rglob('*') if path.is_file())
    return {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest() for path in paths}


def run(args):
    assert args.startup_samples >= 30, 'A release browser benchmark requires at least30 fresh processes/pages'
    reference = prepare_corpus()
    if not args.skip_build:
        subprocess.run([str(ROOT / 'gradlew'), '-p', str(ROOT / 'benchmarks/web'), 'wasmJsBrowserDistribution', 'benchmarkDependencyIdentity', f'-Popencc.group={args.group}', f'-Popencc.version={args.version}', f'-Popencc.repository={args.repository.resolve()}'], check=True)
    dependency_identity = json.loads((ROOT / 'benchmarks/web/build/reports/dependencies.json').read_text())
    assert dependency_identity['group'] == args.group and dependency_identity['version'] == args.version, 'Rebuild stale coordinates'
    files = distribution_files(ROOT / 'benchmarks/web/build/dist/wasmJs/productionExecutable')
    assert files, 'Build the isolated benchmarks/web distribution first'
    validate_resources(files, 'one')
    for name in ('benchmark-input.txt', 'benchmark-expected.txt'):
        assert files[name] == (ROOT / 'benchmarks/web/src/wasmJsMain/resources' / name).read_bytes(), 'Rebuild stale corpus assets'
    assert json.loads(files['benchmark-reference.json']) == reference, 'Rebuild stale native reference metadata'
    server = http.server.ThreadingHTTPServer(('127.0.0.1', 0), CompressedApp)
    server.compressed = {name: gzip.compress(value, compresslevel=9, mtime=0) for name, value in files.items()}
    server.delivered = []
    thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
    runs = []
    try:
        for index in range(args.startup_samples):
            before = len(server.delivered)
            process = subprocess.run(['node', str(ROOT / 'tools/web_benchmark_probe.mjs'),
                                      f'http://127.0.0.1:{server.server_port}/', str(args.chrome)], text=True, capture_output=True, timeout=90)
            assert process.returncode == 0, process.stdout + process.stderr
            report = json.loads(process.stdout); assert report['fixture']['output_verified']
            assert report['fixture']['warmup_calls'] == 100 and len(report['fixture']['samples_ms']) == 300
            assert report['fixture']['input_characters'] == 1000
            report['http_payload_bytes'] = sum(size for _, size in server.delivered[before:])
            report['http_requests'] = [name for name, _ in server.delivered[before:]]
            resource_names = [row['name'] for row in report['fixture']['resources']]
            manifest = json.loads(files['opencc/manifest.json'])
            assert set(resource_names) == {'s2t.json', *manifest['configs']['s2t']}
            assert len(resource_names) == len(set(resource_names)), 'Shared dictionaries fetched more than once'
            runs.append(report)
            print(f'Fresh Wasm browser sample {index+1}/{args.startup_samples}: ready {report["fixture"]["navigation_to_ready_ms"]:.2f}ms', flush=True)
    finally:
        server.shutdown(); server.server_close()
    warm = [duration for report in runs for duration in report['fixture']['samples_ms']]
    result = {'schema_version': 1, 'target': 'desktop-browser-wasmJs', 'reference': reference,
              'environment': {'os': platform.platform(), 'machine': platform.machine(), 'browser': runs[0]['browser'],
                              'identity': runs[0]['identity'], 'published_group': args.group, 'published_version': args.version, 'kotlin': '2.4.0',
                              'serving': 'individual gzip level9 resources over localhost HTTP, fresh browser profile each sample'},
              'dependency_identity': dependency_identity, 'harness_sha256': harness_identity(),
              'distribution_sha256': {name: hashlib.sha256(content).hexdigest() for name, content in files.items()},
              'protocol': {'startup_samples': len(runs), 'warmup_calls_per_browser': 100, 'measured_calls_per_browser': 300,
                           'timing': 'performance.now surrounds conversion only; full native-output equality and heap sampling outside intervals',
                           'startup': 'navigation time origin to prepared converter plus verified first conversion; benchmark warmups excluded'},
              'warm_1000': summary(warm), 'startup_to_first_verified_conversion': summary([r['fixture']['navigation_to_ready_ms'] for r in runs]),
              'resource_preparation': summary([r['fixture']['preparation_ms'] for r in runs]),
              'first_conversion': summary([r['fixture']['first_conversion_ms'] for r in runs]),
              'fixture_fetch': summary([r['fixture']['fixture_fetch_ms'] for r in runs]),
              'per_browser_warm': [summary(r['fixture']['samples_ms']) for r in runs],
              'memory': {'maximum_sampled_js_heap_bytes': max(r['fixture']['maximum_sampled_js_heap_bytes'] for r in runs),
                         'scope': 'Chrome performance.memory samples during preparation and after conversions and final CDP metrics; not process RSS or Wasm linear-memory peak'},
              'limitations': ['Single desktop browser/host, localhost network and OS filesystem caches; no mobile/browser fleet inference',
                              'Fresh processes/profiles reset browser code/data cache; host disk caches may remain warm',
                              'JS heap samples omit unmeasured Wasm linear memory, native browser allocations and between-sample peaks',
                              'Warm sample distributions include timer resolution and host scheduling; per-browser summaries retain clustering'],
              'runs': runs}
    args.output.parent.mkdir(parents=True, exist_ok=True); args.output.write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
    if args.check_target:
        assert result['warm_1000']['p95_ms'] <= 30, 'Recorded desktop-host warmed Wasm p95 exceeds30ms; inspect raw samples/environment'
    print(json.dumps({key: result[key] for key in ['warm_1000', 'startup_to_first_verified_conversion', 'resource_preparation', 'memory']}, indent=2))
    return result


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--startup-samples', type=int, default=30)
    parser.add_argument('--chrome', type=Path, default=Path('/Applications/Google Chrome.app/Contents/MacOS/Google Chrome'))
    parser.add_argument('--output', type=Path, default=ROOT / 'measurements/web-performance.json')
    parser.add_argument('--group', default='io.github.opencckmp')
    parser.add_argument('--version', default='0.1.0-SNAPSHOT')
    parser.add_argument('--repository', type=Path, default=ROOT / '.cache/maven')
    parser.add_argument('--check-target', action='store_true', help='Require warmed p95<=30ms on the recorded desktop host only')
    parser.add_argument('--skip-build', action='store_true')
    run(parser.parse_args())
