#!/usr/bin/env python3
"""Generate a deterministic source/config inventory from the verified release."""
import hashlib
import json
from upstream import ROOT, LOCK, fetch
from upstream_operations import public_declarations
from operation_mapping import mapping
from upstream_tests import test_inventory


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
        for path in sorted(list((source / "src").glob("*.hpp")) + list((source / "src").glob("*.h")) + list((source / "src/plugin").glob("*.h")) + list((source / "plugins/jieba/include").glob("*.hpp")))
        if not any(word in path.stem for word in ("Test", "CmdLine", "WinUtil"))
    }
    operations = {}
    for header in headers:
        records = public_declarations((source / header).read_text())
        for record in records:
            record["kotlin_equivalent"], record["mapping_kind"] = mapping(header, record)
        operations[header] = records
    return {"upstream_test_cases": test_inventory(source, ROOT), "public_declarations": operations, "operation_inventory_scope": "Lexical declarations in every reference header; overloads retained; private/protected and internal namespaces excluded. Family mappings require the detailed contract documents and do not assert differential coverage.", "upstream": LOCK, "configs": configs, "base_configs": sorted(base_configs), "reference_headers": headers,
            "cli_sources": sorted(p.name for p in (source / "src/tools").glob("*.cpp"))}


if __name__ == "__main__":
    output = ROOT / "docs/upstream-inventory.json"
    output.parent.mkdir(parents=True, exist_ok=True)
    result = inventory()
    output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + "\n")
    document = ROOT / "docs/UPSTREAM_OPERATIONS.md"
    if document.exists():
        marker = "## Source operation crosswalk"
        intro = document.read_text().split(marker, 1)[0]
        rows = [intro + marker + "\n\n"]
        for header, records in result["public_declarations"].items():
            if not records:
                continue
            rows.append(f"### `{header}`\n\n| Upstream operation | Kotlin equivalent | Mapping |\n| --- | --- | --- |\n")
            seen = set()
            for record in records:
                identity = (record["owner"], record["symbol"], record["kotlin_equivalent"])
                if identity in seen:
                    continue
                seen.add(identity)
                name = (record["owner"] + "::" if record["owner"] else "") + record["symbol"]
                equivalent = record["kotlin_equivalent"].replace("|", "\\|")
                rows.append(f"| `{name}` | {equivalent} | {record['mapping_kind']} |\n")
            rows.append("\n")
        document.write_text("".join(rows))
    print(output)
