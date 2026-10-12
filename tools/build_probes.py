"""Build small test probes against the verified native upstream library."""
import os
import subprocess
from build_oracle import build
from upstream import ROOT, fetch


def build_probes():
    source = fetch()
    oracle = build()
    target = ROOT / ".cache/probes"
    target.mkdir(parents=True, exist_ok=True)
    for probe in sorted((ROOT / "tools/oracle").glob("*_probe.cpp")):
        output = target / probe.stem
        extra = []
        if probe.stem == "jieba_probe":
            plugin = source / "plugins/jieba"
            extra = ["-I", str(plugin / "include"), "-I", str(plugin / "deps/cppjieba/include"),
                     str(plugin / "src/JiebaSegmentation.cpp")]
        subprocess.run([os.environ.get("CXX", "c++"), "-std=c++17", "-O2", "-DOpencc_BUILT_AS_STATIC",
                        "-I", str(source / "src"), *extra, str(probe), str(oracle / "src/libopencc.a"),
                        str(oracle / "deps/marisa-0.3.1/libmarisa.a"), "-o", str(output)], check=True)
    return target


if __name__ == "__main__":
    print(build_probes())
