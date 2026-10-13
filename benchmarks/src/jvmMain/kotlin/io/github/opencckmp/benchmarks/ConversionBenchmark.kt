package io.github.opencckmp.benchmarks

import io.github.opencckmp.SimpleConverter
import io.github.opencckmp.platform.FileResourceProvider
import java.nio.file.Path
import java.nio.file.Files
import kotlinx.benchmark.*

/** Explicitly open for JMH; no all-open or runtime reflection dependency enters the library. */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(BenchmarkTimeUnit.MILLISECONDS)
open class ConversionBenchmark {
    @Param("s2t", "t2s", "s2twp")
    var config: String = "s2t"
    private lateinit var provider: FileResourceProvider
    private lateinit var converter: SimpleConverter
    private lateinit var shortText: String
    private lateinit var largeText: String

    @Setup
    open fun prepare() {
        val resources = Path.of(System.getenv("OPENCC_BENCH_RESOURCES")
            ?: error("Set OPENCC_BENCH_RESOURCES to a generated complete/selected resource directory"))
        provider = FileResourceProvider(listOf(resources.toAbsolutePath().toString()))
        converter = SimpleConverter(config, provider)
        shortText = BenchmarkCorpus.thousandCharacters
        largeText = Files.readString(Path.of(System.getenv("OPENCC_BENCH_CORPUS")
            ?: error("Run through tools/run_benchmarks.py to supply the shared native/JVM corpus")))
        // Compare the complete result to the pinned native engine outside the measured operations.
        val expected = Path.of(System.getenv("OPENCC_BENCH_EXPECTED")
            ?: error("Set OPENCC_BENCH_EXPECTED to the native reference outputs directory"))
        check(converter.convert(largeText) == Files.readString(expected.resolve("$config.txt")))
        check(largeText.toByteArray(Charsets.UTF_8).size == 1_048_576)
    }

    @Benchmark open fun loaded1000(): String = converter.convert(shortText)
    @Benchmark open fun loaded1MiB(): String = converter.convert(largeText)

    /** Includes config parsing and dictionary restoration; filesystem OS caches may be warm. */
    @Benchmark open fun initializeAndConvert1000(): String = SimpleConverter(config, provider).convert(shortText)
}

internal object BenchmarkCorpus {
    private const val unit = "中华人民共和国台湾香港软件鼠标后台开发 简体繁體日本語 ASCII123\n"
    val thousandCharacters: String = unit.repeat(1000 / unit.length + 1).take(1000)
}
