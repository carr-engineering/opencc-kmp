# Text, bytes and buffer contracts

String operations use well-formed UTF-16 and half-open UTF-16 ranges. Ranges cannot split a
supplementary pair; malformed UTF-16 raises InvalidUTF16. `UTF8Util.decode` remains a strict Unicode
decoder. Encoded conversion instead preserves OpenCC's byte traversal, with byte inputs/outputs and
half-open byte ranges.

```kotlin
val encoded = "汉字".encodeToByteArray()
val prefix = converter.convertUtf8Prefix(encoded, 3) // exactly three input bytes
val partial = converter.convertUtf8Prefix(encoded, 1) // preserves partial byte e6
val terminated = converter.convertUtf8(
    "汉\u0000字".encodeToByteArray(), mode = EncodedInputMode.NullTerminated)
```

`convertUtf8` defaults to LengthDelimited. Prefix counts do not round to Unicode boundaries.
NullTerminated stops at the first NUL within the selected range, replacing the native pointer/
`size_t(-1)` convention with an explicit option. Invalid ranges are IllegalArgumentException.
Inputs are never mutated; results are Kotlin-owned and need no freeing.

Native traversal accepts recognized leading-byte widths, including legacy five/six-byte sequences,
masks code points for IDS and preserves bounded partial sequences. Non-canonical encodings are not
rewritten. An unrecognized leading byte at an unmatched traversal position raises InvalidUTF8.
Empty unsegmented chains/pipelines can return bytes without traversal. Diagnostics use safe hex
bytes and offsets in the current stage's input rather than native pointers or malformed strings.

NUL behavior is config-dependent and applies to String equivalents too. Unsegmented conversion
processes the complete length-delimited input. Segmented conversion consumes each initial segment
up to its first NUL; subsequent matched segments can still convert. Normalization precedes main
segmentation. `convertWithMap` represents dropped text as deletions and composes their UTF-16 ranges
through the pipeline. Inline JSON validates original string lengths, then stores upstream's key/
value prefixes at NUL.

`convertUtf8To` defaults to NullTerminated input, matching the native buffer overload. Select
LengthDelimited to copy the complete explicit-range conversion. Offset/range/capacity checks are
safe: insufficient space fails before output mutation. Counts are bytes actually written, without
a C terminator. Kotlin buffers copy the complete logical result; unsafe strcpy/allocation mechanics
are not requirements. Text `convertTo` counts UTF-16 units.

`inspectUtf8` returns EncodedInspectionResult with owned input/output arrays, initial snapshots,
numbered stages and recursive pipeline traces. Native inspection reports segment snapshots and
every stage up to NUL. Thus inspection output can differ from conversion for NUL-containing
unsegmented input. String inspection has the same behavior with String results. Compare arrays by
content; results require no manual freeing.

`Utf8ConverterStream` complements String-based ConverterStream. Both are mutable owned sessions
with a default tail of 16 characters. Encoded streaming retains incomplete UTF-8/IDS tails and
matches native backwards-boundary validation, which can reject malformed sequences accepted by
one-shot conversion. Finish flushes partial bytes; empty finish does not invoke conversion. Failures
retain pending input for retry/finish. Serialize calls to each session. OpenCCHandle supports the
same byte ranges, modes and buffers with its context-scoped errors and independent close lifetime.

Standard TextDict, BinaryDict, DartsDict, MarisaDict and groups implement direct encoded prefix
lookup, returning stored entries and candidates. Text tries walk canonical key scalars without
decoding a substring per lookup; Darts walks octets. `keyByteLength` is cached; UTF8Util.byteLength
counts without allocating an array. Custom Dict/Segmentation/Converter implementations may override
encoded methods; default adapters decode valid text through String operations. Document a custom
extension's byte acceptance separately.

The differential corpus compares 71,775 cases across 29 configs, including all 16 built-ins with
original images, text/ocd/ocd2 fixtures, nested groups, normalization, empty chains and inline NUL
values. It checks byte prefixes, explicit/C-string/buffer operations, inspection trees, streaming
chunks and flushes; valid inputs also check String equivalents and composed maps. Eight common
contract tests run on JVM, JS, Wasm, macOS and iOS simulator. See [the audit](ENCODED_CONTRACT_AUDIT.md).
Jieba/CLI, complete utility inventory, malformed resource formats and release/size gates remain
separate acceptance work.

Encoded ambiguity operations are available through the experimental convertWithAmbiguitiesUtf8 and
Utf8AmbiguityStream APIs. Their typed spans use byte offsets/lengths; output and source arrays belong
to callers. String ambiguity operations retain UTF-16 units. Encoded walks preserve noncanonical,
partial and NUL input before a consuming JSON writer applies its own strict encoding policy. Stream
source IDs are global, definitions are emitted once and returned arrays cannot mutate that table.

InvalidUTF8 can include an owned encodedContext and byteOffset from a failing encoded walk. The
context is the logical suffix within that operation's supplied byte range, ending at NUL; its getter
returns a copy. Offsets refer to the failing stage buffer, which can be normalized/converted text.
Ordinary exception messages remain Kotlin strings. Strict text-decoder failures need not supply byte
context. No operation reads past Kotlin array/range bounds to reproduce a native diagnostic pointer.
