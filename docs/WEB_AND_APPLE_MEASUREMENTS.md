# Production web and Swift consumer evidence

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
