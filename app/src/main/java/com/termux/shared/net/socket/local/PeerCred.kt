package com.termux.shared.net.socket.local

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * PeerCred - Credenciales de un proceso peer en un Unix Domain Socket
 *
 * Esta clase es llenada por el motor Rust mediante JNI.
 * Los nombres y tipos de los campos DEBEN coincidir exactamente
 * con lo que espera lib.rs en la función PeerCred::to_java_object()
 *
 * @property pid Process ID del peer
 * @property uid User ID del peer
 * @property gid Group ID del peer
 * @property pname Nombre del proceso (extraído de /proc/[pid]/cmdline)
 * @property cmdline Línea de comandos completa del proceso
 */
@Parcelize
data class PeerCred(
    var pid: Int = -1,
    var uid: Int = -1,
    var gid: Int = -1,
    var pname: String = "",
    var cmdline: String = ""
) : Parcelable {

    companion object {
        init {
            System.loadLibrary("termux_rust_engine")
        }

        /**
         * Obtiene las credenciales de un peer conectado a un socket Unix
         *
         * @param fd File descriptor del socket conectado
         * @return PeerCred con la información del proceso peer
         * @throws RuntimeException si falla la obtención de credenciales
         */
        @JvmStatic
        @Throws(RuntimeException::class)
        external fun getPeerCredNative(fd: Int): PeerCred
    }

    /**
     * Verifica si las credenciales son válidas
     */
    fun isValid(): Boolean = pid >= 0 && uid >= 0 && gid >= 0

    /**
     * Verifica si el peer es el mismo proceso actual
     */
    fun isSelfProcess(): Boolean = pid == android.os.Process.myPid()

    /**
     * Verifica si el peer pertenece al mismo usuario
     */
    fun isSameUser(): Boolean = uid == android.os.Process.myUid()

    override fun toString(): String {
        return buildString {
            append("PeerCred{")
            append("pid=$pid")
            append(", uid=$uid")
            append(", gid=$gid")
            if (pname.isNotEmpty()) {
                append(", pname='$pname'")
            }
            if (cmdline.isNotEmpty()) {
                append(", cmdline='$cmdline'")
            }
            append("}")
        }
    }
}
