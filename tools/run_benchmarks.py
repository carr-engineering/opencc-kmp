"""Run published JVM JMH workloads and the pinned native engine on the same 1 MiB corpus."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess
from build_probes import build_probes
from upstream import ROOT, LOCK

UNIT = '中华人民共和国台湾香港软件鼠标后台开发 简体繁體日本語 ASCII123\n'


def corpus():
    unit = UNIT.encode('utf-8')
    count, rest = divmod(1_048_576, len(unit))
    return unit * count + b'x' * rest


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, default=ROOT / 'opencc-data/build/generated/resources/opencc')
    parser.add_argument('--profile', choices=['main', 'smoke'], default='main')
    parser.add_argument('--probes-ready', action='store_true')
    parser.add_argument('--native-only', action='store_true', help='Prepare/measure native answers without running JMH')
    parser.add_argument('--group', default='io.github.opencckmp')
    parser.add_argument('--version', default='0.1.0-SNAPSHOT')
    parser.add_argument('--repository', type=Path, default=ROOT / '.cache/maven')
    args = parser.parse_args()
    resources = args.resources.resolve()
    if not (resources / 's2t.json').is_file():
        parser.error('Resource directory must contain generated s2t/t2s/s2twp configs and dictionaries')
    if not args.probes_ready:
        build_probes()
    stamp = datetime.now(timezone.utc).strftime('%Y%m%dT%H%M%S%fZ')
    output = ROOT / '.cache/benchmarks' / stamp
    output.mkdir(parents=True)
    text = corpus()
    input_path = output / 'one-mib.txt'
    input_path.write_bytes(text)
    expected = output / 'expected'
    expected.mkdir()
    native = {}
    for config in ('s2t', 't2s', 's2twp'):
        process = subprocess.run([str(ROOT / '.cache/probes/performance_probe'),
            str(resources / f'{config}.json'), str(resources), str(input_path),
            '10' if args.profile == 'smoke' else '30', str(expected / f'{config}.txt')],
            check=True, capture_output=True, text=True)
        native[config] = json.loads(process.stdout)
    environment = os.environ.copy()
    environment.update(OPENCC_BENCH_RESOURCES=str(resources), OPENCC_BENCH_CORPUS=str(input_path),
                       OPENCC_BENCH_EXPECTED=str(expected))
    command = [str(ROOT / ('gradlew.bat' if os.name == 'nt' else 'gradlew')), '--refresh-dependencies', '-p', str(ROOT / 'benchmarks'),
               f'-Popencc.group={args.group}', f'-Popencc.version={args.version}',
               f'-Popencc.repository={args.repository.resolve()}',
               'jvmBenchmark' if args.profile == 'main' else 'jvmSmokeBenchmark']
    report = {'schema_version': 1, 'upstream': LOCK, 'host': platform.platform(),
              'machine': platform.machine(), 'processor': platform.processor(),
              'git_commit': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=ROOT, text=True).strip(),
              'jdk': subprocess.run(['java', '-version'], capture_output=True, text=True, check=True).stderr.strip(),
              'corpus_bytes': len(text), 'corpus_sha256': hashlib.sha256(text).hexdigest(),
              'native_probe_sha256': hashlib.sha256((ROOT / '.cache/probes/performance_probe').read_bytes()).hexdigest(),
              'native_probe_source_sha256': hashlib.sha256((ROOT / 'tools/oracle/performance_probe.cpp').read_bytes()).hexdigest(),
              'expected_output_sha256': {config: hashlib.sha256((expected / f'{config}.txt').read_bytes()).hexdigest()
                                         for config in native},
              'profile': args.profile, 'coordinates': f'{args.group}:*:{args.version}',
              'harness_sources': {str(path.relative_to(ROOT)): hashlib.sha256(path.read_bytes()).hexdigest()
                                  for path in [ROOT / 'benchmarks' / name for name in
                                               ('build.gradle.kts', 'settings.gradle.kts', 'gradle.properties')]
                                  + sorted((ROOT / 'benchmarks/src/jvmMain').rglob('*.kt'))
                                  + [ROOT / 'tools/run_benchmarks.py']},
              'command': command, 'native': native, 'jvm_executed': False,
              'limitations': ['Host benchmark, not physical-device latency evidence',
                              'JMH average times do not establish p95 latency',
                              'Native samples are in-process and include allocation, not config loading',
                              'JVM initialization may read from warm OS filesystem caches']}
    report_path = output / 'report.json'
    report_path.write_text(json.dumps(report, indent=2) + '\n')
    if not args.native_only:
        with (output / 'jmh.log').open('w') as log:
            completed = subprocess.run(command, cwd=ROOT, env=environment, stdout=log, stderr=subprocess.STDOUT)
        if completed.returncode:
            raise RuntimeError(f'JMH failed with exit {completed.returncode}; inspect {output / "jmh.log"}')
        results = ROOT / 'benchmarks/build/reports/benchmarks' / args.profile
        if not results.is_dir():
            raise RuntimeError('JMH finished without its expected reports directory')
        latest = max((path for path in results.iterdir() if path.is_dir()), key=lambda path: path.name)
        shutil.copytree(latest, output / 'jmh')
        measurements = json.loads((latest / 'jvm.json').read_text())
        if len(measurements) != 9:
            raise RuntimeError(f'Expected nine config/workload measurements, received {len(measurements)}')
        report['jmh_results'] = measurements
        dependencies = ROOT / 'benchmarks/build/reports/benchmark-dependencies.json'
        report['dependencies'] = json.loads(dependencies.read_text())
        report['jvm_executed'] = True
        report_path.write_text(json.dumps(report, indent=2) + '\n')
    print(report_path)


if __name__ == '__main__':
    main()
