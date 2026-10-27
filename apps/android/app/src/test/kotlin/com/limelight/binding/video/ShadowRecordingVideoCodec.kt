package com.limelight.binding.video

import android.media.MediaCodec
import android.media.MediaCrypto
import android.media.MediaFormat
import android.view.Surface
import java.io.IOException
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.BlockingQueue
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowMediaCodec
import org.robolectric.shadows.ShadowMediaCodec.CodecConfig
import org.robolectric.util.ReflectionHelpers

/** What the renderer did to the fake codecs, one entry per call that matters, in order. */
sealed interface CodecEvent {
    data class Created(val codec: Int, val name: String) : CodecEvent

    /** `configure`: the format as the codec received it, key by key, and the surface it got. */
    data class Configured(val name: String, val format: Map<String, Any>, val surface: Surface?) :
        CodecEvent

    /** `setVideoScalingMode`, which the renderer calls between `configure` and `start`. */
    data class Scaling(val codec: Int, val mode: Int) : CodecEvent

    data class Started(val codec: Int) : CodecEvent

    data class Stopped(val codec: Int) : CodecEvent

    data class Reset(val codec: Int) : CodecEvent

    data class Released(val codec: Int) : CodecEvent

    /** `queueInputBuffer`: the buffer index, the timestamp, the flags and the bytes queued. */
    data class Queued(
        val codec: Int,
        val index: Int,
        val ptsUs: Long,
        val flags: Int,
        val hex: String,
    ) : CodecEvent

    /**
     * `releaseOutputBuffer`: with `render` for the overload that takes a boolean, with `atNs` for
     * the one that takes a render time in nanoseconds (0 means never drop the frame).
     */
    data class Output(val codec: Int, val index: Int, val render: Boolean?, val atNs: Long?) :
        CodecEvent
}

/** The calls of the fake codec that a test can make fail. */
enum class CodecPoint {
    CREATE,
    CONFIGURE,
    START,
    DEQUEUE_INPUT,
    QUEUE_INPUT,
    DEQUEUE_OUTPUT,
    RELEASE_OUTPUT,
}

/**
 * The `ShadowMediaCodec` of Robolectric with a journal and failures a test scripts. It keeps what
 * that shadow does: ten input buffers, an output buffer for each, and a fake codec per decoder name
 * (`ShadowMediaCodec.addDecoder`) that turns a queued input into an output. This one also records
 * what the renderer does to the codecs, answers the calls that Robolectric leaves native
 * (`stop`, `reset`, `release`), and throws what a test asks for at the next call of a kind.
 *
 * Use it with `@Config(shadows = [ShadowRecordingVideoCodec::class])`. State lives in the
 * companion, which every test of one Robolectric sandbox shares: call [reset] before each test.
 */
@Implements(MediaCodec::class)
open class ShadowRecordingVideoCodec : ShadowMediaCodec() {
    private val id = ids.incrementAndGet()

    init {
        instances += this
    }

    /**
     * Gives back every output that the codec holds, as a consumer that renders nothing would. A test
     * that sends many frames without a renderer thread uses it: ten buffers are all the fake has.
     */
    private fun giveBackOutputs() {
        val info = MediaCodec.BufferInfo()
        while (true) {
            val index = super.native_dequeueOutputBuffer(info, 0)
            if (index == MediaCodec.INFO_TRY_AGAIN_LATER) return
            if (index >= 0) super.releaseOutputBuffer(index, false)
        }
    }

    @Implementation
    protected override fun __constructor__(name: String, nameIsType: Boolean, encoder: Boolean) {
        fire(CodecPoint.CREATE)
        super.__constructor__(name, nameIsType, encoder)
        journal += CodecEvent.Created(id, name)
    }

    @Implementation
    protected fun setVideoScalingMode(mode: Int) {
        journal += CodecEvent.Scaling(id, mode)
    }

    @Implementation
    protected override fun native_start() {
        fire(CodecPoint.START)
        super.native_start()
        journal += CodecEvent.Started(id)
    }

    @Implementation
    protected fun native_stop() {
        journal += CodecEvent.Stopped(id)
    }

    @Implementation
    protected fun native_reset() {
        journal += CodecEvent.Reset(id)
    }

    @Implementation
    protected fun native_release() {
        journal += CodecEvent.Released(id)
    }

