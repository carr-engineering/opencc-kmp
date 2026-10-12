# Initial release APK size evidence

`measurements/android-initial.json` records the first independently resolved, release-shrunk
consumer profiles. Android APKs use the same Activity/Kotlin baseline, SDK 36/min SDK 23 and default
R8 optimization, with no OpenCC keep rules. Data is staged from published ZIP classifiers. The
measurer verifies every manifest hash, exact selected file sets, deduplication and absence of native
runtime libraries or nested data archives in each APK.

| Profile | APK bytes | Increment over baseline | Dependency artifact bytes |
| --- | ---: | ---: | ---: |
| Baseline | 23,549 | 0 | 1,859,465 |
| Engine, inline config | 37,623 | 14,074 | 2,024,071 |
| s2t pack | 558,045 | 534,496 | 2,537,038 |
| s2t + t2s pack | 593,359 | 569,810 | 2,571,854 |
| Complete offline bundle | 6,733,842 | 6,710,293 | 8,613,430 |

These are actual APK/container bytes, not claims about Google Play delivery or a device-specific
App Bundle split. Dependency totals count resolved runtime artifacts plus the selected archive;
they exclude POM/module metadata, sources and build tools. Kotlin stdlib is present in the baseline.
The report separates raw/compressed data and DEX bytes and records dependency coordinates, byte
sizes and SHA-256 values. This initial report predates the optional common Jieba implementation;
its resources were already included. Current algorithm evidence is recorded separately in PROGRESS.md.

The small engine profile exercises inline conversion. R8 may remove APIs it does not use; the
published core library still contains the full implemented API. The one/multi/full profiles exercise
binary dictionary resource loading. Features are selected by modules, application calls and data
closure, rather than omitted from the library distribution.

Regenerate/check with locally published current artifacts:

```sh
./gradlew -Popencc.targets=jvm,android,iosArm64,iosSimulatorArm64,js,wasmJs \
  :opencc-core:publishAllPublicationsToConsumerRepository \
  :opencc-platform:publishAllPublicationsToConsumerRepository \
  :opencc-data-s2t:publishAllPublicationsToConsumerRepository \
  :opencc-data-s2t-t2s:publishAllPublicationsToConsumerRepository \
  :opencc-data:publishAllPublicationsToConsumerRepository
python3 tools/measure_android.py --check
```

The initial regression allowance is measured baseline + 5% + 4,096 bytes for each profile's
incremental APK and dependency-artifact sizes. Update baselines only with a reviewed, explained
measurement and passing conformance. This is an initial Android size gate, not completion of the
plan's browser/Native delivery, startup, peak-memory, physical-device performance or compression
tradeoff gates. Those remain required before release.

Production web and installed Swift profile evidence now supplements these APK measurements; see
[WEB_AND_APPLE_MEASUREMENTS.md](WEB_AND_APPLE_MEASUREMENTS.md) for their separate baselines,
reproducible size gates, Native size-option evaluation and measurement limitations.

## Byte-preserving Android publication compression

`measurements/android-aar-compression.json` measures the attributed current implementation against
the same budgets. `measurements/aar-compression.json` compares alternative nested ZIP layouts using
identical compiled entries and records their hashes. All 127 engine JAR entries match the preceding
published artifact byte for byte, including class files and Kotlin metadata.

Storing the inner JAR entries lets the outer AAR's standard DEFLATE compressor share redundancy
across classes. The engine AAR falls from 235,272 to 159,808 bytes (32.1%), while its intermediate
JAR grows from 241,158 to 511,197 bytes. No runtime representation, public API or decoder changes.
Python extraction/build timings in the audit are diagnostics, not device performance evidence.

| Profile | Incremental APK bytes | Incremental dependency bytes |
| --- | ---: | ---: |
| Engine | 17,289 | 159,808 |
| s2t | 536,628 | 675,850 |
| s2t + t2s | 571,947 | 710,667 |
| Complete offline | 6,712,453 | 6,752,265 |

Every APK is unchanged in size from the preceding attributed-publication measurement. All eight
Android application/download budget checks now pass without increasing any allowance. The complete
API stays in the published engine. Four artifact-payload/routing tests and actual published consumer checks
pass; the byte audit covers all entry names, ordering and contents. Regenerate the compression audit
with `python3 tools/audit_aar_compression.py` after local publication.
