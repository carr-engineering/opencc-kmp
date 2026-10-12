"""Audit bounded custom HMM/resource/schema cases against the pinned native Jieba plugin.

Each malformed model runs in its own subprocess. A native abort is recorded as
such, never relabelled as a native validation exception or a parity pass.
"""
import argparse
import json
import os
import subprocess
from pathlib import Path
from build_probes import build_probes
from upstream import ROOT

DICTIONARY = '勇敢 10 n\n的 1 x\n士兵 10 n\n的士 1 n\n'
EMISSION = '未:0,知:0,中:0,文:0,勇:0,敢:0,的:0,士:0,兵:0,汉:0,𠀀:0,𠮷:0'
MODEL = '\n'.join(['0 -3.14e100 -3.14e100 0',
    '-3.14e100 0 -3.14e100 -3.14e100', '0 -3.14e100 -3.14e100 -3.14e100',
    '-3.14e100 0 -3.14e100 -3.14e100', '0 -3.14e100 -3.14e100 -3.14e100'] + [EMISSION] * 4)
INPUTS = [value.encode() for value in ['勇敢的士兵未知中文𠀀𠮷', '未知中文', '汉汉未知', 'abc1.2 ，未知。', '']]
INPUTS += [bytes.fromhex(value) + '未知'.encode() for value in ['c0af', 'e44141', 'eda080', 'f5808080']]
INPUTS += [value.encode() for value in ['/未知', chr(0x4041) + '未知', '𠀀未知']]


def child_setup():
    # Only the test child is affected; malformed XCHECK cases must not fill disks with core files.
    import resource
    resource.setrlimit(resource.RLIMIT_CORE, (0, 0))


def probe(arguments, environment):
    kwargs = {'preexec_fn': child_setup} if os.name == 'posix' else {}
    result = subprocess.run([str(ROOT / '.cache/probes/jieba_probe'), *map(str, arguments)],
        input=''.join(value.hex() + '\n' for value in INPUTS), text=True,
        capture_output=True, encoding='utf-8', errors='backslashreplace', env=environment, timeout=10, **kwargs)
    status = 'ok' if result.returncode == 0 else ('abort' if result.returncode < 0 or result.returncode in (134, 139) else 'error')
    return {'status': status, 'exit_code': result.returncode, 'lines': result.stdout.splitlines(),
            'diagnostic': result.stderr[-1200:]}


