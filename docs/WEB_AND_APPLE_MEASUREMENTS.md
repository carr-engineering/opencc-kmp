# Production web and Swift consumer evidence

The final operation/resource audit passes every unchanged size gate. Current production evidence
is recorded in [web-parallel-completion.json](../measurements/web-parallel-completion.json) and
[apple-parallel-completion.json](../measurements/apple-parallel-completion.json):

| Profile | JS incremental gzip bytes | Wasm incremental gzip bytes | Swift app incremental ZIP bytes |
| --- | ---: | ---: | ---: |
| Engine | 27,458 | 17,742 | 98,838 |
| s2t | 535,006 | 535,457 | 627,170 |
| s2t + t2s | 569,419 | 569,883 | 662,483 |
| Complete bundle | 6,606,618 | 6,607,081 | 6,705,837 |

Apple uses the explicitly evaluated `smallBinary` option. Every profile executes successfully;
these three-launch fixture measurements retain the limited timing/memory scope described below.
The original baselines and budgets remain unchanged.

The independent consumer build resolves locally published KMP artifacts. Each profile has a
separate output directory, preventing stale dictionaries from influencing smaller profiles.
Production web distributions omit source maps, use JS dead-code elimination/minification or Wasm
Binaryen optimization, and retain exact resource-manifest contents and hashes.

| Profile | JS incremental gzip bytes | Wasm incremental gzip bytes | Swift app incremental ZIP bytes |
| --- | ---: | ---: | ---: |
| Baseline | 0 | 0 | 0 |
| Engine, inline config | 24,338 | 17,270 | 91,147 |
| s2t | 531,185 | 531,563 | 618,583 |
| s2t + t2s | 565,602 | 566,003 | 653,951 |
| Complete bundle | 6,602,782 | 6,603,178 | 6,697,296 |

The detailed reports are `measurements/web-initial.json` and `measurements/apple-initial.json`.
Web totals count individual files compressed with gzip level 9, including all packaged data.
Browsers decode HTTP compression themselves: no additional codec is linked into core. Reports
separate code/data/other raw and compressed bytes and actual first-load HTTP payloads. The full
distribution packages all data but only requests resources needed by the selected initial
conversion; lazy acquisition does not reduce its full offline package size.

Every production profile executed in headless Chrome, including the optimized Wasm module. Three
fresh navigations per profile checked prepared conversion plus fifty 1,000-character conversions.
The installed Swift app links a static release KMP framework with a small public facade, reads
resources from its app bundle through the published provider, and stores an authoritative result
for the runner. All five profiles passed three installations/launches each on an isolated temporary
iPhone 17 Pro/iOS 26 simulator. The runner deletes only the simulator it created.

Warm 1,000-character conversion measurements were generally below 0.3 ms on this Apple Arm64 host.
Initial data preparation/conversion was approximately 180–211 ms for JS, 99–107 ms for Wasm and
97–113 ms in the main Swift simulator run. A separate Swift run under different host load took
186–209 ms. These small samples are diagnostic measurements, not robust physical-device/browser
p95 claims. The source records all runs instead of selecting the fastest.

Memory is explicitly scoped: web reports sampled JS heap plus Chrome protocol metrics, excluding
unmeasured process and Wasm linear-memory peaks. Swift reports Darwin peak process RSS, including
UIKit/runtime overhead. The main Swift baseline was about 109 MiB and the data profiles about
196–202 MiB. Preparation allocations and shared dictionary caching therefore remain optimization
work; package-size measurements alone do not satisfy memory/startup acceptance.

`measurements/apple-small.json` evaluates Kotlin 2.4.0 `smallBinary` on the same s2t app. Its
executable fell from 1,247,064 to 1,151,176 bytes and its ZIP container from 867,805 to 826,729 bytes.
The installed app checks passed. The option is incompatible with debug builds, so validation
explicitly switched to release test binaries: 57 common tests passed across macOS/iOS simulator
engine tests and macOS dictionary-writer/phrase/detection tests. The experimental option stays off
by default. The separate three-launch experiment took 167–220 ms for initial conversion; it does
not establish an improvement in startup performance or satisfy the physical-device release gate.

```sh
python3 tools/measure_web.py --check
python3 tools/measure_apple.py --check
python3 tools/measure_apple.py --profiles one --small-binary \
  --output .cache/measurements/apple-small-current.json
./gradlew -Popencc.targets=macosArm64,iosSimulatorArm64 \
  -Popencc.native.smallBinary=true :opencc-core:macosArm64Test :opencc-core:iosSimulatorArm64Test
```

