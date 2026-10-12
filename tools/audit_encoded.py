"""Record pinned native explicit-length, C-string and buffer behavior for encoded input."""
import argparse
import json
from pathlib import Path
import subprocess
import shutil

from build_probes import build_probes
from upstream import ROOT, fetch


def audit(output: Path, probes_ready=False):
    probes = ROOT / ".cache/probes" if probes_ready else build_probes()
    directory = ROOT / ".cache/conformance/encoded"
    directory.mkdir(parents=True, exist_ok=True)
    conversion = {"dict": {"type": "inline", "entries": {"汉": "漢", "字": "Z", "abc": "ABC", "神": "神明"}}}
    segmentation = {"type": "mmseg", "dict": {"type": "inline", "entries": {"汉": "汉", "字": "字", "abc": "abc", "神": "神"}}}
    configs = {
        "unsegmented": {"name": "encoded", "conversion_chain": [conversion]},
        "segmented": {"name": "encoded", "segmentation": segmentation, "conversion_chain": [conversion]},
        "normalized": {"name": "encoded", "normalization": [{"dict": {"type": "inline", "entries": {"神": "神"}}}], "segmentation": segmentation, "conversion_chain": [conversion]},
        "empty": {"name": "encoded", "conversion_chain": []},
    }
    # Include native trie fast paths and nested precedence without substituting text dictionaries.
    entries = {"汉": "漢", "字": "Z", "abc": "ABC", "神": "神明", "𠀀": "甲"}
    text = directory / "audit.txt"
    text.write_text(''.join(key + '\t' + value + '\n' for key, value in sorted(entries.items())), encoding="utf-8")
    for kind in ["text", "ocd", "ocd2"]:
        filename = "audit.txt" if kind == "text" else "audit." + kind
        if kind != "text":
            subprocess.run([str(ROOT / ".cache/oracle/src/tools/opencc_dict"), "-i", str(text), "-o", str(directory / filename), "-f", "text", "-t", kind], check=True, capture_output=True)
        dictionary = {"type": kind, "file": filename}
        for segmented in [False, True]:
            config = {"name": "encoded", "conversion_chain": [{"dict": dictionary}]}
            if segmented:
                config["segmentation"] = {"type": "mmseg", "dict": dictionary}
            configs[kind + ("_segmented" if segmented else "_unsegmented")] = config
    for policy in ["short_circuit", "union"]:
        nested = {"type": "group", "match_policy": policy, "dicts": [
            {"type": "inline", "entries": {"a": "early", "汉": "first"}},
            {"type": "group", "match_policy": "union", "dicts": [{"type": "ocd2", "file": "audit.ocd2"}, {"type": "text", "file": "audit.txt"}]},
        ]}
        configs[policy] = {"name": "encoded", "segmentation": {"type": "mmseg", "dict": nested}, "conversion_chain": [{"dict": nested}, conversion]}
    configs["inline_nul"] = {"name": "encoded", "conversion_chain": [{"dict": {"type": "inline", "entries": {"汉": "漢\0Z", "a\0b": "key"}}}]}
    # Every built-in config is exercised with the unmodified native images, including normalization,
    # optional risk dictionaries, reverse/generated dictionaries and real phrase segmentation.
    source = fetch()
    for resource in (ROOT / ".cache/oracle/data").glob("*.ocd2"):
        shutil.copyfile(resource, directory / resource.name)
    for path in sorted((source / "data/config").glob("*.json")):
        if path.name.endswith(".schema.json"):
            continue
        configs[path.stem] = json.loads(path.read_text(encoding="utf-8"))
    inputs = [text.encode() for text in ["", "汉", "汉字abc", "𠀀汉", "神汉", "汉\0abc字", "pre\0abc\0post字", "\0汉", "⿰汉字 abc", "⿲汉字木字"]]
    inputs += [bytes([lead]) for lead in [0x00, 0x01, 0x7f, 0x80, 0xbf, 0xc0, 0xc1, 0xc2, 0xdf, 0xe0, 0xe2, 0xef, 0xf0, 0xf4, 0xf5, 0xf7, 0xf8, 0xfb, 0xfc, 0xfd, 0xfe, 0xff]]
    special = [bytes.fromhex(value) for value in ["c080", "c141", "ed a0 80", "f4908080", "e2 7f b0", "f880808080", "fc8080808080", "e2 61 62", "e2 00 80"]]
    inputs += special + [b"a" + data + "汉abc字".encode() for data in special]
    inputs += ["⿰".encode() + data + "汉字".encode() for data in special]
    rows = []
    specifications = []
    for data in inputs:
        for length in sorted(set(range(min(len(data), 16) + 1)) | {len(data)}):
            for operation in ["view", "simple", "buffer", "inspect"]:
                specifications.append((operation, length, data.hex()))
        specifications.extend([(operation, -1, data.hex()) for operation in ["simple", "cstring", "buffer"]])
        specifications.extend([(operation, stride, data.hex()) for operation in ["stream0", "stream16"] for stride in [1, 2, 3, 5, 16]])
    wire = "".join(f"{operation}\t{length}\t{data}\n" for operation, length, data in specifications)
    for name, config in configs.items():
        path = directory / (name + ".json")
        path.write_text(json.dumps(config, ensure_ascii=False), encoding="utf-8")
        result = subprocess.run([str(probes / "encoded_probe"), str(path), str(directory)], input=wire, capture_output=True, text=True, timeout=30, check=True)
        lines = result.stdout.splitlines()
        assert len(lines) == len(specifications), (name, len(lines), len(specifications), result.stderr)
        for spec, line in zip(specifications, lines):
            operation, length, encoded = spec
            fields = line.split('\t')
            row = {"config": name, "operation": operation, "length": length, "input_hex": encoded, "status": fields[0]}
            if fields[0] == "OK":
                assert len(fields) in [3, 4] and len(bytes.fromhex(fields[2])) == int(fields[1]), line
                row.update(reported_bytes=int(fields[1]), output_hex=fields[2])
                if len(fields) == 4:
                    row["inspection" if operation == "inspect" else "chunks"] = json.loads(fields[3])
            else:
                assert fields[0] == "ERROR" and len(fields) == 2, line
                row["error_hex"] = fields[1]
            rows.append(row)
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps({"upstream": json.loads((ROOT / "upstream.lock.json").read_text()), "configs": configs, "cases": rows}, ensure_ascii=False, indent=2) + '\n', encoding="utf-8")
    print(json.dumps({"cases": len(rows), "successes": sum(row['status'] == 'OK' for row in rows), "output": str(output)}, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=ROOT / ".cache/conformance/encoded-cases.json")
    parser.add_argument("--probes-ready", action="store_true")
    args = parser.parse_args()
    audit(args.output, args.probes_ready)
