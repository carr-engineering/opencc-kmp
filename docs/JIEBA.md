# Optional common Kotlin Jieba segmentation

`opencc-jieba` implements the bundled OpenCC 1.4.2 Jieba extension in `commonMain`. It depends only
on core. `opencc-all` includes it; core does not depend on Jieba code, dictionaries or models.
Dictionary frequency routing, prefix search, HMM probabilities/Viterbi, ASCII rules, separator
splitting and user-word precedence run identically through common Kotlin on every target.

```kotlin
import io.github.opencckmp.*
import io.github.opencckmp.jieba.*

val factories = mapOf("jieba" to JiebaSegmentationFactory())
val converter = SimpleConverter("s2twp_jieba", provider, segmentationFactories = factories)
val prepared = prepareConfig("s2twp_jieba", asyncProvider, segmentationFactories = factories)
```

The seven experimental configs are s2t_jieba, s2tw_jieba, s2twp_jieba, s2hk_jieba, s2hkp_jieba,
tw2sp_jieba and hk2sp_jieba. They remain experimental, matching upstream. Both tofu-risk load
options are supported. Preparation is asynchronous when the host provider is asynchronous;
conversion and segmentation are synchronous once their immutable resources are loaded.

## Types, resources and ownership

The upstream path-based JiebaSegmentation constructor maps to the factory plus a ResourceProvider,
or directly to JiebaSegmentation(dictionary: Dict, model: String, userDictionaries: List<String>).
The latter accepts the merged .ocd2 metadata and legacy one-/two-value entries. fromText accepts
cppjieba dictionary text and explicit user-dictionary text values. Kotlin clients supply the user
texts they want; they do not inherit cppjieba's hard-coded build-machine default file path.
Use the factory for the installed-resource defaults and fallback behavior.

The factory receives the usual ordered SegmentationProperties. dict_path and model_path are
required; user_dict_path is optional. Provider names are searched directly, relative to the config
and its parent, then using the pinned development fallback. Thin host providers supply any additional
environment/install search directories. If jieba_merged.ocd2 is absent, the factory tries
jieba.dict.utf8 and user.dict.utf8. A text dictionary uses the installed default user dictionary
unless a nonempty user path is supplied. Auxiliary IDF/stop-word resources are resolved beside the
dictionary, then the model, then the standard jieba_dict directory. Missing required resources
raise OpenCCException with the pinned resource error capability.

Both synchronous creation and suspend prepareResources follow this ordered lookup. Only bytes
found at selected paths are retained in the preparation snapshot. The IDF/stop-word resources are
required by the pinned plugin's initialization and are included in selected pack dependency closure;
Cut does not need to retain their unused keyword-extraction indexes. All original assets remain
available in the complete bundle. Model/dictionary arrays are private and immutable after loading;
per-call scores, backtracking and token ranges are separately owned. Hosts can share one loaded
Segmentation through their factories without global caches or close/free calls.

## Segmentation and encoded boundaries

Direct segment(String) returns Kotlin Segments; text must contain valid UTF-16. Prefix search is
limited to 512 Unicode scalar values as upstream specifies. Equal-score candidates preserve native
shortest-first tie ordering. User-defined single characters are protected from HMM regrouping.
HMM ASCII runs preserve the pinned alphanumeric/optional-decimal rules and separators space, tab,
newline, Chinese comma and ideographic full stop. Returned segment values use the native logical
prefix at NUL, including empty tokens; their count is preserved.

Direct segmentUtf8 accepts meaningful encoded ByteArray ranges and preserves cppjieba's own
permissive rune decoding, including noncanonical bytes. A failed cppjieba decode returns no tokens.
The registered plugin additionally preserves native token alignment/whole-input coverage errors:
explicit-length embedded NUL input reports incomplete coverage; NUL-terminated conversion modes
operate on the logical prefix. Native encoded character widths, malformed/truncated errors and
streaming boundaries remain distinct from strict Kotlin String validation. No UTF-8 byte indexing
is imposed on ordinary Kotlin text operations. Conversion maps retain half-open UTF-16 ranges.

## Verification and limits

The pinned probe compares 2,064 direct segmentation cases using merged dictionaries, text plus
users and text with an explicitly empty user file. It compares 9,632 conversion/error cases across
all seven configs and both risk options, including every upstream testcase input, plugin examples,
supplementary characters, NUL and malformed encoded sequences. Another 4,032 cases compare every
stream chunk and final flush at keep windows 0/16 and byte strides 1/2/7/31. Valid inputs also check
String conversion and composed maps. Eight JVM threads share a real loaded model and compare
native token answers concurrently.

Four portable tests cover routing/HMM/ASCII, user metadata/overrides, scalar limits, byte decoding,
resource fallback, actual suspend/resume, auxiliary closure and facade errors. They run on JVM,
JS, Wasm, macOS and iOS simulator. Independent published full-bundle consumers exercise all seven
configs on JVM/JS/Wasm/iOS and actual web preparation; their optional Jieba dependency is test-only
so engine/data download measurements do not silently gain extension code.

Broader custom numeric/model/malformed-resource acceptance and error audits remain part of overall
conformance work. These checks do not establish physical-device startup, peak-memory or p95 targets;
Jieba's larger model requires its own measurements. A generous consumer conformance timeout is not
a performance allowance. Distribution-wide licensing/export/release checks are still open.

```sh
./gradlew -Popencc.targets=jvm :opencc-jieba:upstreamJiebaConformance
./gradlew -Popencc.targets=jvm,js,wasmJs,macosArm64,iosSimulatorArm64 :opencc-jieba:allTests
python3 tools/test_data.py
python3 tools/test_consumers.py --profile full
```

The algorithms are adapted from OpenCC (Apache-2.0) and cppjieba (MIT), with retained notices in
NOTICE and licenses/CPPJIEBA-LICENSE.
