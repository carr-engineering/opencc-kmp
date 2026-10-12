# Command-line tools

The separate opencc-cli module provides opencc, opencc_dict and opencc_phrase_extract on JVM and
Kotlin/Native desktop targets. Conversion, framing and tool logic are in commonMain; file/stream
I/O, filesystem identity, executable lookup and number formatting use thin platform adapters.
No native OpenCC library, JNI or reflection is used at runtime. opencc-cli is not a core dependency.

## Build and distribution

```sh
./gradlew -Popencc.targets=jvm :opencc-cli:bundleJvmCli
./gradlew -Popencc.targets=macosArm64 :opencc-cli:bundleMacosArm64Cli
```

Archives live in opencc-cli/build/distributions. JVM archives include Unix/Windows launchers and
runtime JARs; JDK 17+ is supported. Native archives include three release executables and complete
offline data in share/opencc. All distributions include LICENSE, NOTICE, AUTHORS and third-party
licence notices. Library modules/data packs remain separate choices for smaller application graphs.

The Native tasks are bundleMacosArm64Cli, bundleMacosX64Cli, bundleLinuxX64Cli and bundleMingwX64Cli;
linking requires a supported host/toolchain. JVM and macOS Arm64 archives have been unpacked and
launched from an unrelated working directory with data/plugin environment variables removed.
Windows/Linux source compilation is verified; their execution/distribution checks remain CI work.

## Pinned options and contracts

opencc preserves -c/--config, -i/--input, -o/--output, --path (repeatable), --noflush (numeric bool
value), --in-place, --resource-zip (also --resource-zip=value), --measured_result, --segmentation,
--inspect, --ambiguities and --include-tofu-risk-dictionaries. Common help/version/ignore-rest and
TCLAP argument/constraint/duplicate diagnostics are retained. The default config is s2t.json and
the CLI skips tofu-risk dictionaries unless explicitly enabled; library defaults are unchanged.
The three structured output modes are mutually exclusive.

The pinned parser's value-argument delimiter is a space; it accepts an embedded token such as
'-c s2t.json' but not GNU-style --config=s2t.json. --resource-zip's equals form is its explicit
exception. --noflush expects numeric 0/1 rather than the text 'true'. --help is available as -h,
including native combined-short-switch behavior. --version describes the pinned 1.4.2 tool contract;
the port's own version remains in artifact metadata.

Plain conversion and ambiguity JSONL use 1 MiB byte blocks and native keep/flush boundaries.
Plain output preserves the pinned fputs behavior at NUL, including its measured byte-accounting
convention. Inspection/segmentation process arbitrarily long lines: stdin preserves a final CR,
file mode removes it, LF separators are emitted between records and no final separator is added.
Segmentation mode calls the segmenter directly rather than running conversion or normalization.
Inspection retains the native input/segments/stages/pipelineStages/output schema. Human JSON output
preserves native byte handling; ambiguity JSONL strictly validates emitted source/output encoding,
reports a failing stream without an end record, and uses define-on-first-use global source IDs.
A variation-selector warning is emitted once, including selectors split across input blocks.

Measured JSON preserves config/input/output, mode/output_mode, load_ms/convert_ms/write_ms/total_ms
and input_bytes/output_bytes. Times are host measurements, not byte-identical values or benchmark
claims. Record separators and plain NUL framing retain the pinned accounting behavior. Default
flushes occur per conversion chunk/inspection line, and ambiguity records flush per chunk.

Config lookup searches the working path, package resources, explicit paths/executable-relative
locations and OPENCC_DATA_DIR. OPENCC_PACKAGE_DATA_DIR configures the port's install-data location;
otherwise Native distributions use ../share/opencc. JVM uses its packaged classpath data and
launchers report their own executable location. Dictionary lookup prioritizes the selected config
directory. Windows native paths, arguments and environment values use typed UTF-16 APIs. Stored
runtime ZIPs follow [ZIP_RESOURCES.md](ZIP_RESOURCES.md); compressed data transport bundles are
unpacked by build/host tooling. The common Jieba factory retains all seven experimental configs.

--in-place updates a nearby temporary file, closes streams before replacement, follows symlink
identity and preserves owner/group/mode on POSIX. Multiple hard links are rejected by the Native
file-identity adapter and POSIX JVM filesystem attributes. Windows JVM link-count parity still
requires validation beyond its available standard filesystem attributes; it is not a verified gate.
Failure cleanup retains the original input and removes the owned temporary file.

opencc_dict preserves repeatable -i/--input, -o/--output, -f/--from and -t/--to, all five input
formats and three output formats, ordered cppjieba base/user inputs and deterministic binary output.
opencc_phrase_extract takes positional input files and -o/--output, applies the pinned two-character
extraction defaults and emits its seven statistical columns with native six-significant-digit
formatting. It also retains the reference's silent failed file-stream behavior and success status
for caught runtime extraction errors. Parser failures still exit 1. Conversion/dictionary failures
exit 1; successful operations/help/version exit 0.

## Verification scope

```sh
./gradlew -Popencc.targets=jvm,macosArm64 :opencc-cli:writeJvmClasspath \
  :opencc-cli:linkOpenccReleaseExecutableMacosArm64 \
  :opencc-cli:linkOpencc_dictReleaseExecutableMacosArm64 \
  :opencc-cli:linkOpencc_phrase_extractReleaseExecutableMacosArm64
python3 tools/test_cli.py
python3 tools/test_cli.py --backend native
python3 tools/test_cli_distributions.py
```

The process matrix compares stdout/stderr, exit codes, generated files, input mutation/mode and
measured-result schemas with the pinned tools. It covers every bundled config in all four output
modes, malformed bytes, NUL/CRLF/variation selectors, stored ZIPs, long UTF-8 input, source definitions,
in-place/symlink/hard-link behavior, binary format combinations, cppjieba metadata and nonempty
phrase statistics. Native/JVM diagnostics include encoded byte context without changing the
library's ordinary Kotlin error-message policy.

Broader argument, malformed-resource/model, filesystem failure and environment/install search
edge audits remain part of final conformance. Windows/Linux runtime checks, published executable
releases, physical-device benchmarks and download regression gates are still open. The source/build
and local distribution checks do not claim the whole PLAN.md objective is complete.
