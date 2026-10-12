#!/usr/bin/env python3
"""Build the pinned OpenCC reference tools/tests, never linked to the Kotlin runtime."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
from upstream import ROOT, CACHE, fetch


def build(cmake=None):
    source = fetch()
    local = ROOT / ".cache/cmake-tools/cmake/data/bin/cmake"
    cmake = cmake or shutil.which("cmake") or (str(local) if local.exists() else None)
    if not cmake:
        raise SystemExit("CMake is required for the test oracle. Install CMake or pass --cmake PATH.")
    output = ROOT / ".cache/oracle"
    env = dict(os.environ, GIT_CEILING_DIRECTORIES=str(CACHE))
    log_path = ROOT / ".cache/oracle-build.log"
    try:
        with log_path.open("w") as log:
            subprocess.run([cmake, "-S", str(source), "-B", str(output),
                            "-DCMAKE_BUILD_TYPE=Release", "-DENABLE_GTEST=ON",
                            "-DBUILD_OPENCC_JIEBA_PLUGIN=OFF", "-DOPENCC_ENABLE_INSTALL=OFF",
                            "-DBUILD_SHARED_LIBS=OFF"], check=True, env=env, stdout=log, stderr=subprocess.STDOUT)
            subprocess.run([cmake, "--build", str(output), "--parallel", "6"], check=True, env=env, stdout=log, stderr=subprocess.STDOUT)
    except subprocess.CalledProcessError:
        print("\n".join(log_path.read_text().splitlines()[-60:]), file=__import__("sys").stderr)
        raise
    print(f"Pinned OpenCC reference ready: {output} (log: {log_path})")
    return output


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cmake")
    args = parser.parse_args()
    print(build(args.cmake))
