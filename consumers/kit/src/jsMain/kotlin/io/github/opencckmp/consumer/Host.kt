package io.github.opencckmp.consumer

internal actual fun clockMillis(): Double = js("performance.now()") as Double
internal actual fun memorySnapshotBytes(): Double? = js("performance.memory ? performance.memory.usedJSHeapSize : null") as Double?
internal actual fun publishReport(report: String) {
    js("globalThis").openccFixtureReport = report
    if (js("typeof document !== 'undefined'") as Boolean) js("document.body").textContent = report
}
