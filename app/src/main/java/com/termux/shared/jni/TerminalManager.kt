package com.termux.shared.jni

import android.content.Context
import android.util.Log
import com.termux.shared.TermuxConstants
import com.termux.shared.bootstrap.BootstrapInstaller
import com.termux.shared.jni.models.JniResult
import com.termux.terminal.JNI
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalOutput
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Dispatchers
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * TerminalManager — Puente entre Rust PTY y TerminalEmulator.kt
 *
 * Arquitectura:
 *   Rust (portable-pty) → bytes raw → JNI callback → onPtyData()
 *   → TerminalEmulator.append() → parsea VTE/ANSI
 *   → Compose lee emulator.screen y renderiza
 */
class TerminalManager {

    companion object {
        private const val TAG = "TerminalManager"

        init {
            Log.i(TAG, "🔧 Cargando biblioteca nativa termux_rust_engine...")
            try {
                System.loadLibrary("termux_rust_engine")
                Log.i(TAG, "✅ Biblioteca nativa cargada")
            } catch (e: UnsatisfiedLinkError) {
                Log.e(TAG, "❌ Error cargando biblioteca nativa", e)
                throw e
            }
        }

        // Contexto de la aplicación (necesario para bootstrap)
        @Volatile
        private var appContext: Context? = null

        @JvmStatic
        fun setContext(context: Context) {
            appContext = context.applicationContext
        }

        // ========================================================================
        // Estado: TerminalEmulator.kt (VTE parser real)
        // ========================================================================

        @Volatile
        private var terminalEmulator: TerminalEmulator? = null

        @Volatile
        private var terminalSession: TerminalSession? = null

        @Volatile
        var isSessionActive: Boolean = false
            private set

        /** Contador de actualizaciones del PTY — para forzar recomposicion en Compose */
        @Volatile
        var ptyDataVersion: Int = 0
            private set

        const val DEFAULT_COLS: Int = 80
        const val DEFAULT_ROWS: Int = 24
        const val TRANSCRIPT_ROWS: Int = 10000

        // Dimensiones de celda en pixeles (se ajustan desde Compose)
        private var cellWidthPixels: Int = 12
        private var cellHeightPixels: Int = 24

        fun setCellDimensions(widthPx: Int, heightPx: Int) {
            cellWidthPixels = widthPx
            cellHeightPixels = heightPx
        }

        /**
         * Acceso al TerminalEmulator desde Compose/UI
         */
        fun getEmulator(): TerminalEmulator? = terminalEmulator

        // ========================================================================
        // Native methods (Rust — solo read/write del FD)
        // ========================================================================

        @JvmStatic
        @Throws(RuntimeException::class)
        external fun attachPtyFdNative(
            logTitle: String?,
            masterFd: Int,
            childPid: Int
        ): JniResult

        @JvmStatic
        @Throws(RuntimeException::class)
        external fun startPtySessionNative(
            logTitle: String?,
            shellPath: String,
            cols: Int,
            rows: Int
        ): JniResult

        @JvmStatic
        @Throws(RuntimeException::class)
        external fun writeNative(
            logTitle: String?,
            data: ByteArray
        ): JniResult

        @JvmStatic
        private external fun nativeFlush()

        @JvmStatic
        @Throws(RuntimeException::class)
        external fun closePtySessionNative(
            logTitle: String?,
            sessionId: Int
        ): JniResult

        // ========================================================================
        // API publica
        // ========================================================================

        /**
         * Inicia sesion PTY + crea TerminalEmulator.kt
         */
        @JvmStatic
        fun startSession(
            shellPath: String? = null,
            cols: Int = DEFAULT_COLS,
            rows: Int = DEFAULT_ROWS
        ): Boolean {
            if (isSessionActive) {
                Log.w(TAG, "Sesion ya activa, reutilizando existente")
                return true
            }

            // Calcular cols/rows basado en pantalla si se usan defaults
            val context = appContext
            val actualCols: Int
            val actualRows: Int
            if (cols == DEFAULT_COLS || rows == DEFAULT_ROWS) {
                val dm = context?.resources?.displayMetrics
                if (dm != null) {
                    val density = dm.density
                    actualCols = if (cols == DEFAULT_COLS) {
                        (dm.widthPixels / (density * 8.5f)).toInt()
                    } else { cols }
                    actualRows = if (rows == DEFAULT_ROWS) {
                        (dm.heightPixels / (density * 20f)).toInt()
                    } else { rows }
                    Log.i(TAG, "📐 Screen ${dm.widthPixels}x${dm.heightPixels}, density=$density → ${actualCols}x${actualRows}")
                } else {
                    actualCols = cols
                    actualRows = rows
                }
            } else {
                actualCols = cols
                actualRows = rows
            }

            // Instalar bootstrap si es necesario (sincrónico — startSession ya corre en background)
            var bootstrapPrefix: String? = null

            if (context != null) {
                Log.e("BOOTSTRAP", "Verificando bootstrap...")
                val bashFile = File("/data/user/0/com.termux.alpa_termuxkit/files/usr/bin/bash")
                Log.e("BOOTSTRAP", "bash existe (antes de install): ${bashFile.exists()}")

                if (bashFile.exists()) {
                    Log.e("BOOTSTRAP", "Bootstrap ya existe, skip download")
                    bootstrapPrefix = "/data/user/0/com.termux.alpa_termuxkit/files/usr"
                } else {
                    Log.e("BOOTSTRAP", "Iniciando BootstrapInstaller sync...")
                    bootstrapPrefix = BootstrapInstaller.install(
                        context,
                        BootstrapInstaller.DownloadProgressListener { percent, downloaded, total ->
                            if (percent % 10 == 0) {
                                Log.i("BOOTSTRAP", "Progreso descarga: $percent%")
                            }
                        }
                    )
                    Log.e("BOOTSTRAP", "Bootstrap install regresó: $bootstrapPrefix")
                    Log.e("BOOTSTRAP", "bash existe (después de install): ${bashFile.exists()}")
                    if (bootstrapPrefix != null) {
                        Log.i(TAG, "Bootstrap instalado en: $bootstrapPrefix")
                    } else {
                        Log.w(TAG, "Bootstrap no disponible, usando shell del sistema")
                    }
                }
            }

            // Determinar el shell: bash directo (PATH hardcodeado en termux.c)
            val actualShell = TermuxConstants.getDefaultShell(bootstrapPrefix)
            Log.i(TAG, "Shell seleccionado: $actualShell (con LD_PRELOAD termux-exec)")

            // Obtener nativeLibraryDir para LD_PRELOAD de termux-exec
            val nativeLibDir = appContext?.applicationInfo?.nativeLibraryDir
            Log.e("BOOTSTRAP", "nativeLibraryDir=$nativeLibDir")

            // Fix 1: Crear directorio HOME si no existe
            val homeDir = File("/data/user/0/com.termux.alpa_termuxkit/files/home")
            if (!homeDir.exists()) {
                homeDir.mkdirs()
                Log.i(TAG, "🏠 Directorio HOME creado: ${homeDir.absolutePath}")
            } else {
                Log.i(TAG, "🏠 Directorio HOME ya existe: ${homeDir.absolutePath}")
            }

            // Fix 3b: .bashrc es creado por BootstrapInstaller con funciones SELinux workaround
            Log.i(TAG, "📝 .bashrc gestionado por BootstrapInstaller")

            // Crear wrapper script que fuerza el PATH antes de bash
            val wrapperScript = File(homeDir, "launch_shell.sh")
            val wrapperContent = """
                |#!/system/bin/sh
                |export PATH=/data/user/0/com.termux.alpa_termuxkit/files/usr/bin:/system/bin:/system/xbin:/bin
                |export LD_LIBRARY_PATH=/data/user/0/com.termux.alpa_termuxkit/files/usr/lib
                |export PREFIX=/data/user/0/com.termux.alpa_termuxkit/files/usr
                |export HOME=/data/user/0/com.termux.alpa_termuxkit/files/home
                |export TMPDIR=/data/user/0/com.termux.alpa_termuxkit/files/usr/tmp
                |export TERM=xterm-256color
                |export LANG=en_US.UTF-8
                |export PS1='termuxkit:\w\$ '
                |exec /data/user/0/com.termux.alpa_termuxkit/files/usr/bin/bash -i
            """.trimMargin()
            wrapperScript.writeText(wrapperContent)
            wrapperScript.setExecutable(true, false)
            Log.i(TAG, "📝 Wrapper script creado: ${wrapperScript.absolutePath}")

            // Fix 3c: Crear .profile en HOME
            val profileFile = File(homeDir, ".profile")
            val profileContent = "export PS1='termuxkit:\\w\\$ '\n"
            if (!profileFile.exists() || profileFile.readText().contains("PS1='\\\$ '")) {
                profileFile.writeText(profileContent)
                Log.i(TAG, "📝 .profile creado en HOME con PS1 personalizado")
            }

            Log.e("TERMINAL_DEBUG", "startSession llamado, shell: $actualShell, bootstrap: $bootstrapPrefix")

            try {
                // 1. Crear TerminalEmulator.kt con TerminalOutput que escribe al PTY
                val output = object : TerminalOutput() {
                    override fun write(data: ByteArray, offset: Int, count: Int) {
                        // Lo que el emulator quiere enviar al PTY (input del usuario)
                        val slice = data.copyOfRange(offset, offset + count)
                        writeBytesToPty(slice)
                    }

                    override fun titleChanged(oldTitle: String?, newTitle: String?) {
                        Log.i(TAG, "📝 Titulo cambiado: $oldTitle → $newTitle")
                    }

                    override fun onCopyTextToClipboard(text: String) {
                        Log.i(TAG, "📋 Copy to clipboard: ${text.take(50)}")
                    }

                    override fun onPasteTextFromClipboard() {
                        Log.i(TAG, "📋 Paste from clipboard")
                    }

                    override fun onBell() {
                        Log.w(TAG, "🔔 Bell!")
                    }

                    override fun onColorsChanged() {
                        Log.d(TAG, "🎨 Colors changed")
                    }
                }

                val client = object : TerminalSessionClient {
                    override fun onTextChanged(changedSession: TerminalSession) {
                        // El screen cambio — notificar al UI
                        Log.d(TAG, "📝 Text changed")
                    }

                    override fun onTitleChanged(changedSession: TerminalSession) {
                        Log.i(TAG, "📝 Title: ${changedSession.title}")
                    }

                    override fun onSessionFinished(finishedSession: TerminalSession) {
                        Log.i(TAG, "🛑 Session finished")
                        isSessionActive = false
                    }

                    override fun onCopyTextToClipboard(session: TerminalSession, text: String) {}
                    override fun onPasteTextFromClipboard(session: TerminalSession?) {}
                    override fun onBell(session: TerminalSession) {}
                    override fun onColorsChanged(session: TerminalSession) {}
                    override fun onTerminalCursorStateChange(state: Boolean) {}
                    override fun setTerminalShellPid(session: TerminalSession, pid: Int) {}
                    override fun getTerminalCursorStyle(): Int? = null

                    override fun logError(tag: String, message: String) { Log.e(tag, message) }
                    override fun logWarn(tag: String, message: String) { Log.w(tag, message) }
                    override fun logInfo(tag: String, message: String) { Log.i(tag, message) }
                    override fun logDebug(tag: String, message: String) { Log.d(tag, message) }
                    override fun logVerbose(tag: String, message: String) {}
                    override fun logStackTraceWithMessage(tag: String, message: String, e: Exception) {
                        Log.e(tag, message, e)
                    }
                    override fun logStackTrace(tag: String, e: Exception) {
                        Log.e(tag, "Stack trace", e)
                    }
                }

                terminalEmulator = TerminalEmulator(
                    session = output,
                    columnsParam = actualCols,
                    rowsParam = actualRows,
                    cellWidthPixelsParam = cellWidthPixels,
                    cellHeightPixelsParam = cellHeightPixels,
                    transcriptRowsParam = TRANSCRIPT_ROWS,
                    client = client
                )

                terminalSession = TerminalSession(
                    terminalEmulator = terminalEmulator!!,
                    shellPath = actualShell,
                    cols = actualCols,
                    rows = actualRows
                )

                Log.e("TERMINAL_DEBUG", "startSession: TerminalEmulator y TerminalSession creados")

                // 2. FORK/EXEC via C nativo (JNI.createSubprocess) — SELinux-safe
                Log.e("TERMINAL_DEBUG", "startSession: llamando a JNI.createSubprocess (C nativo)...")

                // Asegurar que el cwd existe antes de llamar al C
                val cwd = File("/data/user/0/com.termux.alpa_termuxkit/files/home")
                if (!cwd.exists()) {
                    cwd.mkdirs()
                    Log.e("TERMINAL_DEBUG", "startSession: CWD creado")
                }

                val processId = intArrayOf(0)
                // Forzar carga de ~/.bashrc con --rcfile (contiene funciones SELinux workaround)
                val shellArgs = arrayOf("--rcfile", "/data/user/0/com.termux.alpa_termuxkit/files/home/.bashrc", "-i")

                // Construir path de LD_PRELOAD para termux-exec
                val ldPreloadPath = if (nativeLibDir != null) {
                    "$nativeLibDir/libtermux-exec.so"
                } else {
                    ""
                }

                val envVars = arrayOf(
                    "PATH=/data/user/0/com.termux.alpa_termuxkit/files/usr/bin:/system/bin:/bin",
                    "LD_LIBRARY_PATH=/data/user/0/com.termux.alpa_termuxkit/files/usr/lib:/system/lib64:/system/lib",
                    "PREFIX=/data/user/0/com.termux.alpa_termuxkit/files/usr",
                    "HOME=/data/user/0/com.termux.alpa_termuxkit/files/home",
                    "TMPDIR=/data/user/0/com.termux.alpa_termuxkit/files/usr/tmp",
                    "ENV=/data/user/0/com.termux.alpa_termuxkit/files/usr/etc/profile",
                    "TERM=xterm-256color",
                    "LANG=en_US.UTF-8",
                    "PS1=termuxkit:\\w\\$ ",
                    "TERMUX_NATIVE_LIB_DIR=${nativeLibDir ?: ""}"
                )

                Log.e("BOOTSTRAP", "LD_PRELOAD=$ldPreloadPath")

                val masterFd: Int
                try {
                    masterFd = JNI.createSubprocess(
                        cmd = actualShell,
                        cwd = "/data/user/0/com.termux.alpa_termuxkit/files/home",
                        args = shellArgs,
                        envVars = envVars,
                        processId = processId,
                        rows = actualRows,
                        columns = actualCols,
                        cellWidth = cellWidthPixels,
                        cellHeight = cellHeightPixels
                    )
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error creando subprocess con C nativo", e)
                    Log.e("TERMINAL_DEBUG", "createSubprocess exception: ${e.message}")
                    terminalEmulator = null
                    terminalSession = null
                    return false
                }

                val childPid = processId[0]
                Log.e("TERMINAL_DEBUG", "startSession: C nativo regreso masterFd=$masterFd, childPid=$childPid")

                if (masterFd < 0) {
                    Log.e(TAG, "❌ C nativo devolvio FD invalido: $masterFd")
                    terminalEmulator = null
                    terminalSession = null
                    return false
                }

                // Pequeño delay para que el child process abra el slave y ejecute el shell
                // antes de que Rust empiece a leer del master
                Thread.sleep(200)

                // 3. Pasar el FD a Rust para el read/write loop
                Log.e("TERMINAL_DEBUG", "startSession: llamando a attachPtyFdNative (Rust)...")
                val rustResult = attachPtyFdNative(TAG, masterFd, childPid)
                Log.e("TERMINAL_DEBUG", "startSession: attachPtyFdNative regreso, isSuccess=${rustResult.isSuccess()}, isErr=${rustResult.isError()}, errmsg='${rustResult.errmsg}'")
                if (rustResult.isError()) {
                    Log.e(TAG, "Failed to attach PTY FD to Rust: ${rustResult.errmsg}")
                    Log.e("TERMINAL_DEBUG", "startSession: ERROR, retornando false")
                    terminalEmulator = null
                    terminalSession = null
                    return false
                }

                // Send welcome message AFTER PTY is ready
                val welcome = "\u001b[32mWelcome to TermuxKit\u001b[0m\r\n".toByteArray(Charsets.UTF_8)
                onPtyData(0, welcome)
                Log.e("TERMINAL_DEBUG", "startSession: welcome message enviado")

                isSessionActive = true
                Log.i(TAG, "✅ PTY session + TerminalEmulator creados (C fork/exec + Rust I/O)")
                Log.e("TERMINAL_DEBUG", "startSession: isSessionActive=true, session lista")
                return true

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error creando sesion", e)
                isSessionActive = false
                return false
            }
        }

        /**
         * Escribe texto al PTY (input del usuario)
         */
        @JvmStatic
        fun write(text: String): Boolean {
            return writeBytes(text.toByteArray(Charsets.UTF_8))
        }

        /**
         * Escribe bytes directamente al PTY
         */
        @JvmStatic
        fun writeBytes(bytes: ByteArray): Boolean {
            if (!isSessionActive) return false
            return try {
                val result = writeNative(TAG, bytes)
                result.isSuccess()
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library not available", e)
                false
            } catch (e: Exception) {
                Log.e(TAG, "Error writing to PTY", e)
                false
            }
        }

        /** Alias para compatibilidad con TerminalInputView */
        @JvmStatic
        fun writeInput(bytes: ByteArray): Boolean = writeBytes(bytes)

        private fun writeBytesToPty(bytes: ByteArray): Boolean {
            return writeBytes(bytes)
        }

        /**
         * Callback NATIVO desde Rust cuando llegan datos del PTY.
         *
         * Rust llama: TerminalManager.onPtyData(sessionId, byteArray)
         * → Esto pasa los bytes al TerminalEmulator.kt que parsea VTE/ANSI
         */
        @JvmStatic
        fun onPtyData(sessionId: Int, data: ByteArray) {
            val preview = try { String(data, Charsets.UTF_8).take(50) } catch (e: Exception) { "<binary>" }
            Log.e("PTY", "Bytes recibidos: ${data.size} — '$preview'")
            synchronized(TerminalManager::class.java) {
                val emulator = terminalEmulator ?: run {
                    Log.e("TERMINAL_DEBUG", "onPtyData: terminalEmulator es NULL, ignorando")
                    return
                }
                try {
                    emulator.append(data, data.size)
                    ptyDataVersion++
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error en emulator.append()", e)
                }
            }
        }

        /**
         * Detiene la sesion
         */
        @JvmStatic
        fun stopSession() {
            if (!isSessionActive) return
            try {
                closePtySessionNative(TAG, 0)
            } catch (e: Exception) {
                Log.w(TAG, "Error cerrando sesion", e)
            }
            terminalEmulator = null
            terminalSession = null
            isSessionActive = false
        }
    }
}
