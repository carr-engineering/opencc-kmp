# Focused Swift and JavaScript exports

This separate build consumes published `opencc-core` artifacts and exposes a small host-language
facade. Kotlin clients retain the complete API through the normal KMP modules. It adds no dictionaries,
network client, CLI, tooling or broad framework `export(core)` dependency. Keep it opt-in; ordinary
Kotlin engine consumers do not retain these exports.

`OpenCCResources` owns copies of encoded dictionary/model bytes supplied by the host. JavaScript uses
`Int8Array` for `ByteArray`; Swift uses the generated `KotlinByteArray` wrapper. Binary images stay
binary. Hosts may prepare/download resources asynchronously, then call synchronous `load`/`loadJson`.
This focused facade supports the built-in text/ocd/ocd2 dictionary readers, normalization, group policies,
conversion/prefix/range/UTF-8 operations, inspection, map composition and streaming. Custom segmentation
extensions and optional Jieba assembly remain available through the complete Kotlin modules; this
facade does not silently bundle them. Errors cross the Swift boundary as thrown errors and the JS
boundary as exceptions.

Ranges and segment maps are half-open UTF-16 offsets. Encoded conversion uses `ByteArray` lengths;
returned values require no manual freeing. `close()` releases the session's converter reference.
Existing streams retain their converter and their independently owned buffered state. Resources can
be cleared after loading without invalidating an initialized session.

```sh
./gradlew -p interop jvmTest jsNodeTest macosArm64Test
./gradlew -p interop linkReleaseFrameworkMacosArm64 jsBrowserProductionLibraryDistribution
```

Use `opencc.group`, `opencc.version` and `opencc.repository` to test exact release coordinates. The
build is intentionally separate from the engine graph. Generated declarations and direct Swift/JS
consumer execution are the evidence for export compatibility; app-side wrappers alone do not prove
the shipped facade is usable.

After building, `python3 tools/test_exports.py` compiles and executes an independent Swift consumer and
imports the production ES module from ordinary Node JavaScript. Both inject an actual upstream `.ocd2`
image as binary data, clear the resource store, exercise prefix/UTF-16/UTF-8 conversion, catch errors,
inspect stages and segment ranges, then verify stream lifetime after closing the originating session.
Generated TypeScript definitions are validated during JS compilation; the Swift check rejects broad
core type exports in the generated framework header. Host metrics are recorded in `.cache/exports/report.json`.

`bundleJavaScript` produces the production JS modules, TypeScript declarations and attribution without
source maps. `bundleAppleFramework` packages the release XCFramework for iOS device/simulators and
macOS ARM64/x64 with attribution. Maven root classifiers are `javascript@zip` and `apple@zip`. The
normal target KLIB/JAR publications retain standard KMP routing. The standalone project has its own
ABI baselines and is verified in Apple CI. It consumes the engine publication; it never substitutes
repository project dependencies.

Release documentation JARs include rendered Dokka HTML for the focused facade, together with this
contract guide and its KDoc source. `./gradlew -p interop dokkaGenerate` generates the HTML independently
of publication; release staging depends on that task before embedding notices and signing.
