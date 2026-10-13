#!/usr/bin/env python3
"""Record both native optimized paths against the pinned pre-skip reference loop."""
import os
import json
import subprocess
from upstream import ROOT, fetch


def audit():
    source = fetch(); target = ROOT / '.cache/conformance/conversion-fuzz'; target.mkdir(parents=True, exist_ok=True)
    resources = {'phrases.txt': '太后\t太后\n头发\t頭髮\n干燥\t乾燥\n鼠标\t鼠標\n', 'characters.txt': '后\t后 後\n发\t發 髮\n干\t幹 乾 干\n里\t裏 里\n'}
    for name, text in resources.items(): (target / name).write_text(text)
    config = {'conversion_chain': [{'dict': {'type': 'group', 'dicts': [{'type': 'text', 'file': name} for name in resources]}}]}
    (target / 'config.json').write_text(json.dumps(config))
    image_text = ''.join(resources.values()); (target / 'all.txt').write_text(image_text)
    subprocess.run([str(ROOT / '.cache/oracle/src/tools/opencc_dict'), '-i', str(target/'all.txt'), '-o', str(target/'all.ocd2'), '-f', 'text', '-t', 'ocd2'], check=True, capture_output=True)
    binary = ROOT / '.cache/probes/conversion_fuzz_probe'
    subprocess.run([os.environ.get('CXX', 'c++'), '-std=c++17', '-O2', '-DOpencc_BUILT_AS_STATIC', '-I', str(source/'src'), str(ROOT/'tools/oracle/conversion_fuzz_probe.cpp'), str(ROOT/'.cache/oracle/src/libopencc.a'), str(ROOT/'.cache/oracle/deps/marisa-0.3.1/libmarisa.a'), '-o', str(binary)], check=True)
    rows = subprocess.run([str(binary), str(target/'config.json'), str(target)], check=True, capture_output=True, text=True).stdout.splitlines()
    assert len(rows) == 600
    fixture = 'package io.github.opencckmp\n\ninternal val nativeConversionFuzzRows = """\n'+'\n'.join(rows)+'\n""".trim().lines()\n'
    fixture += 'internal val nativeConversionFuzzImage = """'+(target/'all.ocd2').read_bytes().hex()+'""".chunked(2).map { it.toInt(16).toByte() }.toByteArray()\n'
    (ROOT/'opencc-core/src/commonTest/kotlin/io/github/opencckmp/NativeConversionFuzzCases.kt').write_text(fixture)
    print('Native reference loop: 600 cases, seed 20260724, table and MARISA paths agree')


if __name__ == '__main__': audit()
