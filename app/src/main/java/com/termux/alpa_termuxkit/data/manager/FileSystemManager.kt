package com.termux.alpa_termuxkit.data.manager

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import java.io.File

/**
 * FileSystemManager - Gestor de rutas del sistema para Alpa TermuxKit
 *
 * Define las rutas críticas para la operación del terminal:
 * - /system/bin: Binarios del sistema Android
 * - /home: Directorio home del usuario
 * - /internal.sock: Socket Unix para comunicación interna
 *
 * @param context Contexto de la aplicación
 */
@RequiresApi(Build.VERSION_CODES.Q) // Android 10+ (API 29)
class FileSystemManager(private val context: Context) {

    companion object {
        private const val TAG = "FileSystemManager"

        // =====================================================================
        // Rutas críticas para la operación del terminal (ORDEN 01)
        // =====================================================================
        
        /** /system/bin - Binarios del sistema Android */
        const val SYSTEM_BIN_PATH = "/system/bin"
        
        /** /home - Directorio home del usuario (ruta absoluta) */
        const val HOME_PATH = "/home"
        
        /** /internal.sock - Socket Unix para comunicación interna */
        const val INTERNAL_SOCKET_PATH = "/internal.sock"

        // Rutas relativas al directorio de datos de la app (para Android sandbox)
        private const val FILES_DIR = "files"
        private const val HOME_DIR = "home"
        private const val BINARY_DIR = "usr/bin"
        private const val SOCKET_DIR = "sockets"

        // Nombres de sockets
        const val INTERNAL_SOCKET_NAME = "internal.sock"
        const val SESSION_SOCKET_NAME = "session.sock"
        const val COMMAND_SOCKET_NAME = "command.sock"
    }

    // Directorio raíz de datos de la aplicación
    val appDataDir: File by lazy {
        context.filesDir.parentFile ?: context.filesDir
    }

    // Directorio home del usuario
    val homeDir: File by lazy {
        File(appDataDir, HOME_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    // Directorio de binarios
    val binaryDir: File by lazy {
        File(appDataDir, BINARY_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    // Directorio de sockets
    val socketDir: File by lazy {
        File(appDataDir, SOCKET_DIR).apply {
            if (!exists()) mkdirs()
        }
    }

    // Ruta completa del socket interno
    val internalSocketPath: String by lazy {
        File(socketDir, INTERNAL_SOCKET_NAME).absolutePath
    }

    // Ruta completa del socket de sesión
    val sessionSocketPath: String by lazy {
        File(socketDir, SESSION_SOCKET_NAME).absolutePath
    }

    // Ruta completa del socket de comandos
    val commandSocketPath: String by lazy {
        File(socketDir, COMMAND_SOCKET_NAME).absolutePath
    }

    // Ruta del shell (bash)
    val shellPath: String = "$SYSTEM_BIN_PATH/bash"

    // Ruta de herramientas básicas
    val toolPaths: Map<String, String> by lazy {
        mapOf(
            "sh" to "$SYSTEM_BIN_PATH/sh",
            "bash" to "$SYSTEM_BIN_PATH/bash",
            "cat" to "$SYSTEM_BIN_PATH/cat",
            "ls" to "$SYSTEM_BIN_PATH/ls",
            "cd" to "$SYSTEM_BIN_PATH/cd",
            "pwd" to "$SYSTEM_BIN_PATH/pwd",
            "echo" to "$SYSTEM_BIN_PATH/echo",
            "grep" to "$SYSTEM_BIN_PATH/grep",
            "sed" to "$SYSTEM_BIN_PATH/sed",
            "awk" to "$SYSTEM_BIN_PATH/awk",
            "find" to "$SYSTEM_BIN_PATH/find",
            "tar" to "$SYSTEM_BIN_PATH/tar",
            "gzip" to "$SYSTEM_BIN_PATH/gzip",
            "chmod" to "$SYSTEM_BIN_PATH/chmod",
            "chown" to "$SYSTEM_BIN_PATH/chown",
            "ln" to "$SYSTEM_BIN_PATH/ln",
            "mkdir" to "$SYSTEM_BIN_PATH/mkdir",
            "rm" to "$SYSTEM_BIN_PATH/rm",
            "cp" to "$SYSTEM_BIN_PATH/cp",
            "mv" to "$SYSTEM_BIN_PATH/mv",
            "ps" to "$SYSTEM_BIN_PATH/ps",
            "kill" to "$SYSTEM_BIN_PATH/kill",
            "uname" to "$SYSTEM_BIN_PATH/uname",
            "id" to "$SYSTEM_BIN_PATH/id",
            "whoami" to "$SYSTEM_BIN_PATH/whoami",
            "env" to "$SYSTEM_BIN_PATH/env",
            "clear" to "$SYSTEM_BIN_PATH/clear",
            "tty" to "$SYSTEM_BIN_PATH/tty",
            "stty" to "$SYSTEM_BIN_PATH/stty",
            "su" to "$SYSTEM_BIN_PATH/su"
        )
    }

    /**
     * Verifica si una ruta existe y es accesible
     */
    fun isPathAccessible(path: String): Boolean {
        return try {
            val file = File(path)
            file.exists() && file.canRead()
        } catch (e: SecurityException) {
            false
        }
    }

    /**
     * Verifica si el socket interno está disponible
     */
    fun isInternalSocketAvailable(): Boolean {
        return isPathAccessible(internalSocketPath)
    }

    /**
     * Obtiene la ruta absoluta para un archivo en el directorio home
     */
    fun getHomePath(relativePath: String): String {
        return File(homeDir, relativePath).absolutePath
    }

    /**
     * Obtiene la ruta absoluta para un binario
     */
    fun getBinaryPath(name: String): String? {
        return toolPaths[name]
    }

    /**
     * Limpia los sockets temporales
     */
    fun cleanupSockets() {
        socketDir.listFiles()?.forEach { file ->
            if (file.extension == "sock") {
                try {
                    file.delete()
                } catch (e: Exception) {
                    android.util.Log.w(TAG, "Failed to delete socket file: ${file.name}", e)
                }
            }
        }
    }

    /**
     * Inicializa el sistema de archivos necesario
     */
    fun initialize() {
        // Asegurar que los directorios existan
        listOf(homeDir, binaryDir, socketDir).forEach { dir ->
            if (!dir.exists()) {
                dir.mkdirs()
            }
        }

        // Limpiar sockets residuales
        cleanupSockets()

        android.util.Log.i(TAG, "FileSystemManager initialized")
        android.util.Log.i(TAG, "Home dir: ${homeDir.absolutePath}")
        android.util.Log.i(TAG, "Socket dir: ${socketDir.absolutePath}")
        android.util.Log.i(TAG, "Internal socket: $internalSocketPath")
    }
}
