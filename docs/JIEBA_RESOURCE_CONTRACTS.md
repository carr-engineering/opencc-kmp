# Custom Jieba model and resource contracts

This audit targets OpenCC `ver.1.4.2` commit
`025f371dc76b598d77384fbdab90c937471844d8` and its pinned cppjieba headers.
The optional `opencc-jieba` module keeps conversion and model parsing in common
Kotlin. Providers own resource delivery, and the factory supports both synchronous
loading and asynchronous resource preparation.

## Encoded HMM models

`JiebaSegmentation(dictionary, model, userDictionaries)` and
`JiebaSegmentation.fromText(dictionary, model, userDictionaries)` accept a `String`
or `ByteArray` HMM model. A string uses canonical UTF-8 encoding. The byte-array
overload preserves encoded model capabilities: cppjieba masks lead/continuation
bytes and accepts noncanonical emission keys, surrogate rune values and rune
values above the Unicode scalar range. Those keys are stored as integer runes,
without forcing them through Kotlin UTF-16 text. `segmentUtf8` can consume matching
encoded input. The registered factory loads HMM resources through the byte-array
operation. `JiebaSegmentation.fromBytes` also accepts encoded text dictionary and
user-dictionary resources, or a loaded OpenCC `Dict` with encoded user resources. Model construction owns the parsed state; later mutation of source
bytes cannot change a loaded segmenter.

Parsing preserves ASCII trimming, skipped comments and empty rows, four-state
probability rows, B/E/M/S emission row ordering, ignored trailing model rows,
accepted trailing field delimiters and last-value wins for duplicate emission
keys. Probability tokens use the separately audited C numeric parsing contract.
The encoded parser requires exactly one cppjieba rune per emission key.

For malformed model rows, the pinned C++ implementation calls `XCHECK`, which can
terminate the embedding process. Kotlin returns `InvalidFormat` for incomplete
models, incorrect field counts and invalid emission-key encodings. Invalid ordinary
Kotlin text uses its typed text-encoding errors. Process termination and unsafe
memory handling are not library API compatibility requirements.

## Encoded dictionary resources

The packed trie stores integer Rune arcs, so base/user dictionary keys preserve
cppjieba's masked decoder semantics rather than requiring Unicode scalar values.
Ordinary `String` loaders delegate through canonical UTF-8 encoding. Valid
supplementary characters, noncanonical keys, surrogate runes and values above the
Unicode range remain distinct. Lookup uses primitive integer binary searches and
allocates no keys or per-lookup objects. Later duplicate user entries override
previous entries, including differently encoded keys representing the same rune
sequence. Single-rune user protection applies to decoded runes, and failed native
user-word decoding is mirrored by ignoring an unusable word. Invalid base-word
encodings return typed `InvalidFormat` instead of a native fatal abort.

The parser traverses byte ranges without building or retaining a whole-file list
of dictionary lines. Loaded trie/model state is independent of input byte-array
mutation. Merged frequency metadata uses the separately audited complete C
`strtod` token contract, including ASCII leading whitespace, hexadecimal values,
non-finite tokens and C-string termination, while rejecting trailing characters.

## Resources and schema

The factory requires `dict_path` and `model_path`, supports explicit user-dictionary
resources and preserves first-occurrence plugin option lookup. Missing resources
and incorrect JSON value types retain typed Kotlin error capabilities. Unknown
string plugin properties are accepted. Shared dictionary/model dependencies are
prepared before conversion; auxiliary IDF and stopword resources remain part of
the complete resource closure.

The pinned release plugin accepts empty IDF/stopword files. Its direct header test
build asserts on those files because assertions are enabled in that build. The
Kotlin factory preserves release-plugin acceptance, rather than introducing extra
validation for auxiliary data unused by OpenCC's segmentation operation.

## Bounded verification

`tools/audit_jieba_resources.py` runs each model/resource/schema profile in an
isolated subprocess, disables core dumps in that child only, and records accepted
operations, returned errors and process aborts separately. It writes native
fixtures and diagnostics under `.cache/conformance/jieba-resources`. The JVM task
`:opencc-jieba:resourceConformance` compares public Kotlin encoded segmentation
and conversion operations against these fixtures. The full
`:opencc-jieba:upstreamJiebaConformance` task includes this audit.

The matrix contains 109 profiles, twelve encoded inputs per accepted profile, valid
custom models, comments/CRLF/trimming, supplementary and noncanonical emission
keys, duplicate/trailing fields, model truncation, invalid base dictionary rows
and bytes, user overrides, auxiliary-resource presence, plugin schema errors and
duplicate option precedence, encoded base/user keys, ignored malformed user keys,
duplicate encoded user ordering and 38 merged-frequency metadata cases. Native
release results comprise 57 accepted profiles, 31 returned errors and 21 process
aborts. There are 636 encoded segmentation and 684 encoded conversion
comparisons. These counts describe this
bounded matrix, not exhaustive malformed-input parity.

`tools/audit_jieba_trie_layout.py` compares the previous UTF-16 and new Rune
layouts against all 348,986 entries in the complete merged dictionary. Retained
primitive-array element storage increases from 11,755,846 to 12,751,840 bytes:
995,994 additional bytes (8.472%). This is a measured structural storage tradeoff
for complete encoded Rune support; it excludes array/object headers and transient
construction peak memory. The packaged dictionary bytes remain identical. See
`measurements/jieba-rune-layout.json`. Process startup, throughput and peak memory
require the separate performance harness measurements.

Remaining audit scope includes platform filesystem discovery beyond
provider-delivered resources, every merged-dictionary metadata combination and
every possible malformed byte stream. This bounded matrix and the existing full
bundled conformance corpus do not constitute an exhaustive malformed-input proof.
