package com.termux.terminal

import java.util.Arrays

import kotlin.math.min

/**
 * A circular buffer of [TerminalRow]:s which keeps notes about what is visible on a logical screen and the scroll
 * history.
 *
 * See [externalToInternalRow] for how to map from logical screen rows to array indices.
 */
class TerminalBuffer(
    var columns: Int,
    var totalRows: Int,
    var screenRows: Int
) {

    var lines: Array<TerminalRow?> = arrayOfNulls(totalRows)
        private set
    /** The number of rows kept in history. */
    var activeTranscriptRows: Int = 0
        private set
    /** The index in the circular buffer where the visible screen starts. */
    var screenFirstRow: Int = 0
        private set

    init {
        blockSet(0, 0, columns, screenRows, ' '.code, TextStyle.NORMAL)
    }

    fun getTranscriptText(): String {
        return getSelectedText(0, -activeTranscriptRows, columns, screenRows).trim()
    }

    fun getTranscriptTextWithoutJoinedLines(): String {
        return getSelectedText(0, -activeTranscriptRows, columns, screenRows, false).trim()
    }

    fun getTranscriptTextWithFullLinesJoined(): String {
        return getSelectedText(0, -activeTranscriptRows, columns, screenRows, true, true).trim()
    }

    fun getSelectedText(selX1: Int, selY1: Int, selX2: Int, selY2: Int): String {
        return getSelectedText(selX1, selY1, selX2, selY2, true)
    }

    fun getSelectedText(selX1: Int, selY1: Int, selX2: Int, selY2: Int, joinBackLines: Boolean): String {
        return getSelectedText(selX1, selY1, selX2, selY2, joinBackLines, false)
    }

    fun getSelectedText(selX1: Int, selY1: Int, selX2: Int, selY2: Int, joinBackLines: Boolean, joinFullLines: Boolean): String {
        val builder = StringBuilder()
        val columns = columns

        var adjustedSelY1 = selY1
        var adjustedSelY2 = selY2
        if (adjustedSelY1 < -activeTranscriptRows) adjustedSelY1 = -activeTranscriptRows
        if (adjustedSelY2 >= screenRows) adjustedSelY2 = screenRows - 1

        for (row in adjustedSelY1..adjustedSelY2) {
            val x1 = if (row == adjustedSelY1) selX1 else 0
            val x2 = if (row == adjustedSelY2) {
                minOf(selX2 + 1, columns)
            } else {
                columns
            }
            val lineObject = lines[externalToInternalRow(row)] ?: continue
            val x1Index = lineObject.findStartOfColumn(x1)
            val x2Index = if (x2 < columns) lineObject.findStartOfColumn(x2) else lineObject.getSpaceUsed()
            var adjustedX2Index = x2Index
            if (adjustedX2Index == x1Index) {
                // Selected the start of a wide character.
                adjustedX2Index = lineObject.findStartOfColumn(x2 + 1)
            }
            val line = lineObject.text
            var lastPrintingCharIndex = -1
            if (x2Index > x1Index) {
                val rowLineWrap = getLineWrap(row)
                if (rowLineWrap && x2 == columns) {
                    // If the line was wrapped, we shouldn't lose trailing space:
                    lastPrintingCharIndex = adjustedX2Index - 1
                } else {
                    for (i in x1Index until adjustedX2Index) {
                        if (line[i] != ' ') lastPrintingCharIndex = i
                    }
                }

                val len = lastPrintingCharIndex - x1Index + 1
                if (lastPrintingCharIndex != -1 && len > 0)
                    builder.append(line, x1Index, len)

                val lineFillsWidth = lastPrintingCharIndex == adjustedX2Index - 1
                if ((!joinBackLines || !rowLineWrap) && (!joinFullLines || !lineFillsWidth)
                    && row < adjustedSelY2 && row < screenRows - 1
                ) builder.append('\n')
            }
        }
        return builder.toString()
    }

    fun getWordAtLocation(x: Int, y: Int): String {
        // Set y1 and y2 to the lines where the wrapped line starts and ends.
        // I.e. if a line that is wrapped to 3 lines starts at line 4, and this
        // is called with y=5, then y1 would be set to 4 and y2 would be set to 6.
        var y1 = y
        var y2 = y
        while (y1 > 0 && !getSelectedText(0, y1 - 1, columns, y, true, true).contains("\n")) {
            y1--
        }
        while (y2 < screenRows && !getSelectedText(0, y, columns, y2 + 1, true, true).contains("\n")) {
            y2++
        }

        // Get the text for the whole wrapped line
        val text = getSelectedText(0, y1, columns, y2, true, true)
        // The index of x in text
        val textOffset = (y - y1) * columns + x

        if (textOffset >= text.length) {
            // The click was to the right of the last word on the line, so
            // there's no word to return
            return ""
        }

        // Set x1 and x2 to the indices of the last space before x and the
        // first space after x in text respectively
        var x1 = text.lastIndexOf(' ', textOffset)
        var x2 = text.indexOf(' ', textOffset)
        if (x2 == -1) {
            x2 = text.length
        }

        if (x1 == x2) {
            // The click was on a space, so there's no word to return
            return ""
        }
        return text.substring(x1 + 1, x2)
    }

    val activeRows: Int
        get() = activeTranscriptRows + screenRows

    /**
     * Convert a row value from the public external coordinate system to our internal private coordinate system.
     *
     * <pre>
     * - External coordinate system: -activeTranscriptRows to screenRows-1, with the screen being 0..screenRows-1.
     * - Internal coordinate system: the screenRows lines starting at screenFirstRow comprise the screen, while the
     *   activeTranscriptRows lines ending at screenFirstRow-1 form the transcript (as a circular buffer).
     *
     * External ↔ Internal:
     *
     * [ ...                            ]     [ ...                                     ]
     * [ -activeTranscriptRows         ]     [ screenFirstRow - activeTranscriptRows ]
     * [ ...                            ]     [ ...                                     ]
     * [ 0 (visible screen starts here) ]  ↔  [ screenFirstRow                         ]
     * [ ...                            ]     [ ...                                     ]
     * [ screenRows-1                  ]     [ screenFirstRow + screenRows-1         ]
     * </pre>
     *
     * @param externalRow a row in the external coordinate system.
     * @return The row corresponding to the input argument in the private coordinate system.
     */
    fun externalToInternalRow(externalRow: Int): Int {
        if (externalRow < -activeTranscriptRows || externalRow > screenRows)
            throw IllegalArgumentException("extRow=$externalRow, screenRows=$screenRows, activeTranscriptRows=$activeTranscriptRows")
        val internalRow = screenFirstRow + externalRow
        return if (internalRow < 0) (totalRows + internalRow) else (internalRow % totalRows)
    }

    fun setLineWrap(row: Int) {
        lines[externalToInternalRow(row)]?.lineWrap = true
    }

    fun getLineWrap(row: Int): Boolean {
        return lines[externalToInternalRow(row)]?.lineWrap ?: false
    }

    fun clearLineWrap(row: Int) {
        lines[externalToInternalRow(row)]?.lineWrap = false
    }

    /**
     * Resize the screen which this transcript backs. Currently, this only works if the number of columns does not
     * change or the rows expand (that is, it only works when shrinking the number of rows).
     *
     * @param newColumns The number of columns the screen should have.
     * @param newRows    The number of rows the screen should have.
     * @param cursor     An int[2] containing the (column, row) cursor location.
     */
    fun resize(
        newColumns: Int,
        newRows: Int,
        newTotalRows: Int,
        cursor: IntArray,
        currentStyle: Long,
        altScreen: Boolean
    ) {
        // newRows > totalRows should not normally happen since totalRows is TRANSCRIPT_ROWS (10000):
        if (newColumns == columns && newRows <= totalRows) {
            // Fast resize where just the rows changed.
            var shiftDownOfTopRow = screenRows - newRows
            if (shiftDownOfTopRow > 0 && shiftDownOfTopRow < screenRows) {
                // Shrinking. Check if we can skip blank rows at bottom below cursor.
                for (i in screenRows - 1 downTo 1) {
                    if (cursor[1] >= i) break
                    val r = externalToInternalRow(i)
                    if (lines[r] == null || (lines[r]?.isBlank() ?: true)) {
                        if (--shiftDownOfTopRow == 0) break
                    }
                }
            } else if (shiftDownOfTopRow < 0) {
                // Negative shift down = expanding. Only move screen up if there is transcript to show:
                val actualShift = maxOf(shiftDownOfTopRow, -activeTranscriptRows)
                if (shiftDownOfTopRow != actualShift) {
                    // The new lines revealed by the resizing are not all from the transcript. Blank the below ones.
                    for (i in 0 until actualShift - shiftDownOfTopRow)
                        allocateFullLineIfNecessary((screenFirstRow + screenRows + i) % totalRows).clear(currentStyle)
                    shiftDownOfTopRow = actualShift
                }
            }
            screenFirstRow += shiftDownOfTopRow
            screenFirstRow = if (screenFirstRow < 0) (screenFirstRow + totalRows) else (screenFirstRow % totalRows)
            totalRows = newTotalRows
            activeTranscriptRows = if (altScreen) 0 else maxOf(0, activeTranscriptRows + shiftDownOfTopRow)
            cursor[1] -= shiftDownOfTopRow
            screenRows = newRows
        } else {
            // Copy away old state and update new:
            val oldLines = lines
            lines = arrayOfNulls(newTotalRows)
            for (i in 0 until newTotalRows)
                lines[i] = TerminalRow(newColumns, currentStyle)

            val oldActiveTranscriptRows = activeTranscriptRows
            val oldScreenFirstRow = screenFirstRow
            val oldScreenRows = screenRows
            val oldTotalRows = totalRows
            totalRows = newTotalRows
            screenRows = newRows
            activeTranscriptRows = 0
            screenFirstRow = 0
            columns = newColumns

            var newCursorRow = -1
            var newCursorColumn = -1
            val oldCursorRow = cursor[1]
            val oldCursorColumn = cursor[0]
            var newCursorPlaced = false

            var currentOutputExternalRow = 0
            var currentOutputExternalColumn = 0

            // Loop over every character in the initial state.
            // Blank lines should be skipped only if at end of transcript (just as is done in the "fast" resize), so we
            // keep track how many blank lines we have skipped if we later on find a non-blank line.
            var skippedBlankLines = 0
            for (externalOldRow in -oldActiveTranscriptRows until oldScreenRows) {
                // Do what externalToInternalRow() does but for the old state:
                var internalOldRow = oldScreenFirstRow + externalOldRow
                internalOldRow = if (internalOldRow < 0) (oldTotalRows + internalOldRow) else (internalOldRow % oldTotalRows)

                val oldLine = oldLines[internalOldRow]
                val cursorAtThisRow = externalOldRow == oldCursorRow
                // The cursor may only be on a non-null line, which we should not skip:
                if (oldLine == null || (!(!newCursorPlaced && cursorAtThisRow)) && oldLine.isBlank()) {
                    skippedBlankLines++
                    continue
                } else if (skippedBlankLines > 0) {
                    // After skipping some blank lines we encounter a non-blank line. Insert the skipped blank lines.
                    for (i in 0 until skippedBlankLines) {
                        if (currentOutputExternalRow == screenRows - 1) {
                            scrollDownOneLine(0, screenRows, currentStyle)
                        } else {
                            currentOutputExternalRow++
                        }
                        currentOutputExternalColumn = 0
                    }
                    skippedBlankLines = 0
                }

                var lastNonSpaceIndex = 0
                var justToCursor = false
                if (cursorAtThisRow || oldLine.lineWrap) {
                    // Take the whole line, either because of cursor on it, or if line wrapping.
                    lastNonSpaceIndex = oldLine.getSpaceUsed()
                    if (cursorAtThisRow) justToCursor = true
                } else {
                    for (i in 0 until oldLine.getSpaceUsed())
                        if (oldLine.text[i] != ' ')
                            lastNonSpaceIndex = i + 1
                }

                var currentOldCol = 0
                var styleAtCol: Long = 0
                var i = 0
                while (i < lastNonSpaceIndex) {
                    // Note that looping over java character, not cells.
                    val c = oldLine.text[i]
                    val codePoint = if (Character.isHighSurrogate(c)) {
                        Character.toCodePoint(c, oldLine.text[++i])
                    } else {
                        c.code
                    }
                    val displayWidth = WcWidth.width(codePoint)
                    // Use the last style if this is a zero-width character:
                    if (displayWidth > 0) styleAtCol = oldLine.getStyle(currentOldCol)

                    // Line wrap as necessary:
                    if (currentOutputExternalColumn + displayWidth > columns) {
                        setLineWrap(currentOutputExternalRow)
                        if (currentOutputExternalRow == screenRows - 1) {
                            if (newCursorPlaced) newCursorRow--
                            scrollDownOneLine(0, screenRows, currentStyle)
                        } else {
                            currentOutputExternalRow++
                        }
                        currentOutputExternalColumn = 0
                    }

                    val offsetDueToCombiningChar = if (displayWidth <= 0 && currentOutputExternalColumn > 0) 1 else 0
                    val outputColumn = currentOutputExternalColumn - offsetDueToCombiningChar
                    setChar(outputColumn, currentOutputExternalRow, codePoint, styleAtCol)

                    if (displayWidth > 0) {
                        if (oldCursorRow == externalOldRow && oldCursorColumn == currentOldCol) {
                            newCursorColumn = currentOutputExternalColumn
                            newCursorRow = currentOutputExternalRow
                            newCursorPlaced = true
                        }
                        currentOldCol += displayWidth
                        currentOutputExternalColumn += displayWidth
                        if (justToCursor && newCursorPlaced) break
                    }
                    i++
                }
                // Old row has been copied. Check if we need to insert newline if old line was not wrapping:
                if (externalOldRow != (oldScreenRows - 1) && !oldLine.lineWrap) {
                    if (currentOutputExternalRow == screenRows - 1) {
                        if (newCursorPlaced) newCursorRow--
                        scrollDownOneLine(0, screenRows, currentStyle)
                    } else {
                        currentOutputExternalRow++
                    }
                    currentOutputExternalColumn = 0
                }
            }

            cursor[0] = newCursorColumn
            cursor[1] = newCursorRow
        }

        // Handle cursor scrolling off screen:
        if (cursor[0] < 0 || cursor[1] < 0) {
            cursor[0] = 0
            cursor[1] = 0
        }
    }

    /**
     * Block copy lines and associated metadata from one location to another in the circular buffer, taking wraparound
     * into account.
     *
     * @param srcInternal The first line to be copied.
     * @param len         The number of lines to be copied.
     */
    private fun blockCopyLinesDown(srcInternal: Int, len: Int) {
        if (len == 0) return
        val totalRows = totalRows

        val start = len - 1
        // Save away line to be overwritten:
        val lineToBeOverWritten = lines[(srcInternal + start + 1) % totalRows]
        // Do the copy from bottom to top.
        for (i in start downTo 0)
            lines[(srcInternal + i + 1) % totalRows] = lines[(srcInternal + i) % totalRows]
        // Put back overwritten line, now above the block:
        lines[(srcInternal) % totalRows] = lineToBeOverWritten
    }

    /**
     * Scroll the screen down one line. To scroll the whole screen of a 24 line screen, the arguments would be (0, 24).
     *
     * @param topMargin    First line that is scrolled.
     * @param bottomMargin One line after the last line that is scrolled.
     * @param style        the style for the newly exposed line.
     */
    fun scrollDownOneLine(topMargin: Int, bottomMargin: Int, style: Long) {
        if (topMargin > bottomMargin - 1 || topMargin < 0 || bottomMargin > screenRows)
            throw IllegalArgumentException("topMargin=$topMargin, bottomMargin=$bottomMargin, screenRows=$screenRows")

        // Copy the fixed topMargin lines one line down so that they remain on screen in same position:
        blockCopyLinesDown(screenFirstRow, topMargin)
        // Copy the fixed screenRows-bottomMargin lines one line down so that they remain on screen in same
        // position:
        blockCopyLinesDown(externalToInternalRow(bottomMargin), screenRows - bottomMargin)

        // Update the screen location in the ring buffer:
        screenFirstRow = (screenFirstRow + 1) % totalRows
        // Note that the history has grown if not already full:
        if (activeTranscriptRows < totalRows - screenRows) activeTranscriptRows++

        // Blank the newly revealed line above the bottom margin:
        val blankRow = externalToInternalRow(bottomMargin - 1)
        if (lines[blankRow] == null) {
            lines[blankRow] = TerminalRow(columns, style)
        } else {
            lines[blankRow]?.clear(style)
        }
    }

    /**
     * Block copy characters from one position in the screen to another. The two positions can overlap. All characters
     * of the source and destination must be within the bounds of the screen, or else an InvalidParameterException will
     * be thrown.
     *
     * @param sx source X coordinate
     * @param sy source Y coordinate
     * @param w  width
     * @param h  height
     * @param dx destination X coordinate
     * @param dy destination Y coordinate
     */
    fun blockCopy(sx: Int, sy: Int, w: Int, h: Int, dx: Int, dy: Int) {
        if (w == 0) return
        if (sx < 0 || sx + w > columns || sy < 0 || sy + h > screenRows || dx < 0 || dx + w > columns || dy < 0 || dy + h > screenRows)
            throw IllegalArgumentException()
        val copyingUp = sy > dy
        for (y in 0 until h) {
            val y2 = if (copyingUp) y else (h - (y + 1))
            val sourceRow = allocateFullLineIfNecessary(externalToInternalRow(sy + y2))
            allocateFullLineIfNecessary(externalToInternalRow(dy + y2)).copyInterval(sourceRow, sx, sx + w, dx)
        }
    }

    /**
     * Block set characters. All characters must be within the bounds of the screen, or else and
     * InvalidParemeterException will be thrown. Typically this is called with a "val" argument of 32 to clear a block
     * of characters.
     */
    fun blockSet(sx: Int, sy: Int, w: Int, h: Int, value: Int, style: Long) {
        if (sx < 0 || sx + w > columns || sy < 0 || sy + h > screenRows) {
            throw IllegalArgumentException(
                "Illegal arguments! blockSet($sx, $sy, $w, $h, $value, $columns, $screenRows)"
            )
        }
        for (y in 0 until h)
            for (x in 0 until w)
                setChar(sx + x, sy + y, value, style)
    }

    fun allocateFullLineIfNecessary(row: Int): TerminalRow {
        return if (lines[row] == null) {
            TerminalRow(columns, 0).also { lines[row] = it }
        } else {
            lines[row] ?: TerminalRow(columns, 0)
        }
    }

    fun setChar(column: Int, row: Int, codePoint: Int, style: Long) {
        if (row < 0 || row >= screenRows || column < 0 || column >= columns)
            throw IllegalArgumentException("TerminalBuffer.setChar(): row=$row, column=$column, screenRows=$screenRows, columns=$columns")
        val internalRow = externalToInternalRow(row)
        allocateFullLineIfNecessary(internalRow).setChar(column, codePoint, style)
    }

    fun getStyleAt(externalRow: Int, column: Int): Long {
        return allocateFullLineIfNecessary(externalToInternalRow(externalRow)).getStyle(column)
    }

    /** Support for http://vt100.net/docs/vt510-rm/DECCARA and http://vt100.net/docs/vt510-rm/DECCARA */
    fun setOrClearEffect(
        bits: Int,
        setOrClear: Boolean,
        reverse: Boolean,
        rectangular: Boolean,
        leftMargin: Int,
        rightMargin: Int,
        top: Int,
        left: Int,
        bottom: Int,
        right: Int
    ) {
        for (y in top until bottom) {
            val line = lines[externalToInternalRow(y)] ?: return
            val startOfLine = if (rectangular || y == top) left else leftMargin
            val endOfLine = if (rectangular || y + 1 == bottom) right else rightMargin
            for (x in startOfLine until endOfLine) {
                val currentStyle = line.getStyle(x)
                val foreColor = TextStyle.decodeForeColor(currentStyle)
                val backColor = TextStyle.decodeBackColor(currentStyle)
                var effect = TextStyle.decodeEffect(currentStyle)
                effect = if (reverse) {
                    // Clear out the bits to reverse and add them back in reversed:
                    (effect and bits.inv()) or (bits and effect.inv())
                } else if (setOrClear) {
                    effect or bits
                } else {
                    effect and bits.inv()
                }
                line.style[x] = TextStyle.encode(foreColor, backColor, effect)
            }
        }
    }

    fun clearTranscript() {
        if (screenFirstRow < activeTranscriptRows) {
            Arrays.fill(lines, totalRows + screenFirstRow - activeTranscriptRows, totalRows, null)
            Arrays.fill(lines, 0, screenFirstRow, null)
        } else {
            Arrays.fill(lines, screenFirstRow - activeTranscriptRows, screenFirstRow, null)
        }
        activeTranscriptRows = 0
    }
}
