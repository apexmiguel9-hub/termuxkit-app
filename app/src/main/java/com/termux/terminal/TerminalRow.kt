package com.termux.terminal

import java.util.Arrays

/**
 * A row in a terminal, composed of a fixed number of cells.
 *
 * The text in the row is stored in a char array, [text], for quick access during rendering.
 */
class TerminalRow(columns: Int, style: Long) {

    private companion object {
        private const val SPARE_CAPACITY_FACTOR = 1.5f

        /**
         * Max combining characters that can exist in a column, that are separate from the base character
         * itself. Any additional combining characters will be ignored and not added to the column.
         *
         * There does not seem to be limit in unicode standard for max number of combination characters
         * that can be combined but such characters are primarily under 10.
         *
         * "Section 3.6 Combination" of unicode standard contains combining characters info.
         * - https://www.unicode.org/versions/Unicode15.0.0/ch03.pdf
         * - https://en.wikipedia.org/wiki/Combining_character#Unicode_ranges
         * - https://stackoverflow.com/questions/71237212/what-is-the-maximum-number-of-unicode-combined-characters-that-may-be-needed-to
         *
         * UAX15-D3 Stream-Safe Text Format limits to max 30 combining characters.
         * > The value of 30 is chosen to be significantly beyond what is required for any linguistic or technical usage.
         * > While it would have been feasible to chose a smaller number, this value provides a very wide margin,
         * > yet is well within the buffer size limits of practical implementations.
         * - https://unicode.org/reports/tr15/#Stream_Safe_Text_Format
         * - https://stackoverflow.com/a/11983435/14686958
         *
         * We choose the value 15 because it should be enough for terminal based applications and keep
         * the memory usage low for a terminal row, won't affect performance or cause terminal to
         * lag or hang, and will keep malicious applications from causing harm. The value can be
         * increased if ever needed for legitimate applications.
         */
        private const val MAX_COMBINING_CHARACTERS_PER_COLUMN = 15
    }

    /** The number of columns in this terminal row. */
    private val columns: Int = columns
    /** The text filling this terminal row. */
    var text: CharArray = CharArray((SPARE_CAPACITY_FACTOR * columns).toInt())
    /** The number of java chars used in {@link #text}. */
    private var spaceUsed: Short = 0
    /** If this row has been line wrapped due to text output at the end of line. */
    var lineWrap: Boolean = false
    /** The style bits of each cell in the row. See {@link TextStyle}. */
    var style: LongArray = LongArray(columns)
    /** If this row might contain chars with width != 1, used for deactivating fast path */
    var hasNonOneWidthOrSurrogateChars: Boolean = false

    init {
        clear(style)
    }

    /** NOTE: The sourceX2 is exclusive. */
    fun copyInterval(line: TerminalRow, sourceX1: Int, sourceX2: Int, destinationX: Int) {
        hasNonOneWidthOrSurrogateChars = hasNonOneWidthOrSurrogateChars or line.hasNonOneWidthOrSurrogateChars
        var destX = destinationX
        var srcX1 = sourceX1
        val x1 = line.findStartOfColumn(srcX1)
        val x2 = line.findStartOfColumn(sourceX2)
        var startingFromSecondHalfOfWideChar = (srcX1 > 0 && line.wideDisplayCharacterStartingAt(srcX1 - 1))
        val sourceChars = if (this == line) Arrays.copyOf(line.text, line.text.size) else line.text
        var latestNonCombiningWidth = 0
        var i = x1
        while (i < x2) {
            var sourceChar = sourceChars[i]
            var codePoint = if (Character.isHighSurrogate(sourceChar)) Character.toCodePoint(sourceChar, sourceChars[++i]) else sourceChar.code
            if (startingFromSecondHalfOfWideChar) {
                // Just treat copying second half of wide char as copying whitespace.
                codePoint = ' '.code
                startingFromSecondHalfOfWideChar = false
            }
            val w = WcWidth.width(codePoint)
            if (w > 0) {
                destX += latestNonCombiningWidth
                srcX1 += latestNonCombiningWidth
                latestNonCombiningWidth = w
            }
            setChar(destX, codePoint, line.getStyle(srcX1))
            i++
        }
    }

    fun getSpaceUsed(): Int {
        return spaceUsed.toInt()
    }

