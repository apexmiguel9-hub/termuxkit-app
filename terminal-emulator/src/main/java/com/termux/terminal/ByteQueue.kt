package com.termux.terminal

import kotlin.math.min

/** A circular byte buffer allowing one producer and one consumer thread. */
internal class ByteQueue(size: Int) {

    private val buffer: ByteArray = ByteArray(size)
    private var head: Int = 0
    private var storedBytes: Int = 0
    private var open: Boolean = true
    private val lock = Any()

    fun close() {
        synchronized(lock) {
            open = false
            (lock as java.lang.Object).notifyAll()
        }
    }

    fun read(outBuffer: ByteArray, block: Boolean): Int {
        synchronized(lock) {
            while (storedBytes == 0 && open) {
                if (block) {
                    try {
                        (lock as java.lang.Object).wait()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                } else {
                    return 0
                }
            }
            if (!open) return -1

            var totalRead = 0
            val bufferSize = buffer.size
            val wasFull = bufferSize == storedBytes
            var remainingToRead = outBuffer.size
            var offset = 0
            while (remainingToRead > 0 && storedBytes > 0) {
                val bytesToEnd = bufferSize - head
                val canReadInThisRun = min(bytesToEnd, storedBytes)
                val bytesToCopy = min(remainingToRead, canReadInThisRun)
                buffer.copyInto(outBuffer, offset, head, head + bytesToCopy)
                head = (head + bytesToCopy) % bufferSize
                storedBytes -= bytesToCopy
                remainingToRead -= bytesToCopy
                offset += bytesToCopy
                totalRead += bytesToCopy
            }
            if (wasFull) (lock as java.lang.Object).notifyAll()
            return totalRead
        }
    }

    /**
     * Attempt to write the specified portion of the provided buffer to the queue.
     *
     * Returns whether the output was totally written, false if it was closed before.
     */
    fun write(buffer: ByteArray, offset: Int, lengthToWrite: Int): Boolean {
        require(lengthToWrite + offset <= buffer.size) { "length + offset > buffer.length" }
        require(lengthToWrite > 0) { "length <= 0" }

        val bufferSize = this.buffer.size
        var off = offset
        var lenToWrite = lengthToWrite

        synchronized(lock) {
            while (lenToWrite > 0) {
                while (bufferSize == storedBytes && open) {
                    try {
                        (lock as java.lang.Object).wait()
                    } catch (e: InterruptedException) {
                        Thread.currentThread().interrupt()
                    }
                }
                if (!open) return false
                val wasEmpty = storedBytes == 0
                var bytesToWriteBeforeWaiting = min(lenToWrite, bufferSize - storedBytes)
                lenToWrite -= bytesToWriteBeforeWaiting

                while (bytesToWriteBeforeWaiting > 0) {
                    var tail = head + storedBytes
                    val oneRun: Int
                    if (tail >= bufferSize) {
                        tail = tail - bufferSize
                        oneRun = head - tail
                    } else {
                        oneRun = bufferSize - tail
                    }
                    val bytesToCopy = min(oneRun, bytesToWriteBeforeWaiting)
                    this.buffer.copyInto(buffer, off, tail, tail + bytesToCopy)
                    off += bytesToCopy
                    bytesToWriteBeforeWaiting -= bytesToCopy
                    storedBytes += bytesToCopy
                }
                if (wasEmpty) (lock as java.lang.Object).notifyAll()
            }
            return true
        }
    }
}
