package com.termux.terminal

import android.graphics.Color
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Current terminal colors (if different from default).
 */
class TerminalColors {

    /** The current terminal colors, normally set from the color theme. */
    val currentColors = IntArray(TextStyle.NUM_INDEXED_COLORS)

    init {
        reset()
    }

    /** Reset a particular indexed color with the default color from the color theme. */
    fun reset(index: Int) {
        currentColors[index] = COLOR_SCHEME.defaultColors[index]
    }

    /** Reset all indexed colors with the default color from the color theme. */
    fun reset() {
        COLOR_SCHEME.defaultColors.copyInto(currentColors)
    }

    /** Try parse a color from a text parameter and store it into a specified index. */
    fun tryParseColor(intoIndex: Int, textParameter: String) {
        val c = parse(textParameter)
        if (c != 0) {
            currentColors[intoIndex] = c
        }
    }

    companion object {
        val COLOR_SCHEME = TerminalColorScheme()

        /**
         * Parse color according to XQueryColor spec.
         * Returns 0xFFRRGGBB or 0 if failed.
         */
        internal fun parse(c: String): Int {
            if (c.isEmpty()) return 0
            
            return try {
                val skipInitial: Int
                val skipBetween: Int
                
                when {
                    c[0] == '#' -> {
                        skipInitial = 1
                        skipBetween = 0
                    }
                    c.startsWith("rgb:") -> {
                        skipInitial = 4
                        skipBetween = 1
                    }
                    else -> return 0
                }

                val charsForColors = c.length - skipInitial - 2 * skipBetween
                if (charsForColors % 3 != 0) return 0

                val componentLength = charsForColors / 3
                val mult = 255.0 / (2.0.pow(componentLength * 4) - 1.0)

                var currentPos = skipInitial
                
                val rString = c.substring(currentPos, currentPos + componentLength)
                currentPos += componentLength + skipBetween
                val gString = c.substring(currentPos, currentPos + componentLength)
                currentPos += componentLength + skipBetween
                val bString = c.substring(currentPos, currentPos + componentLength)

                val r = (rString.toInt(16) * mult).toInt()
                val g = (gString.toInt(16) * mult).toInt()
                val b = (bString.toInt(16) * mult).toInt()

                (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            } catch (e: Exception) {
                0
            }
        }

        /**
         * Get the perceived brightness of the color based on its RGB components.
         */
        fun getPerceivedBrightnessOfColor(color: Int): Int {
            val r = Color.red(color).toDouble()
            val g = Color.green(color).toDouble()
            val b = Color.blue(color).toDouble()
            
            val brightness = sqrt(
                r * r * 0.241 +
                g * g * 0.691 +
                b * b * 0.068
            )
            return floor(brightness).toInt()
        }
    }
}
