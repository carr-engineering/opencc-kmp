"""Compare exact retained primitive-array storage of UTF-16 and Rune trie layouts.

This is a structural storage measurement, not process RSS, allocation overhead or
peak memory. It inventories the complete pinned merged dictionary with its native
text serializer and counts the same prefix sharing as each packed layout.
"""
import argparse
import hashlib
import json
import subprocess
from pathlib import Path
from upstream import ROOT


def prefix_nodes(keys):
    previous = ()
    nodes = 1
    for key in sorted(keys):
        shared = 0
        while shared < min(len(previous), len(key)) and previous[shared] == key[shared]:
            shared += 1
        nodes += len(key) - shared
        previous = key
    return nodes


def measure(dictionary, output):
    dictionary = Path(dictionary)
    text = ROOT / '.cache/jieba-rune-layout.txt'
    subprocess.run([str(ROOT / '.cache/oracle/src/tools/opencc_dict'), '-i', str(dictionary),
                    '-o', str(text), '-f', 'ocd2', '-t', 'text'], check=True)
    keys = []
    singles = 0
    for line in text.read_text().splitlines():
        key, values = line.split('\t', 1)
        keys.append(key)
        if len(key) == 1 and values.split(' ')[-1] in ['user_freq', 'user_default']:
            singles += 1
    rune_keys = [tuple(map(ord, key)) for key in keys]
    rune_nodes = prefix_nodes(rune_keys)
    del rune_keys
    utf16_keys = []
    for key in keys:
        encoded = key.encode('utf-16-le')
        utf16_keys.append(tuple(encoded[i] | encoded[i + 1] << 8 for i in range(0, len(encoded), 2)))
    utf16_nodes = prefix_nodes(utf16_keys)
    common = 8 * len(keys) + 4 * singles
    old = 12 * utf16_nodes + 6 * (utf16_nodes - 1) + common
    new = 12 * rune_nodes + 8 * (rune_nodes - 1) + common
    report = {'upstream': 'ver.1.4.2', 'dictionary': dictionary.name,
        'dictionary_sha256': hashlib.sha256(dictionary.read_bytes()).hexdigest(),
        'scope': 'exact retained primitive-array element storage; excludes headers and peak construction memory',
        'entries': len(keys), 'single_user_runes': singles,
        'utf16': {'nodes': utf16_nodes, 'retained_primitive_bytes': old, 'label_bytes': 2 * (utf16_nodes - 1)},
        'rune': {'nodes': rune_nodes, 'retained_primitive_bytes': new, 'label_bytes': 4 * (rune_nodes - 1)},
        'delta_bytes': new - old, 'delta_percent': round((new - old) * 100 / old, 3),
        'packaged_dictionary_delta_bytes': 0,
        'hot_path': 'primitive integer binary search, with no allocated keys or per-lookup objects'}
    output = Path(output)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report))
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--dictionary', type=Path, default=ROOT / '.cache/conformance/jieba/opencc/jieba_dict/jieba_merged.ocd2')
    parser.add_argument('--output', type=Path, default=ROOT / 'measurements/jieba-rune-layout.json')
    args = parser.parse_args()
    measure(args.dictionary, args.output)
