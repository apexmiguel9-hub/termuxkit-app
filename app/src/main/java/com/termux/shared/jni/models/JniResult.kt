package com.termux.shared.jni.models

import android.os.Parcelable
import com.termux.shared.net.socket.local.LocalSocketException
import kotlinx.parcelize.Parcelize

/**
 * JniResult - Resultado de operaciones JNI nativas
 *
 * Esta clase es llenada por el motor Rust mediante JNI.
 * Los nombres y tipos de los campos DEBEN coincidir exactamente
 * con lo que espera lib.rs en la función JniResult::to_java_object()
 *
 * @property retval Valor de retorno (0 = éxito, -1 = error)
 * @property errno Código de error de Linux (0 si éxito)
 * @property errmsg Mensaje de error descriptivo
 * @property intData Datos enteros adicionales (ej: FD de socket)
 */
@Parcelize
data class JniResult(
    val retval: Int = 0,
    val errno: Int = 0,
    val errmsg: String = "",
    val intData: Int = 0
) : Parcelable {

    companion object {
        init {
            System.loadLibrary("termux_rust_engine")
        }
    }

    /**
     * Verifica si la operación fue exitosa
     */
    fun isSuccess(): Boolean = retval >= 0

    /**
     * Verifica si ocurrió un error
     */
    fun isError(): Boolean = retval < 0

    /**
     * Obtiene el FD de socket si la operación fue exitosa
     */
    fun getSocketFd(): Int = intData

    /**
     * Lanza una excepción si hubo error
     */
    @Throws(LocalSocketException::class)
    fun throwIfError(operation: String = "Socket operation") {
        if (isError()) {
            throw LocalSocketException(
                message = errmsg.ifEmpty { "Unknown error" },
                errno = errno,
                operation = operation
            )
        }
    }

    override fun toString(): String {
        return buildString {
            append("JniResult{")
            append("retval=$retval")
            append(", errno=$errno")
            if (errmsg.isNotEmpty()) {
                append(", errmsg='$errmsg'")
            }
            append(", intData=$intData")
            append("}")
        }
    }
}
