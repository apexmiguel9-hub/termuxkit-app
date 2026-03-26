package com.termux.terminal

/**
 * Utility class for encoding and decoding terminal text styles into a long.
 */
class TextStyle {
    companion object {
        const val CHARACTER_ATTRIBUTE_BOLD = 1
        const val CHARACTER_ATTRIBUTE_ITALIC = 1 shl 1
        const val CHARACTER_ATTRIBUTE_UNDERLINE = 1 shl 2
        const val CHARACTER_ATTRIBUTE_BLINK = 1 shl 3
        const val CHARACTER_ATTRIBUTE_INVERSE = 1 shl 4
        const val CHARACTER_ATTRIBUTE_INVISIBLE = 1 shl 5
        const val CHARACTER_ATTRIBUTE_STRIKETHROUGH = 1 shl 6
        const val CHARACTER_ATTRIBUTE_PROTECTED = 1 shl 7
        const val CHARACTER_ATTRIBUTE_DIM = 1 shl 8
        
        const val CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND = 1L shl 9
        const val CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND = 1L shl 10

        const val COLOR_INDEX_FOREGROUND = 256
        const val COLOR_INDEX_BACKGROUND = 257
        const val COLOR_INDEX_CURSOR = 258
        const val NUM_INDEXED_COLORS = 259

        @JvmField
        val NORMAL: Long = encode(COLOR_INDEX_FOREGROUND, COLOR_INDEX_BACKGROUND, 0)

        @JvmStatic
        fun encode(foreColor: Int, backColor: Int, effect: Int): Long {
            var result = effect.toLong() and 0x1FFL
            
            // Encode Foreground
            result = if ((foreColor.toLong() and 0xff000000L) == 0xff000000L) {
                result or CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND or ((foreColor.toLong() and 0x00ffffffL) shl 40)
            } else {
                result or ((foreColor.toLong() and 0x1FFL) shl 40)
            }
            
            // Encode Background
            result = if ((backColor.toLong() and 0xff000000L) == 0xff000000L) {
                result or CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND or ((backColor.toLong() and 0x00ffffffL) shl 16)
            } else {
                result or ((backColor.toLong() and 0x1FFL) shl 16)
            }
            
            return result
        }

        @JvmStatic
        fun decodeForeColor(style: Long): Int = 
            if ((style and CHARACTER_ATTRIBUTE_TRUECOLOR_FOREGROUND) == 0L) {
                ((style shr 40) and 0x1FFL).toInt()
            } else {
                0xff000000.toInt() or ((style shr 40) and 0x00ffffffL).toInt()
            }

        @JvmStatic
        fun decodeBackColor(style: Long): Int =
            if ((style and CHARACTER_ATTRIBUTE_TRUECOLOR_BACKGROUND) == 0L) {
                ((style shr 16) and 0x1FFL).toInt()
            } else {
                0xff000000.toInt() or ((style shr 16) and 0x00ffffffL).toInt()
            }

        @JvmStatic
        fun decodeEffect(style: Long): Int = (style and 0x1FFL).toInt()
    }
}
