package io.github.magalz.borealis.nativecore

/** Safe loader for one JNI library; never throws into the caller. */
@Suppress(
    "FunctionOnlyReturningConstant",
    "UnusedParameter",
) // Stub until the tested implementation.
class NativeLibrary(name: String, loader: (String) -> Unit = System::loadLibrary) {
    /** Loads the library once and returns whether it is usable. */
    fun load(): Boolean = false

    /** Runs a native call, returning null when the library is unavailable or the call fails. */
    fun <T> call(operation: String, block: () -> T?): T? = null
}