def profiles():
    rows = []
    def add(name, model=MODEL, dictionary=DICTIONARY, user='', idf='字 1\n', stop='的\n', schema=None, merged_token=None, kind='base'):
        rows.append(dict(name=name, model=model, dictionary=dictionary, user=user, idf=idf, stop=stop, schema=schema, merged_token=merged_token, kind=kind))
    add('baseline')
    add('comments-and-crlf', model='\r\n # ignored\r\n' + MODEL.replace('\n', '\r\n\r\n # comment\r\n') + '\r\n')
    add('ascii-trimming', model='\n'.join('\t\v\f ' + line + ' \r\t' for line in MODEL.splitlines()))
    add('ignored-extra-lines', model=MODEL + '\ninvalid ignored tail\n')
    add('trailing-emission-delimiters', model=MODEL.replace(EMISSION, EMISSION + ','))
    add('trailing-value-delimiters', model=MODEL.replace('未:0', '未:0:'))
    add('duplicate-emission-last-wins', model=MODEL.replace('未:0', '未:100,未:0'))
    add('ascii-emission', model=MODEL.replace('未:0', 'A:0'))
    add('nul-emission', model=MODEL.replace('未:0', '\x00:0'))
    add('overlong-emission', model=MODEL.encode().replace('未:0'.encode(), bytes.fromhex('c0af') + b':0'))
    add('masked-continuation-emission', model=MODEL.encode().replace('未:0'.encode(), bytes.fromhex('e44141') + b':0'))
    add('surrogate-emission', model=MODEL.encode().replace('未:0'.encode(), bytes.fromhex('eda080') + b':0'))
    add('outside-unicode-emission', model=MODEL.encode().replace('未:0'.encode(), bytes.fromhex('f5808080') + b':0'))
    add('empty-model', model='')
    add('comments-only-model', model='# comment\n \n\t# another\n')
    add('missing-emission-row', model='\n'.join(MODEL.splitlines()[:-1]))
    add('missing-probability-field', model=MODEL.replace('0 -3.14e100 -3.14e100 0', '0 0 0', 1))
    add('extra-probability-field', model=MODEL.replace('0 -3.14e100 -3.14e100 0', '0 0 0 0 0', 1))
    add('repeated-space-probability', model=MODEL.replace('0 -3.14e100', '0  -3.14e100', 1))
    add('tab-probability', model=MODEL.replace('0 -3.14e100', '0\t-3.14e100', 1))
    add('repeated-emission-separator', model=MODEL.replace('未:0,知:0', '未:0,,知:0'))
    add('missing-emission-value', model=MODEL.replace('未:0', '未:'))
    add('empty-emission-key', model=MODEL.replace('未:0', ':0'))
    add('multiple-rune-emission-key', model=MODEL.replace('未:0', '未知:0'))
    add('invalid-leading-emission-byte', model=MODEL.encode().replace('未:0'.encode(), b'\xff:0'))
    add('truncated-emission-key', model=MODEL.encode().replace('未:0'.encode(), b'\xe4\xb8:0'))
    add('bom-comment-model', model='\ufeff# comment\n' + MODEL)
    add('empty-base-dictionary', dictionary='')
    add('blank-base-dictionary-row', dictionary=DICTIONARY + '\n')
    add('missing-base-dictionary-field', dictionary='勇敢 10\n')
    add('extra-base-dictionary-field', dictionary='勇敢 10 n extra\n')
    add('repeated-base-dictionary-space', dictionary='勇敢  10 n\n')
    add('trailing-base-dictionary-delimiter', dictionary=DICTIONARY.replace('10 n', '10 n '))
    add('crlf-base-dictionary', dictionary=DICTIONARY.replace('\n', '\r\n'))
    add('invalid-base-dictionary-byte', dictionary=b'\xff 10 n\n')
    add('truncated-base-dictionary-key', dictionary=b'\xe4\xb8 10 n\n')
    for label, encoded in [('overlong', 'c0af'), ('masked', 'e44141'), ('surrogate', 'eda080'), ('outside-unicode', 'f5808080')]:
        key = bytes.fromhex(encoded)
        add('encoded-base-' + label, dictionary=key + '未知'.encode() + b' 100 n\n' + DICTIONARY.encode())
        add('encoded-user-' + label, user=key + b'\n')
    add('supplementary-base-key', dictionary='𠀀未知 100 n\n' + DICTIONARY)
    add('overlong-user-protection', user=bytes.fromhex('f0869caa') + b'\n')
    add('invalid-user-key-ignored', user=b'\xff\n' + '未\n'.encode())
    add('truncated-user-key-ignored', user=b'\xe4\xb8\n' + '未\n'.encode())
    add('duplicate-user-order', user='未 10 n\n未知 100 n\n未知 1 n\n')
    add('duplicate-encoded-user-order', user='未知 100 n\n'.encode() + bytes.fromhex('f0869caa') + '知 1 n\n'.encode())
    tokens = ['0x1p4', ' 12.5', '\t12.5', '\v\f12.5', '12.5 ', '12.5suffix', '12.5\x00suffix',
              '12.5e', '12.5e+', '12.5e0', '1.5e-400', '-0', '0', '-1', '\u00a012.5', '\u200312.5', '1.5\n', 'INF', 'NaN']
    for kind in ['base', 'user_freq']:
        for index, token in enumerate(tokens):
            add(f'merged-{kind}-{index:02}', merged_token=token, kind=kind)
    add('empty-user-dictionary', user='')
    add('single-rune-user-protection', user='未\n')
    add('two-field-user-tag', user='未 tag\n')
    add('ignored-extra-user-fields', user='未 1 tag extra\n')
    add('idf-malformed-lines-ignored', idf='bad\n\n字 1\n')
    add('idf-empty-release-assertion', idf='')
    add('stopwords-empty-release-assertion', stop='')
    for name in ['missing-dict-path', 'missing-model-path', 'missing-dict-resource', 'missing-model-resource',
                 'missing-user-resource', 'missing-idf-resource', 'missing-stopwords-resource',
                 'numeric-dict-path', 'null-model-path', 'array-resources', 'unknown-string-property',
                 'unknown-numeric-property', 'duplicate-first-valid', 'duplicate-first-missing']:
        add(name, schema=name)
    return rows


