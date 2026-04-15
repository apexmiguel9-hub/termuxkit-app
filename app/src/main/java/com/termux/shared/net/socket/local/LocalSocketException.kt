package com.termux.shared.net.socket.local

import java.io.IOException

/**
 * LocalSocketException - Excepción para operaciones de Unix Domain Socket
 *
 * @param message Mensaje de error descriptivo
 * @param errno Código de error de Linux
 * @param operation Nombre de la operación que falló
 */
class LocalSocketException(
    message: String,
    val errno: Int = 0,
    val operation: String = "Socket operation"
) : IOException("$operation failed: $message (errno: $errno)") {

    companion object {
        // Códigos de error comunes de Linux
        const val EINVAL = 22      // Invalid argument
        const val ECONNREFUSED = 111  // Connection refused
        const val ETIMEDOUT = 110     // Connection timed out
        const val EAGAIN = 11         // Resource temporarily unavailable
        const val EBADF = 9           // Bad file descriptor
        const val EPIPE = 32          // Broken pipe
    }
}
