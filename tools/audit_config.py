"""Record JSON acceptance, first schema warning, semantic errors and load options from native OpenCC."""
import argparse
import copy
import json
from pathlib import Path
import re
import random
import subprocess

from build_probes import build_probes
from upstream import ROOT


def fixtures():
    inline = {"type": "inline", "entries": {"汉": "漢", "abc": "ABC", "里": "里"}}
    step = {"dict": inline}
    base = {"name": "audit", "conversion_chain": [step]}
    result = []
    def add(name, value):
        raw = value if isinstance(value, bytes) else value.encode() if isinstance(value, str) else json.dumps(value, ensure_ascii=True).encode()
        result.append((name, raw))
    add("base", base)
    for index, value in enumerate([None, False, True, 0, "x", [], [base]]):
        add(f"nonobject_root_{index}", json.dumps(value))
    for field in ["name", "conversion_chain"]:
        missing = copy.deepcopy(base); missing.pop(field); add("missing_" + field, missing)
    values = [None, False, True, 0, 1.5, "", "x", [], {}, [None], [False], [0], ["x"], [{}]]
    for field in ["name", "segmentation", "conversion_chain", "normalization", "unknown"]:
        for index, value in enumerate(values):
            altered = copy.deepcopy(base); altered[field] = value; add(f"root_{field}_{index}", altered)
    dictionaries = [inline, {"type": "text", "file": "audit.txt"}, {"type": "group", "match_policy": "union", "dicts": [inline]},
                    {"type": "group", "dicts": [inline]}, {"type": "inline", "entries": {}}]
    for kind, dictionary in enumerate(dictionaries):
        add(f"dict_{kind}", {"name": "audit", "conversion_chain": [{"dict": dictionary}]})
        for field in ["type", "file", "entries", "dicts", "match_policy", "may_output_tofu", "unknown"]:
            for index, value in enumerate(values):
                altered = copy.deepcopy(dictionary); altered[field] = value
                add(f"dict_{kind}_{field}_{index}", {"name": "audit", "conversion_chain": [{"dict": altered}]})
        for field in list(dictionary):
            altered = copy.deepcopy(dictionary); altered.pop(field)
            add(f"dict_{kind}_missing_{field}", {"name": "audit", "conversion_chain": [{"dict": altered}]})
    for key in ["", "a/b", "a~b", "~", "/", "汉", "\0", "a\0tail"]:
        for value in ["", "A", "\0", "A\0tail", None, 1, [], {}]:
            add(f"entry_{len(result)}", {"name": "audit", "conversion_chain": [{"dict": {"type": "inline", "entries": {key: value}}}]})
    for segmentation in [{"type": "mmseg", "dict": inline}, {"type": "custom"}, {"type": ""}, {"type": "mmseg\0suffix", "dict": inline}]:
        for field in ["type", "dict", "resources", "extra"]:
            for index, value in enumerate(values + [{"a": "file"}, {"a": 5}, {"a": None}]):
                altered = copy.deepcopy(segmentation); altered[field] = value
                config = copy.deepcopy(base); config["segmentation"] = altered
                add(f"seg_{len(result)}_{field}_{index}", config)
    serialized = json.dumps(base)
    for name, raw in {
        "comments": '/* head */' + serialized.replace('"name"', '/* field */"name"') + '// end',
        "trailing": serialized[:-1] + ',}',
        "trailing_nested": '{"name":"x","conversion_chain":[{"dict":{"type":"inline","entries":{"a":"A",},},},],}',
        "suffix": serialized + ' ignored', "nul_suffix": serialized + '\0 ignored',
        "bom": '\ufeff' + serialized, "leading_nul": '\0' + serialized,
        "unclosed_comment": serialized + ' /*', "duplicate_root": '{"name":"x","conversion_chain":[],"conversion_chain":false}',
        "duplicate_type": '{"name":"x","conversion_chain":[{"dict":{"type":"inline","type":"text","entries":{"a":"A"}}}]}',
        "duplicate_entry": '{"name":"x","conversion_chain":[{"dict":{"type":"inline","entries":{"a":"A","a":"B"}}}]}',
    }.items(): add(name, raw)
    for literal in ['0', '-0', '01', '-01', '.1', '1.', '1e', '1e+', '1e999', '-1e999', '1e-999',
                    '18446744073709551616', '9' * 400, 'NaN', 'Infinity', '-Infinity', '+1', '0x10']:
        add("number_" + str(len(result)), '{"name":"x","conversion_chain":[],"unknown":' + literal + '}')
    # Pinned staged arithmetic differs from mathematical finiteness for some tokens.
    literals = ['0e309', '-0e999', '0.0e310', '0e-9999999999999999999999',
                '9'*400+'e-400', '9'*400+'e-1000', '1.7976931348623157e308',
                '1.7976931348623158e308', '1.7976931348623159e308',
                '0.'+'0'*350+'1e660', '0.'+'0'*350+'1e661',
                '18446744073709551615.999e289', '-9223372036854775808.999e289']
    rng = random.Random(142308)
    for index in range(240):
        count = rng.choice([1, 16, 17, 18, 19, 20, 21, 308, 309, 400])
        digits = str(rng.randrange(1,10))+''.join(str(rng.randrange(10)) for _ in range(count-1))
        split = rng.choice([0, 1, min(count,17), count])
        mantissa = ('0.'+digits) if split==0 else digits[:split]+('.'+digits[split:] if split<count else '')
        exponent = rng.choice([-1000,-400,-309,-308,-1,0,1,308,309,400,1000,308-split])
        literals.append(('-' if index%2 else '')+mantissa+'e'+str(exponent))
    for index,literal in enumerate(literals):
        add('number_range_'+str(index), '{"name":"x","conversion_chain":[],"unknown":'+literal+'}')
    for escape in [r'\ud800', r'\udc00', r'\ud800\udc00', r'\ud800\ud800', r'\ud800x', r'\u12', r'\u-001', r'\u+001', r'\uZZZZ', r'\x20']:
        add("escape_name_" + str(len(result)), '{"name":"' + escape + '","conversion_chain":[]}')
    add("nul_type", {"name": "x", "conversion_chain": [{"dict": {"type": "inline\0suffix", "entries": {"汉": "漢"}}}]})
    add("nul_file", {"name": "x", "conversion_chain": [{"dict": {"type": "text", "file": "audit.txt\0suffix"}}]})
    add("nul_policy", {"name": "x", "conversion_chain": [{"dict": {"type": "group", "match_policy": "union\0suffix", "dicts": [inline]}}]})
    for dictionary in [{"type": "text", "file": "missing.txt", "may_output_tofu": True},
                       {"type": "unknown", "file": 5, "may_output_tofu": True},
                       {"type": "group", "dicts": [{"type": "text", "file": "missing.txt", "may_output_tofu": True}]}]:
        add("risk_" + str(len(result)), {"name": "x", "conversion_chain": [{"dict": dictionary}]})
        add("risk_seg_" + str(len(result)), {"name": "x", "segmentation": {"type": "mmseg", "dict": dictionary}, "conversion_chain": [step]})
    for key in ["a/b", "a~b", "a\0tail", "汉/字~"]:
        extra = copy.deepcopy(base); extra[key] = 5; add("pointer_root_" + str(len(result)), extra)
        extra = copy.deepcopy(base); extra['conversion_chain'][0][key] = 5; add("pointer_step_" + str(len(result)), extra)
    for depth in [257, 300, 512]:
        add(f"deep_ignored_{depth}", '{"name":"x","conversion_chain":[],"ignored":' + '[' * depth + '0' + ']' * depth + '}')
    add("dup_plugin_type", '{"name":"x","segmentation":{"type":"mmseg","type":"custom"},"conversion_chain":[]}')
    add("dup_plugin_type_reverse", '{"name":"x","segmentation":{"type":"custom","type":"mmseg"},"conversion_chain":[]}')
    add("warning_priority", {"conversion_chain": [], "name": 5})
    add("warning_priority_reverse", {"name": 5, "conversion_chain": []})
    return result