def prepare(probes_ready=False):
    if not probes_ready:
        build_probes()
    output = ROOT / '.cache/conformance/jieba-resources'
    output.mkdir(parents=True, exist_ok=True)
    environment = os.environ.copy()
    environment['OPENCC_SEGMENTATION_PLUGIN_PATH'] = str(ROOT / '.cache/oracle/plugins/jieba')
    environment.pop('OPENCC_DATA_DIR', None)
    records = []
    fixture_rows = []
    for profile in profiles():
        directory = output / profile['name']
        directory.mkdir(parents=True, exist_ok=True)
        files = {key: directory / name for key, name in [('dictionary', 'merged.ocd2' if profile['merged_token'] is not None else 'dict.txt'), ('model', 'model.utf8'),
                 ('user', 'user.txt'), ('idf', 'idf.utf8'), ('stop', 'stop_words.utf8')]}
        for key, file in files.items():
            data = profile[key]
            file.write_bytes(data.encode() if isinstance(data, str) else data)
        if profile['merged_token'] is not None:
            subprocess.run([str(ROOT / '.cache/probes/jieba_merged_fixture_probe'), str(files['dictionary']),
                            profile['merged_token'].encode().hex(), profile['kind']], check=True)
        resources = {'dict_path': str(files['dictionary']), 'model_path': str(files['model']),
                     'user_dict_path': str(files['user'])}
        segmentation = {'type': 'jieba', 'resources': resources}
        schema = profile['schema']
        if schema == 'missing-dict-path': resources.pop('dict_path')
        elif schema == 'missing-model-path': resources.pop('model_path')
        elif schema in ['missing-dict-resource', 'missing-model-resource', 'missing-user-resource']:
            key = {'missing-dict-resource': 'dict_path', 'missing-model-resource': 'model_path', 'missing-user-resource': 'user_dict_path'}[schema]
            resources[key] = str(directory / 'missing')
        elif schema == 'missing-idf-resource': files['idf'].unlink()
        elif schema == 'missing-stopwords-resource': files['stop'].unlink()
        elif schema == 'numeric-dict-path': resources['dict_path'] = 42
        elif schema == 'null-model-path': resources['model_path'] = None
        elif schema == 'array-resources': segmentation['resources'] = []
        elif schema == 'unknown-string-property': segmentation['custom'] = 'ignored'
        elif schema == 'unknown-numeric-property': segmentation['custom'] = 42
        document = json.dumps({'name': profile['name'], 'segmentation': segmentation, 'conversion_chain': []})
        if schema in ['duplicate-first-valid', 'duplicate-first-missing']:
            valid, missing = json.dumps(str(files['dictionary'])), json.dumps(str(directory / 'missing'))
            values = [valid, missing] if schema.endswith('valid') else [missing, valid]
            document = document.replace('"dict_path": ' + valid, '"dict_path": ' + values[0] + ', "dict_path": ' + values[1])
        config = directory / 'config.json'
        config.write_text(document)
        direct = probe(['segment', files['dictionary'], files['model'], files['user'], directory], environment) if not schema else None
        factory = probe(['convert', config, directory, 'false', directory], environment)
        records.append({'name': profile['name'], 'direct': direct, 'factory': factory})
        fixture_rows.append('\t'.join([profile['name'], str(directory), direct['status'] if direct else 'skip',
                                      factory['status'], files['dictionary'].name]))
        if direct and direct['status'] == 'ok':
            assert len(direct['lines']) == len(INPUTS), profile['name']
            for value, line in zip(INPUTS, direct['lines']):
                status, count, segments = line.split('\t')
                assert status == 'OK'
                fixture_rows.append('\t'.join(['S', profile['name'], value.hex(), count, segments]))
        if factory['status'] == 'ok':
            assert len(factory['lines']) == len(INPUTS), profile['name']
            for value, line in zip(INPUTS, factory['lines']):
                fixture_rows.append('\t'.join(['C', profile['name'], value.hex(), line]))
    report = {'upstream': 'ver.1.4.2', 'scope': 'bounded HMM/custom resource/plugin schema matrix', 'profiles': records}
    (output / 'report.json').write_text(json.dumps(report, indent=2, ensure_ascii=True) + '\n')
    (output / 'cases.tsv').write_text('\n'.join(fixture_rows) + '\n')
    print(json.dumps({'profiles': len(records), 'factory_accepted': sum(r['factory']['status'] == 'ok' for r in records),
                      'factory_errors': sum(r['factory']['status'] == 'error' for r in records),
                      'factory_aborts': sum(r['factory']['status'] == 'abort' for r in records), 'file': str(output / 'report.json')}))
    return output


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--probes-ready', action='store_true')
    prepare(parser.parse_args().probes_ready)
