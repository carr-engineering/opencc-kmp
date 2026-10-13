This standalone Wasm/browser benchmark consumes published `opencc-core`,
`opencc-platform`, and the deduplicated `opencc-data-s2t:resources@zip` pack. Its
Gradle dependency graph is isolated from the repository and size-consumer fixtures.

Run `python3 tools/run_web_benchmark.py` from the repository root after publication
and native reference setup. The driver creates the same 1000-character corpus as
JVM/Android and records the exact pinned native s2t output and SHA-256 values.
BrowserResourceProvider fetches resources asynchronously; loaded conversion is
synchronous. Networking remains in the host adapter.

Each of 30 fresh Chrome processes/profiles/pages prepares s2t, verifies its first
complete output, warms 100 calls, and measures 300 conversion-only intervals with
`performance.now`. Full output equality and sampled heap checks occur outside
those intervals. Reports retain every sample, per-browser p50/p95, pooled p50/p95,
navigation-to-ready startup, fixture fetch, resource fetch/preparation, first call,
browser/OS identity and timer resolution.

The gzip localhost server measures a local desktop deployment. OS disk caches may
remain warm. Chrome JS heap samples and final CDP metrics are observations, not
process RSS, Wasm linear-memory size, or a measured allocation peak.
