package io.ondeviceai.core
/** Owns a processing frame until complete(), including after close(). */
class LatestFrameQueue<T : Any>(private val onRelease: (T) -> Unit = {}) {
    private var processing: T? = null
    private var pending: T? = null
    private var closed = false
    var droppedCount: Int = 0
        private set
    @Synchronized fun offer(frame: T) {
        if (closed) { onRelease(frame); return }
        pending?.let { droppedCount++; onRelease(it) }; pending = frame
    }
    @Synchronized fun next(): T? {
        if (closed || processing != null) return null
        processing = pending; pending = null; return processing
    }
    @Synchronized fun complete() { processing?.let(onRelease); processing = null }
    @Synchronized fun close() { closed = true; pending?.let(onRelease); pending = null }
}
