"""Generate the 1000-character Android corpus and exact pinned native expected assets."""
import argparse
import hashlib
import json
from pathlib import Path
import subprocess
from run_benchmarks import UNIT
from upstream import ROOT, LOCK


def prepare(resources):
    text = (UNIT * (1000 // len(UNIT) + 1))[:1000]
    assets = ROOT / 'benchmarks/android/src/main/assets'
    assets.mkdir(parents=True, exist_ok=True)
    input_file = assets / 'benchmark-input.txt'
    expected_file = assets / 'benchmark-expected.txt'
    input_file.write_text(text, encoding='utf-8')
    subprocess.run([str(ROOT / '.cache/oracle/src/tools/opencc'), '-c', str(resources / 's2t.json'),
                    '--include-tofu-risk-dictionaries', '-i', str(input_file), '-o', str(expected_file)], check=True)
    metadata = {'upstream': LOCK, 'config': 's2t', 'include_tofu_risk_dictionaries': True, 'input_characters': 1000,
                'input_sha256': hashlib.sha256(input_file.read_bytes()).hexdigest(),
                'expected_sha256': hashlib.sha256(expected_file.read_bytes()).hexdigest()}
    (assets / 'benchmark-reference.json').write_text(json.dumps(metadata, indent=2) + '\n')
    print(json.dumps(metadata))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--resources', type=Path, default=ROOT / 'opencc-data/build/generated/resources/opencc')
    args = parser.parse_args()
    prepare(args.resources.resolve())
