# Alignment, candidates and script detection

`Converter.convertWithMap(text)` is a Kotlin extension beyond upstream. It returns `MappedConversion`
with the same output as `convert` and an ordered `map` of half-open UTF-16 ranges. The map covers all
input and output, including empty output from deletion. No boundary splits a supplementary pair.
Each dictionary match and each unmatched scalar/complete IDS is atomic. When a later match crosses
earlier boundaries, the composed entry covers their full range; it cannot safely assign part of a
phrase replacement to one source character. Normalization and every pipeline stage participate.
Host converters without a chain expose one conservative whole-input transformation.

```kotlin
val mapped = converter.convertWithMap("神𠀀")
for (entry in mapped.map) {
    val sourceRange = entry.sourceStart until entry.sourceEnd
    val targetRange = entry.targetStart until entry.targetEnd
}
```

The experimental upstream-equivalent APIs require `@OptIn(ExperimentalOpenCCApi::class)`:

- `getAllConversions(word)` carries all exact-match candidates through the chain, preserving
  discovery order and deduplicating after each stage. Prefix conversion still feeds later stages,
  but at least one exact match is required for a nonempty result. Key-only entries pass through.
- `convertWithAmbiguities(text)` returns default output plus potential one-to-many spans. Offsets
  and lengths count UTF-16 units. Sources are deduplicated in first-use order and refer to normalized
  input when a normalization pre-pass exists. Later candidate convergence does not remove a span.
- `AmbiguityStream` reports spans relative to each chunk, globally indexed sources, newly defined
  sources and `sourceCount`. Its windows match `ConverterStream`; sessions are caller-owned.
- Converters without one chain return an empty candidate list and `analyzed = false` annotations.
  Empty stream chunks retain upstream's default `analyzed = true` until a window is analyzed.

Inline JSON dictionary values are **one literal string**, including any spaces. Ordered multi-value
entries come from text/binary dictionaries or `DictEntry(key, values)`; the inline config syntax
does not encode a candidate list.

`opencc-detect` is an optional Kotlin extra. It depends on core only and has no bundled data or
network dependency. Supply dictionaries explicitly or use `ScriptDetector.fromResources(provider)`
to load `STCharacters`, `TSCharacters`, `HKVariants`, `TWVariants`, `HKPhrases` and `TWPhrases` as
ocd2 resources. `includeRegions = false` loads just the two character dictionaries.

The simplified-only set is ST keys minus TS keys minus ST candidate values; the traditional-only
set is the mirror rule. Detection counts distinguishing Unicode scalars. Default simplified and
traditional ratio thresholds are 0.8 and 0.2, respectively; zero votes is Neutral, otherwise ratios
between the thresholds are Mixed. Fewer than three votes gives Low confidence. All thresholds are
configurable. For Traditional text, each region scores greedy nonoverlapping matches against that
region's variant and phrase candidate values; higher score wins, ties yield Generic. Reported
scores expose this heuristic. It is not an upstream conversion rule or a certainty about locale.
