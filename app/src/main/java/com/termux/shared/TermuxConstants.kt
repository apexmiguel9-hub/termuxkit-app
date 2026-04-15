package com.termux.shared

import android.os.Environment

/**
 * Constantes de paths — mismas rutas que usa Termux original.
 * Package name: com.termux.alpa_termuxkit
 */
object TermuxConstants {
    // Package name de esta app
    const val TERMUX_PACKAGE_NAME = "com.termux.alpa_termuxkit"

    // Paths dentro del directorio de datos de la app
    // NOTA: /data/data y /data/user/0 son equivalentes en Android
    const val TERMUX_PREFIX_PATH = "/data/data/$TERMUX_PACKAGE_NAME/files/usr"
    const val TERMUX_FILES_DIR = "/data/data/$TERMUX_PACKAGE_NAME/files"

    // Binarios clave
    const val TERMUX_BASH_PATH = "$TERMUX_PREFIX_PATH/bin/bash"
    const val TERMUX_ZSH_PATH = "$TERMUX_PREFIX_PATH/bin/zsh"
    const val TERMUX_SH_PATH = "$TERMUX_PREFIX_PATH/bin/sh"

    // Fallback si el bootstrap no está disponible
    const val SYSTEM_SH_PATH = "/system/bin/sh"

    // Archivo bootstrap en assets
    const val BOOTSTRAP_ASSET_NAME = "bootstrap.zip"

    // Variables de entorno que Termux usa
    const val TERMUX_PREFIX_ENV = "PREFIX=$TERMUX_PREFIX_PATH"
    const val TERMUX_HOME_ENV = "HOME=$TERMUX_FILES_DIR/home"
    const val TERMUX_LD_LIBRARY_PATH = "LD_LIBRARY_PATH=$TERMUX_PREFIX_PATH/lib"
    const val TERMUX_PATH_ENV = "PATH=$TERMUX_PREFIX_PATH/bin:$TERMUX_PREFIX_PATH/bin/applets:/system/bin:/system/xbin"

    // Shell por defecto a usar (bootstrap bash si existe y funciona, sino dash, sino system sh)
    fun getDefaultShell(bootstrapPrefix: String? = null): String {
        if (bootstrapPrefix != null) {
            // Intentar bash primero (el bootstrap oficial de Termux incluye libreadline)
            val bashPath = "$bootstrapPrefix/bin/bash"
            if (java.io.File(bashPath).exists()) {
                return bashPath
            }
            // Fallback a dash si bash no existe
            val dashPath = "$bootstrapPrefix/bin/dash"
            if (java.io.File(dashPath).exists()) {
                return dashPath
            }
        }
        // Fallback al sh del sistema
        return SYSTEM_SH_PATH
    }
}
