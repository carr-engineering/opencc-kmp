package io.github.opencckmp.consumer

import kotlinx.cinterop.*
import platform.posix.*

@OptIn(ExperimentalForeignApi::class)
internal actual fun clockMillis(): Double = memScoped {
    val time = alloc<timespec>()
    check(clock_gettime(CLOCK_MONOTONIC.toUInt(), time.ptr) == 0)
    time.tv_sec * 1000.0 + time.tv_nsec / 1_000_000.0
}
@OptIn(ExperimentalForeignApi::class)
internal actual fun memorySnapshotBytes(): Double? = memScoped {
    val usage = alloc<rusage>()
    if (getrusage(RUSAGE_SELF, usage.ptr) != 0) null else usage.ru_maxrss.toDouble()
}
internal actual fun publishReport(report: String) { println(report) }

/** The Swift fixture exports one small facade, keeping dependency APIs out of the framework header. */
object ConsumerExample {
    fun run(): String {
        var result: String? = null
        runMeasurement { result = it }
        return result ?: error("Bundled resource preparation unexpectedly suspended")
    }
}