    @Implementation
    protected fun native_getSupportedVendorParameters(): List<String> = emptyList()

    @Implementation
    protected override fun getBuffer(input: Boolean, index: Int): ByteBuffer? {
        if (input && nullInputBuffers.getAndDecrement() > 0) return null
        return super.getBuffer(input, index)
    }

    @Implementation
    protected override fun native_dequeueInputBuffer(timeoutUs: Long): Int {
        dequeueInputCalls.incrementAndGet()
        fire(CodecPoint.DEQUEUE_INPUT)
        if (starveInput) {
            Thread.sleep(STARVE_SLEEP_MS)
            return -1
        }
        return super.native_dequeueInputBuffer(timeoutUs)
    }

    @Implementation
    protected override fun native_queueInputBuffer(
        index: Int,
        offset: Int,
        size: Int,
        presentationTimeUs: Long,
        flags: Int,
    ) {
        fire(CodecPoint.QUEUE_INPUT)
        val buffer = super.getBuffer(true, index)
        val bytes = ByteArray(size)
        if (buffer != null) buffer.duplicate().apply { position(offset) }.get(bytes)
        journal +=
            CodecEvent.Queued(id, index, presentationTimeUs, flags, hex(bytes))
        if (flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
            consumeParameterSets(index)
            return
        }
        val delay = outputDelayNanos
        if (delay > 0) {
            val until = System.nanoTime() + delay
            while (System.nanoTime() < until) Thread.onSpinWait()
        }
        val outputPts = outputTimestamp?.invoke(presentationTimeUs) ?: presentationTimeUs
        super.native_queueInputBuffer(index, offset, size, outputPts, flags)
    }

    /**
     * A real decoder takes parameter sets without giving an output frame for them: the input buffer
     * is free again at once, as after a released frame, and no output is made.
     */
    private fun consumeParameterSets(index: Int) {
        val waiting = ReflectionHelpers.getField<MutableList<Int>>(this, "inputBuffersPendingQueuing")
        val free = ReflectionHelpers.getField<BlockingQueue<Int>>(this, "inputBuffersPendingDequeue")
        waiting.remove(Integer.valueOf(index))
        getBuffers(true)[index].clear()
        free.add(index)
    }

    @Implementation
    protected override fun native_dequeueOutputBuffer(
        info: MediaCodec.BufferInfo,
        timeoutUs: Long,
    ): Int {
        dequeueOutputCalls.incrementAndGet()
        fire(CodecPoint.DEQUEUE_OUTPUT)
        outputAnswers.poll()?.let {
            return it
        }
        return super.native_dequeueOutputBuffer(info, timeoutUs)
    }

    @Implementation
    protected override fun releaseOutputBuffer(index: Int, render: Boolean) {
        fire(CodecPoint.RELEASE_OUTPUT)
        journal += CodecEvent.Output(id, index, render, null)
        super.releaseOutputBuffer(index, render)
    }

    @Implementation
    protected override fun releaseOutputBuffer(index: Int, renderTimestampNs: Long) {
        fire(CodecPoint.RELEASE_OUTPUT)
        journal += CodecEvent.Output(id, index, null, renderTimestampNs)
        super.releaseOutputBuffer(index, renderTimestampNs)
    }

    /**
     * The fake codec of one decoder name: it copies each input to its output, as the default
     * codec of Robolectric does, and records the format it is configured with.
     */
    class FakeDecoder(private val name: String) : CodecConfig.Codec {
        override fun process(input: ByteBuffer, output: ByteBuffer) {
            output.put(input)
        }

        override fun onConfigured(
            format: MediaFormat?,
            surface: Surface?,
            crypto: MediaCrypto?,
            flags: Int,
        ) {
            journal += CodecEvent.Configured(name, entriesOf(checkNotNull(format)), surface)
            fire(CodecPoint.CONFIGURE)
        }
    }

