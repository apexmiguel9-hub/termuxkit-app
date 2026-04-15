package com.termux.alpa_termuxkit.ui.component

import android.content.Context
import android.graphics.Typeface
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.util.Log
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.termux.alpa_termuxkit.presentation.viewmodel.TerminalViewModel
import com.termux.shared.jni.TerminalManager
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalRow
import com.termux.terminal.TextStyle

private object AnsiColorPalette {
    private val colors = IntArray(259)

    init {
        colors[0] = 0xFF000000.toInt(); colors[1] = 0xFFCD0000.toInt()
        colors[2] = 0xFF00CD00.toInt(); colors[3] = 0xFFCDCD00.toInt()
        colors[4] = 0xFF0000EE.toInt(); colors[5] = 0xFFCD00CD.toInt()
        colors[6] = 0xFF00CDCD.toInt(); colors[7] = 0xFFE5E5E5.toInt()
        colors[8] = 0xFF7F7F7F.toInt();  colors[9] = 0xFFFF0000.toInt()
        colors[10] = 0xFF00FF00.toInt(); colors[11] = 0xFFFFFF00.toInt()
        colors[12] = 0xFF5C5CFF.toInt(); colors[13] = 0xFFFF00FF.toInt()
        colors[14] = 0xFF00FFFF.toInt(); colors[15] = 0xFFFFFFFF.toInt()
        var idx = 16
        for (r in 0..5) for (g in 0..5) for (b in 0..5) {
            val red = if (r == 0) 0 else r * 40 + 55
            val green = if (g == 0) 0 else g * 40 + 55
            val blue = if (b == 0) 0 else b * 40 + 55
            colors[idx++] = (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
        }
        for (i in 0..23) {
            val gray = 8 + i * 10
            colors[idx++] = (0xFF shl 24) or (gray shl 16) or (gray shl 8) or gray
        }
        colors[256] = 0xFF00FF00.toInt()
        colors[257] = 0xFF1A1A2E.toInt()
        colors[258] = 0xFF00FF00.toInt()
    }

    fun resolve(index: Int, default: Int): Int {
        if (index < 0) return default
        if ((index and 0xFF000000.toInt()) != 0) return index
        return if (index < colors.size) colors[index] else default
    }
}

@Composable
fun TerminalScreen(
    viewModel: TerminalViewModel,
    onKeyboardRequest: () -> Unit = {}
) {
    val gridState by viewModel.gridState.collectAsState()
    val isRunning by viewModel.isRunning.collectAsState()
    val emulator = TerminalManager.getEmulator()

    LaunchedEffect(emulator, isRunning) {
        onKeyboardRequest()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.statusBars)
    ) {
        if (emulator != null && isRunning) {
            TerminalCanvas(
                emulator = emulator,
                gridState = gridState,
                modifier = Modifier.fillMaxSize()
            )
        }

        AndroidView(
            factory = { ctx ->
                TerminalInputView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    setOnClickListener {
                        requestFocus()
                        val imm = ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                        imm.showSoftInput(this, InputMethodManager.SHOW_FORCED)
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )
    }
}

@Composable
fun TerminalCanvas(
    emulator: TerminalEmulator,
    gridState: Int,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val cellWidth = with(density) { 10.dp.toPx() }
    val cellHeight = with(density) { 20.dp.toPx() }
    val textSizePx = cellHeight * 0.75f

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        val unusedGridState = gridState
        if (unusedGridState < 0) return@Canvas

        val screen: TerminalBuffer = emulator.getScreen()
        val columns = screen.columns
        val rows = screen.screenRows

        val textPaint = android.graphics.Paint().apply {
            typeface = Typeface.MONOSPACE
            textSize = textSizePx
            isAntiAlias = true
            isFakeBoldText = false
            style = android.graphics.Paint.Style.FILL
        }

        val bgPaint = android.graphics.Paint().apply {
            style = android.graphics.Paint.Style.FILL
        }

        val cursorPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.FILL_AND_STROKE
            strokeWidth = 2f
            alpha = 180
        }

        // Helper: get TerminalRow for a logical screen row
        fun getLineForRow(row: Int): TerminalRow? {
            return try {
                screen.lines[screen.externalToInternalRow(row)]
            } catch (e: Exception) {
                null
            }
        }

        // Build visual lines: merge rows where lineWrap=true
        // A "visual line" is one or more physical rows joined by lineWrap
        data class VisualSegment(
            val startRow: Int,    // first physical row in this visual line
            val rowCount: Int,    // how many physical rows it spans
            val text: String,     // combined text
            val styles: List<Long> // combined styles
        )

        val visualLines = mutableListOf<VisualSegment>()
        var segStartRow = 0
        var segText = StringBuilder()
        var segStyles = mutableListOf<Long>()
        var segRowCount = 0

        for (row in 0 until rows) {
            val line = getLineForRow(row) ?: continue
            segRowCount++

            // Read row characters
            val rowChars = CharArray(columns)
            val rowStyles = LongArray(columns)
            for (col in 0 until columns) {
                val startOfCol = line.findStartOfColumn(col)
                val c = if (startOfCol >= 0 && startOfCol < line.text.size) {
                    line.text[startOfCol]
                } else {
                    ' '
                }
                rowChars[col] = if (c == '\u0000') ' ' else c
                rowStyles[col] = screen.getStyleAt(row, col)
            }

            // Trim trailing spaces for this physical row
            var lastVisible = -1
            for (col in columns - 1 downTo 0) {
                if (rowChars[col] != ' ') {
                    lastVisible = col
                    break
                }
            }

            val effectiveLen = if (lastVisible >= 0) lastVisible + 1 else 0
            for (col in 0 until effectiveLen) {
                segText.append(rowChars[col])
                segStyles.add(rowStyles[col])
            }

            // If this row does NOT wrap, the visual line ends here
            val wraps = screen.getLineWrap(row)
            if (!wraps || row == rows - 1) {
                visualLines.add(
                    VisualSegment(
                        startRow = segStartRow,
                        rowCount = segRowCount,
                        text = segText.toString(),
                        styles = segStyles.toList()
                    )
                )
                segStartRow = row + 1
                segRowCount = 0
                segText = StringBuilder()
                segStyles = mutableListOf()
            }
        }

        // Render visual lines
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            Log.e("RENDER", "Render tick=$gridState, rows=$rows, cursor=${emulator.getCursorRow()},${emulator.getCursorCol()}, visualLines=${visualLines.size}")
            nativeCanvas.drawColor(android.graphics.Color.rgb(26, 26, 46))

            for (vlIdx in visualLines.indices) {
                val vl = visualLines[vlIdx]
                val text = vl.text
                val styles = vl.styles
                if (text.isEmpty()) continue

                // The visual line occupies vl.rowCount physical rows
                // The Y position is based on the first row of the segment
                val yBase = cellHeight * vl.startRow
                val yText = yBase + cellHeight * 0.82f

                // Draw character by character (or batch by style)
                var batchStart = 0
                var batchLen = 0

                for (i in text.indices) {
                    val style = styles[i]
                    val foreColor = TextStyle.decodeForeColor(style)
                    val backColor = TextStyle.decodeBackColor(style)
                    val effect = TextStyle.decodeEffect(style)

                    batchLen++

                    val isEnd = (i == text.lastIndex)
                    val nextFore = if (i + 1 < text.length) TextStyle.decodeForeColor(styles[i + 1]) else -1
                    val nextEffect = if (i + 1 < text.length) TextStyle.decodeEffect(styles[i + 1]) else -1
                    val nextBack = if (i + 1 < text.length) TextStyle.decodeBackColor(styles[i + 1]) else -1
                    val styleChanged = (nextFore != foreColor || nextEffect != effect || nextBack != backColor)

                    if (styleChanged || isEnd) {
                        val textToDraw = text.substring(batchStart, batchStart + batchLen)
                        val x = batchStart * cellWidth

                        // Background
                        val bgResolved = if (backColor >= 0 && backColor <= 259) {
                            AnsiColorPalette.resolve(backColor, android.graphics.Color.rgb(26, 26, 46))
                        } else {
                            android.graphics.Color.rgb(26, 26, 46)
                        }

                        if (bgResolved != android.graphics.Color.rgb(26, 26, 46)) {
                            bgPaint.color = bgResolved
                            nativeCanvas.drawRect(
                                x,
                                yBase,
                                x + batchLen * cellWidth,
                                yBase + cellHeight * vl.rowCount,
                                bgPaint
                            )
                        }

                        // Text
                        val fgResolved = if (foreColor >= 0 && foreColor <= 259) {
                            AnsiColorPalette.resolve(foreColor, android.graphics.Color.rgb(0, 255, 0))
                        } else {
                            android.graphics.Color.rgb(0, 255, 0)
                        }

                        textPaint.color = fgResolved
                        textPaint.isFakeBoldText = (effect and TextStyle.CHARACTER_ATTRIBUTE_BOLD) != 0
                        textPaint.isUnderlineText = (effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE) != 0

                        nativeCanvas.drawText(textToDraw, x, yText, textPaint)

                        batchStart = i + 1
                        batchLen = 0
                    }
                }
            }

            // Cursor — draw at correct physical row
            val cursorCol = emulator.getCursorCol()
            val cursorRow = emulator.getCursorRow()
            if (cursorRow >= 0 && cursorRow < rows && cursorCol >= 0 && cursorCol < columns) {
                nativeCanvas.drawRect(
                    cursorCol * cellWidth,
                    cursorRow * cellHeight,
                    (cursorCol + 1) * cellWidth,
                    (cursorRow + 1) * cellHeight,
                    cursorPaint
                )
            }
        }
    }
}

private const val TAG = "TerminalScreen"
