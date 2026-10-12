package io.github.opencckmp

/** Base for errors exposed by the Kotlin OpenCC API. */
open class OpenCCException(message: String, cause: Throwable? = null) : Exception(message, cause)
class FileNotFound(val resourceName: String) : OpenCCException("$resourceName not found or not accessible.")
class FileNotWritable(val resourceName: String) : OpenCCException("$resourceName not writable.")
open class InvalidFormat(message: String, cause: Throwable? = null) : OpenCCException("Invalid format: $message", cause)
class InvalidTextDictionary(message: String, val lineNumber: Int) :
    InvalidFormat("Invalid text dictionary at line $lineNumber: $message")
class InvalidUTF8(message: String, cause: Throwable? = null, encodedContext: ByteArray? = null,
                  val byteOffset: Int? = null) : OpenCCException("Invalid UTF8: $message", cause) {
    private val context = encodedContext?.copyOf()
    /** Caller-owned copy of the failing encoded suffix, when an encoded operation supplies it. */
    val encodedContext: ByteArray? get() = context?.copyOf()
}
class InvalidUTF16(val offset: Int) : OpenCCException("Unpaired UTF-16 surrogate at offset $offset")
class ShouldNotBeHere : OpenCCException("ShouldNotBeHere! This must be a bug.")
class UnavailableSegmentationExtension(val type: String) : OpenCCException("Unavailable segmentation extension: $type")