    /** Note that the column may end of second half of wide character. */
    fun findStartOfColumn(column: Int): Int {
        if (column == columns) return spaceUsed.toInt()

        var currentColumn = 0
        var currentCharIndex = 0
        while (true) {
            var newCharIndex = currentCharIndex
            val c = text[newCharIndex++]
            val isHigh = Character.isHighSurrogate(c)
            val codePoint = if (isHigh) Character.toCodePoint(c, text[newCharIndex++]) else c.code
            val wcwidth = WcWidth.width(codePoint)
            if (wcwidth > 0) {
                currentColumn += wcwidth
                if (currentColumn == column) {
                    while (newCharIndex < spaceUsed) {
                        // Skip combining chars.
                        if (Character.isHighSurrogate(text[newCharIndex])) {
                            if (WcWidth.width(Character.toCodePoint(text[newCharIndex], text[newCharIndex + 1])) <= 0) {
                                newCharIndex += 2
                            } else {
                                break
                            }
                        } else if (WcWidth.width(text[newCharIndex].code) <= 0) {
                            newCharIndex++
                        } else {
                            break
                        }
                    }
                    return newCharIndex
                } else if (currentColumn > column) {
                    // Wide column going past end.
                    return currentCharIndex
                }
            }
            currentCharIndex = newCharIndex
        }
    }

    private fun wideDisplayCharacterStartingAt(column: Int): Boolean {
        var currentCharIndex = 0
        var currentColumn = 0
        while (currentCharIndex < spaceUsed) {
            val c = text[currentCharIndex++]
            val codePoint = if (Character.isHighSurrogate(c)) Character.toCodePoint(c, text[currentCharIndex++]) else c.code
            val wcwidth = WcWidth.width(codePoint)
            if (wcwidth > 0) {
                if (currentColumn == column && wcwidth == 2) return true
                currentColumn += wcwidth
                if (currentColumn > column) return false
            }
        }
        return false
    }

    fun clear(styleValue: Long) {
        java.util.Arrays.fill(text, ' ')
        java.util.Arrays.fill(style, styleValue)
        spaceUsed = columns.toShort()
        hasNonOneWidthOrSurrogateChars = false
    }

