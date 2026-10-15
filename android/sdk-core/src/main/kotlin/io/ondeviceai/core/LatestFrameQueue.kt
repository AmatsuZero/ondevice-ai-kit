package io.ondeviceai.core
/** Owns a processing frame until complete(), including after close().
 * Release callbacks run after state is committed and outside the monitor, so they may reenter.
 */
class LatestFrameQueue<T : Any>(private val onRelease: (T) -> Unit = {}) {
    private var processing: T? = null
    private var pending: T? = null
    private var closed = false
    private var dropped = 0
    val droppedCount: Int get() = synchronized(this) { dropped }
    fun offer(frame: T) {
        val released = synchronized(this) {
            if (closed) frame
            else { val old = pending; if (old != null) dropped++; pending = frame; old }
        }
        released?.let(onRelease)
    }
    @Synchronized fun next(): T? {
        if (closed || processing != null) return null
        processing = pending; pending = null; return processing
    }
    fun complete() {
        val released = synchronized(this) { val old = processing; processing = null; old }
        released?.let(onRelease)
    }
    fun close() {
        val released = synchronized(this) { closed = true; val old = pending; pending = null; old }
        released?.let(onRelease)
    }
}