def audit(output, probes_ready=False):
    probes = ROOT / ".cache/probes" if probes_ready else build_probes()
    directory = ROOT / ".cache/conformance/config"
    directory.mkdir(parents=True, exist_ok=True)
    (directory / "audit.txt").write_text("汉\t漢\nabc\tABC\n里\t里\n", encoding="utf-8")
    rows = []
    warning = re.compile(rb'warning: config does not conform to schema: document path "(.*?)", schema path "(.*?)", keyword "(.*?)"')
    for name, raw in fixtures():
        path = directory / (name + ".json")
        path.write_bytes(raw)
        for include in [True, False]:
            for mode in ["string", "file"]:
                result = subprocess.run([str(probes / "config_probe"), str(path), mode, str(include).lower(), str(directory)], capture_output=True, timeout=15)
                row = {"id": name, "json_hex": raw.hex(), "mode": mode, "include_tofu": include}
                if result.returncode != 0:
                    row.update(status="NATIVE_ABORT", returncode=result.returncode, stderr_hex=result.stderr.hex())
                else:
                    fields = result.stdout.decode('ascii').strip().split('\t')
                    row['status'] = fields[0]
                    if fields[0] == 'OK': row['output_hex'] = fields[1] if len(fields) > 1 else ''
                    else: row.update(error_kind=fields[1], error_hex=fields[2])
                row['warnings'] = [{"path": path.decode('utf-8', errors='backslashreplace'), "schema_path": schema.decode(), "keyword": keyword.decode()} for path, schema, keyword in warning.findall(result.stderr)]
                rows.append(row)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"upstream": json.loads((ROOT / 'upstream.lock.json').read_text()), "cases": rows}, indent=2) + '\n')
    print(json.dumps({"cases": len(rows), "successes": sum(row['status'] == 'OK' for row in rows), "aborts": sum(row['status'] == 'NATIVE_ABORT' for row in rows), "output": str(output)}, indent=2))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, default=ROOT / '.cache/conformance/config-cases.json')
    parser.add_argument('--probes-ready', action='store_true')
    args = parser.parse_args()
    audit(args.output, args.probes_ready)