    // https://github.com/steven676/Android-Terminal-Emulator/commit/9a47042620bec87617f0b4f5d50568535668fe26
    fun setChar(columnToSet: Int, codePoint: Int, styleValue: Long) {
        if (columnToSet < 0 || columnToSet >= style.size)
            throw IllegalArgumentException("TerminalRow.setChar(): columnToSet=$columnToSet, codePoint=$codePoint, style=$styleValue")

        style[columnToSet] = styleValue

        val newCodePointDisplayWidth = WcWidth.width(codePoint)

        // Fast path when we don't have any chars with width != 1
        if (!hasNonOneWidthOrSurrogateChars) {
            if (codePoint >= Character.MIN_SUPPLEMENTARY_CODE_POINT || newCodePointDisplayWidth != 1) {
                hasNonOneWidthOrSurrogateChars = true
            } else {
                text[columnToSet] = codePoint.toChar()
                return
            }
        }

        val newIsCombining = newCodePointDisplayWidth <= 0

        var col = columnToSet
        var wasExtraColForWideChar = (col > 0) && wideDisplayCharacterStartingAt(col - 1)

        if (newIsCombining) {
            // When standing at second half of wide character and inserting combining:
            if (wasExtraColForWideChar) col--
        } else {
            // Check if we are overwriting the second half of a wide character starting at the previous column:
            if (wasExtraColForWideChar) setChar(col - 1, ' '.code, styleValue)
            // Check if we are overwriting the first half of a wide character starting at the next column:
            val overwritingWideCharInNextColumn = newCodePointDisplayWidth == 2 && wideDisplayCharacterStartingAt(col + 1)
            if (overwritingWideCharInNextColumn) setChar(col + 1, ' '.code, styleValue)
        }

        val oldStartOfColumnIndex = findStartOfColumn(columnToSet)
        val oldCodePointDisplayWidth = WcWidth.width(this.text, oldStartOfColumnIndex)

        // Get the number of elements in the text array this column uses now
        val oldCharactersUsedForColumn = if (columnToSet + oldCodePointDisplayWidth < columns) {
            val oldEndOfColumnIndex = findStartOfColumn(columnToSet + oldCodePointDisplayWidth)
            oldEndOfColumnIndex - oldStartOfColumnIndex
        } else {
            // Last character.
            spaceUsed.toInt() - oldStartOfColumnIndex
        }

        // If MAX_COMBINING_CHARACTERS_PER_COLUMN already exist in column, then ignore adding additional combining characters.
        if (newIsCombining) {
            val combiningCharsCount = WcWidth.zeroWidthCharsCount(this.text, oldStartOfColumnIndex, oldStartOfColumnIndex + oldCharactersUsedForColumn)
            if (combiningCharsCount >= MAX_COMBINING_CHARACTERS_PER_COLUMN)
                return
        }

        // Find how many chars this column will need
        var newCharactersUsedForColumn = Character.charCount(codePoint)
        if (newIsCombining) {
            // Combining characters are added to the contents of the column instead of overwriting them, so that they
            // modify the existing contents.
            // FIXME: Unassigned characters also get width=0.
            newCharactersUsedForColumn += oldCharactersUsedForColumn
        }

        val oldNextColumnIndex = oldStartOfColumnIndex + oldCharactersUsedForColumn
        val newNextColumnIndex = oldStartOfColumnIndex + newCharactersUsedForColumn

        val javaCharDifference = newCharactersUsedForColumn - oldCharactersUsedForColumn
        if (javaCharDifference > 0) {
            // Shift the rest of the line right.
            val oldCharactersAfterColumn = spaceUsed.toInt() - oldNextColumnIndex
            if (spaceUsed + javaCharDifference > this.text.size) {
                // We need to grow the array
                val newText = CharArray(this.text.size + columns)
                System.arraycopy(this.text, 0, newText, 0, oldNextColumnIndex)
                System.arraycopy(this.text, oldNextColumnIndex, newText, newNextColumnIndex, oldCharactersAfterColumn)
                this.text = newText
            } else {
                System.arraycopy(this.text, oldNextColumnIndex, this.text, newNextColumnIndex, oldCharactersAfterColumn)
            }
        } else if (javaCharDifference < 0) {
            // Shift the rest of the line left.
            System.arraycopy(this.text, oldNextColumnIndex, this.text, newNextColumnIndex, spaceUsed.toInt() - oldNextColumnIndex)
        }
        spaceUsed = (spaceUsed + javaCharDifference).toShort()

        // Store char. A combining character is stored at the end of the existing contents so that it modifies them:
        //noinspection ResultOfMethodCallIgnored - since we already now how many java chars is used.
        Character.toChars(codePoint, this.text, oldStartOfColumnIndex + if (newIsCombining) oldCharactersUsedForColumn else 0)

        if (oldCodePointDisplayWidth == 2 && newCodePointDisplayWidth == 1) {
            // Replace second half of wide char with a space. Which mean that we actually add a ' ' java character.
            if (spaceUsed + 1 > this.text.size) {
                val newText = CharArray(this.text.size + columns)
                System.arraycopy(this.text, 0, newText, 0, newNextColumnIndex)
                System.arraycopy(this.text, newNextColumnIndex, newText, newNextColumnIndex + 1, spaceUsed.toInt() - newNextColumnIndex)
                this.text = newText
            } else {
                System.arraycopy(this.text, newNextColumnIndex, this.text, newNextColumnIndex + 1, spaceUsed.toInt() - newNextColumnIndex)
            }
            this.text[newNextColumnIndex] = ' '

            spaceUsed = (spaceUsed + 1).toShort()
        } else if (oldCodePointDisplayWidth == 1 && newCodePointDisplayWidth == 2) {
            if (col == columns - 1) {
                throw IllegalArgumentException("Cannot put wide character in last column")
            } else if (col == columns - 2) {
                // Truncate the line to the second part of this wide char:
                spaceUsed = newNextColumnIndex.toShort()
            } else {
                // Overwrite the contents of the next column, which mean we actually remove java characters. Due to the
                // check at the beginning of this method we know that we are not overwriting a wide char.
                val newNextNextColumnIndex = newNextColumnIndex + if (Character.isHighSurrogate(this.text[newNextColumnIndex])) 2 else 1
                val nextLen = newNextNextColumnIndex - newNextColumnIndex

                // Shift the array leftwards.
                System.arraycopy(this.text, newNextNextColumnIndex, this.text, newNextColumnIndex, spaceUsed.toInt() - newNextNextColumnIndex)
                spaceUsed = (spaceUsed - nextLen).toShort()
            }
        }
    }

    fun isBlank(): Boolean {
        for (charIndex in 0 until spaceUsed.toInt())
            if (text[charIndex] != ' ') return false
        return true
    }

    fun getStyle(column: Int): Long {
        return style[column]
    }
}
