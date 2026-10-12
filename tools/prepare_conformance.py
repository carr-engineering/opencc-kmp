#!/usr/bin/env python3
"""Prepare upstream-generated text dictionaries for milestone-two conformance tests."""
from pathlib import Path
import shutil
import subprocess
from upstream import ROOT, fetch
from build_oracle import build


def prepare():
    source = fetch()
    oracle = build()
    target = ROOT / ".cache/conformance/text"
    target.mkdir(parents=True, exist_ok=True)
    dict_tool = oracle / "src/tools/opencc_dict"
    if not dict_tool.exists():
        dict_tool = dict_tool.with_suffix(".exe")
    for dictionary in sorted((oracle / "data").glob("*.ocd2")):
        subprocess.run([str(dict_tool), "-i", str(dictionary), "-o", str(target / (dictionary.stem + ".txt")),
                        "-f", "ocd2", "-t", "text"], check=True)
    for config in sorted((source / "data/config").glob("*.json")):
        if config.name.endswith(".schema.json"):
            continue
        subprocess.run(["python3", str(source / "data/scripts/convert_config_dict_format.py"),
                        str(config), str(target / config.name), "text"], check=True)
    print(target)


if __name__ == "__main__":
    prepare()

