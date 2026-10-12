"""Direct pinned CLI assertion ports for filesystem, stream boundaries and tofu-risk defaults."""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import test_cli
from upstream import ROOT

CASES = {
    'SkipsTofuRiskDictionariesByDefault': ('t2s', '㑮'.encode(), b'', []),
    'IncludeTofuRiskDictionariesFlagRestoresLegacy': ('t2s', '㑮'.encode(), b'', ['--include-tofu-risk-dictionaries']),
    'StdinPreservesLineEndingsAndUnknownCharacters': ('s2t', '鼠标=mouse\r\n123\n未登录'.encode(), b'', []),
    'WarnsWhenInputContainsVariationSelector': ('s2t', '汉禰\U000e0100'.encode(), b'', []),
    'WarnsWhenSupplementaryVariationSelectorCrossesChunkBoundary': ('s2t', b'a' * (1048576 - 1) + '\U000e0100'.encode(), b'', []),
    'PreservesLineEnding_LF': ('s2t', '第一行\n第二行\n第三行\n'.encode(), b'', []),
    'PreservesLineEnding_CRLF': ('s2t', '第一行\r\n第二行\r\n第三行\r\n'.encode(), b'', []),
    'PreservesLineEnding_CRLF_ASCII': ('s2t', b'hello\r\nworld\r\n', b'', []),
    'PreservesLineEnding_NoTrailingNewline_LF': ('s2t', '第一行\n第二行'.encode(), b'', []),
    'PreservesLineEnding_NoTrailingNewline_CRLF': ('s2t', '第一行\r\n第二行'.encode(), b'', []),
    'ChunkingBoundaryTruncationFix': ('s2t', b'a' * (1048576 - 3) + '头发尾巴'.encode(), b'', []),
    'ExactChunkSizeDoesNotTruncate': ('s2t', b'a' * 1048576, b'', []),
    'MissingInputDoesNotTruncateOutput': ('s2t', b'', b'existing output', ['missing']),
}


def verify(backend, target):
    test_cli.BACKEND = backend
    test_cli.NATIVE_TARGET = target
    directory = ROOT / '.cache/cli-upstream'
    directory.mkdir(parents=True, exist_ok=True)
    data = ROOT / '.cache/conformance/jieba/opencc'
    rows = []
    for name, (config, input_bytes, original_output, flags) in CASES.items():
        source = directory / '输入 文件-😀.txt'
        output = directory / '輸出 文件.txt'
        captures = []
        for native in (True, False):
            source.write_bytes(input_bytes)
            output.write_bytes(original_output)
            args = ['-c', str(data / f'{config}.json'), '-o', str(output)]
            missing = flags == ['missing']
            if missing:
                args += ['-i', str(directory / '不存在.txt')]
            elif name == 'StdinPreservesLineEndingsAndUnknownCharacters':
                pass
            else:
                args += ['-i', str(source)]
            if not missing: args += flags
            result = test_cli.run('opencc', args, input_bytes, native)
            captures.append((result.returncode, result.stdout, result.stderr, output.read_bytes()))
        assert captures[0] == captures[1], f'{name}: native/Kotlin process results differ'
        code, _, stderr, emitted = captures[1]
        if name == 'MissingInputDoesNotTruncateOutput':
            assert code != 0 and emitted == original_output, name
        else:
            assert code == 0, (name, stderr)
        if name.startswith('WarnsWhen'):
            assert b'warning: input contains Unicode variation selectors' in stderr, name
        if name == 'SkipsTofuRiskDictionariesByDefault': assert emitted == '㑮'.encode()
        if name == 'IncludeTofuRiskDictionariesFlagRestoresLegacy': assert emitted == '𫝈'.encode()
        if name == 'ChunkingBoundaryTruncationFix': assert emitted.endswith('頭髮尾巴'.encode())
        if name == 'ExactChunkSizeDoesNotTruncate': assert len(emitted) == 1048576
        rows.append({'case': name, 'result': 'passed'})
    report = {'schema_version': 1, 'backend': backend, 'target': target, 'cases': rows,
              'scope': '13 direct pinned CLI assertions; broader CLI matrix and platform gates remain separate'}
    (directory / f'report-{backend}.json').write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps({'backend': backend, 'cases': len(rows), 'result': 'passed'}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--backend', choices=['jvm', 'native'], default='jvm')
    parser.add_argument('--native-target', default=test_cli.NATIVE_TARGET)
    args = parser.parse_args()
    verify(args.backend, args.native_target)
