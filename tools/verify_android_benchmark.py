"""Verify benchmark-only R8 APK/resource closure and record exact artifact identities."""
import argparse
from datetime import datetime, timezone
import hashlib
import json
from pathlib import Path
import zipfile
from upstream import ROOT


def digest(data):
    return hashlib.sha256(data).hexdigest()


def required_files(config):
    files = set()
    def walk(value):
        if isinstance(value, dict):
            if 'file' in value and 'type' in value:
                files.add(value['file'])
            for child in value.values():
                walk(child)
        elif isinstance(value, list):
            for child in value:
                walk(child)
    walk(config)
    return files


def verify(apk, output):
    project = ROOT / 'benchmarks/android'
    dependencies = json.loads((project / 'build/reports/dependencies.json').read_text())
    coordinates = [item['coordinate'] for item in dependencies]
    assert any(':opencc-core-android:' in item for item in coordinates)
    assert any(':opencc-platform-android:' in item for item in coordinates)
    assert any(':opencc-data-s2t:' in item and item.endswith(':resources@zip') for item in coordinates)
    assert all(not any(part in item for part in ['opencc-jieba', 'opencc-all', 'opencc-dictionary-tools',
                                                'opencc-phrase-extract', 'opencc-cli']) for item in coordinates)
    mapping = project / 'build/outputs/mapping/release/mapping.txt'
    assert mapping.is_file() and 'io.github.opencckmp.androidbenchmark.MainActivity' in mapping.read_text()
    configuration = project / 'build/outputs/mapping/release/configuration.txt'
    assert configuration.is_file()
    with zipfile.ZipFile(apk) as archive:
        infos = {item.filename: item for item in archive.infolist() if not item.is_dir()}
        assert not any(name.endswith('.so') for name in infos), 'Fixture must not acquire a native engine dependency'
        manifest_bytes = archive.read('assets/opencc/manifest.json')
        manifest = json.loads(manifest_bytes)
        config = json.loads(archive.read('assets/opencc/s2t.json'))
        closure = required_files(config)
        assert set(manifest['configs']) == {'s2t'}
        assert closure == set(manifest['configs']['s2t'])
        resources = []
        for name, record in sorted(manifest['files'].items()):
            path = 'assets/opencc/' + name
            data = archive.read(path)
            assert len(data) == record['size'] and digest(data) == record['sha256'], name
            resources.append({'name': name, 'raw_bytes': len(data), 'zip_compressed_bytes': infos[path].compress_size,
                              'sha256': digest(data), 'conversion_resource': name in closure or name == 's2t.json'})
        assets = {name.removeprefix('assets/opencc/') for name in infos if name.startswith('assets/opencc/')}
        assert assets == set(manifest['files']) | {'manifest.json'}, 'No unmanifested/unrelated assets may be shipped'
        dictionaries = {name for name in assets if name.endswith(('.ocd', '.ocd2'))}
        assert dictionaries == closure, 'Ship the full s2t closure and no unrelated dictionaries'
        reference = json.loads(archive.read('assets/benchmark-reference.json'))
        assert digest(archive.read('assets/benchmark-input.txt')) == reference['input_sha256']
        assert digest(archive.read('assets/benchmark-expected.txt')) == reference['expected_sha256']
        assert reference['include_tofu_risk_dictionaries'] is True
        dex = [{'name': name, 'raw_bytes': infos[name].file_size, 'zip_compressed_bytes': infos[name].compress_size,
                'sha256': digest(archive.read(name))} for name in sorted(infos) if name.endswith('.dex')]
    source_hashes = {str(path.relative_to(ROOT)): digest(path.read_bytes()) for path in sorted(project.rglob('*'))
                     if path.is_file() and not any(part in ('build', '.gradle', '.kotlin') for part in path.relative_to(project).parts)}
    report = {'schema_version': 1, 'verified_utc': datetime.now(timezone.utc).isoformat(),
              'scope': 'Build and static packaging verification only; no device or performance measurement',
              'project': 'benchmarks/android', 'agp': '8.13.2', 'kotlin': '2.4.0', 'compile_sdk': 36, 'min_sdk': 23,
              'apk': {'file': apk.name, 'bytes': apk.stat().st_size, 'sha256': digest(apk.read_bytes())},
              'r8': {'enabled': True, 'mapping_sha256': digest(mapping.read_bytes()),
                     'configuration_sha256': digest(configuration.read_bytes()), 'dex': dex},
              'dependency_artifacts': dependencies, 'required_s2t_dictionaries': sorted(closure),
              'unrelated_dictionaries': [], 'resource_files': resources,
              'resource_manifest_sha256': digest(manifest_bytes),
              'native_reference': reference, 'benchmark_source_hashes': source_hashes}
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n')
    print(f'R8 APK verified: {apk.stat().st_size} bytes, {len(dictionaries)} required dictionaries, '
          f'{len(dependencies)} dependency artifacts; {output}')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--apk', type=Path, default=ROOT / 'benchmarks/android/build/outputs/apk/release/opencc-android-performance-release.apk')
    parser.add_argument('--output', type=Path, default=ROOT / 'measurements/android-benchmark-build-validation.json')
    args = parser.parse_args()
    verify(args.apk.resolve(), args.output.resolve())
