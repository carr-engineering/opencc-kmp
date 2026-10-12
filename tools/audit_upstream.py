#!/usr/bin/env python3
"""Generate a deterministic source/config inventory from the verified release."""
import hashlib
import json
from upstream import ROOT, LOCK, fetch


def references(node):
    result = set()
    if isinstance(node, dict):
        if "file" in node and isinstance(node["file"], str):
            result.add(node["file"])
        for value in node.get("resources", {}).values():
            if isinstance(value, str):
                result.add(value)
        for value in node.values():
            result.update(references(value))
    elif isinstance(node, list):
        for value in node:
            result.update(references(value))
    return result


def inventory():
    source = fetch()
    configs = {}
    base_configs = []
    paths = list((source / "data/config").glob("*.json")) + list((source / "plugins/jieba/data/config").glob("*.json"))
    for path in sorted(paths):
        if path.name.endswith(".schema.json"):
            continue
        configs[path.stem] = {
            "sha256": hashlib.sha256(path.read_bytes()).hexdigest(),
            "resources": sorted(references(json.loads(path.read_text()))),
            "extension": "jieba" if "plugins" in path.parts else None,
        }
        if "plugins" not in path.parts:
            base_configs.append(path.stem)
    headers = {
        str(path.relative_to(source)): hashlib.sha256(path.read_bytes()).hexdigest()
        for path in sorted(list((source / "src").glob("*.hpp")) + list((source / "src").glob("*.h")) + list((source / "src/plugin").glob("*.h")))
        if not any(word in path.stem for word in ("Test", "CmdLine", "WinUtil"))
    }
    return {"upstream": LOCK, "configs": configs, "base_configs": sorted(base_configs), "reference_headers": headers,
            "cli_sources": sorted(p.name for p in (source / "src/tools").glob("*.cpp"))}


if __name__ == "__main__":
    output = ROOT / "docs/upstream-inventory.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(json.dumps(inventory(), indent=2, ensure_ascii=False) + "\n")
    print(output)
