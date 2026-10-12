#!/usr/bin/env python3
"""Fetch and verify the immutable OpenCC reference. Never imported by the runtime."""
import argparse
import hashlib
import json
from pathlib import Path
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
LOCK = json.loads((ROOT / "upstream.lock.json").read_text())
CACHE = ROOT / ".cache" / "upstream"
ARCHIVE = CACHE / "opencc-1.4.2.tar.gz"
SOURCE = CACHE / ("OpenCC-" + LOCK["commit"])


def verify():
    digest = hashlib.sha256(ARCHIVE.read_bytes()).hexdigest()
    if digest != LOCK["archive_sha256"]:
        raise SystemExit(f"Upstream archive checksum mismatch: {digest}")
    return digest


def fetch():
    CACHE.mkdir(parents=True, exist_ok=True)
    if not ARCHIVE.exists():
        temporary = ARCHIVE.with_suffix(".download")
        try:
            with urllib.request.urlopen(LOCK["archive_url"], timeout=90) as response:
                with temporary.open("wb") as output:
                    while chunk := response.read(1024 * 1024):
                        output.write(chunk)
            if hashlib.sha256(temporary.read_bytes()).hexdigest() != LOCK["archive_sha256"]:
                raise SystemExit("Downloaded upstream archive checksum mismatch")
            temporary.replace(ARCHIVE)
        finally:
            temporary.unlink(missing_ok=True)
    verify()
    if not SOURCE.exists():
        with tarfile.open(ARCHIVE) as archive:
            archive.extractall(CACHE, filter="data")
    return SOURCE


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=["fetch", "verify"])
    args = parser.parse_args()
    if args.command == "fetch":
        print(fetch())
    else:
        print(verify())

