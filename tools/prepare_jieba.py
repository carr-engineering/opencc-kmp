"""Produce pinned native segmentation/config answers for the optional common Jieba port."""
import argparse
import json
import os
from pathlib import Path
import random
import re
import subprocess
from build_data import create_bundle
from build_probes import build_probes
from upstream import ROOT, fetch


def probe(arguments, inputs, environment):
    process = subprocess.run([str(ROOT / '.cache/probes/jieba_probe'), *map(str, arguments)],
                             input=''.join(value.hex() + '\n' for value in inputs), text=True,
                             capture_output=True, env=environment)
    lines = process.stdout.splitlines()
    if process.returncode:
        raise RuntimeError(f"Probe exited {process.returncode} after {len(lines)} lines; next inputs: {inputs[max(0,len(lines)-2):len(lines)+1]!r}; stderr: {process.stderr[-2000:]}")
    assert len(lines) == len(inputs), (len(lines), len(inputs), process.stderr[-1000:])
    result = []
    for line in lines:
        status, _, output = line.partition('\t')
        result.append({'status': status, 'value': output})
    return result


def prepare(probes_ready):
    source = fetch()
    if not probes_ready:
        build_probes()
    oracle = ROOT / '.cache/oracle'
    target = ROOT / '.cache/conformance/jieba'
    bundle = target / 'opencc'
    create_bundle(bundle, oracle=oracle)
    environment = os.environ.copy()
    environment['OPENCC_SEGMENTATION_PLUGIN_PATH'] = str(oracle / 'plugins/jieba')
    raw = bundle / 'jieba_dict'
    comparisons = json.loads((source / 'plugins/jieba/tests/data/jieba_comparison_testcases.json').read_text())['cases']
    samples = ['', '勇敢的士兵', '长春市长春节讲话', '南京市长江大桥', '𠀀𠮷神你汉字',
               'abc1.2 5G3.14 3.14.15 A.1 B. xyz 中文', '汉\x00字', '\r\n\t 空格，。!?：',
               'OpenCC2.4使用C++和Kotlin转换Chinese测试ABC123']
    samples += [row['input'] for row in comparisons]
    # The pinned testcase file permits comments/trailing commas. Decode its individual JSON
    # input strings rather than imposing strict-JSON acceptance on the entire upstream document.
    testcase_text = (source/'test/testcases/testcases.json').read_text()
    samples += [json.loads(match.group(1)) for match in re.finditer(r'"input"\s*:\s*("(?:\\.|[^"\\])*")', testcase_text)]
    samples += ['\x00', '\x00\x00', '尾巴\x00', '\x00开头']
    randomizer = random.Random(142)
    alphabet = '的一了是在我不有人来國臺灣香港中文汉龙发著里髮後干杯計程車出租车线鼠标软件服务器𠀀𠮷abcXYZ0123.，。!? \t\r\n'
    samples += [''.join(randomizer.choice(alphabet) for _ in range(randomizer.randrange(1, 90))) for _ in range(400)]
    samples = list(dict.fromkeys(samples))
    valid = [s.encode() for s in samples]
    malformed = [bytes.fromhex(value) for value in ['80', 'c0af', 'c1bf', 'e080af', 'eda080', 'f0908080',
                   'f4908080', 'f5808080', 'e4b8', '61e482', 'f8', 'ff', '80a0', 'e44141']]
    inputs = valid + malformed
    runs = []
    empty_user = target/'empty-user.utf8'
    empty_user.write_text('')
    for name, dictionary, user in [('merged', raw/'jieba_merged.ocd2', ''),
                                    ('text', raw/'jieba.dict.utf8', raw/'user.dict.utf8'),
                                    ('text-base', raw/'jieba.dict.utf8', empty_user)]:
        output = probe(['segment', dictionary, raw/'hmm_model.utf8', user, raw], inputs, environment)
        segment_rows = []
        for value, line in zip(inputs, output):
            assert line['status'] == 'OK', line
            count, segments = line['value'].split('\t', 1)
            segments = segments.split(',') if int(count) else []
            assert len(segments) == int(count)
            segment_rows.append({'input_hex': value.hex(), 'segments_hex': segments})
        runs.append({'name': name, 'dictionary': str(dictionary.relative_to(bundle)),
                     'users': [str(user.relative_to(bundle))] if user and name != 'text-base' else [], 'cases': segment_rows})
    conversions = []
    streams = []
    for config in sorted((source/'plugins/jieba/data/config').glob('*.json')):
        for tofu in (False, True):
            output = probe(['convert', bundle/config.name, bundle, str(tofu).lower(), raw], inputs, environment)
            conversions.append({'config': config.name, 'include_tofu': tofu,
                'cases': [{'input_hex': value.hex(), 'status': line['status'], 'output_hex': line['value']}
                          for value, line in zip(inputs, output)]})
            stream_inputs = inputs[:48] + inputs[-24:]
            for keep, stride in [(0, 1), (0, 7), (16, 2), (16, 31)]:
                answers = probe([f'stream{keep}:{stride}', bundle/config.name, bundle, str(tofu).lower(), raw], stream_inputs, environment)
                streams.append({'config': config.name, 'include_tofu': tofu, 'keep': keep, 'stride': stride,
                    'cases': [{'input_hex': value.hex(), 'status': line['status'], 'output': line['value']} for value,line in zip(stream_inputs, answers)]})
    report = {'upstream': 'ver.1.4.2', 'segmentation': runs, 'conversion': conversions, 'streaming': streams}
    (target/'cases.json').write_text(json.dumps(report, ensure_ascii=True, sort_keys=True, indent=2)+'\n')
    rows = []
    for run in runs:
        rows += ['\t'.join(['S', run['name'], row['input_hex'], str(len(row['segments_hex'])), ','.join(row['segments_hex'])]) for row in run['cases']]
    for run in conversions:
        rows += ['\t'.join(['C', run['config'], str(run['include_tofu']).lower(), row['input_hex'], row['status'], row['output_hex']]) for row in run['cases']]
    for run in streams:
        rows += ['\t'.join(['T', run['config'], str(run['include_tofu']).lower(), str(run['keep']), str(run['stride']), row['input_hex'], row['status'], row['output']]) for row in run['cases']]
    (target/'cases.tsv').write_text('\n'.join(rows)+'\n')
    print(json.dumps({'samples': len(valid), 'segment_cases': sum(len(r['cases']) for r in runs),
                      'conversion_cases': sum(len(r['cases']) for r in conversions),
                      'stream_cases': sum(len(r['cases']) for r in streams)}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--probes-ready', action='store_true')
    prepare(parser.parse_args().probes_ready)
