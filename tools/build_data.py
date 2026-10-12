#!/usr/bin/env python3
"""Build complete or configuration-selected, deterministic binary OpenCC resource bundles."""
import argparse
import hashlib
import json
from pathlib import Path
import shutil
import struct
import sys
import zipfile
from upstream import ROOT, LOCK, fetch
from build_oracle import build


def references(node):
    result = set()
    if isinstance(node, dict):
        if isinstance(node.get("file"), str):
            result.add(node["file"])
        for name in node.get("resources", {}).values():
            if isinstance(name, str):
                result.add(name)
        for value in node.values():
            result.update(references(value))
    elif isinstance(node, list):
        for value in node:
            result.update(references(value))
    return result


def inventory(source, oracle):
    configs = {path.stem: path for path in (source / "data/config").glob("*.json")
               if not path.name.endswith(".schema.json")}
    configs.update({path.stem: path for path in (source / "plugins/jieba/data/config").glob("*.json")})
    resources = {path.name: path for path in (oracle / "data").glob("*.ocd2")}
    for path in (source / "plugins/jieba/deps/cppjieba/dict").glob("*.utf8"):
        # Match the upstream installed files, excluding unrelated POS dictionaries.
        if path.name in {"jieba.dict.utf8", "user.dict.utf8", "hmm_model.utf8", "idf.utf8", "stop_words.utf8"}:
            resources["jieba_dict/" + path.name] = path
    merged = oracle / "plugins/jieba/jieba_dict/jieba_merged.ocd2"
    if not merged.exists():
        matches = sorted((oracle / "plugins/jieba").glob("*/jieba_dict/jieba_merged.ocd2"))
        if not matches:
            raise ValueError("Upstream Jieba merged dictionary was not generated")
        merged = matches[0]
    resources["jieba_dict/jieba_merged.ocd2"] = merged
    return configs, resources


def create_bundle(output, selected=None, oracle=None, archive=None):
    source = fetch()
    oracle = oracle or build()
    configs, resources = inventory(source, oracle)
    selected = sorted(set(selected or configs))
    unknown = set(selected) - set(configs)
    if unknown:
        raise ValueError("Unknown configurations: " + ", ".join(sorted(unknown)))
    dependencies = {name: sorted(references(json.loads(configs[name].read_text()))) for name in selected}
    required = set(resources) if len(selected) == len(configs) else set().union(*map(set, dependencies.values()))
    missing = required - set(resources)
    if missing:
        raise ValueError("Unresolved config resources: " + ", ".join(sorted(missing)))
    files = {name + ".json": configs[name] for name in selected}
    files.update({name: resources[name] for name in sorted(required)})
    files.update({name: ROOT / name for name in ("LICENSE", "NOTICE", "AUTHORS", "licenses/marisa.txt", "licenses/darts-clone.txt")})
    files["licenses/cppjieba.txt"] = source / "plugins/jieba/deps/cppjieba/LICENSE"
    manifest = {
        "schema_version": 1, "upstream": LOCK, "configs": dependencies,
        "image_byte_order": sys.byteorder, "image_word_size": struct.calcsize("P") * 8,
        "files": {name: {"size": path.stat().st_size,
                         "sha256": hashlib.sha256(path.read_bytes()).hexdigest()} for name, path in sorted(files.items())},
    }
    output = Path(output).resolve()
    output.mkdir(parents=True, exist_ok=True)
    expected = set(files) | {"manifest.json"}
    # Only prune files from a directory created by this tool with its previous manifest.
    old_manifest = output / "manifest.json"
    if old_manifest.exists():
        previous = json.loads(old_manifest.read_text())
        for name in set(previous.get("files", {})) - expected:
            target = output / name
            if target.resolve().is_relative_to(output):
                target.unlink(missing_ok=True)
    for name, source_file in sorted(files.items()):
        target = output / name
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(source_file, target)
    old_manifest.write_text(json.dumps(manifest, ensure_ascii=False, sort_keys=True, indent=2) + "\n")
    archive = Path(archive).resolve() if archive else output.with_suffix(".zip")
    archive.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(archive, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as bundle:
        for name in sorted(expected):
            info = zipfile.ZipInfo("opencc/" + name, date_time=(1980, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            bundle.writestr(info, (output / name).read_bytes(), compresslevel=9)
    return {"configs": selected, "resource_count": len(required),
            "raw_bytes": sum(spec["size"] for spec in manifest["files"].values()) + old_manifest.stat().st_size,
            "zip_bytes": archive.stat().st_size, "zip_sha256": hashlib.sha256(archive.read_bytes()).hexdigest(),
            "directory": str(output), "archive": str(archive)}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--configs", help="Comma-separated config names; omit for all built-in and Jieba data")
    parser.add_argument("--output", type=Path, default=ROOT / "opencc-data/build/bundle/opencc")
    parser.add_argument("--archive", type=Path, help="Optional zip destination outside runtime resource folders")
    parser.add_argument("--oracle", type=Path, help="Already prepared pinned reference build (Gradle prepares it once)")
    args = parser.parse_args()
    try:
        print(json.dumps(create_bundle(args.output, args.configs.split(',') if args.configs else None,
                                      oracle=args.oracle, archive=args.archive), indent=2))
    except ValueError as error:
        parser.error(str(error))
