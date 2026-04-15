package com.termux.alpa_termuxkit.ui.component

/**
 * TerminalCell - Representa una celda individual de la terminal
 *
 * @property char Carácter Unicode a mostrar
 * @property fg Color de frente (ARGB)
 * @property bg Color de fondo (ARGB)
 * @property bold Texto en negrita
 * @property underline Texto subrayado
 */
data class TerminalCell(
    val char: Char = ' ',
    val fg: Int = 0xFFFFFFFF.toInt(),
    val bg: Int = 0xFF000000.toInt(),
    val bold: Boolean = false,
    val underline: Boolean = false
) {
    companion object {
        /**
         * Celda vacía por defecto
         */
        fun empty() = TerminalCell()
    }
}
