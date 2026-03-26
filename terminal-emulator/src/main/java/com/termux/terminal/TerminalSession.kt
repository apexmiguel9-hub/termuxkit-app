package com.termux.terminal

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Message
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * A terminal session, consisting of a process coupled to a terminal interface.
 */
class TerminalSession(
    private val shellPath: String,
    private val cwd: String,
    private val args: Array<String>,
    private val env: Array<String>,
    private val transcriptRows: Int?,
    private var client: TerminalSessionClient
) : TerminalOutput() {

    companion object {
        private const val MSG_NEW_INPUT = 1
        private const val MSG_PROCESS_EXITED = 4
        private const val LOG_TAG = "TerminalSession"
    }

    val handle: String = UUID.randomUUID().toString()
    var emulator: TerminalEmulator? = null
    internal val processToTerminalIOQueue = ByteQueue(64 * 1024)
    internal val terminalToProcessIOQueue = ByteQueue(4096)
    private val utf8InputBuffer = ByteArray(5)
    private var terminalFileDescriptor: Int = 0
    var sessionName: String? = null
    private val mainThreadHandler = MainThreadHandler()
    private var shellPid: Int = 0
    private var shellExitStatus: Int = 0

    val isRunning: Boolean get() = synchronized(this) { shellPid != -1 }
    val exitStatus: Int get() = synchronized(this) { shellExitStatus }

    fun updateTerminalSessionClient(client: TerminalSessionClient) {
        this.client = client
        emulator?.updateTerminalSessionClient(client)
    }

    fun updateSize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        val emu = emulator
        if (emu == null) {
            initializeEmulator(columns, rows, cellWidthPixels, cellHeightPixels)
        } else {
            JNI.setPtyWindowSize(terminalFileDescriptor, rows, columns, cellWidthPixels, cellHeightPixels)
            emu.resize(columns, rows, cellWidthPixels, cellHeightPixels)
        }
    }

    fun getTitle(): String? = emulator?.getTitle()

    private fun initializeEmulator(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        emulator = TerminalEmulator(this, columns, rows, cellWidthPixels, cellHeightPixels, transcriptRows, client)

        val processId = IntArray(1)
        terminalFileDescriptor = JNI.createSubprocess(shellPath, cwd, args, env, processId, rows, columns, cellWidthPixels, cellHeightPixels)
        shellPid = processId[0]
        client.setTerminalShellPid(this, shellPid)

        val terminalFdWrapped = wrapFileDescriptor(terminalFileDescriptor)

        Thread({
            try {
                FileInputStream(terminalFdWrapped).use { termIn ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val read = termIn.read(buffer)
                        if (read == -1 || !processToTerminalIOQueue.write(buffer, 0, read)) return@Thread
                        mainThreadHandler.sendEmptyMessage(MSG_NEW_INPUT)
                    }
                }
            } catch (e: Exception) { /* Shutting down */ }
        }, "TermSessionInputReader[pid=$shellPid]").start()

        Thread({
            try {
                FileOutputStream(terminalFdWrapped).use { termOut ->
                    val buffer = ByteArray(4096)
                    while (true) {
                        val bytesToWrite = terminalToProcessIOQueue.read(buffer, true)
                        if (bytesToWrite == -1) return@Thread
                        termOut.write(buffer, 0, bytesToWrite)
                    }
                }
            } catch (e: IOException) { /* Ignore */ }
        }, "TermSessionOutputWriter[pid=$shellPid]").start()

        Thread({
            val processExitCode = JNI.waitFor(shellPid)
            mainThreadHandler.sendMessage(mainThreadHandler.obtainMessage(MSG_PROCESS_EXITED, processExitCode))
        }, "TermSessionWaiter[pid=$shellPid]").start()
    }

    override fun write(data: ByteArray, offset: Int, count: Int) {
        if (shellPid > 0) terminalToProcessIOQueue.write(data, offset, count)
    }

    fun writeCodePoint(prependEscape: Boolean, codePoint: Int) {
        if (codePoint > 1114111 || (codePoint in 0xD800..0xDFFF)) {
            throw IllegalArgumentException("Invalid code point: $codePoint")
        }

        var pos = 0
        if (prependEscape) utf8InputBuffer[pos++] = 27.toByte()

        when {
            codePoint <= 0b1111111 -> {
                utf8InputBuffer[pos++] = codePoint.toByte()
            }
            codePoint <= 0b11111111111 -> {
                utf8InputBuffer[pos++] = (0b11000000 or (codePoint shr 6)).toByte()
                utf8InputBuffer[pos++] = (0b10000000 or (codePoint and 0b111111)).toByte()
            }
            codePoint <= 0b1111111111111111 -> {
                utf8InputBuffer[pos++] = (0b11100000 or (codePoint shr 12)).toByte()
                utf8InputBuffer[pos++] = (0b10000000 or ((codePoint shr 6) and 0b111111)).toByte()
                utf8InputBuffer[pos++] = (0b10000000 or (codePoint and 0b111111)).toByte()
            }
            else -> {
                utf8InputBuffer[pos++] = (0b11110000 or (codePoint shr 18)).toByte()
                utf8InputBuffer[pos++] = (0b10000000 or ((codePoint shr 12) and 0b111111)).toByte()
                utf8InputBuffer[pos++] = (0b10000000 or ((codePoint shr 6) and 0b111111)).toByte()
                utf8InputBuffer[pos++] = (0b10000000 or (codePoint and 0b111111)).toByte()
            }
        }
        write(utf8InputBuffer, 0, pos)
    }

    private fun notifyScreenUpdate() = client.onTextChanged(this)

    fun reset() {
        emulator?.reset()
        notifyScreenUpdate()
    }

    fun finishIfRunning() {
        if (isRunning) {
            try {
                Os.kill(shellPid, OsConstants.SIGKILL)
            } catch (e: ErrnoException) {
                Logger.logWarn(LOG_TAG, "Failed sending SIGKILL: ${e.message}")
            }
        }
    }

    private fun cleanupResources(exitStatus: Int) {
        synchronized(this) {
            shellPid = -1
            shellExitStatus = exitStatus
        }
        terminalToProcessIOQueue.close()
        processToTerminalIOQueue.close()
        JNI.close(terminalFileDescriptor)
    }

    override fun titleChanged(oldTitle: String?, newTitle: String?) = client.onTitleChanged(this)
    override fun onCopyTextToClipboard(text: String) = client.onCopyTextToClipboard(this, text)
    override fun onPasteTextFromClipboard() = client.onPasteTextFromClipboard(this)
    override fun onBell() = client.onBell(this)
    override fun onColorsChanged() = client.onColorsChanged(this)

    fun getCwd(): String? {
        if (shellPid < 1) return null
        return try {
            val cwdSymlink = "/proc/$shellPid/cwd/"
            File(cwdSymlink).canonicalPath.let { path ->
                if (cwdSymlink != (if (path.endsWith("/")) path else "$path/")) path else null
            }
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error getting current directory", e)
            null
        }
    }

    private fun wrapFileDescriptor(fd: Int): FileDescriptor {
        val result = FileDescriptor()
        try {
            val field = try {
                FileDescriptor::class.java.getDeclaredField("descriptor")
            } catch (e: NoSuchFieldException) {
                FileDescriptor::class.java.getDeclaredField("fd")
            }
            field.isAccessible = true
            field.set(result, fd)
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error accessing FileDescriptor field", e)
            System.exit(1)
        }
        return result
    }

    @SuppressLint("HandlerLeak")
    inner class MainThreadHandler : Handler() {
        private val receiveBuffer = ByteArray(64 * 1024)

        override fun handleMessage(msg: Message) {
            val bytesRead = processToTerminalIOQueue.read(receiveBuffer, false)
            if (bytesRead > 0) {
                emulator?.append(receiveBuffer, bytesRead)
                notifyScreenUpdate()
            }

            if (msg.what == MSG_PROCESS_EXITED) {
                val exitCode = msg.obj as Int
                cleanupResources(exitCode)

                val exitDesc = "\r\n[Process completed${if (exitCode > 0) " (code $exitCode)" else if (exitCode < 0) " (signal ${-exitCode})" else ""} - press Enter]"
                val bytes = exitDesc.toByteArray(StandardCharsets.UTF_8)
                emulator?.append(bytes, bytes.size)
                notifyScreenUpdate()
                client.onSessionFinished(this@TerminalSession)
            }
        }
    }
}
