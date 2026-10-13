# Filesystem configuration loading

`FileConfigLoader` in `opencc-platform` supplies the local-file equivalents of
OpenCC's filesystem/provider `Config` overloads. The engine still performs parsing
and conversion in `commonMain`, with no filesystem, networking, data or tooling
dependency.

| Upstream operation | Kotlin equivalent |
| --- | --- |
| `Config::NewFromFile(fileName)` | `FileConfigLoader().newFromFile(fileName)` |
| `Config::NewFromFile(fileName, provider, options)` | `FileConfigLoader(provider).newFromFile(fileName, options)` |
| `Config::NewFromFile(fileName, paths, argv0, options)` | `FileConfigLoader(searchPaths = paths, executablePath = argv0).newFromFile(fileName, options)` |
| `Config::NewFromString(json, configDirectory)` | `FileConfigLoader().newFromString(json, configDirectory)` |
| `Config::NewFromString(json, paths, options)` | `FileConfigLoader(searchPaths = paths).newFromString(json, options = options)` |
| `Config::NewFromString(json, provider, options)` | `Config(provider).newFromString(json, "", options, null)`; the fourth parameter preserves declared provider names |

The loader tries an explicit provider first for configuration bytes. Only
`FileNotFound` triggers fallback to local configuration discovery. This preserves
provider configuration precedence over a conflicting file in the working directory,
and permits an absolute host configuration to use dictionaries supplied through a
ZIP or another provider.

Explicit dictionary providers receive dictionary filenames as declared, including
absolute paths and literal POSIX backslashes. They are not prefixed with the
configuration directory. Without an explicit provider, dictionaries search the
configuration directory, the working directory, then supplied roots. Relative
parent dictionary paths remain ordinary filesystem paths. Locally readable
configuration discovery and regular-file dictionary resolution use their separate
upstream rules; a configuration directory itself returns `FileNotFound`.

`searchPaths`, optional `executablePath`, `packageDataDirectory` and `dataDirectory`
provide host filesystem locations. The executable's parent supplements caller
paths. Package lookup supports the `.json` suffix used by upstream package lookup.
`OPENCC_DATA_DIR` supplies the default data-directory fallback on filesystem hosts;
passing `dataDirectory = null` disables it. The environment value is captured when
the loader is constructed. Package installation locations and Windows portable
application/module locations belong to the host; supply their directories through
these inputs rather than requiring engine dependencies or C++ build constants.
Browser hosts retain provider/async preparation operations; synchronous filesystem
access reports typed missing-file errors.

The real configuration directory remains available as `__config_dir` to registered
segmentation factories. Custom dictionary readers, factories, advisory warning
sinks, load options and explicitly owned dictionary caches pass through the
adapter. Each call creates independent loading state. The loader does not close a
caller-owned cache or provider.

The existing common `Config.newFromFile` and three-argument `newFromString`
operations retain their logical-resource behaviour: names resolve relative to a
logical configuration directory and normalize provider separators. The additive
four-argument `newFromString` separates plugin/configuration metadata from resource
name resolution. A nullable `resourceBaseDirectory` means that `null` passes names
verbatim to an already-rooted provider; a nonnull string uses logical relative name
resolution. This keeps both provider conventions explicit and avoids filesystem
I/O inside core.

Validation compares filesystem/ZIP provider integration with the pinned native
`ver.1.4.2` reference: separate configuration/dictionary roots, first-match ordering,
provider configuration precedence, absolute host fallback with ZIP dictionaries,
parent paths, Unicode filenames, environment discovery, load options, missing
resource errors and literal POSIX backslashes. Tests also verify custom factories,
reader registration, warning propagation and cache delegation. The separate
provider differential covers regularity, resource snapshots and cache freshness.

Cross-compilation checks every published target. Actual Windows/Linux/Android
runtime validation remains a host CI requirement, independent of these source-level
contracts and locally executed differential assertions.
