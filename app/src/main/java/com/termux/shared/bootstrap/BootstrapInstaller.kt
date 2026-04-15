package com.termux.shared.bootstrap

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.*
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicReference

object BootstrapInstaller {
    private const val TAG = "BootstrapInstaller"

    // URL oficial del bootstrap de Termux para aarch64
    // Pattern: https://github.com/termux/termux-packages/releases/download/{TAG}/bootstrap-aarch64.zip
    // TAG ejemplo: bootstrap-2026.04.05-r1+apt.android-7
    const val BOOTSTRAP_URL =
        "https://github.com/termux/termux-packages/releases/download" +
            "/bootstrap-2026.04.05-r1+apt.android-7/bootstrap-aarch64.zip"

    // Callback para progreso de descarga (0-100)
    fun interface DownloadProgressListener {
        fun onProgress(percent: Int, bytesDownloaded: Long, totalBytes: Long)
    }

    /**
     * Instala el bootstrap. Si no existe en assets, lo descarga de la URL oficial.
     * Esta versión sincrónica — debe llamarse desde un hilo de background.
     */
    @JvmOverloads
    fun install(
        context: Context,
        progressListener: DownloadProgressListener? = null
    ): String? {
        val filesDir = context.filesDir
        val prefixDir = File(filesDir, "usr")

        // Si ya existe bin/bash, aseguramos permisos y retornamos
        val bashPath = File(prefixDir, "bin/bash")
        if (bashPath.exists()) {
            ensureExecutable(bashPath)
            Log.i(TAG, "Bootstrap ya instalado en: ${prefixDir.absolutePath}")
            return prefixDir.absolutePath
        }

        Log.i(TAG, "Iniciando instalación de bootstrap...")

        try {
            // Limpiar instalación previa rota
            if (prefixDir.exists()) {
                Log.w(TAG, "Limpiando instalación previa rota...")
                prefixDir.deleteRecursively()
            }

            // Obtener inputStream (assets o descarga en background thread)
            val inputStream = getBootstrapInputStream(context, progressListener)
                ?: run {
                    Log.e(TAG, "No se pudo obtener bootstrap (ni assets ni descarga)")
                    return null
                }

            Log.i(TAG, "Extrayendo bootstrap...")
            val extracted = extractZip(inputStream, prefixDir, progressListener)
            inputStream.close()

            // Limpiar archivo temporal de descarga si existe
            cleanTempDownloads(context)

            if (!extracted) {
                Log.e(TAG, "Error extrayendo bootstrap")
                return null
            }

            // Verificar y configurar
            if (bashPath.exists()) {
                bashPath.setExecutable(true, false)
                makeBinariesExecutable(prefixDir)
                Log.i(TAG, "Bootstrap instalado exitosamente en: ${prefixDir.absolutePath}")
                return prefixDir.absolutePath
            } else {
                Log.e(TAG, "bootstrap no contiene bin/bash después de extraer")
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error instalando bootstrap: ${e.message}", e)
            return null
        }
    }

    /**
     * Instala el bootstrap de forma asíncrona (seguro para llamar desde main thread).
     * El resultado se entrega vía callback en el main thread.
     */
    @JvmOverloads
    fun installAsync(
        context: Context,
        progressListener: DownloadProgressListener? = null,
        onComplete: (String?) -> Unit
    ) {
        Thread {
            val result = install(context, progressListener)
            Handler(Looper.getMainLooper()).post {
                onComplete(result)
            }
        }.start()
    }

    /**
     * Obtiene el inputStream del bootstrap: primero intenta assets, sino descarga.
     */
    private fun getBootstrapInputStream(
        context: Context,
        progressListener: DownloadProgressListener?
    ): InputStream? {
        // 1. Intentar assets primero (si existe bootstrap.zip local)
        try {
            val assetManager = context.assets
            val assetList = assetManager.list("") ?: emptyArray()
            when {
                assetList.contains("bootstrap.zip") -> {
                    Log.i(TAG, "Usando bootstrap.zip desde assets")
                    return assetManager.open("bootstrap.zip")
                }
                assetList.contains("bootstrap.zip.jar") -> {
                    Log.i(TAG, "Usando bootstrap.zip.jar desde assets (AAPT comprimido)")
                    return assetManager.open("bootstrap.zip.jar")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error leyendo assets: ${e.message}")
        }

        // 2. Descargar de la URL oficial
        Log.i(TAG, "Descargando bootstrap oficial de Termux: $BOOTSTRAP_URL")
        return downloadBootstrap(context, BOOTSTRAP_URL) { percent, downloaded, total ->
            progressListener?.onProgress(percent, downloaded, total)
            Log.i(TAG, "Descarga: $percent% (${formatBytes(downloaded)}/${formatBytes(total)})")
        }
    }

    /**
     * Descarga el bootstrap desde la URL oficial y retorna un InputStream.
     * El archivo temporal se mantiene en filesDir hasta que el caller cierre el stream.
     */
    private fun downloadBootstrap(
        context: Context,
        url: String,
        logProgress: (Int, Long, Long) -> Unit
    ): InputStream? {
        var tempFile: File? = null
        return try {
            val connection = URL(url).openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 60_000
            connection.readTimeout = 120_000
            connection.setRequestProperty("User-Agent", "AlphaTermuxKit/1.0")
            connection.instanceFollowRedirects = true
            connection.connect()

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                Log.e(TAG, "HTTP $responseCode al descargar bootstrap")
                connection.disconnect()
                return null
            }

            val contentLength = connection.contentLengthLong
            Log.i(TAG, "Descarga iniciada: ${formatBytes(contentLength)}")

            // Descargar a archivo temporal en filesDir
            tempFile = File(context.filesDir, "bootstrap_download_${System.currentTimeMillis()}.zip")

            connection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(8192)
                    var totalRead = 0L
                    var bytesRead: Int
                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        output.write(buffer, 0, bytesRead)
                        totalRead += bytesRead
                        if (contentLength > 0) {
                            val percent = ((totalRead * 100) / contentLength).toInt()
                            logProgress(percent, totalRead, contentLength)
                        }
                    }
                    Log.i(TAG, "Descarga completada: ${formatBytes(totalRead)}")
                }
            }

            connection.disconnect()

            // Retornar FileInputStream — el caller debe cerrarlo
            FileInputStream(tempFile)
        } catch (e: Exception) {
            Log.e(TAG, "Error descargando bootstrap: ${e.message}", e)
            tempFile?.delete()
            null
        }
    }

    /**
     * Extrae el ZIP con reporte de progreso.
     */
    private fun extractZip(
        inputStream: InputStream,
        destDir: File,
        progressListener: DownloadProgressListener?
    ): Boolean {
        return try {
            val bufferSize = 8192
            val buffer = ByteArray(bufferSize)
            val zipInputStream = ZipInputStream(BufferedInputStream(inputStream))

            var entry: ZipEntry?
            var fileCount = 0
            while (zipInputStream.nextEntry.also { entry = it } != null) {
                val entryName = entry!!.name
                val outFile = File(destDir, entryName)

                // Directory traversal protection
                val canonicalPath = outFile.canonicalPath
                if (!canonicalPath.startsWith(destDir.canonicalPath)) {
                    Log.w(TAG, "Skipping malicious zip entry: $entryName")
                    zipInputStream.closeEntry()
                    continue
                }

                if (entry!!.isDirectory) {
                    outFile.mkdirs()
                    zipInputStream.closeEntry()
                    continue
                }

                outFile.parentFile?.mkdirs()

                FileOutputStream(outFile).use { fos ->
                    var bytesRead: Int
                    while (zipInputStream.read(buffer, 0, bufferSize).also { bytesRead = it } != -1) {
                        fos.write(buffer, 0, bytesRead)
                    }
                }

                if (entryName.startsWith("bin/")) {
                    outFile.setExecutable(true, false)
                }

                fileCount++

                // Reportar progreso basado en archivos extraídos (estimado)
                progressListener?.onProgress(
                    minOf(99, (fileCount * 100) / 3500), // ~3500 archivos en bootstrap
                    fileCount.toLong(),
                    3500L
                )

                zipInputStream.closeEntry()
            }

            Log.i(TAG, "Extraídos $fileCount archivos del bootstrap")
            progressListener?.onProgress(100, 3500, 3500)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error extrayendo ZIP: ${e.message}", e)
            false
        }
    }

    private fun ensureExecutable(file: File) {
        if (!file.canExecute()) {
            file.setExecutable(true, false)
            Log.i(TAG, "Permisos de ejecución corregidos en ${file.name}")
        }
    }

    private fun makeBinariesExecutable(prefixDir: File) {
        val binDir = File(prefixDir, "bin")
        if (binDir.exists()) {
            binDir.listFiles()?.forEach { file ->
                if (!file.isFile) return@forEach

                // Detectar es script (shebang) o binario ELF
                val isScript = try {
                    val header = file.readBytes().take(4)
                    val headerStr = String(header.toByteArray(), Charsets.UTF_8)
                    headerStr.startsWith("#!")
                } catch (e: Exception) { false }

                if (isScript) {
                    // NO dar permisos de ejecución a scripts.
                    // SELinux bloquea execute_no_trans para archivos en app_data_file.
                    // command_not_found_handle en .bashrc los ejecutará via /system/bin/sh
                    file.setReadable(true, false)
                    file.setExecutable(false, false)
                    Log.i(TAG, "Script sin exec: bin/${file.name} (SELinux workaround)")
                } else {
                    // Binario ELF — dar permisos de ejecución
                    file.setExecutable(true, false)
                    file.setReadable(true, false)
                }
            }
        }
        // También lib/ — y crear symlinks .so.X -> .so.X.Y
        val libDir = File(prefixDir, "lib")
        if (libDir.exists()) {
            libDir.listFiles()?.forEach { file ->
                if (file.isFile && file.extension == "so") {
                    file.setReadable(true, false)
                }
                // Crear symlinks para libs versionadas: libfoo.so.8.3 -> libfoo.so.8
                if (file.name.matches(Regex(""".*\.so\.\d+\.\d+$"""))) {
                    val shortName = file.name.substringBeforeLast(".")
                    val symlink = File(file.parentFile, shortName)
                    if (!symlink.exists()) {
                        try {
                            java.nio.file.Files.createSymbolicLink(
                                symlink.toPath(),
                                file.toPath()
                            )
                            Log.i(TAG, "Symlink creado: $shortName -> ${file.name}")
                        } catch (e: Exception) {
                            Log.w(TAG, "No se pudo crear symlink $shortName: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        }
    }

    /**
     * Limpia archivos temporales de descarga previa.
     */
    private fun cleanTempDownloads(context: Context) {
        try {
            context.filesDir.listFiles { file ->
                file.name.startsWith("bootstrap_download_") && file.name.endsWith(".zip")
            }?.forEach { it.delete() }
        } catch (e: Exception) {
            Log.w(TAG, "Error limpiando descargas temporales: ${e.message}")
        }
    }
}
