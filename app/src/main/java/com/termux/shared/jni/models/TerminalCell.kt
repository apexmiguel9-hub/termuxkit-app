package com.termux.shared.jni.models

/**
 * TerminalCell - Modelo de celda de terminal
 *
 * Coincide exactamente con la estructura Rust Cell:
 * - char: u32 (Int en Kotlin)
 * - fg_color: u32 (Int ARGB)
 * - bg_color: u32 (Int ARGB)
 * - flags: u8 (Short en Kotlin: Bold=1, Underline=2, Reverse=4)
 */
data class TerminalCell(
    val char: Int = ' '.code,
    val fgColor: Int = 0xFFFFFFFF.toInt(),
    val bgColor: Int = 0xFF000000.toInt(),
    val flags: Short = 0
) {
    val isBold: Boolean get() = (flags.toInt() and 0x01) != 0
    val isUnderline: Boolean get() = (flags.toInt() and 0x02) != 0
    val isReverse: Boolean get() = (flags.toInt() and 0x04) != 0
    
    companion object {
        fun empty() = TerminalCell()
    }
}
