package com.termux.shared.net.socket.local

import android.util.Log
import com.termux.shared.jni.models.JniResult
import java.io.Closeable

/**
 * LocalSocketManager - Gestor de operaciones nativas para Unix Domain Sockets
 *
 * Esta clase proporciona la interfaz Kotlin para el motor Rust de sockets Unix.
 * Todas las funciones native deben coincidir exactamente con las declaradas en lib.rs:
 *
 * - Java_com_termux_shared_net_socket_local_LocalSocketManager_*
 *
 * @see PeerCred
 * @see JniResult
 */
class LocalSocketManager : Closeable {

    companion object {
        private const val TAG = "LocalSocketManager"
        private const val DEFAULT_BACKLOG = 50

        init {
            System.loadLibrary("termux_rust_engine")
        }

        // ========================================================================
        // Funciones Native - Declaraciones que coinciden con lib.rs
        // ========================================================================

        /**
         * Crea un socket servidor Unix Domain
         *
         * @param logTitle Título para logs (puede ser null)
         * @param pathArray Ruta del socket como ByteArray
         * @param backlog Cola máxima de conexiones pendientes (1-500)
         * @return JniResult con el FD del socket en intData si éxito
         */
        @JvmStatic
        @Throws(LocalSocketException::class)
        external fun createServerSocketNative(
            logTitle: String?,
            pathArray: ByteArray,
            backlog: Int
        ): JniResult

        /**
         * Cierra un socket por su file descriptor
         *
         * @param logTitle Título para logs (puede ser null)
         * @param fd File descriptor del socket a cerrar
         * @return JniResult indicando éxito o error
         */
        @JvmStatic
        @Throws(LocalSocketException::class)
        external fun closeSocketNative(
            logTitle: String?,
            fd: Int
        ): JniResult

        /**
         * Acepta una conexión de cliente en un socket servidor
         *
         * @param logTitle Título para logs (puede ser null)
         * @param fd File descriptor del socket servidor
         * @return JniResult con el FD del cliente en intData si éxito
         */
        @JvmStatic
        @Throws(LocalSocketException::class)
        external fun acceptNative(
            logTitle: String?,
            fd: Int
        ): JniResult

        /**
         * Lee datos de un socket con soporte para deadline
         *
         * @param logTitle Título para logs (puede ser null)
         * @param fd File descriptor del socket
         * @param dataArray ByteArray donde se almacenarán los datos leídos
         * @param deadline Tiempo máximo en milisegundos (0 = sin límite)
         * @return JniResult con bytes leídos en intData si éxito
         */
        @JvmStatic
        @Throws(LocalSocketException::class)
        external fun readNative(
            logTitle: String?,
            fd: Int,
            dataArray: ByteArray,
            deadline: Long
        ): JniResult

        /**
         * Envía datos a un socket con soporte para deadline
         *
         * @param logTitle Título para logs (puede ser null)
         * @param fd File descriptor del socket
         * @param dataArray ByteArray con los datos a enviar
         * @param deadline Tiempo máximo en milisegundos (0 = sin límite)
         * @return JniResult indicando éxito o error
         */
        @JvmStatic
        @Throws(LocalSocketException::class)
        external fun sendNative(
            logTitle: String?,
            fd: Int,
            dataArray: ByteArray,
            deadline: Long
        ): JniResult

        /**
         * Obtiene el número de bytes disponibles en el buffer de recepción
         *
         * @param logTitle Título para logs (puede ser null)
         * @param fd File descriptor del socket
         * @return JniResult con bytes disponibles en intData
         */
        @JvmStatic
        @Throws(LocalSocketException::class)
        external fun availableNative(
            logTitle: String?,
            fd: Int
        ): JniResult

        /**
         * Configura timeout de lectura del socket (SO_RCVTIMEO)
         *
         * @param logTitle Título para logs (puede ser null)
         * @param fd File descriptor del socket
         * @param timeout Timeout en milisegundos
         * @return JniResult indicando éxito o error
         */
        @JvmStatic
        @Throws(LocalSocketException::class)
        external fun setSocketReadTimeoutNative(
            logTitle: String?,
            fd: Int,
            timeout: Int
        ): JniResult

        /**
         * Configura timeout de envío del socket (SO_SNDTIMEO)
         *
         * @param logTitle Título para logs (puede ser null)
         * @param fd File descriptor del socket
         * @param timeout Timeout en milisegundos
         * @return JniResult indicando éxito o error
         */
        @JvmStatic
        @Throws(LocalSocketException::class)
        external fun setSocketSendTimeoutNative(
            logTitle: String?,
            fd: Int,
            timeout: Int
        ): JniResult

        /**
         * Obtiene credenciales del peer (uid, gid, pid, pname, cmdline)
         *
         * @param logTitle Título para logs (puede ser null)
         * @param fd File descriptor del socket conectado
         * @param peerCred Objeto PeerCred a llenar con la información
         * @return JniResult indicando éxito o error
         */
        @JvmStatic
        @Throws(LocalSocketException::class)
        external fun getPeerCredNative(
            logTitle: String?,
            fd: Int,
            peerCred: PeerCred
        ): JniResult
    }