    companion object {
        /** The size of every input and output buffer of the fake codecs. */
        const val BUFFER_SIZE = 16 * 1024
        private const val STARVE_SLEEP_MS = 1L
        private val ids = AtomicInteger()

        @JvmStatic val journal = CopyOnWriteArrayList<CodecEvent>()

        private val instances = CopyOnWriteArrayList<ShadowRecordingVideoCodec>()

        /** How many times the renderer asked for an input buffer, whatever the answer. */
        @JvmStatic val dequeueInputCalls = AtomicInteger()

        /** How many times the renderer asked for an output buffer, whatever the answer. */
        @JvmStatic val dequeueOutputCalls = AtomicInteger()

        /**
         * How many of the next calls of `getInputBuffer` answer null, which the documentation allows
         * "if the index is not a dequeued input buffer".
         */
        @JvmStatic val nullInputBuffers = AtomicInteger()

        /**
         * How long the fake takes to turn a frame into an output, from the call that queues it. A
         * test uses it to give the decoder a time that it can then read back.
         */
        @Volatile @JvmStatic var outputDelayNanos = 0L

        /** While true, no input buffer is ever free: `dequeueInputBuffer` times out. */
        @Volatile @JvmStatic var starveInput = false

        /**
         * The timestamp that an output frame carries for an input queued with the one given; null
         * keeps it. Some decoders return another timestamp than the one they were given.
         */
        @Volatile @JvmStatic var outputTimestamp: ((Long) -> Long)? = null

        private val outputAnswers = ConcurrentLinkedQueue<Int>()

        /** Makes the next `dequeueOutputBuffer` answer [code], a negative info code, and no frame. */
        @JvmStatic
        fun answerNextOutputDequeue(code: Int) {
            outputAnswers.add(code)
        }

        private val failures = ConcurrentHashMap<CodecPoint, ConcurrentLinkedQueue<() -> Throwable?>>()

        /** Makes the next [times] calls of kind [point] throw what [failure] makes. */
        @JvmStatic
        fun failNext(point: CodecPoint, times: Int = 1, failure: () -> Throwable) {
            val queue = failures.getOrPut(point) { ConcurrentLinkedQueue() }
            repeat(times) { queue.add(failure) }
        }

        /** Lets the next [passes] calls of kind [point] work, and makes the one after fail. */
        @JvmStatic
        fun failAfter(point: CodecPoint, passes: Int, failure: () -> Throwable) {
            val queue = failures.getOrPut(point) { ConcurrentLinkedQueue() }
            repeat(passes) { queue.add { null } }
            queue.add(failure)
        }

        /** The `IOException` that `MediaCodec.createByCodecName` declares. */
        @JvmStatic fun ioFailure(): () -> Throwable = { IOException("no such codec") }

        /** Frees the output buffers of every codec, so that more inputs can be queued. */
        @JvmStatic
        fun freeOutputs() {
            instances.forEach { it.giveBackOutputs() }
        }

        private fun fire(point: CodecPoint) {
            failures[point]?.poll()?.invoke()?.let { throw it }
        }

        /** Registers a fake codec for [name], with input buffers of [inputSize] bytes. */
        @JvmStatic
        fun register(name: String, inputSize: Int = BUFFER_SIZE) {
            ShadowMediaCodec.addDecoder(name, CodecConfig(inputSize, BUFFER_SIZE, FakeDecoder(name)))
        }

        /**
         * Forgets the journal, the failures and the fake codecs; Robolectric does not reset a shadow
         * of @Config. The name is not `reset`: Robolectric would call a static method of that name
         * in place of `MediaCodec.reset`.
         */
        @JvmStatic
        fun forgetEverything() {
            journal.clear()
            instances.clear()
            failures.clear()
            outputAnswers.clear()
            starveInput = false
            nullInputBuffers.set(0)
            outputDelayNanos = 0
            outputTimestamp = null
            dequeueInputCalls.set(0)
            dequeueOutputCalls.set(0)
            ShadowMediaCodec.clearCodecs()
        }


        private fun entriesOf(format: MediaFormat): Map<String, Any> =
            format.keys.sorted().associateWith { key ->
                when (format.getValueTypeForKey(key)) {
                    MediaFormat.TYPE_INTEGER -> format.getInteger(key)
                    MediaFormat.TYPE_LONG -> format.getLong(key)
                    MediaFormat.TYPE_FLOAT -> format.getFloat(key)
                    MediaFormat.TYPE_STRING -> format.getString(key).orEmpty()
                    MediaFormat.TYPE_BYTE_BUFFER -> {
                        val bytes = checkNotNull(format.getByteBuffer(key)).duplicate()
                        "bytes:" + hex(ByteArray(bytes.remaining()).also { bytes.get(it) })
                    }
                    else -> "?"
                }
            }
    }
}