Measured container-size budgets permit baseline increment + 5% + 4,096 bytes. Web and Apple gates
are independently reproducible. They do not replace App Store/Play delivery measurements,
physical-device performance, comprehensive peak-memory profiling, the planned benchmark corpus
and CI/release conformance checks. Additional internal compression/representations still require
complete dictionary semantics and measured total decoder/startup/memory tradeoffs.

The subsequent dictionary-construction experiment retains a primitive-array UTF-16 trie builder.
It avoids allocating a map and object for every trie node while keeping the same lookup layout,
public entry identities, candidates and file readers. The s2t app ZIP fell by 668 bytes and its
simulator peak RSS was 152–154 MiB, versus 196–198 MiB in the initial run. JS gzip increased by
79 bytes and Wasm by 707 bytes; first conversion was 154–171 ms and 78–82 ms respectively.
Simulator first conversion varied from 113–173 ms, so these observations establish reduced
preparation allocation, not a physical-device startup improvement. Android release size budgets
also passed. The full 65,175-entry upstream dictionary comparison now checks every prefix match.

A separate direct-MARISA lookup prototype was rejected: it saved similar simulator memory but
added 6,664 app ZIP bytes, increased browser decoder size and slowed JS preparation/hot lookup.
The default reader still restores dictionary keys and uses the packed UTF-16 trie. Both experiments
preserved conversion output and candidates. Complete three-run evidence is retained in the
`web-primitive-trie`, `apple-primitive-trie`, `web-compact-marisa` and `apple-compact-marisa` JSON
reports in `measurements/`; the compact-MARISA implementation is not shipped.

The encoded contract milestone adds faithful byte traversal/inspection/streaming and config-dependent
NUL behavior, retaining all features in core. Internal JSON field holders remove unused generated
copy/equality code, saving 3,265 Android dependency bytes and 479 APK bytes versus that checkpoint.
All production web profiles and installed Swift conversions passed. The new Native engine-only ZIP
increment is 102,182 bytes, exceeding its original 99,800-byte allowance by 2,382 bytes; selected/full
profiles pass. Android engine dependency downloads likewise exceed the earlier partial-engine budget
by 12,411 bytes. These failed gates are recorded, not widened or treated as release acceptance.
See `measurements/android-lean-json.json`, `measurements/apple-encoded.json` and
[ENCODED_API.md](ENCODED_API.md) for the exact sizes and contracts.

## Current decimal parser and explicit Native production option

The attributed-publication report exposed a Native regression: the general Kotlin float parser
pulled in regex code, with engine ZIP increment 303,491 bytes. Common JSON already validates decimal
grammar; its range checker now follows the pinned RapidJSON arithmetic instead. The engine contains
no regex symbols. All 4,952 config cases and 71,775 encoded cases pass alongside conversion/format
checks, including newly covered zero/overflow/exponent behavior. Powers of ten use 36 constants and
per-exponent ULP corrections; tests compare all 309 values against pinned literals bit for bit on
JVM/JS/Wasm/macOS/iOS, including Native release builds. No API capability is removed.

The current `apple-decimal-compact-small.json` pairs every profile with a baseline built using the
same `smallBinary` option. Incremental ZIP bytes are engine 99,054; one 624,630; multi 659,910;
complete 6,703,271. All unchanged budgets pass and each installed app passes three launches.
The initial larger decimal table was rejected for its Android APK cost before this compact variant.
Earlier default-option reports remain evidence of that earlier configuration, rather than a claim
that every possible consumer build meets the optimized production budget.

The Apple CI size gate explicitly passes `--small-binary`. With the pinned Kotlin 2.4 toolchain,
production consumers can set `binaryOption("smallBinary", "true")` on release frameworks/executables;
debug builds do not support it. General library/build defaults stay off. Current release validation
covers engine tests on macOS/iOS, cache concurrency, Jieba and stored ZIP tests, in addition to the
installed Swift fixtures. This is a measured size setting, not physical-device p95 proof.

Regenerate the current Apple gate with:

```sh
python3 tools/measure_apple.py --check --small-binary
```

`web-decimal-compact.json` records the matching production/browser run. All JS/Wasm budgets pass:
engine gzip increments are 27,425/17,715 bytes, one 534,927/535,263, multi 569,340/569,706,
complete 6,606,536/6,606,902. These are paired application/transport measurements, rather than Maven
dependency estimates. JVM/JS/Wasm/iOS published-consumer checks also pass with 148 HTTP requests.