    // ========================================================================
    // Métodos de instancia con API amigable
    // ========================================================================

    /**
     * Crea un socket servidor en la ruta especificada
     *
     * @param path Ruta del socket Unix
     * @param backlog Cola máxima de conexiones (default: 50)
     * @return File descriptor del socket servidor
     * @throws LocalSocketException si falla la creación
     */
    @Throws(LocalSocketException::class)
    fun createServerSocket(path: String, backlog: Int = DEFAULT_BACKLOG): Int {
        val result = createServerSocketNative(TAG, path.toByteArray(), backlog)
        result.throwIfError("createServerSocket")
        return result.intData
    }

    /**
     * Cierra un socket
     *
     * @param fd File descriptor del socket
     * @throws LocalSocketException si falla el cierre
     */
    @Throws(LocalSocketException::class)
    fun closeSocket(fd: Int) {
        val result = closeSocketNative(TAG, fd)
        result.throwIfError("closeSocket")
    }

    /**
     * Acepta una conexión cliente
     *
     * @param fd File descriptor del socket servidor
     * @return File descriptor del socket cliente
     * @throws LocalSocketException si falla la aceptación
     */
    @Throws(LocalSocketException::class)
    fun accept(fd: Int): Int {
        val result = acceptNative(TAG, fd)
        result.throwIfError("accept")
        return result.intData
    }

    /**
     * Lee datos de un socket
     *
     * @param fd File descriptor del socket
     * @param buffer Buffer donde almacenar los datos
     * @param deadline Timeout en milisegundos (0 = sin límite)
     * @return Número de bytes leídos
     * @throws LocalSocketException si falla la lectura
     */
    @Throws(LocalSocketException::class)
    fun read(fd: Int, buffer: ByteArray, deadline: Long = 0): Int {
        val result = readNative(TAG, fd, buffer, deadline)
        result.throwIfError("read")
        return result.intData
    }

    /**
     * Envía datos a un socket
     *
     * @param fd File descriptor del socket
     * @param data Datos a enviar
     * @param deadline Timeout en milisegundos (0 = sin límite)
     * @throws LocalSocketException si falla el envío
     */
    @Throws(LocalSocketException::class)
    fun send(fd: Int, data: ByteArray, deadline: Long = 0) {
        val result = sendNative(TAG, fd, data, deadline)
        result.throwIfError("send")
    }

    /**
     * Obtiene bytes disponibles en el buffer de recepción
     *
     * @param fd File descriptor del socket
     * @return Número de bytes disponibles
     * @throws LocalSocketException si falla la consulta
     */
    @Throws(LocalSocketException::class)
    fun available(fd: Int): Int {
        val result = availableNative(TAG, fd)
        result.throwIfError("available")
        return result.intData
    }

    /**
     * Configura timeout de lectura
     *
     * @param fd File descriptor del socket
     * @param timeout Timeout en milisegundos
     * @throws LocalSocketException si falla la configuración
     */
    @Throws(LocalSocketException::class)
    fun setReadTimeout(fd: Int, timeout: Int) {
        val result = setSocketReadTimeoutNative(TAG, fd, timeout)
        result.throwIfError("setReadTimeout")
    }

    /**
     * Configura timeout de envío
     *
     * @param fd File descriptor del socket
     * @param timeout Timeout en milisegundos
     * @throws LocalSocketException si falla la configuración
     */
    @Throws(LocalSocketException::class)
    fun setSendTimeout(fd: Int, timeout: Int) {
        val result = setSocketSendTimeoutNative(TAG, fd, timeout)
        result.throwIfError("setSendTimeout")
    }

    /**
     * Obtiene credenciales del peer conectado
     *
     * @param fd File descriptor del socket conectado
     * @return PeerCred con la información del proceso peer
     * @throws LocalSocketException si falla la obtención
     */
    @Throws(LocalSocketException::class)
    fun getPeerCred(fd: Int): PeerCred {
        val peerCred = PeerCred()
        val result = getPeerCredNative(TAG, fd, peerCred)
        result.throwIfError("getPeerCred")
        return peerCred
    }

    /**
     * Cierra el socket si es necesario
     */
    override fun close() {
        // Los sockets deben cerrarse explícitamente con closeSocket()
        Log.d(TAG, "LocalSocketManager closed")
    }
}
