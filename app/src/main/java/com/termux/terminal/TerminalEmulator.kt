package com.termux.terminal

import android.util.Base64
import java.nio.charset.StandardCharsets
import java.util.Arrays
import java.util.Locale
import java.util.Stack

/**
 * Renders text into a screen. Contains all the terminal-specific knowledge and state. Emulates a subset of the X Window
 * System xterm terminal, which in turn is an emulator for a subset of the Digital Equipment Corporation vt100 terminal.
 *
 * References:
 * - http://invisible-island.net/xterm/ctlseqs/ctlseqs.html
 * - http://en.wikipedia.org/wiki/ANSI_escape_code
 * - http://man.he.net/man4/console_codes
 * - http://bazaar.launchpad.net/~leonerd/libvterm/trunk/view/head:/src/state.c
 * - http://www.columbia.edu/~kermit/k95manual/iso2022.html
 * - http://www.vt100.net/docs/vt510-rm/chapter4
 * - http://en.wikipedia.org/wiki/ISO/IEC_2022 - for 7-bit and 8-bit GL GR explanation
 * - http://bjh21.me.uk/all-escapes/all-escapes.txt - extensive!
 * - http://woldlab.caltech.edu/~diane/kde4.10/workingdir/kubuntu/konsole/doc/developer/old-documents/VT100/techref.html - document for konsole - accessible!
 */
@Suppress("LargeClass")
class TerminalEmulator constructor(
    private val session: TerminalOutput,
    columnsParam: Int,
    rowsParam: Int,
    cellWidthPixelsParam: Int,
    cellHeightPixelsParam: Int,
    transcriptRowsParam: Int?,
    client: TerminalSessionClient
) {

    companion object {
        /** Log unknown or unimplemented escape sequences received from the shell process. */
        private const val LOG_ESCAPE_SEQUENCES = false

        const val MOUSE_LEFT_BUTTON = 0

        /** Mouse moving while having left mouse button pressed. */
        const val MOUSE_LEFT_BUTTON_MOVED = 32
        const val MOUSE_WHEELUP_BUTTON = 64
        const val MOUSE_WHEELDOWN_BUTTON = 65

        /** Used for invalid data - http://en.wikipedia.org/wiki/Replacement_character#Replacement_character */
        const val UNICODE_REPLACEMENT_CHAR = 0xFFFD

        /** Escape processing: Not currently in an escape sequence. */
        private const val ESC_NONE = 0
        /** Escape processing: Have seen an ESC character - proceed to [doEsc] */
        private const val ESC = 1
        /** Escape processing: Have seen ESC POUND */
        private const val ESC_POUND = 2
        /** Escape processing: Have seen ESC and a character-set-select ( char */
        private const val ESC_SELECT_LEFT_PAREN = 3
        /** Escape processing: Have seen ESC and a character-set-select ) char */
        private const val ESC_SELECT_RIGHT_PAREN = 4
        /** Escape processing: "ESC [" or CSI (Control Sequence Introducer). */
        private const val ESC_CSI = 6
        /** Escape processing: ESC [ ? */
        private const val ESC_CSI_QUESTIONMARK = 7
        /** Escape processing: ESC [ $ */
        private const val ESC_CSI_DOLLAR = 8
        /** Escape processing: ESC % */
        private const val ESC_PERCENT = 9
        /** Escape processing: ESC ] (AKA OSC - Operating System Controls) */
        private const val ESC_OSC = 10
        /** Escape processing: ESC ] (AKA OSC - Operating System Controls) ESC */
        private const val ESC_OSC_ESC = 11
        /** Escape processing: ESC [ > */
        private const val ESC_CSI_BIGGERTHAN = 12
        /** Escape procession: "ESC P" or Device Control String (DCS) */
        private const val ESC_P = 13
        /** Escape processing: CSI > */
        private const val ESC_CSI_QUESTIONMARK_ARG_DOLLAR = 14
        /** Escape processing: CSI $ARGS ' ' */
        private const val ESC_CSI_ARGS_SPACE = 15
        /** Escape processing: CSI $ARGS '*' */
        private const val ESC_CSI_ARGS_ASTERIX = 16
        /** Escape processing: CSI " */
        private const val ESC_CSI_DOUBLE_QUOTE = 17
        /** Escape processing: CSI ' */
        private const val ESC_CSI_SINGLE_QUOTE = 18
        /** Escape processing: CSI ! */
        private const val ESC_CSI_EXCLAMATION = 19
        /** Escape processing: "ESC _" or Application Program Command (APC). */
        private const val ESC_APC = 20
        /** Escape processing: "ESC _" or Application Program Command (APC), followed by Escape. */
        private const val ESC_APC_ESCAPE = 21
        /** Escape processing: ESC [ <parameter bytes> */
        private const val ESC_CSI_UNSUPPORTED_PARAMETER_BYTE = 22
        /** Escape processing: ESC [ <parameter bytes> <intermediate bytes> */
        private const val ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE = 23

        /** The number of parameter arguments including colon separated sub-parameters. */
        private const val MAX_ESCAPE_PARAMETERS = 32

        /** Needs to be large enough to contain reasonable OSC 52 pastes. */
        private const val MAX_OSC_STRING_LENGTH = 8192

        /** DECSET 1 - application cursor keys. */
        private const val DECSET_BIT_APPLICATION_CURSOR_KEYS = 1
        private const val DECSET_BIT_REVERSE_VIDEO = 1 shl 1
        /**
         * http://www.vt100.net/docs/vt510-rm/DECOM: "When DECOM is set, the home cursor position is at the upper-left
         * corner of the screen, within the margins. The starting point for line numbers depends on the current top margin
         * setting. The cursor cannot move outside of the margins. When DECOM is reset, the home cursor position is at the
         * upper-left corner of the screen. The starting point for line numbers is independent of the margins. The cursor
         * can move outside of the margins."
         */
        private const val DECSET_BIT_ORIGIN_MODE = 1 shl 2
        /**
         * http://www.vt100.net/docs/vt510-rm/DECAWM: "If the DECAWM function is set, then graphic characters received when
         * the cursor is at the right border of the page appear at the beginning of the next line. Any text on the page
         * scrolls up if the cursor is at the end of the scrolling region. If the DECAWM function is reset, then graphic
         * characters received when the cursor is at the right border of the page replace characters already on the page."
         */
        private const val DECSET_BIT_AUTOWRAP = 1 shl 3
        /** DECSET 25 - if the cursor should be enabled, [isCursorEnabled]. */
        private const val DECSET_BIT_CURSOR_ENABLED = 1 shl 4
        private const val DECSET_BIT_APPLICATION_KEYPAD = 1 shl 5
        /** DECSET 1000 - if to report mouse press&release events. */
        private const val DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE = 1 shl 6
        /** DECSET 1002 - like 1000, but report moving mouse while pressed. */
        private const val DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT = 1 shl 7
        /** DECSET 1004 - NOT implemented. */
        private const val DECSET_BIT_SEND_FOCUS_EVENTS = 1 shl 8
        /** DECSET 1006 - SGR-like mouse protocol (the modern sane choice). */
        private const val DECSET_BIT_MOUSE_PROTOCOL_SGR = 1 shl 9
        /** DECSET 2004 - see [paste] */
        private const val DECSET_BIT_BRACKETED_PASTE_MODE = 1 shl 10
        /** Toggled with DECLRMM - http://www.vt100.net/docs/vt510-rm/DECLRMM */
        private const val DECSET_BIT_LEFTRIGHT_MARGIN_MODE = 1 shl 11
        /** Not really DECSET bit... - http://www.vt100.net/docs/vt510-rm/DECSACE */
        private const val DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE = 1 shl 12

        const val TERMINAL_TRANSCRIPT_ROWS_MIN = 100
        const val TERMINAL_TRANSCRIPT_ROWS_MAX = 50000
        const val DEFAULT_TERMINAL_TRANSCRIPT_ROWS = 2000

        /* The supported terminal cursor styles. */

        const val TERMINAL_CURSOR_STYLE_BLOCK = 0
        const val TERMINAL_CURSOR_STYLE_UNDERLINE = 1
        const val TERMINAL_CURSOR_STYLE_BAR = 2
        const val DEFAULT_TERMINAL_CURSOR_STYLE = TERMINAL_CURSOR_STYLE_BLOCK
        val TERMINAL_CURSOR_STYLES_LIST = arrayOf(TERMINAL_CURSOR_STYLE_BLOCK, TERMINAL_CURSOR_STYLE_UNDERLINE, TERMINAL_CURSOR_STYLE_BAR)

        private const val LOG_TAG = "TerminalEmulator"

        fun mapDecSetBitToInternalBit(decsetBit: Int): Int {
            return when (decsetBit) {
                1 -> DECSET_BIT_APPLICATION_CURSOR_KEYS
                5 -> DECSET_BIT_REVERSE_VIDEO
                6 -> DECSET_BIT_ORIGIN_MODE
                7 -> DECSET_BIT_AUTOWRAP
                25 -> DECSET_BIT_CURSOR_ENABLED
                66 -> DECSET_BIT_APPLICATION_KEYPAD
                69 -> DECSET_BIT_LEFTRIGHT_MARGIN_MODE
                1000 -> DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE
                1002 -> DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT
                1004 -> DECSET_BIT_SEND_FOCUS_EVENTS
                1006 -> DECSET_BIT_MOUSE_PROTOCOL_SGR
                2004 -> DECSET_BIT_BRACKETED_PASTE_MODE
                else -> throw IllegalArgumentException("Unsupported decset: $decsetBit")
            }
        }
    }

    private var title: String? = null
    private val titleStack = ArrayDeque<String>()

    /** The cursor position. Between (0,0) and (rows-1, columns-1). */
    private var cursorRow: Int = 0
    private var cursorCol: Int = 0

    /** The number of character rows and columns in the terminal screen. */
    var rows: Int = 0
        private set
    var columns: Int = 0
        private set

    /** Size of a terminal cell in pixels. */
    var cellWidthPixels: Int = 0
        private set
    var cellHeightPixels: Int = 0
        private set

    /** The terminal cursor styles. */
    var cursorStyle: Int = DEFAULT_TERMINAL_CURSOR_STYLE
        private set

    /** The normal screen buffer. Stores the characters that appear on the screen of the emulated terminal. */
    private val mainBuffer: TerminalBuffer

    /**
     * The alternate screen buffer, exactly as large as the display and contains no additional saved lines (so that when
     * the alternate screen buffer is active, you cannot scroll back to view saved lines).
     *
     * See http://www.xfree86.org/current/ctlseqs.html#The%20Alternate%20Screen%20Buffer
     */
    val altBuffer: TerminalBuffer

    /** The current screen buffer, pointing either at [mainBuffer] or [altBuffer]. */
    private var screen: TerminalBuffer

    var client: TerminalSessionClient? = null
        private set

    /** Keeps track of the current argument of the current escape sequence. Ranges from 0 to MAX_ESCAPE_PARAMETERS-1. */
    private var argIndex: Int = 0

    /** Holds the arguments of the current escape sequence. */
    private val args = IntArray(MAX_ESCAPE_PARAMETERS)

    /** Holds the bit flags which arguments are sub parameters (after a colon) - bit N is set if `args[N]` is a sub parameter. */
    private var argsSubParamsBitSet = 0

    /** Holds OSC and device control arguments, which can be strings. */
    private val oscOrDeviceControlArgs = StringBuilder()

    /**
     * True if the current escape sequence should continue, false if the current escape sequence should be terminated.
     * Used when parsing a single character.
     */
    private var continueSequence: Boolean = false

    /** The current state of the escape sequence state machine. One of the ESC_* constants. */
    private var escapeState: Int = ESC_NONE

    private val savedStateMain = SavedScreenState()
    private val savedStateAlt = SavedScreenState()

    /** http://www.vt100.net/docs/vt102-ug/table5-15.html */
    private var useLineDrawingG0: Boolean = false
    private var useLineDrawingG1: Boolean = false
    private var useLineDrawingUsesG0: Boolean = true

    /**
     * @see TerminalEmulator.mapDecSetBitToInternalBit
     */
    private var currentDecSetFlags: Int = 0
    private var savedDecSetFlags: Int = 0

    /**
     * If insert mode (as opposed to replace mode) is active. In insert mode new characters are inserted, pushing
     * existing text to the right. Characters moved past the right margin are lost.
     */
    private var insertMode: Boolean = false

    /** An array of tab stops. tabStop[i] is true if there is a tab stop set for column i. */
    private var tabStop: BooleanArray

    /**
     * Top margin of screen for scrolling ranges from 0 to rows-2. Bottom margin ranges from topMargin + 2 to rows
     * (Defines the first row after the scrolling region). Left/right margin in [0, columns].
     */
    private var topMargin: Int = 0
    private var bottomMargin: Int = 0
    private var leftMargin: Int = 0
    private var rightMargin: Int = 0

    /**
     * If the next character to be emitted will be automatically wrapped to the next line. Used to disambiguate the case
     * where the cursor is positioned on the last column (columns-1). When standing there, a written character will be
     * output in the last column, the cursor not moving but this flag will be set. When outputting another character
     * this will move to the next line.
     */
    private var aboutToAutoWrap: Boolean = false

    /**
     * If the cursor blinking is enabled. It requires cursor itself to be enabled, which is controlled
     * by whether [DECSET_BIT_CURSOR_ENABLED] bit is set or not.
     */
    private var cursorBlinkingEnabled: Boolean = false

    /**
     * If currently cursor should be in a visible state or not if [cursorBlinkingEnabled]
     * is `true`.
     */
    private var cursorBlinkState: Boolean = false

    /**
     * Current foreground, background and underline colors. Can either be a color index in [0,259] or a truecolor (24-bit) value.
     * For a 24-bit value the top byte (0xff000000) is set.
     *
     * Note that the underline color is currently parsed but not yet used during rendering.
     *
     * @see TextStyle
     */
    internal var foreColor: Int = 0
    internal var backColor: Int = 0
    internal var underlineColor: Int = 0

    /** Current [TextStyle] effect. */
    internal var effect: Int = 0

    /**
     * The number of scrolled lines since last calling [clearScrollCounter]. Used for moving selection up along
     * with the scrolling text.
     */
    private var scrollCounter: Int = 0

    /** If automatic scrolling of terminal is disabled */
    private var autoScrollDisabled: Boolean = false

    private var utf8ToFollow: Byte = 0
    private var utf8Index: Byte = 0
    private val utf8InputBuffer = ByteArray(4)
    private var lastEmittedCodePoint: Int = -1

    val colors: TerminalColors = TerminalColors()

    init {
        screen = TerminalBuffer(columnsParam, getTerminalTranscriptRows(transcriptRowsParam), rowsParam).also { mainBuffer = it }
        altBuffer = TerminalBuffer(columnsParam, rowsParam, rowsParam)
        this.client = client
        this.rows = rowsParam
        this.columns = columnsParam
        cellWidthPixels = cellWidthPixelsParam
        cellHeightPixels = cellHeightPixelsParam
        tabStop = BooleanArray(columnsParam)
        reset()
    }

    private fun isDecsetInternalBitSet(bit: Int): Boolean {
        return (currentDecSetFlags and bit) != 0
    }

    private fun setDecsetinternalBit(internalBit: Int, set: Boolean) {
        if (set) {
            // The mouse modes are mutually exclusive.
            if (internalBit == DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) {
                setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT, false)
            } else if (internalBit == DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT) {
                setDecsetinternalBit(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE, false)
            }
        }
        currentDecSetFlags = if (set) {
            currentDecSetFlags or internalBit
        } else {
            currentDecSetFlags and internalBit.inv()
        }
    }

    fun updateTerminalSessionClient(client: TerminalSessionClient) {
        this.client = client
        setCursorStyle()
        setCursorBlinkState(true)
    }

    fun getScreen(): TerminalBuffer {
        return screen
    }

    fun isAlternateBufferActive(): Boolean {
        return screen == altBuffer
    }

    private fun getTerminalTranscriptRows(transcriptRows: Int?): Int {
        return if (transcriptRows == null || transcriptRows < TERMINAL_TRANSCRIPT_ROWS_MIN || transcriptRows > TERMINAL_TRANSCRIPT_ROWS_MAX)
            DEFAULT_TERMINAL_TRANSCRIPT_ROWS
        else
            transcriptRows
    }

    /**
     * @param mouseButton one of the MOUSE_* constants of this class.
     */
    fun sendMouseEvent(mouseButton: Int, column: Int, row: Int, pressed: Boolean) {
        var col = column.coerceIn(1, columns)
        var r = row.coerceIn(1, rows)

        if (mouseButton == MOUSE_LEFT_BUTTON_MOVED && !isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT)) {
            // Do not send tracking.
        } else if (isDecsetInternalBitSet(DECSET_BIT_MOUSE_PROTOCOL_SGR)) {
            session.write(String.format("\u001B[<%d;%d;%d" + (if (pressed) 'M' else 'm'), mouseButton, col, r))
        } else {
            val mouseBtn = if (pressed) mouseButton else 3 // 3 for release of all buttons.
            // Clip to screen, and clip to the limits of 8-bit data.
            val outOfBounds = col > 255 - 32 || r > 255 - 32
            if (!outOfBounds) {
                val data = byteArrayOf('\u001B'.code.toByte(), '['.code.toByte(), 'M'.code.toByte(), (32 + mouseBtn).toByte(), (32 + col).toByte(), (32 + r).toByte())
                session.write(data, 0, data.size)
            }
        }
    }

    fun resize(columns: Int, rows: Int, cellWidthPixels: Int, cellHeightPixels: Int) {
        this.cellWidthPixels = cellWidthPixels
        this.cellHeightPixels = cellHeightPixels

        if (this.rows == rows && this.columns == columns) {
            return
        } else if (columns < 2 || rows < 2) {
            throw IllegalArgumentException("rows=$rows, columns=$columns")
        }

        if (this.rows != rows) {
            this.rows = rows
            topMargin = 0
            bottomMargin = rows
        }
        if (this.columns != columns) {
            val oldColumns = this.columns
            this.columns = columns
            val oldTabStop = tabStop
            tabStop = BooleanArray(columns)
            setDefaultTabStops()
            val toTransfer = oldColumns.coerceAtMost(columns)
            System.arraycopy(oldTabStop, 0, tabStop, 0, toTransfer)
            leftMargin = 0
            rightMargin = columns
        }

        resizeScreen()
    }

    private fun resizeScreen() {
        val cursor = intArrayOf(cursorCol, cursorRow)
        val newTotalRows = if (screen == altBuffer) rows else mainBuffer.totalRows
        screen.resize(columns, rows, newTotalRows, cursor, getStyle(), isAlternateBufferActive())
        cursorCol = cursor[0]
        cursorRow = cursor[1]
    }

    fun getCursorRow(): Int {
        return cursorRow
    }

    fun getCursorCol(): Int {
        return cursorCol
    }

    /** Set the terminal cursor style. */
    fun setCursorStyle() {
        val clientCursorStyle: Int? = client?.getTerminalCursorStyle()

        cursorStyle = if (clientCursorStyle == null || clientCursorStyle !in TERMINAL_CURSOR_STYLES_LIST)
            DEFAULT_TERMINAL_CURSOR_STYLE
        else
            clientCursorStyle
    }

    fun isReverseVideo(): Boolean {
        return isDecsetInternalBitSet(DECSET_BIT_REVERSE_VIDEO)
    }

    fun isCursorEnabled(): Boolean {
        return isDecsetInternalBitSet(DECSET_BIT_CURSOR_ENABLED)
    }

    fun shouldCursorBeVisible(): Boolean {
        if (!isCursorEnabled())
            return false
        else
            return if (cursorBlinkingEnabled) cursorBlinkState else true
    }

    fun setCursorBlinkingEnabled(cursorBlinkingEnabled: Boolean) {
        this.cursorBlinkingEnabled = cursorBlinkingEnabled
    }

    fun setCursorBlinkState(cursorBlinkState: Boolean) {
        this.cursorBlinkState = cursorBlinkState
    }

    fun isKeypadApplicationMode(): Boolean {
        return isDecsetInternalBitSet(DECSET_BIT_APPLICATION_KEYPAD)
    }

    fun isCursorKeysApplicationMode(): Boolean {
        return isDecsetInternalBitSet(DECSET_BIT_APPLICATION_CURSOR_KEYS)
    }

    /** If mouse events are being sent as escape codes to the terminal. */
    fun isMouseTrackingActive(): Boolean {
        return isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_PRESS_RELEASE) || isDecsetInternalBitSet(DECSET_BIT_MOUSE_TRACKING_BUTTON_EVENT)
    }

    private fun setDefaultTabStops() {
        for (i in 0 until columns)
            tabStop[i] = (i and 7) == 0 && i != 0
    }

    /**
     * Accept bytes (typically from the pseudo-teletype) and process them.
     *
     * @param buffer a byte array containing the bytes to be processed
     * @param length the number of bytes in the array to process
     */
    fun append(buffer: ByteArray, length: Int) {
        for (i in 0 until length)
            processByte(buffer[i])
    }

    private fun processByte(byteToProcess: Byte) {
        if (utf8ToFollow > 0) {
            if ((byteToProcess.toInt() and 0b11000000) == 0b10000000) {
                // 10xxxxxx, a continuation byte.
                utf8InputBuffer[utf8Index.toInt()] = byteToProcess
                utf8ToFollow--
                if (utf8ToFollow == 0.toByte()) {
                    val firstByteMask = when (utf8Index.toInt()) {
                        2 -> 0b00011111
                        3 -> 0b00001111
                        else -> 0b00000111
                    }.toByte()
                    var codePoint = (utf8InputBuffer[0].toInt() and firstByteMask.toInt())
                    for (i in 1 until utf8Index.toInt())
                        codePoint = ((codePoint shl 6) or (utf8InputBuffer[i].toInt() and 0b00111111))
                    if (((codePoint <= 0b1111111) && utf8Index > 1) || (codePoint < 0b11111111111 && utf8Index > 2)
                        || (codePoint < 0b1111111111111111 && utf8Index > 3)
                    ) {
                        // Overlong encoding.
                        codePoint = UNICODE_REPLACEMENT_CHAR
                    }

                    utf8Index = 0.toByte()
                    utf8ToFollow = 0.toByte()

                    if (codePoint >= 0x80 && codePoint <= 0x9F) {
                        // Sequence decoded to a C1 control character which we ignore. They are
                        // not used nowadays and increases the risk of messing up the terminal state
                        // on binary input. XTerm does not allow them in utf-8:
                        // "It is not possible to use a C1 control obtained from decoding the
                        // UTF-8 text" - http://invisible-island.net/xterm/ctlseqs/ctlseqs.html
                    } else {
                        when (Character.getType(codePoint).toByte()) {
                            Character.UNASSIGNED, Character.SURROGATE -> codePoint = UNICODE_REPLACEMENT_CHAR
                        }
                        processCodePoint(codePoint)
                    }
                }
            } else {
                // Not a UTF-8 continuation byte so replace the entire sequence up to now with the replacement char:
                utf8Index = 0.toByte()
                utf8ToFollow = 0.toByte()
                emitCodePoint(UNICODE_REPLACEMENT_CHAR)
                // The Unicode Standard Version 6.2 – Core Specification
                // (http://www.unicode.org/versions/Unicode6.2.0/ch03.pdf):
                // "If the converter encounters an ill-formed UTF-8 code unit sequence which starts with a valid first
                // byte, but which does not continue with valid successor bytes (see Table 3-7), it must not consume the
                // successor bytes as part of the ill-formed subsequence
                // whenever those successor bytes themselves constitute part of a well-formed UTF-8 code unit
                // subsequence."
                processByte(byteToProcess)
            }
        } else {
            if ((byteToProcess.toInt() and 0b10000000) == 0) { // The leading bit is not set so it is a 7-bit ASCII character.
                processCodePoint(byteToProcess.toInt())
                return
            } else if ((byteToProcess.toInt() and 0b11100000) == 0b11000000) { // 110xxxxx, a two-byte sequence.
                utf8ToFollow = 1
            } else if ((byteToProcess.toInt() and 0b11110000) == 0b11100000) { // 1110xxxx, a three-byte sequence.
                utf8ToFollow = 2
            } else if ((byteToProcess.toInt() and 0b11111000) == 0b11110000) { // 11110xxx, a four-byte sequence.
                utf8ToFollow = 3
            } else {
                // Not a valid UTF-8 sequence start, signal invalid data:
                processCodePoint(UNICODE_REPLACEMENT_CHAR)
                return
            }
            utf8InputBuffer[utf8Index.toInt()] = byteToProcess
            utf8Index++
        }
    }

    fun processCodePoint(b: Int) {
        // The Application Program-Control (APC) string might be arbitrary non-printable characters, so handle that early.
        if (escapeState == ESC_APC) {
            doApc(b)
            return
        } else if (escapeState == ESC_APC_ESCAPE) {
            doApcEscape(b)
            return
        }

        when (b) {
            0 -> // Null character (NUL, ^@). Do nothing.
                {}
            7 -> // Bell (BEL, ^G, \a). If in an OSC sequence, BEL may terminate a string; otherwise signal bell.
                if (escapeState == ESC_OSC)
                    doOsc(b)
                else
                    session.onBell()
            8 -> // Backspace (BS, ^H).
                if (leftMargin == cursorCol) {
                    // Jump to previous line if it was auto-wrapped.
                    val previousRow = cursorRow - 1
                    if (previousRow >= 0 && screen.getLineWrap(previousRow)) {
                        screen.clearLineWrap(previousRow)
                        setCursorRowCol(previousRow, rightMargin - 1)
                    }
                } else {
                    setCursorCol(cursorCol - 1)
                }
            9 -> // Horizontal tab (HT, \t) - move to next tab stop, but not past edge of screen
                // XXX: Should perhaps use color if writing to new cells. Try with
                //       printf "\033[41m\tXX\033[0m\n"
                // The OSX Terminal.app colors the spaces from the tab red, but xterm does not.
                // Note that Terminal.app only colors on new cells, in e.g.
                //       printf "\033[41m\t\r\033[42m\tXX\033[0m\n"
                // the first cells are created with a red background, but when tabbing over
                // them again with a green background they are not overwritten.
                cursorCol = nextTabStop(1)
            10, 11, 12 -> // Line feed (LF, \n), Vertical tab (VT, \v), Form feed (FF, \f).
                doLinefeed()
            13 -> // Carriage return (CR, \r).
                setCursorCol(leftMargin)
            14 -> // Shift Out (Ctrl-N, SO) → Switch to Alternate Character Set. This invokes the G1 character set.
                useLineDrawingUsesG0 = false
            15 -> // Shift In (Ctrl-O, SI) → Switch to Standard Character Set. This invokes the G0 character set.
                useLineDrawingUsesG0 = true
            24, 26 -> // CAN, SUB.
                if (escapeState != ESC_NONE) {
                    // FIXME: What is this??
                    escapeState = ESC_NONE
                    emitCodePoint(127)
                }
            27 -> // ESC
                // Starts an escape sequence unless we're parsing a string
                if (escapeState == ESC_P) {
                    // XXX: Ignore escape when reading device control sequence, since it may be part of string terminator.
                    return
                } else if (escapeState != ESC_OSC) {
                    startEscapeSequence()
                } else {
                    doOsc(b)
                }
            else -> {
                continueSequence = false
                when (escapeState) {
                    ESC_NONE -> if (b >= 32) emitCodePoint(b)
                    ESC -> doEsc(b)
                    ESC_POUND -> doEscPound(b)
                    ESC_SELECT_LEFT_PAREN -> // Designate G0 Character Set (ISO 2022, VT100).
                        useLineDrawingG0 = (b == '0'.code)
                    ESC_SELECT_RIGHT_PAREN -> // Designate G1 Character Set (ISO 2022, VT100).
                        useLineDrawingG1 = (b == '0'.code)
                    ESC_CSI -> doCsi(b)
                    ESC_CSI_UNSUPPORTED_PARAMETER_BYTE, ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE -> doCsiUnsupportedParameterOrIntermediateByte(b)
                    ESC_CSI_EXCLAMATION -> if (b == 'p'.code) { // Soft terminal reset (DECSTR, http://vt100.net/docs/vt510-rm/DECSTR).
                        reset()
                    } else {
                        unknownSequence(b)
                    }
                    ESC_CSI_QUESTIONMARK -> doCsiQuestionMark(b)
                    ESC_CSI_BIGGERTHAN -> doCsiBiggerThan(b)
                    ESC_CSI_DOLLAR -> {
                        val originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)
                        val effectiveTopMargin = if (originMode) topMargin else 0
                        val effectiveBottomMargin = if (originMode) bottomMargin else rows
                        val effectiveLeftMargin = if (originMode) leftMargin else 0
                        val effectiveRightMargin = if (originMode) rightMargin else columns
                        when (b) {
                            'v'.code -> // ${CSI}${SRC_TOP}${SRC_LEFT}${SRC_BOTTOM}${SRC_RIGHT}${SRC_PAGE}${DST_TOP}${DST_LEFT}${DST_PAGE}$v"
                                // Copy rectangular area (DECCRA - http://vt100.net/docs/vt510-rm/DECCRA):
                                // "If Pbs is greater than Pts, or Pls is greater than Prs, the terminal ignores DECCRA.
                                // The coordinates of the rectangular area are affected by the setting of origin mode (DECOM).
                                // DECCRA is not affected by the page margins.
                                // The copied text takes on the line attributes of the destination area.
                                // If the value of Pt, Pl, Pb, or Pr exceeds the width or height of the active page, then the value
                                // is treated as the width or height of that page.
                                // If the destination area is partially off the page, then DECCRA clips the off-page data.
                                // DECCRA does not change the active cursor position."
                                run {
                                    val topSource = getArg(0, 1, true).coerceAtMost(rows) - 1 + effectiveTopMargin
                                    val leftSource = getArg(1, 1, true).coerceAtMost(columns) - 1 + effectiveLeftMargin
                                    // Inclusive, so do not subtract one:
                                    val bottomSource = getArg(2, rows, true).coerceAtLeast(topSource).coerceAtMost(rows) + effectiveTopMargin
                                    val rightSource = getArg(3, columns, true).coerceAtLeast(leftSource).coerceAtMost(columns) + effectiveLeftMargin
                                    // int sourcePage = getArg(4, 1, true);
                                    val destionationTop = getArg(5, 1, true).coerceAtMost(rows) - 1 + effectiveTopMargin
                                    val destinationLeft = getArg(6, 1, true).coerceAtMost(columns) - 1 + effectiveLeftMargin
                                    // int destinationPage = getArg(7, 1, true);
                                    val heightToCopy = (rows - destionationTop).coerceAtMost(bottomSource - topSource)
                                    val widthToCopy = (columns - destinationLeft).coerceAtMost(rightSource - leftSource)
                                    screen.blockCopy(leftSource, topSource, widthToCopy, heightToCopy, destinationLeft, destionationTop)
                                }
                            '{'.code -> // ${CSI}${TOP}${LEFT}${BOTTOM}${RIGHT}${"
                                // Selective erase rectangular area (DECSERA - http://www.vt100.net/docs/vt510-rm/DECSERA).
                                doEraseOrFillRectangularArea(effectiveTopMargin, effectiveBottomMargin, effectiveLeftMargin, effectiveRightMargin, true, true)
                            'x'.code -> // ${CSI}${CHAR};${TOP}${LEFT}${BOTTOM}${RIGHT}$x"
                                // Fill rectangular area (DECFRA - http://www.vt100.net/docs/vt510-rm/DECFRA).
                                doEraseOrFillRectangularArea(effectiveTopMargin, effectiveBottomMargin, effectiveLeftMargin, effectiveRightMargin, false, false)
                            'z'.code -> // ${CSI}$${TOP}${LEFT}${BOTTOM}${RIGHT}$z"
                                // Erase rectangular area (DECERA - http://www.vt100.net/docs/vt510-rm/DECERA).
                                doEraseOrFillRectangularArea(effectiveTopMargin, effectiveBottomMargin, effectiveLeftMargin, effectiveRightMargin, true, false)
                            'r'.code -> // "${CSI}${TOP}${LEFT}${BOTTOM}${RIGHT}${ATTRIBUTES}$r"
                                // Change attributes in rectangular area (DECCARA - http://vt100.net/docs/vt510-rm/DECCARA).
                                doChangeAttributesInRectangularArea(effectiveTopMargin, effectiveBottomMargin, effectiveLeftMargin, effectiveRightMargin, false)
                            't'.code -> // "${CSI}${TOP}${LEFT}${BOTTOM}${RIGHT}${ATTRIBUTES}$t"
                                // Reverse attributes in rectangular area (DECRARA - http://www.vt100.net/docs/vt510-rm/DECRARA).
                                doChangeAttributesInRectangularArea(effectiveTopMargin, effectiveBottomMargin, effectiveLeftMargin, effectiveRightMargin, true)
                            else -> unknownSequence(b)
                        }
                    }
                    ESC_CSI_DOUBLE_QUOTE -> if (b == 'q'.code) {
                        // http://www.vt100.net/docs/vt510-rm/DECSCA
                        val arg = getArg0(0)
                        if (arg == 0 || arg == 2) {
                            // DECSED and DECSEL can erase characters.
                            effect = effect and TextStyle.CHARACTER_ATTRIBUTE_PROTECTED.inv()
                        } else if (arg == 1) {
                            // DECSED and DECSEL cannot erase characters.
                            effect = effect or TextStyle.CHARACTER_ATTRIBUTE_PROTECTED
                        } else {
                            unknownSequence(b)
                        }
                    } else {
                        unknownSequence(b)
                    }
                    ESC_CSI_SINGLE_QUOTE -> when (b) {
                        '}'.code -> { // Insert Ps Column(s) (default = 1) (DECIC), VT420 and up.
                            val columnsAfterCursor = rightMargin - cursorCol
                            val columnsToInsert = getArg0(1).coerceAtMost(columnsAfterCursor)
                            val columnsToMove = columnsAfterCursor - columnsToInsert
                            screen.blockCopy(cursorCol, 0, columnsToMove, rows, cursorCol + columnsToInsert, 0)
                            blockClear(cursorCol, 0, columnsToInsert, rows)
                        }
                        '~'.code -> { // Delete Ps Column(s) (default = 1) (DECDC), VT420 and up.
                            val columnsAfterCursor = rightMargin - cursorCol
                            val columnsToDelete = getArg0(1).coerceAtMost(columnsAfterCursor)
                            val columnsToMove = columnsAfterCursor - columnsToDelete
                            screen.blockCopy(cursorCol + columnsToDelete, 0, columnsToMove, rows, cursorCol, 0)
                        }
                        else -> unknownSequence(b)
                    }
                    ESC_PERCENT -> {}
                    ESC_OSC -> doOsc(b)
                    ESC_OSC_ESC -> doOscEsc(b)
                    ESC_P -> doDeviceControl(b)
                    ESC_CSI_QUESTIONMARK_ARG_DOLLAR -> if (b == 'p'.code) {
                        // Request DEC private mode (DECRQM).
                        val mode = getArg0(0)
                        val value = when {
                            mode == 47 || mode == 1047 || mode == 1049 -> {
                                // This state is carried by screen pointer.
                                if (screen == altBuffer) 1 else 2
                            }
                            else -> {
                                val internalBit = mapDecSetBitToInternalBit(mode)
                                if (internalBit != -1) {
                                    if (isDecsetInternalBitSet(internalBit)) 1 else 2 // 1=set, 2=reset.
                                } else {
                                    Logger.logError(LOG_TAG, "Got DECRQM for unrecognized private DEC mode=$mode")
                                    0 // 0=not recognized, 3=permanently set, 4=permanently reset
                                }
                            }
                        }
                        session.write(String.format(Locale.US, "\u001b[?%d;%d${'$'}y", mode, value))
                    } else {
                        unknownSequence(b)
                    }
                    ESC_CSI_ARGS_SPACE -> {
                        val arg = getArg0(0)
                        when (b) {
                            'q'.code -> // "${CSI}${STYLE} q" - set cursor style (http://www.vt100.net/docs/vt510-rm/DECSCUSR).
                                when (arg) {
                                    0, 1, 2 -> cursorStyle = TERMINAL_CURSOR_STYLE_BLOCK
                                    3, 4 -> cursorStyle = TERMINAL_CURSOR_STYLE_UNDERLINE
                                    5, 6 -> cursorStyle = TERMINAL_CURSOR_STYLE_BAR
                                }
                            't'.code, 'u'.code -> {
                                // Set margin-bell volume - ignore.
                            }
                            else -> unknownSequence(b)
                        }
                    }
                    ESC_CSI_ARGS_ASTERIX -> {
                        when (b) {
                            '}'.code -> { // "${CSI}${CHAR} }" - Insert Ps Column(s) (default = 1) (DECIC), VT420 and up.
                                val columnsAfterCursor = rightMargin - cursorCol
                                val columnsToInsert = getArg0(1).coerceAtMost(columnsAfterCursor)
                                val columnsToMove = columnsAfterCursor - columnsToInsert
                                screen.blockCopy(cursorCol, 0, columnsToMove, rows, cursorCol + columnsToInsert, 0)
                                blockClear(cursorCol, 0, columnsToInsert, rows)
                            }
                            '~'.code -> { // "${CSI}${CHAR} ~" - Delete Ps Column(s) (default = 1) (DECDC), VT420 and up.
                                val columnsAfterCursor = rightMargin - cursorCol
                                val columnsToDelete = getArg0(1).coerceAtMost(columnsAfterCursor)
                                val columnsToMove = columnsAfterCursor - columnsToDelete
                                screen.blockCopy(cursorCol + columnsToDelete, 0, columnsToMove, rows, cursorCol, 0)
                            }
                            else -> unknownSequence(b)
                        }
                    }
                    else -> unknownSequence(b)
                }
            }
        }
    }

    private fun doEraseOrFillRectangularArea(effectiveTopMargin: Int, effectiveBottomMargin: Int, effectiveLeftMargin: Int, effectiveRightMargin: Int, erase: Boolean, selective: Boolean) {
        // Only DECSERA keeps visual attributes, DECERA does not:
        val keepVisualAttributes = erase && selective
        var argIndex = 0
        val fillChar = if (erase) ' '.code else getArg(argIndex++, -1, true)
        // "Pch can be any value from 32 to 126 or from 160 to 255. If Pch is not in this range, then the
        // terminal ignores the DECFRA command":
        if ((fillChar >= 32 && fillChar <= 126) || (fillChar >= 160 && fillChar <= 255)) {
            // "If the value of Pt, Pl, Pb, or Pr exceeds the width or height of the active page, the value
            // is treated as the width or height of that page."
            val top = getArg(argIndex++, 1, true).coerceAtMost(effectiveBottomMargin + 1) + effectiveTopMargin
            val left = getArg(argIndex++, 1, true).coerceAtMost(effectiveRightMargin + 1) + effectiveLeftMargin
            val bottom = getArg(argIndex++, rows, true).coerceAtMost(effectiveBottomMargin) + effectiveTopMargin
            val right = getArg(argIndex, columns, true).coerceAtMost(effectiveRightMargin) + effectiveLeftMargin
            val style = getStyle()
            for (row in top - 1 until bottom)
                for (col in left - 1 until right)
                    if (!selective || (TextStyle.decodeEffect(screen.getStyleAt(row, col)) and TextStyle.CHARACTER_ATTRIBUTE_PROTECTED) == 0)
                        screen.setChar(col, row, fillChar, if (keepVisualAttributes) screen.getStyleAt(row, col) else style)
        }
    }

    private fun doChangeAttributesInRectangularArea(effectiveTopMargin: Int, effectiveBottomMargin: Int, effectiveLeftMargin: Int, effectiveRightMargin: Int, reverse: Boolean) {
        // FIXME: "coordinates of the rectangular area are affected by the setting of origin mode (DECOM)".
        val top = getArg(0, 1, true).coerceAtMost(effectiveBottomMargin) - 1 + effectiveTopMargin
        val left = getArg(1, 1, true).coerceAtMost(effectiveRightMargin) - 1 + effectiveLeftMargin
        val bottom = getArg(2, rows, true).coerceAtMost(effectiveBottomMargin - 1) + 1 + effectiveTopMargin
        val right = getArg(3, columns, true).coerceAtMost(effectiveRightMargin - 1) + 1 + effectiveLeftMargin
        if (argIndex >= 4) {
            if (argIndex >= args.size) argIndex = args.size - 1
            for (i in 4..argIndex) {
                var bits = 0
                var setOrClear = true // True if setting, false if clearing.
                when (getArg(i, 0, false)) {
                    0 -> { // Attributes off (no bold, no underline, no blink, positive image).
                        bits = (TextStyle.CHARACTER_ATTRIBUTE_BOLD or TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE or TextStyle.CHARACTER_ATTRIBUTE_BLINK
                            or TextStyle.CHARACTER_ATTRIBUTE_INVERSE)
                        if (!reverse) setOrClear = false
                    }
                    1 -> bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD // Bold.
                    4 -> bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE // Underline.
                    5 -> bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK // Blink.
                    7 -> bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE // Negative image.
                    22 -> { // No bold.
                        bits = TextStyle.CHARACTER_ATTRIBUTE_BOLD
                        setOrClear = false
                    }
                    24 -> { // No underline.
                        bits = TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
                        setOrClear = false
                    }
                    25 -> { // No blink.
                        bits = TextStyle.CHARACTER_ATTRIBUTE_BLINK
                        setOrClear = false
                    }
                    27 -> { // Positive image.
                        bits = TextStyle.CHARACTER_ATTRIBUTE_INVERSE
                        setOrClear = false
                    }
                }
                if (reverse && !setOrClear) {
                    // Reverse attributes in rectangular area ignores non-(1,4,5,7) bits.
                } else {
                    screen.setOrClearEffect(bits, setOrClear, reverse, isDecsetInternalBitSet(DECSET_BIT_RECTANGULAR_CHANGEATTRIBUTE),
                        effectiveLeftMargin, effectiveRightMargin, top, left, bottom, right)
                }
            }
        }
    }

    /**
     * When in [ESC_APC] (APC, Application Program Command) sequence.
     */
    private fun doApc(b: Int) {
        if (b == 27) {
            continueSequence(ESC_APC_ESCAPE)
        }
        // Eat APC sequences silently for now.
    }

    /**
     * When in [ESC_APC] (APC, Application Program Command) sequence.
     */
    private fun doApcEscape(b: Int) {
        if (b == '\\'.code) {
            // A String Terminator (ST), ending the APC escape sequence.
            finishSequence()
        } else {
            // The Escape character was not the start of a String Terminator (ST),
            // but instead just data inside of the APC escape sequence.
            continueSequence(ESC_APC)
        }
    }

    private fun nextTabStop(numTabs: Int): Int {
        var tabs = numTabs
        for (i in cursorCol + 1 until columns)
            if (tabStop[i] && --tabs == 0) return Math.min(i, rightMargin)
        return rightMargin - 1
    }

    /**
     * Process byte while in the [ESC_CSI_UNSUPPORTED_PARAMETER_BYTE] or
     * [ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE] escape state.
     *
     * Parse unsupported parameter, intermediate and final bytes but ignore them.
     *
     * > For Control Sequence Introducer, ... the ESC [ is followed by
     * > - any number (including none) of "parameter bytes" in the range 0x30–0x3F (ASCII 0–9:;<=>?),
     * > - then by any number of "intermediate bytes" in the range 0x20–0x2F (ASCII space and !"#$%&'()*+,-./),
     * > - then finally by a single "final byte" in the range 0x40–0x7E (ASCII @A–Z[\]^_`a–z{|}~).
     *
     * - https://en.wikipedia.org/wiki/ANSI_escape_code#Control_Sequence_Introducer_commands
     * - https://invisible-island.net/xterm/ecma-48-parameter-format.html#section5.4
     */
    private fun doCsiUnsupportedParameterOrIntermediateByte(b: Int) {
        if (escapeState == ESC_CSI_UNSUPPORTED_PARAMETER_BYTE && b >= 0x30 && b <= 0x3F) {
            // Supported `0–9:;>?` or unsupported `<=` parameter byte after an
            // initial unsupported parameter byte in `doCsi()`, or a sequential parameter byte.
            continueSequence(ESC_CSI_UNSUPPORTED_PARAMETER_BYTE)
        } else if (b >= 0x20 && b <= 0x2F) {
            // Optional intermediate byte `!"#$%&'()*+,-./` after parameter or intermediate byte.
            continueSequence(ESC_CSI_UNSUPPORTED_INTERMEDIATE_BYTE)
        } else if (b >= 0x40 && b <= 0x7E) {
            // Final byte `@A–Z[\]^_`a–z{|}~` after parameter or intermediate byte.
            // Calling `unknownSequence()` would log an error with only a final byte, so ignore it for now.
            finishSequence()
        } else {
            unknownSequence(b)
        }
    }

    /** Process byte while in the [ESC_CSI_QUESTIONMARK] escape state. */
    private fun doCsiQuestionMark(b: Int) {
        when (b) {
            'J'.code, // Selective erase in display (DECSED) - http://www.vt100.net/docs/vt510-rm/DECSED.
            'K'.code -> { // Selective erase in line (DECSEL) - http://vt100.net/docs/vt510-rm/DECSEL.
                aboutToAutoWrap = false
                val fillChar = ' '.code
                var startCol = -1
                var startRow = -1
                var endCol = -1
                var endRow = -1
                val justRow = (b == 'K'.code)
                when (getArg0(0)) {
                    0 -> { // Erase from the active position to the end, inclusive (default).
                        startCol = cursorCol
                        startRow = cursorRow
                        endCol = columns
                        endRow = if (justRow) (cursorRow + 1) else rows
                    }
                    1 -> { // Erase from start to the active position, inclusive.
                        startCol = 0
                        startRow = if (justRow) cursorRow else 0
                        endCol = cursorCol + 1
                        endRow = cursorRow + 1
                    }
                    2 -> { // Erase all of the display/line.
                        startCol = 0
                        startRow = if (justRow) cursorRow else 0
                        endCol = columns
                        endRow = if (justRow) (cursorRow + 1) else rows
                    }
                    else -> unknownSequence(b)
                }
                val style = getStyle()
                for (row in startRow until endRow) {
                    for (col in startCol until endCol) {
                        if ((TextStyle.decodeEffect(screen.getStyleAt(row, col)) and TextStyle.CHARACTER_ATTRIBUTE_PROTECTED) == 0)
                            screen.setChar(col, row, fillChar, style)
                    }
                }
            }
            'h'.code, 'l'.code -> {
                if (argIndex >= args.size) argIndex = args.size - 1
                for (i in 0..argIndex)
                    doDecSetOrReset(b == 'h'.code, args[i])
            }
            'n'.code -> // Device Status Report (DSR, DEC-specific).
                when (getArg0(-1)) {
                    6 -> {
                        // Extended Cursor Position (DECXCPR - http://www.vt100.net/docs/vt510-rm/DECXCPR). Page=1.
                        session.write(String.format(Locale.US, "\u001b[?%d;%d;1R", cursorRow + 1, cursorCol + 1))
                    }
                    else -> {
                        finishSequence()
                        return
                    }
                }
            'r'.code, 's'.code -> {
                if (argIndex >= args.size) argIndex = args.size - 1
                for (i in 0..argIndex) {
                    val externalBit = args[i]
                    val internalBit = mapDecSetBitToInternalBit(externalBit)
                    if (internalBit == -1) {
                        Logger.logWarn(LOG_TAG, "Ignoring request to save/recall decset bit=$externalBit")
                    } else {
                        if (b == 's'.code) {
                            savedDecSetFlags = savedDecSetFlags or internalBit
                        } else {
                            doDecSetOrReset((savedDecSetFlags and internalBit) != 0, externalBit)
                        }
                    }
                }
            }
            '$'.code -> {
                continueSequence(ESC_CSI_QUESTIONMARK_ARG_DOLLAR)
                return
            }
            else -> parseArg(b)
        }
    }

    fun doDecSetOrReset(setting: Boolean, externalBit: Int) {
        val internalBit = mapDecSetBitToInternalBit(externalBit)
        if (internalBit != -1) {
            setDecsetinternalBit(internalBit, setting)
        }
        when (externalBit) {
            1 -> // Application Cursor Keys (DECCKM).
                {}
            3 -> // Set: 132 column mode (. Reset: 80 column mode. ANSI name: DECCOLM.
                // We don't actually set/reset 132 cols, but we do want the side effects
                // (FIXME: Should only do this if the 95 DECSET bit (DECNCSM) is set, and if changing value?):
                // Sets the left, right, top and bottom scrolling margins to their default positions, which is important for
                // the "reset" utility to really reset the terminal:
                run {
                    leftMargin = 0
                    topMargin = 0
                    bottomMargin = rows
                    rightMargin = columns
                    // "DECCOLM resets vertical split screen mode (DECLRMM) to unavailable":
                    setDecsetinternalBit(DECSET_BIT_LEFTRIGHT_MARGIN_MODE, false)
                    // "Erases all data in page memory":
                    blockClear(0, 0, columns, rows)
                    setCursorRowCol(0, 0)
                }
            4 -> // DECSCLM-Scrolling Mode. Ignore.
                {}
            5 -> // Reverse video. No action.
                {}
            6 -> // Set: Origin Mode. Reset: Normal Cursor Mode. Ansi name: DECOM.
                if (setting) setCursorPosition(0, 0)
            7, // Wrap-around bit, not specific action.
            8, // Auto-repeat Keys (DECARM). Do not implement.
            9, // X10 mouse reporting - outdated. Do not implement.
            12, // Control cursor blinking - ignore.
            25 -> { // Hide/show cursor - no action needed, renderer will check with shouldCursorBeVisible().
                client?.onTerminalCursorStateChange(setting)
            }
            40, // Allow 80 => 132 Mode, ignore.
            45, // TODO: Reverse wrap-around. Implement???
            66, // Application keypad (DECNKM).
            69 -> // Left and right margin mode (DECLRMM).
                if (!setting) {
                    leftMargin = 0
                    rightMargin = columns
                }
            1000, 1001, 1002, 1003, 1004, 1005, // UTF-8 mouse mode, ignore.
            1006, // SGR Mouse Mode
            1015, 1034 -> // Interpret "meta" key, sets eighth bit.
                {}
            1048 -> // Set: Save cursor as in DECSC. Reset: Restore cursor as in DECRC.
                if (setting)
                    saveCursor()
                else
                    restoreCursor()
            47, 1047, 1049 -> {
                // Set: Save cursor as in DECSC and use Alternate Screen Buffer, clearing it first.
                // Reset: Use Normal Screen Buffer and restore cursor as in DECRC.
                val newScreen = if (setting) altBuffer else mainBuffer
                if (newScreen != screen) {
                    val resized = !(newScreen.columns == columns && newScreen.screenRows == rows)
                    if (setting) saveCursor()
                    screen = newScreen
                    if (!setting) {
                        val col = savedStateMain.savedCursorCol
                        val row = savedStateMain.savedCursorRow
                        restoreCursor()
                        if (resized) {
                            // Restore cursor position _not_ clipped to current screen (let resizeScreen() handle that):
                            cursorCol = col
                            cursorRow = row
                        }
                    }
                    // Check if buffer size needs to be updated:
                    if (resized) resizeScreen()
                    // Clear new screen if alt buffer:
                    if (newScreen == altBuffer)
                        newScreen.blockSet(0, 0, columns, rows, ' '.code, getStyle())
                }
            }
            2004 -> {
                // Bracketed paste mode - setting bit is enough.
            }
            else -> unknownParameter(externalBit)
        }
    }

    private fun doCsiBiggerThan(b: Int) {
        when (b) {
            'c'.code -> // "${CSI}>c" or "${CSI}>c". Secondary Device Attributes (DA2).
                // Originally this was used for the terminal to respond with "identification code, firmware version level,
                // and hardware options" (http://vt100.net/docs/vt510-rm/DA2), with the first "41" meaning the VT420
                // terminal type. This is not used anymore, but the second version level field has been changed by xterm
                // to mean it's release number ("patch numbers" listed at http://invisible-island.net/xterm/xterm.log.html),
                // and some applications use it as a feature check:
                // * tmux used to have a "xterm won't reach version 500 for a while so set that as the upper limit" check,
                // and then check "xterm_version > 270" if rectangular area operations such as DECCRA could be used.
                // * vim checks xterm version number >140 for "Request termcap/terminfo string" functionality >276 for SGR
                // mouse report.
                // The third number is a keyboard identifier not used nowadays.
                session.write("\u001b[>41;320;0c")
            'm'.code -> {
                // https://bugs.launchpad.net/gnome-terminal/+bug/96676/comments/25
                // Depending on the first number parameter, this can set one of the xterm resources
                // modifyKeyboard, modifyCursorKeys, modifyFunctionKeys and modifyOtherKeys.
                // http://invisible-island.net/xterm/manpage/xterm.html#RESOURCES

                // * modifyKeyboard (parameter=1):
                // Normally xterm makes a special case regarding modifiers (shift, control, etc.) to handle special keyboard
                // layouts (legacy and vt220). This is done to provide compatible keyboards for DEC VT220 and related
                // terminals that implement user-defined keys (UDK).
                // The bits of the resource value selectively enable modification of the given category when these keyboards
                // are selected. The default is "0":
                // (0) The legacy/vt220 keyboards interpret only the Control-modifier when constructing numbered
                // function-keys. Other special keys are not modified.
                // (1) allows modification of the numeric keypad
                // (2) allows modification of the editing keypad
                // (4) allows modification of function-keys, overrides use of Shift-modifier for UDK.
                // (8) allows modification of other special keys

                // * modifyCursorKeys (parameter=2):
                // Tells how to handle the special case where Control-, Shift-, Alt- or Meta-modifiers are used to add a
                // parameter to the escape sequence returned by a cursor-key. The default is "2".
                // - Set it to -1 to disable it.
                // - Set it to 0 to use the old/obsolete behavior.
                // - Set it to 1 to prefix modified sequences with CSI.
                // - Set it to 2 to force the modifier to be the second parameter if it would otherwise be the first.
                // - Set it to 3 to mark the sequence with a ">" to hint that it is private.

                // * modifyFunctionKeys (parameter=3):
                // Tells how to handle the special case where Control-, Shift-, Alt- or Meta-modifiers are used to add a
                // parameter to the escape sequence returned by a (numbered) function-
                // key. The default is "2". The resource values are similar to modifyCursorKeys:
                // Set it to -1 to permit the user to use shift- and control-modifiers to construct function-key strings
                // using the normal encoding scheme.
                // - Set it to 0 to use the old/obsolete behavior.
                // - Set it to 1 to prefix modified sequences with CSI.
                // - Set it to 2 to force the modifier to be the second parameter if it would otherwise be the first.
                // - Set it to 3 to mark the sequence with a ">" to hint that it is private.
                // If modifyFunctionKeys is zero, xterm uses Control- and Shift-modifiers to allow the user to construct
                // numbered function-keys beyond the set provided by the keyboard:
                // (Control) adds the value given by the ctrlFKeys resource.
                // (Shift) adds twice the value given by the ctrlFKeys resource.
                // (Control/Shift) adds three times the value given by the ctrlFKeys resource.
                //
                // As a special case, legacy (when oldFunctionKeys is true) or vt220 (when sunKeyboard is true)
                // keyboards interpret only the Control-modifier when constructing numbered function-keys.
                // This is done to provide compatible keyboards for DEC VT220 and related terminals that
                // implement user-defined keys (UDK).

                // * modifyOtherKeys (parameter=4):
                // Like modifyCursorKeys, tells xterm to construct an escape sequence for other keys (such as "2") when
                // modified by Control-, Alt- or Meta-modifiers. This feature does not apply to function keys and
                // well-defined keys such as ESC or the control keys. The default is "0".
                // (0) disables this feature.
                // (1) enables this feature for keys except for those with well-known behavior, e.g., Tab, Backarrow and
                // some special control character cases, e.g., Control-Space to make a NUL.
                // (2) enables this feature for keys including the exceptions listed.
                Logger.logError(LOG_TAG, "(ignored) CSI > MODIFY RESOURCE: " + getArg0(-1) + " to " + getArg1(-1))
            }
            else -> parseArg(b)
        }
    }

    private fun startEscapeSequence() {
        escapeState = ESC
        argIndex = 0
        Arrays.fill(args, -1)
        argsSubParamsBitSet = 0
    }

    private fun doLinefeed() {
        val belowScrollingRegion = cursorRow >= bottomMargin
        var newCursorRow = cursorRow + 1
        if (belowScrollingRegion) {
            // Move down (but not scroll) as long as we are above the last row.
            if (cursorRow != rows - 1) {
                setCursorRow(newCursorRow)
            }
        } else {
            if (newCursorRow == bottomMargin) {
                scrollDownOneLine()
                newCursorRow = bottomMargin - 1
            }
            setCursorRow(newCursorRow)
        }
    }

    private fun continueSequence(state: Int) {
        escapeState = state
        continueSequence = true
    }

    private fun doEscPound(b: Int) {
        when (b) {
            '8'.code -> // Esc # 8 - DEC screen alignment test - fill screen with E's.
                screen.blockSet(0, 0, columns, rows, 'E'.code, getStyle())
            else -> unknownSequence(b)
        }
    }

    /** Encountering a character in the [ESC] state. */
    private fun doEsc(b: Int) {
        when (b) {
            '#'.code -> continueSequence(ESC_POUND)
            '('.code -> continueSequence(ESC_SELECT_LEFT_PAREN)
            ')'.code -> continueSequence(ESC_SELECT_RIGHT_PAREN)
            '6'.code -> // Back index (http://www.vt100.net/docs/vt510-rm/DECBI). Move left, insert blank column if start.
                if (cursorCol > leftMargin) {
                    cursorCol--
                } else {
                    val rows = bottomMargin - topMargin
                    screen.blockCopy(leftMargin, topMargin, rightMargin - leftMargin - 1, rows, leftMargin + 1, topMargin)
                    screen.blockSet(leftMargin, topMargin, 1, rows, ' '.code, TextStyle.encode(foreColor, backColor, 0))
                }
            '7'.code -> // DECSC save cursor - http://www.vt100.net/docs/vt510-rm/DECSC
                saveCursor()
            '8'.code -> // DECRC restore cursor - http://www.vt100.net/docs/vt510-rm/DECRC
                restoreCursor()
            '9'.code -> // Forward Index (http://www.vt100.net/docs/vt510-rm/DECFI). Move right, insert blank column if end.
                if (cursorCol < rightMargin - 1) {
                    cursorCol++
                } else {
                    val rows = bottomMargin - topMargin
                    screen.blockCopy(leftMargin + 1, topMargin, rightMargin - leftMargin - 1, rows, leftMargin, topMargin)
                    screen.blockSet(rightMargin - 1, topMargin, 1, rows, ' '.code, TextStyle.encode(foreColor, backColor, 0))
                }
            'c'.code -> // RIS - Reset to Initial State (http://vt100.net/docs/vt510-rm/RIS).
                run {
                    reset()
                    mainBuffer.clearTranscript()
                    blockClear(0, 0, columns, rows)
                    setCursorPosition(0, 0)
                }
            'D'.code -> // INDEX
                doLinefeed()
            'E'.code -> // Next line (http://www.vt100.net/docs/vt510-rm/NEL).
                run {
                    setCursorCol(if (isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)) leftMargin else 0)
                    doLinefeed()
                }
            'F'.code -> // Cursor to lower-left corner of screen
                setCursorRowCol(0, bottomMargin - 1)
            'H'.code -> // Tab set
                tabStop[cursorCol] = true
            'M'.code -> // "${ESC}M" - reverse index (RI).
                // http://www.vt100.net/docs/vt100-ug/chapter3.html: "Move the active position to the same horizontal
                // position on the preceding line. If the active position is at the top margin, a scroll down is performed".
                if (cursorRow <= topMargin) {
                    screen.blockCopy(leftMargin, topMargin, rightMargin - leftMargin, bottomMargin - (topMargin + 1), leftMargin, topMargin + 1)
                    blockClear(leftMargin, topMargin, rightMargin - leftMargin)
                } else {
                    cursorRow--
                }
            'N'.code, // SS2, ignore.
            '0'.code -> // SS3, ignore.
                {}
            'P'.code -> // Device control string
                run {
                    oscOrDeviceControlArgs.setLength(0)
                    continueSequence(ESC_P)
                }
            '['.code -> continueSequence(ESC_CSI)
            '='.code -> // DECKPAM
                setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, true)
            ']'.code -> // OSC
                run {
                    oscOrDeviceControlArgs.setLength(0)
                    continueSequence(ESC_OSC)
                }
            '>'.code -> // DECKPNM
                setDecsetinternalBit(DECSET_BIT_APPLICATION_KEYPAD, false)
            '_'.code -> // APC - Application Program Command.
                continueSequence(ESC_APC)
            else -> unknownSequence(b)
        }
    }

    /** DECSC save cursor - http://www.vt100.net/docs/vt510-rm/DECSC. */
    private fun saveCursor() {
        val state = if (screen == mainBuffer) savedStateMain else savedStateAlt
        state.savedCursorRow = cursorRow
        state.savedCursorCol = cursorCol
        state.savedEffect = effect
        state.savedForeColor = foreColor
        state.savedBackColor = backColor
        state.savedDecFlags = currentDecSetFlags
        state.useLineDrawingG0 = useLineDrawingG0
        state.useLineDrawingG1 = useLineDrawingG1
        state.useLineDrawingUsesG0 = useLineDrawingUsesG0
    }

    /** DECRS restore cursor - http://www.vt100.net/docs/vt510-rm/DECRC. See [saveCursor]. */
    private fun restoreCursor() {
        val state = if (screen == mainBuffer) savedStateMain else savedStateAlt
        setCursorRowCol(state.savedCursorRow, state.savedCursorCol)
        effect = state.savedEffect
        foreColor = state.savedForeColor
        backColor = state.savedBackColor
        val mask = (DECSET_BIT_AUTOWRAP or DECSET_BIT_ORIGIN_MODE)
        currentDecSetFlags = (currentDecSetFlags and mask.inv()) or (state.savedDecFlags and mask)
        useLineDrawingG0 = state.useLineDrawingG0
        useLineDrawingG1 = state.useLineDrawingG1
        useLineDrawingUsesG0 = state.useLineDrawingUsesG0
    }

    /** Following a CSI - Control Sequence Introducer, "\033[". [ESC_CSI]. */
    private fun doCsi(b: Int) {
        // Reset continueSequence by default - cases that need to continue will call continueSequence(newState)
        continueSequence = false
        when (b) {
            '!'.code -> continueSequence(ESC_CSI_EXCLAMATION)
            '"'.code -> continueSequence(ESC_CSI_DOUBLE_QUOTE)
            '\''.code -> continueSequence(ESC_CSI_SINGLE_QUOTE)
            '$'.code -> continueSequence(ESC_CSI_DOLLAR)
            '*'.code -> continueSequence(ESC_CSI_ARGS_ASTERIX)
            ' '.code -> continueSequence(ESC_CSI_ARGS_SPACE)
            '@'.code -> {
                // "CSI{n}@" - Insert ${n} space characters (ICH) - http://www.vt100.net/docs/vt510-rm/ICH.
                aboutToAutoWrap = false
                val columnsAfterCursor = columns - cursorCol
                val spacesToInsert = getArg0(1).coerceAtMost(columnsAfterCursor)
                val charsToMove = columnsAfterCursor - spacesToInsert
                screen.blockCopy(cursorCol, cursorRow, charsToMove, 1, cursorCol + spacesToInsert, cursorRow)
                blockClear(cursorCol, cursorRow, spacesToInsert)
            }
            'A'.code -> // "CSI${n}A" - Cursor up (CUU) ${n} rows.
                setCursorRow((cursorRow - getArg0(1)).coerceAtLeast(0))
            'B'.code -> // "CSI${n}B" - Cursor down (CUD) ${n} rows.
                setCursorRow((cursorRow + getArg0(1)).coerceAtMost(rows - 1))
            'C'.code, // "CSI${n}C" - Cursor forward (CUF).
            'a'.code -> // "CSI${n}a" - Horizontal position relative (HPR). From ISO-6428/ECMA-48.
                setCursorCol((cursorCol + getArg0(1)).coerceAtMost(rightMargin - 1))
            'D'.code -> // "CSI${n}D" - Cursor backward (CUB) ${n} columns.
                setCursorCol((cursorCol - getArg0(1)).coerceAtLeast(leftMargin))
            'E'.code -> // "CSI{n}E - Cursor Next Line (CNL). From ISO-6428/ECMA-48.
                setCursorPosition(0, cursorRow + getArg0(1))
            'F'.code -> // "CSI{n}F - Cursor Previous Line (CPL). From ISO-6428/ECMA-48.
                setCursorPosition(0, cursorRow - getArg0(1))
            'G'.code -> // "CSI${n}G" - Cursor horizontal absolute (CHA) to column ${n}.
                setCursorCol(getArg0(1).coerceIn(1, columns) - 1)
            'H'.code, // "${CSI}${ROW};${COLUMN}H" - Cursor position (CUP).
            'f'.code -> // "${CSI}${ROW};${COLUMN}f" - Horizontal and Vertical Position (HVP).
                setCursorPosition(getArg1(1) - 1, getArg0(1) - 1)
            'I'.code -> // Cursor Horizontal Forward Tabulation (CHT). Move the active position n tabs forward.
                setCursorCol(nextTabStop(getArg0(1)))
            'J'.code -> // "${CSI}${0,1,2,3}J" - Erase in Display (ED)
                // ED ignores the scrolling margins.
                run {
                    when (getArg0(0)) {
                        0 -> { // Erase from the active position to the end of the screen, inclusive (default).
                            blockClear(cursorCol, cursorRow, columns - cursorCol)
                            blockClear(0, cursorRow + 1, columns, rows - (cursorRow + 1))
                        }
                        1 -> { // Erase from start of the screen to the active position, inclusive.
                            blockClear(0, 0, columns, cursorRow)
                            blockClear(0, cursorRow, cursorCol + 1)
                        }
                        2 -> // Erase all of the display - all lines are erased, changed to single-width, and the cursor does not
                            // move..
                            blockClear(0, 0, columns, rows)
                        3 -> // Delete all lines saved in the scrollback buffer (xterm etc)
                            mainBuffer.clearTranscript()
                        else -> {
                            unknownSequence(b)
                            return
                        }
                    }
                    aboutToAutoWrap = false
                }
            'K'.code -> // "CSI{n}K" - Erase in line (EL).
                run {
                    when (getArg0(0)) {
                        0 -> // Erase from the cursor to the end of the line, inclusive (default)
                            blockClear(cursorCol, cursorRow, columns - cursorCol)
                        1 -> // Erase from the start of the screen to the cursor, inclusive.
                            blockClear(0, cursorRow, cursorCol + 1)
                        2 -> // Erase all of the line.
                            blockClear(0, cursorRow, columns)
                        else -> {
                            unknownSequence(b)
                            return
                        }
                    }
                    aboutToAutoWrap = false
                }
            'L'.code -> { // "${CSI}{N}L" - insert ${N} lines (IL).
                val linesAfterCursor = bottomMargin - cursorRow
                val linesToInsert = getArg0(1).coerceAtMost(linesAfterCursor)
                val linesToMove = linesAfterCursor - linesToInsert
                screen.blockCopy(0, cursorRow, columns, linesToMove, 0, cursorRow + linesToInsert)
                blockClear(0, cursorRow, columns, linesToInsert)
            }
            'M'.code -> { // "${CSI}${N}M" - delete N lines (DL).
                aboutToAutoWrap = false
                val linesAfterCursor = bottomMargin - cursorRow
                val linesToDelete = getArg0(1).coerceAtMost(linesAfterCursor)
                val linesToMove = linesAfterCursor - linesToDelete
                screen.blockCopy(0, cursorRow + linesToDelete, columns, linesToMove, 0, cursorRow)
                blockClear(0, cursorRow + linesToMove, columns, linesToDelete)
            }
            'P'.code -> { // "${CSI}{N}P" - delete ${N} characters (DCH).
                // http://www.vt100.net/docs/vt510-rm/DCH: "If ${N} is greater than the number of characters between the
                // cursor and the right margin, then DCH only deletes the remaining characters.
                // As characters are deleted, the remaining characters between the cursor and right margin move to the left.
                // Character attributes move with the characters. The terminal adds blank spaces with no visual character
                // attributes at the right margin. DCH has no effect outside the scrolling margins."
                aboutToAutoWrap = false
                val cellsAfterCursor = columns - cursorCol
                val cellsToDelete = getArg0(1).coerceAtMost(cellsAfterCursor)
                val cellsToMove = cellsAfterCursor - cellsToDelete
                screen.blockCopy(cursorCol + cellsToDelete, cursorRow, cellsToMove, 1, cursorCol, cursorRow)
                blockClear(cursorCol + cellsToMove, cursorRow, cellsToDelete)
            }
            'S'.code -> { // "${CSI}${N}S" - scroll up ${N} lines (default = 1) (SU).
                val linesToScroll = getArg0(1)
                for (i in 0 until linesToScroll)
                    scrollDownOneLine()
            }
            'T'.code -> if (argIndex == 0) {
                // "${CSI}${N}T" - Scroll down N lines (default = 1) (SD).
                // http://vt100.net/docs/vt510-rm/SD: "N is the number of lines to move the user window up in page
                // memory. N new lines appear at the top of the display. N old lines disappear at the bottom of the
                // display. You cannot pan past the top margin of the current page".
                val linesToScrollArg = getArg0(1)
                val linesBetweenTopAndBottomMargins = bottomMargin - topMargin
                val linesToScroll = (linesBetweenTopAndBottomMargins).coerceAtMost(linesToScrollArg)
                screen.blockCopy(leftMargin, topMargin, rightMargin - leftMargin, linesBetweenTopAndBottomMargins - linesToScroll, leftMargin, topMargin + linesToScroll)
                blockClear(leftMargin, topMargin, rightMargin - leftMargin, linesToScroll)
            } else {
                // "${CSI}${func};${startx};${starty};${firstrow};${lastrow}T" - initiate highlight mouse tracking.
                unimplementedSequence(b)
            }
            'X'.code -> { // "${CSI}${N}X" - Erase ${N:=1} character(s) (ECH). FIXME: Clears character attributes?
                aboutToAutoWrap = false
                screen.blockSet(cursorCol, cursorRow, getArg0(1).coerceAtMost(columns - cursorCol), 1, ' '.code, getStyle())
            }
            'Z'.code -> { // Cursor Backward Tabulation (CBT). Move the active position n tabs backward.
                var numberOfTabs = getArg0(1)
                var newCol = leftMargin
                for (i in cursorCol - 1 downTo 0)
                    if (tabStop[i]) {
                        if (--numberOfTabs == 0) {
                            newCol = i.coerceAtLeast(leftMargin)
                            break
                        }
                    }
                cursorCol = newCol
            }
            '?'.code -> // Esc [ ? -- start of a private parameter byte
                continueSequence(ESC_CSI_QUESTIONMARK)
            '>'.code -> // "Esc [ >" -- start of a private parameter byte
                continueSequence(ESC_CSI_BIGGERTHAN)
            '<'.code, // "Esc [ <" -- start of a private parameter byte
            '='.code -> // "Esc [ =" -- start of a private parameter byte
                continueSequence(ESC_CSI_UNSUPPORTED_PARAMETER_BYTE)
            '`'.code -> // Horizontal position absolute (HPA - http://www.vt100.net/docs/vt510-rm/HPA).
                setCursorColRespectingOriginMode(getArg0(1) - 1)
            'b'.code -> { // Repeat the preceding graphic character Ps times (REP).
                if (lastEmittedCodePoint == -1) return
                val numRepeat = getArg0(1)
                for (i in 0 until numRepeat) emitCodePoint(lastEmittedCodePoint)
            }
            'c'.code -> // Primary Device Attributes (http://www.vt100.net/docs/vt510-rm/DA1) if argument is missing or zero.
                // The important part that may still be used by some (tmux stores this value but does not currently use it)
                // is the first response parameter identifying the terminal service class, where we send 64 for "vt420".
                // This is followed by a list of attributes which is probably unused by applications. Send like xterm.
                if (getArg0(0) == 0) session.write("\u001b[?64;1;2;6;9;15;18;21;22c")
            'd'.code -> // ESC [ Pn d - Vert Position Absolute
                setCursorRow(getArg0(1).coerceIn(1, rows) - 1)
            'e'.code -> // Vertical Position Relative (VPR). From ISO-6429 (ECMA-48).
                setCursorPosition(cursorCol, cursorRow + getArg0(1))
            // case 'f': "${CSI}${ROW};${COLUMN}f" - Horizontal and Vertical Position (HVP). Grouped with case 'H'.
            'g'.code -> // Clear tab stop
                when (getArg0(0)) {
                    0 -> tabStop[cursorCol] = false
                    3 -> for (i in 0 until columns) {
                        tabStop[i] = false
                    }
                    else -> {
                        // Specified to have no effect.
                    }
                }
            'h'.code -> // Set Mode
                doSetMode(true)
            'l'.code -> // Reset Mode
                doSetMode(false)
            'm'.code -> // Esc [ Pn m - character attributes. (can have up to 16 numerical arguments)
                selectGraphicRendition()
            'n'.code -> // Esc [ Pn n - ECMA-48 Status Report Commands
                // sendDeviceAttributes()
                when (getArg0(0)) {
                    5 -> // Device status report (DSR):
                        // Answer is ESC [ 0 n (Terminal OK).
                        run {
                            val dsr = byteArrayOf(27.toByte(), '['.code.toByte(), '0'.code.toByte(), 'n'.code.toByte())
                            session.write(dsr, 0, dsr.size)
                        }
                    6 -> // Cursor position report (CPR):
                        // Answer is ESC [ y ; x R, where x,y is
                        // the cursor location.
                        session.write(String.format(Locale.US, "\u001b[%d;%dR", cursorRow + 1, cursorCol + 1))
                    else -> {}
                }
            'r'.code -> { // "CSI${top};${bottom}r" - set top and bottom Margins (DECSTBM).
                // https://vt100.net/docs/vt510-rm/DECSTBM.html
                // The top margin defaults to 1, the bottom margin defaults to rows.
                // The escape sequence numbers top 1..23, but we number top 0..22.
                // The escape sequence numbers bottom 2..24, and so do we (because we use a zero based numbering
                // scheme, but we store the first line below the bottom-most scrolling line.
                // As a result, we adjust the top line by -1, but we leave the bottom line alone.
                // Also require that top + 2 <= bottom.
                topMargin = (getArg0(1) - 1).coerceIn(0, rows - 2)
                bottomMargin = (topMargin + 2).coerceAtMost(getArg1(rows)).coerceAtMost(rows)

                // DECSTBM moves the cursor to column 1, line 1 of the page respecting origin mode.
                setCursorPosition(0, 0)
            }
            's'.code -> if (isDecsetInternalBitSet(DECSET_BIT_LEFTRIGHT_MARGIN_MODE)) {
                // Set left and right margins (DECSLRM - http://www.vt100.net/docs/vt510-rm/DECSLRM).
                leftMargin = (getArg0(1) - 1).coerceAtMost(columns - 2)
                rightMargin = (leftMargin + 1).coerceAtMost(getArg1(columns)).coerceAtMost(columns)
                // DECSLRM moves the cursor to column 1, line 1 of the page.
                setCursorPosition(0, 0)
            } else {
                // Save cursor (ANSI.SYS), available only when DECLRMM is disabled.
                saveCursor()
            }
            't'.code -> // Window manipulation (from dtterm, as well as extensions)
                when (getArg0(0)) {
                    11 -> // Report xterm window state. If the xterm window is open (non-iconified), it returns CSI 1 t .
                        session.write("\u001b[1t")
                    13 -> // Report xterm window position. Result is CSI 3 ; x ; y t
                        session.write("\u001b[3;0;0t")
                    14 -> // Report xterm window in pixels. Result is CSI 4 ; height ; width t
                        session.write(String.format(Locale.US, "\u001b[4;%d;%dt", rows * cellHeightPixels, columns * cellWidthPixels))
                    16 -> // Report xterm character cell size in pixels. Result is CSI 6 ; height ; width t
                        session.write(String.format(Locale.US, "\u001b[6;%d;%dt", cellHeightPixels, cellWidthPixels))
                    18 -> // Report the size of the text area in characters. Result is CSI 8 ; height ; width t
                        session.write(String.format(Locale.US, "\u001b[8;%d;%dt", rows, columns))
                    19 -> // Report the size of the screen in characters. Result is CSI 9 ; height ; width t
                        // We report the same size as the view, since it's the view really isn't resizable from the shell.
                        session.write(String.format(Locale.US, "\u001b[9;%d;%dt", rows, columns))
                    20 -> // Report xterm windows icon label. Result is OSC L label ST. Disabled due to security concerns:
                        session.write("\u001b]LIconLabel\u001b\\")
                    21 -> // Report xterm windows title. Result is OSC l label ST. Disabled due to security concerns:
                        session.write("\u001b]l\u001b\\")
                    22 -> {
                        // 22;0 -> Save xterm icon and window title on stack.
                        // 22;1 -> Save xterm icon title on stack.
                        // 22;2 -> Save xterm window title on stack.
                        title?.let { titleStack.addFirst(it) }
                        if (titleStack.size > 20) {
                            // Limit size - remove oldest (last)
                            titleStack.removeLast()
                        }
                    }
                    23 -> // Like 22 above but restore from stack.
                        if (titleStack.isNotEmpty()) setTitle(titleStack.removeFirst())
                    else -> {
                        // Ignore window manipulation.
                    }
                }
            'u'.code -> // Restore cursor (ANSI.SYS).
                restoreCursor()
            // ' '.code -> continueSequence(ESC_CSI_ARGS_SPACE)  // Already moved above
            else -> parseArg(b)
        }
        // If no continueSequence(newState) was called, exit CSI mode
        if (!continueSequence) {
            escapeState = ESC_NONE
        }
    }

    /** Select Graphic Rendition (SGR) - see http://en.wikipedia.org/wiki/ANSI_escape_code#graphics. */
    private fun selectGraphicRendition() {
        if (argIndex >= args.size) argIndex = args.size - 1
        var i = 0
        while (i <= argIndex) {
            // Skip leading sub parameters:
            if ((argsSubParamsBitSet and (1 shl i)) != 0) {
                i++
                continue
            }

            var code = getArg(i, 0, false)
            if (code < 0) {
                if (argIndex > 0) {
                    i++
                    continue
                } else {
                    code = 0
                }
            }
            when {
                code == 0 -> { // reset
                    foreColor = TextStyle.COLOR_INDEX_FOREGROUND
                    backColor = TextStyle.COLOR_INDEX_BACKGROUND
                    effect = 0
                }
                code == 1 -> effect = effect or TextStyle.CHARACTER_ATTRIBUTE_BOLD
                code == 2 -> effect = effect or TextStyle.CHARACTER_ATTRIBUTE_DIM
                code == 3 -> effect = effect or TextStyle.CHARACTER_ATTRIBUTE_ITALIC
                code == 4 -> {
                    if (i + 1 <= argIndex && ((argsSubParamsBitSet and (1 shl (i + 1))) != 0)) {
                        // Sub parameter, see https://sw.kovidgoyal.net/kitty/underlines/
                        i++
                        if (args[i] == 0) {
                            // No underline.
                            effect = effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE.inv()
                        } else {
                            // Different variations of underlines: https://sw.kovidgoyal.net/kitty/underlines/
                            effect = effect or TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
                        }
                    } else {
                        effect = effect or TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE
                    }
                }
                code == 5 -> effect = effect or TextStyle.CHARACTER_ATTRIBUTE_BLINK
                code == 7 -> effect = effect or TextStyle.CHARACTER_ATTRIBUTE_INVERSE
                code == 8 -> effect = effect or TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE
                code == 9 -> effect = effect or TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH
                code == 10 -> {
                    // Exit alt charset (TERM=linux) - ignore.
                }
                code == 11 -> {
                    // Enter alt charset (TERM=linux) - ignore.
                }
                code == 22 -> { // Normal color or intensity, neither bright, bold nor faint.
                    effect = effect and (TextStyle.CHARACTER_ATTRIBUTE_BOLD or TextStyle.CHARACTER_ATTRIBUTE_DIM).inv()
                }
                code == 23 -> { // not italic, but rarely used as such; clears standout with TERM=screen
                    effect = effect and TextStyle.CHARACTER_ATTRIBUTE_ITALIC.inv()
                }
                code == 24 -> { // underline: none
                    effect = effect and TextStyle.CHARACTER_ATTRIBUTE_UNDERLINE.inv()
                }
                code == 25 -> { // blink: none
                    effect = effect and TextStyle.CHARACTER_ATTRIBUTE_BLINK.inv()
                }
                code == 27 -> { // image: positive
                    effect = effect and TextStyle.CHARACTER_ATTRIBUTE_INVERSE.inv()
                }
                code == 28 -> {
                    effect = effect and TextStyle.CHARACTER_ATTRIBUTE_INVISIBLE.inv()
                }
                code == 29 -> {
                    effect = effect and TextStyle.CHARACTER_ATTRIBUTE_STRIKETHROUGH.inv()
                }
                code in 30..37 -> {
                    foreColor = code - 30
                }
                code == 38 || code == 48 || code == 58 -> {
                    // Extended set foreground(38)/background(48)/underline(58) color.
                    // This is followed by either "2;$R;$G;$B" to set a 24-bit color or
                    // "5;$INDEX" to set an indexed color.
                    if (i + 2 > argIndex) {
                        i++
                        continue
                    }
                    val firstArg = args[i + 1]
                    if (firstArg == 2) {
                        if (i + 4 > argIndex) {
                            Logger.logWarn(LOG_TAG, "Too few CSI" + code + ";2 RGB arguments")
                        } else {
                            val red = getArg(i + 2, 0, false)
                            val green = getArg(i + 3, 0, false)
                            val blue = getArg(i + 4, 0, false)

                            if (red < 0 || green < 0 || blue < 0 || red > 255 || green > 255 || blue > 255) {
                                finishSequenceAndLogError("Invalid RGB: $red,$green,$blue")
                            } else {
                                val argbColor = -0x1000000 or (red shl 16) or (green shl 8) or blue
                                when (code) {
                                    38 -> foreColor = argbColor
                                    48 -> backColor = argbColor
                                    58 -> underlineColor = argbColor
                                }
                            }
                            i += 4 // "2;P_r;P_g;P_r"
                        }
                    } else if (firstArg == 5) {
                        val color = getArg(i + 2, 0, false)
                        i += 2 // "5;P_s"
                        if (color >= 0 && color <= 255) {
                            when (code) {
                                38 -> foreColor = color
                                48 -> backColor = color
                                58 -> underlineColor = color
                            }
                        } else {
                            finishSequenceAndLogError("Invalid color index: $color")
                        }
                    } else {
                        finishSequenceAndLogError("Invalid extended color argument: $firstArg")
                    }
                }
                code in 39..47 -> {
                    backColor = code - 39
                }
                code == 49 -> backColor = TextStyle.COLOR_INDEX_BACKGROUND
                code in 50..55 -> {
                    // Ignore underline color for 50-55 range.
                }
                code in 90..97 -> { // Bright foreground colors (aixterm codes).
                    foreColor = code - 90 + 8
                }
                code in 100..107 -> { // Bright background colors (aixterm codes).
                    backColor = code - 100 + 8
                }
                else -> {
                    // Unknown SGR code.
                }
            }
            i++
        }
    }

    private fun doDeviceControl(b: Int) {
        when (b) {
            '\\'.code -> { // String terminator.
                val dcs = oscOrDeviceControlArgs.toString()
                if (dcs.startsWith("|") || dcs.startsWith("}")) {
                    // Termcap/terminfo query: https://github.com/teambition/terminfo2json
                    // https://www.xfree86.org/current/ctlseqs.html#PC-Style%20Function%20Keys
                    // Reply with DCS 1 + r Pt ST (xterm) or DCS 0 + r Pt ST (invalid request).
                    val part = dcs.substring(1)
                    if (part.length % 2 == 0) {
                        // The part string should contain pairs of "two-character abbreviations of the names for the capabilities".
                        val hexEncoded = StringBuilder()
                        for (j in part.indices step 2) {
                            val trans = part.substring(j, j + 2)
                            when (trans) {
                                "%1" -> // Help key - ignore
                                    {}
                                "&8" -> // Undo key - ignore.
                                    {}
                                else -> Logger.logWarn(LOG_TAG, "Unhandled termcap/terminfo name: '$trans'")
                            }
                            hexEncoded.append(String.format("%02X", trans[0].code))
                            hexEncoded.append(String.format("%02X", trans[1].code))
                        }
                        session.write("\u001bP1+r" + part + "=" + hexEncoded + "\u001b\\")
                    } else {
                        // Invalid request
                        session.write("\u001bP0+r" + part + "\u001b\\")
                    }
                } else {
                    if (LOG_ESCAPE_SEQUENCES)
                        Logger.logError(LOG_TAG, "Unrecognized device control string: $dcs")
                }
                finishSequence()
            }
            else -> {
                if (oscOrDeviceControlArgs.length > MAX_OSC_STRING_LENGTH) {
                    // Too long.
                    oscOrDeviceControlArgs.setLength(0)
                    finishSequence()
                } else {
                    oscOrDeviceControlArgs.appendCodePoint(b)
                    continueSequence(escapeState)
                }
            }
        }
    }

    private fun doOsc(b: Int) {
        when (b) {
            7 -> // BEL - Bell.
                doOscSetTextParameters("\u0007")
            27 -> // ESC - Escape character.
                continueSequence(ESC_OSC_ESC)
            92 -> // \ - Backslash - String Terminator (ST).
                doOscSetTextParameters("\u001b\\")
            else -> {
                // The ESC character was not followed by a \, so insert the ESC and
                // the current character in arg buffer.
                collectOSCArgs(27)
                collectOSCArgs(b)
                continueSequence(ESC_OSC)
            }
        }
    }

    private fun doOscEsc(b: Int) {
        if (b == '\\'.code) {
            doOscSetTextParameters("\u001b\\")
        } else {
            // The ESC character was not followed by a \, so insert the ESC and
            // the current character in arg buffer.
            collectOSCArgs(27)
            collectOSCArgs(b)
            continueSequence(ESC_OSC)
        }
    }

    /** An Operating System Controls (OSC) Set Text Parameters. May come here from BEL or ST. */
    private fun doOscSetTextParameters(bellOrStringTerminator: String) {
        var value = -1
        var textParameter = ""
        // Extract initial $value from initial "$value;..." string.
        var oscArgTokenizerIndex = 0
        while (oscArgTokenizerIndex < oscOrDeviceControlArgs.length) {
            val b = oscOrDeviceControlArgs[oscArgTokenizerIndex]
            if (b == ';') {
                textParameter = oscOrDeviceControlArgs.substring(oscArgTokenizerIndex + 1)
                break
            } else if (b in '0'..'9') {
                value = if (value < 0) 0 else value * 10
                value = value + (b.code - '0'.code)
            } else {
                unknownSequence(b.code)
                return
            }
            oscArgTokenizerIndex++
        }

        when (value) {
            0, // Change icon name and window title to T.
            1, // Change icon name to T.
            2 -> // Change window title to T.
                setTitle(textParameter)
            4 -> {
                // P s = 4 ; c ; spec → Change Color Number c to the color specified by spec. This can be a name or RGB
                // specification as per XParseColor. Any number of c name pairs may be given. The color numbers correspond
                // to the ANSI colors 0-7, their bright versions 8-15, and if supported, the remainder of the 88-color or
                // 256-color table.
                // If a "?" is given rather than a name or RGB specification, xterm replies with a control sequence of the
                // same form which can be used to set the corresponding color. Because more than one pair of color number
                // and specification can be given in one control sequence, xterm can make more than one reply.
                var colorIndex = -1
                var parsingPairStart = -1
                var i = 0
                while (true) {
                    val endOfInput = i == textParameter.length
                    val b = if (endOfInput) ';'.code else textParameter[i].code
                    if (b == ';'.code) {
                        if (parsingPairStart < 0) {
                            parsingPairStart = i + 1
                        } else {
                            if (colorIndex < 0 || colorIndex > 255) {
                                unknownSequence(b)
                                return
                            } else {
                                colors.tryParseColor(colorIndex, textParameter.substring(parsingPairStart, i))
                                session.onColorsChanged()
                                colorIndex = -1
                                parsingPairStart = -1
                            }
                        }
                    } else if (parsingPairStart >= 0) {
                        // We have passed a color index and are now going through color spec.
                    } else if (parsingPairStart < 0 && (b >= '0'.code && b <= '9'.code)) {
                        colorIndex = if (colorIndex < 0) 0 else colorIndex * 10
                        colorIndex = colorIndex + (b - '0'.code)
                    } else {
                        unknownSequence(b)
                        return
                    }
                    if (endOfInput) break
                    i++
                }
            }
            10, // Set foreground color.
            11, // Set background color.
            12 -> { // Set cursor color.
                val specialIndex = TextStyle.COLOR_INDEX_FOREGROUND + (value - 10)
                var lastSemiIndex = 0
                var charIndex = 0
                while (true) {
                    val endOfInput = charIndex == textParameter.length
                    if (endOfInput || textParameter[charIndex] == ';') {
                        try {
                            val colorSpec = textParameter.substring(lastSemiIndex, charIndex)
                            if ("?" == colorSpec) {
                                // Report current color in the same format xterm and gnome-terminal does.
                                val rgb = colors.currentColors[specialIndex]
                                val r = (65535 * ((rgb and 0x00FF0000) shr 16)) / 255
                                val g = (65535 * ((rgb and 0x0000FF00) shr 8)) / 255
                                val b = (65535 * ((rgb and 0x000000FF))) / 255
                                session.write("\u001b]" + value + ";rgb:" + String.format(Locale.US, "%04x", r) + "/" + String.format(Locale.US, "%04x", g) + "/"
                                    + String.format(Locale.US, "%04x", b) + bellOrStringTerminator)
                            } else {
                                colors.tryParseColor(specialIndex, colorSpec)
                                session.onColorsChanged()
                            }
                            if (endOfInput || (specialIndex > TextStyle.COLOR_INDEX_CURSOR) || ++charIndex >= textParameter.length)
                                break
                            lastSemiIndex = charIndex
                        } catch (e: NumberFormatException) {
                            // Ignore.
                        }
                    }
                    charIndex++
                }
            }
            52 -> { // Manipulate Selection Data. Skip the optional first selection parameter(s).
                val startIndex = textParameter.indexOf(";") + 1
                try {
                    val clipboardText = String(Base64.decode(textParameter.substring(startIndex), 0), StandardCharsets.UTF_8)
                    session.onCopyTextToClipboard(clipboardText)
                } catch (e: Exception) {
                    Logger.logError(LOG_TAG, "OSC Manipulate selection, invalid string '$textParameter")
                }
            }
            104 -> {
                // "104;$c" → Reset Color Number $c. It is reset to the color specified by the corresponding X
                // resource. Any number of c parameters may be given. These parameters correspond to the ANSI colors 0-7,
                // their bright versions 8-15, and if supported, the remainder of the 88-color or 256-color table. If no
                // parameters are given, the entire table will be reset.
                if (textParameter.isEmpty()) {
                    colors.reset()
                    session.onColorsChanged()
                } else {
                    var lastIndex = 0
                    var charIndex = 0
                    while (true) {
                        val endOfInput = charIndex == textParameter.length
                        if (endOfInput || textParameter[charIndex] == ';') {
                            try {
                                val colorToReset = textParameter.substring(lastIndex, charIndex).toInt()
                                colors.reset(colorToReset)
                                session.onColorsChanged()
                                if (endOfInput) break
                                charIndex++
                                lastIndex = charIndex
                            } catch (e: NumberFormatException) {
                                // Ignore.
                            }
                        }
                        charIndex++
                    }
                }
            }
            110, // Reset foreground color.
            111, // Reset background color.
            112 -> { // Reset cursor color.
                colors.reset(TextStyle.COLOR_INDEX_FOREGROUND + (value - 110))
                session.onColorsChanged()
            }
            119 -> { // Reset highlight color.
            }
            else -> unknownParameter(value)
        }
        finishSequence()
    }

    private fun blockClear(sx: Int, sy: Int, w: Int) {
        blockClear(sx, sy, w, 1)
    }

    private fun blockClear(sx: Int, sy: Int, w: Int, h: Int) {
        screen.blockSet(sx, sy, w, h, ' '.code, getStyle())
    }

    private fun getStyle(): Long {
        return TextStyle.encode(foreColor, backColor, effect)
    }

    /** "CSI P_m h" for set or "CSI P_m l" for reset ANSI mode. */
    private fun doSetMode(newValue: Boolean) {
        val modeBit = getArg0(0)
        when (modeBit) {
            4 -> // Set="Insert Mode". Reset="Replace Mode". (IRM).
                insertMode = newValue
            20 -> // Normal Linefeed (LNM).
                unknownParameter(modeBit)
            // http://www.vt100.net/docs/vt510-rm/LNM
            34 -> {
                // Normal cursor visibility - when using TERM=screen, see
                // http://www.gnu.org/software/screen/manual/html_node/Control-Sequences.html
            }
            else -> unknownParameter(modeBit)
        }
    }

    /**
     * NOTE: The parameters of this function respect the [DECSET_BIT_ORIGIN_MODE]. Use
     * [setCursorRowCol] for absolute pos.
     */
    private fun setCursorPosition(x: Int, y: Int) {
        val originMode = isDecsetInternalBitSet(DECSET_BIT_ORIGIN_MODE)
        val effectiveTopMargin = if (originMode) topMargin else 0
        val effectiveBottomMargin = if (originMode) bottomMargin else rows
        val effectiveLeftMargin = if (originMode) leftMargin else 0
        val effectiveRightMargin = if (originMode) rightMargin else columns
        val newRow = effectiveTopMargin.coerceAtLeast((effectiveTopMargin + y).coerceAtMost(effectiveBottomMargin - 1))
        val newCol = effectiveLeftMargin.coerceAtLeast((effectiveLeftMargin + x).coerceAtMost(effectiveRightMargin - 1))
        setCursorRowCol(newRow, newCol)
    }

    private fun scrollDownOneLine() {
        scrollCounter++
        val currentStyle = getStyle()
        if (leftMargin != 0 || rightMargin != columns) {
            // Horizontal margin: Do not put anything into scroll history, just non-margin part of screen up.
            screen.blockCopy(leftMargin, topMargin + 1, rightMargin - leftMargin, bottomMargin - topMargin - 1, leftMargin, topMargin)
            // .. and blank bottom row between margins:
            screen.blockSet(leftMargin, bottomMargin - 1, rightMargin - leftMargin, 1, ' '.code, currentStyle)
        } else {
            screen.scrollDownOneLine(topMargin, bottomMargin, currentStyle)
        }
    }

    /**
     * Process the next ASCII character of a parameter.
     *
     *
     * You must use the ; character to separate parameters and : to separate sub-parameters.
     *
     *
     * Parameter characters modify the action or interpretation of the sequence. Originally
     * you can use up to 16 parameters per sequence, but following at least xterm and alacritty
     * we use a common space for parameters and sub-parameters, allowing 32 in total.
     *
     *
     * All parameters are unsigned, positive decimal integers, with the most significant
     * digit sent first. Any parameter greater than 9999 (decimal) is set to 9999
     * (decimal). If you do not specify a value, a 0 value is assumed. A 0 value
     * or omitted parameter indicates a default value for the sequence. For most
     * sequences, the default value is 1.
     *
     * References:
     * - [VT510 Video Terminal Programmer Information: Control Sequences](https://vt100.net/docs/vt510-rm/chapter4.html#S4.3.3)
     * - [alacritty/vte: Implement colon separated CSI parameters](https://github.com/alacritty/vte/issues/22)
     */
    private fun parseArg(b: Int) {
        if (b >= '0'.code && b <= '9'.code) {
            if (argIndex < args.size) {
                val oldValue = args[argIndex]
                val thisDigit = b - '0'.code
                var calculatedValue = if (oldValue >= 0) {
                    oldValue * 10 + thisDigit
                } else {
                    thisDigit
                }
                if (calculatedValue > 9999) calculatedValue = 9999
                args[argIndex] = calculatedValue
            }
            continueSequence(escapeState)
        } else if (b == ';'.code || b == ':'.code) {
            if (argIndex + 1 < args.size) {
                argIndex++
                if (b == ':'.code) {
                    argsSubParamsBitSet = argsSubParamsBitSet or (1 shl argIndex)
                }
            } else {
                logError("Too many parameters when in state: $escapeState")
            }
            continueSequence(escapeState)
        } else {
            unknownSequence(b)
        }
    }

    private fun getArg0(defaultValue: Int): Int {
        return getArg(0, defaultValue, true)
    }

    private fun getArg1(defaultValue: Int): Int {
        return getArg(1, defaultValue, true)
    }

    private fun getArg(index: Int, defaultValue: Int, treatZeroAsDefault: Boolean): Int {
        var result = args[index]
        if (result < 0 || (result == 0 && treatZeroAsDefault)) {
            result = defaultValue
        }
        return result
    }

    private fun collectOSCArgs(b: Int) {
        if (oscOrDeviceControlArgs.length < MAX_OSC_STRING_LENGTH) {
            oscOrDeviceControlArgs.appendCodePoint(b)
            continueSequence(escapeState)
        } else {
            unknownSequence(b)
        }
    }

    private fun unimplementedSequence(b: Int) {
        logError("Unimplemented sequence char '" + b.toChar() + "' (U+" + String.format("%04x", b) + ")")
        finishSequence()
    }

    private fun unknownSequence(b: Int) {
        logError("Unknown sequence char '" + b.toChar() + "' (numeric value=$b)")
        finishSequence()
    }

    private fun unknownParameter(parameter: Int) {
        logError("Unknown parameter: $parameter")
        finishSequence()
    }

    private fun logError(errorType: String) {
        if (LOG_ESCAPE_SEQUENCES) {
            val buf = StringBuilder()
            buf.append(errorType)
            buf.append(", escapeState=")
            buf.append(escapeState)
            var firstArg = true
            if (argIndex >= args.size) argIndex = args.size - 1
            for (i in 0..argIndex) {
                val value = args[i]
                if (value >= 0) {
                    if (firstArg) {
                        firstArg = false
                        buf.append(", args={")
                    } else {
                        buf.append(',')
                    }
                    buf.append(value)
                }
            }
            if (!firstArg) buf.append('}')
            finishSequenceAndLogError(buf.toString())
        }
    }

    private fun finishSequenceAndLogError(error: String) {
        if (LOG_ESCAPE_SEQUENCES) Logger.logWarn(LOG_TAG, error)
        finishSequence()
    }

    private fun finishSequence() {
        escapeState = ESC_NONE
    }

    /**
     * Send a Unicode code point to the screen.
     *
     * @param codePoint The code point of the character to display
     */
    private fun emitCodePoint(codePoint: Int) {
        lastEmittedCodePoint = codePoint
        if (useLineDrawingUsesG0 && useLineDrawingG0 || !useLineDrawingUsesG0 && useLineDrawingG1) {
            // http://www.vt100.net/docs/vt102-ug/table5-15.html.
            val mappedCodePoint = when (codePoint) {
                '_'.code -> ' '.code // Blank.
                '`'.code -> '◆'.code // Diamond.
                '0'.code -> '█'.code // Solid block;
                'a'.code -> '▒'.code // Checker board.
                'b'.code -> '␉'.code // Horizontal tab.
                'c'.code -> '␌'.code // Form feed.
                'd'.code -> '\r'.code // Carriage return.
                'e'.code -> '␊'.code // Linefeed.
                'f'.code -> '°'.code // Degree.
                'g'.code -> '±'.code // Plus-minus.
                'h'.code -> '\n'.code // Newline.
                'i'.code -> '␋'.code // Vertical tab.
                'j'.code -> '┘'.code // Lower right corner.
                'k'.code -> '┐'.code // Upper right corner.
                'l'.code -> '┌'.code // Upper left corner.
                'm'.code -> '└'.code // Left left corner.
                'n'.code -> '┼'.code // Crossing lines.
                'o'.code -> '⎺'.code // Horizontal line - scan 1.
                'p'.code -> '⎻'.code // Horizontal line - scan 3.
                'q'.code -> '─'.code // Horizontal line - scan 5.
                'r'.code -> '⎼'.code // Horizontal line - scan 7.
                's'.code -> '⎽'.code // Horizontal line - scan 9.
                't'.code -> '├'.code // T facing rightwards.
                'u'.code -> '┤'.code // T facing leftwards.
                'v'.code -> '┴'.code // T facing upwards.
                'w'.code -> '┬'.code // T facing downwards.
                'x'.code -> '│'.code // Vertical line.
                'y'.code -> '≤'.code // Less than or equal to.
                'z'.code -> '≥'.code // Greater than or equal to.
                '{'.code -> 'π'.code // Pi.
                '|'.code -> '≠'.code // Not equal to.
                '}'.code -> '£'.code // UK pound.
                '~'.code -> '·'.code // Centered dot.
                else -> codePoint
            }
            emitCodePointWithLineDrawing(mappedCodePoint)
        } else {
            emitCodePointWithLineDrawing(codePoint)
        }
    }

    @Synchronized
    private fun emitCodePointWithLineDrawing(codePoint: Int) {
        val autoWrap = isDecsetInternalBitSet(DECSET_BIT_AUTOWRAP)
        val displayWidth = WcWidth.width(codePoint)
        val cursorInLastColumn = cursorCol == rightMargin - 1

        if (autoWrap) {
            if (cursorInLastColumn && ((aboutToAutoWrap && displayWidth == 1) || displayWidth == 2)) {
                screen.setLineWrap(cursorRow)
                cursorCol = leftMargin
                if (cursorRow + 1 < bottomMargin) {
                    cursorRow++
                } else {
                    scrollDownOneLine()
                }
            }
        } else if (cursorInLastColumn && displayWidth == 2) {
            // The behaviour when a wide character is output with cursor in the last column when
            // autowrap is disabled is not obvious - it's ignored here.
            return
        }

        if (insertMode && displayWidth > 0) {
            // Move character to right one space.
            val destCol = cursorCol + displayWidth
            if (destCol < rightMargin)
                screen.blockCopy(cursorCol, cursorRow, rightMargin - destCol, 1, destCol, cursorRow)
        }

        var offsetDueToCombiningChar = if ((displayWidth <= 0 && cursorCol > 0 && !aboutToAutoWrap)) 1 else 0
        var column = cursorCol - offsetDueToCombiningChar

        // Fix TerminalRow.setChar() ArrayIndexOutOfBoundsException index=-1 exception reported
        // The offsetDueToCombiningChar would never be 1 if cursorCol was 0 to get column/index=-1,
        // so was cursorCol changed after the offsetDueToCombiningChar conditional by another thread?
        // Synchronized method prevents race conditions with cursorCol and cursorRow from other threads.
        if (column < 0) column = 0
        screen.setChar(column, cursorRow, codePoint, getStyle())

        if (autoWrap && displayWidth > 0)
            aboutToAutoWrap = (cursorCol == rightMargin - displayWidth)

        cursorCol = (cursorCol + displayWidth).coerceAtMost(rightMargin - 1)
    }

    private fun setCursorRow(row: Int) {
        cursorRow = row
        aboutToAutoWrap = false
    }

    private fun setCursorCol(col: Int) {
        cursorCol = col
        aboutToAutoWrap = false
    }

    /** Set the cursor mode, but limit it to margins if [DECSET_BIT_ORIGIN_MODE] is enabled. */
    private fun setCursorColRespectingOriginMode(col: Int) {
        setCursorPosition(col, cursorRow)
    }

    /** TODO: Better name, distinguished from [setCursorPosition] by not regarding origin mode. */
    private fun setCursorRowCol(row: Int, col: Int) {
        cursorRow = row.coerceIn(0, rows - 1)
        cursorCol = col.coerceIn(0, columns - 1)
        aboutToAutoWrap = false
    }

    fun getScrollCounter(): Int {
        return scrollCounter
    }

    fun clearScrollCounter() {
        scrollCounter = 0
    }

    fun isAutoScrollDisabled(): Boolean {
        return autoScrollDisabled
    }

    fun toggleAutoScrollDisabled() {
        autoScrollDisabled = !autoScrollDisabled
    }

    /** Reset terminal state so user can interact with it regardless of present state. */
    fun reset() {
        setCursorStyle()
        argIndex = 0
        continueSequence = false
        escapeState = ESC_NONE
        insertMode = false
        topMargin = 0
        leftMargin = 0
        bottomMargin = rows
        rightMargin = columns
        aboutToAutoWrap = false
        foreColor = TextStyle.COLOR_INDEX_FOREGROUND
        savedStateMain.savedForeColor = foreColor
        savedStateAlt.savedForeColor = foreColor
        backColor = TextStyle.COLOR_INDEX_BACKGROUND
        savedStateMain.savedBackColor = backColor
        savedStateAlt.savedBackColor = backColor
        setDefaultTabStops()

        useLineDrawingG0 = false
        useLineDrawingG1 = false
        useLineDrawingUsesG0 = true

        savedStateMain.savedCursorRow = 0
        savedStateMain.savedCursorCol = 0
        savedStateMain.savedEffect = 0
        savedStateMain.savedDecFlags = 0
        savedStateAlt.savedCursorRow = 0
        savedStateAlt.savedCursorCol = 0
        savedStateAlt.savedEffect = 0
        savedStateAlt.savedDecFlags = 0
        currentDecSetFlags = 0
        // Initial wrap-around is not accurate but makes terminal more useful, especially on a small screen:
        setDecsetinternalBit(DECSET_BIT_AUTOWRAP, true)
        setDecsetinternalBit(DECSET_BIT_CURSOR_ENABLED, true)
        savedStateMain.savedDecFlags = currentDecSetFlags
        savedStateAlt.savedDecFlags = currentDecSetFlags
        savedDecSetFlags = currentDecSetFlags

        // XXX: Should we set terminal driver back to IUTF8 with termios?
        utf8Index = 0.toByte()
        utf8ToFollow = 0.toByte()

        colors.reset()
        session.onColorsChanged()
    }

    fun getSelectedText(x1: Int, y1: Int, x2: Int, y2: Int): String {
        return screen.getSelectedText(x1, y1, x2, y2)
    }

    /** Get the terminal session's title (null if not set). */
    fun getTitle(): String? {
        return title
    }

    /** Change the terminal session's title. */
    private fun setTitle(newTitle: String?) {
        val oldTitle = title
        title = newTitle
        if (oldTitle != newTitle) {
            session.titleChanged(oldTitle, newTitle)
        }
    }

    /** If DECSET 2004 is set, prefix paste with "\033[200~" and suffix with "\033[201~". */
    fun paste(text: String) {
        // First: Always remove escape key and C1 control characters [0x80,0x9F]:
        var processedText = text.replace("(\u001B|[\u0080-\u009F])".toRegex(), "")
        // Second: Replace all newlines (\n) or CRLF (\r\n) with carriage returns (\r).
        processedText = processedText.replace("\r?\n".toRegex(), "\r")

        // Then: Implement bracketed paste mode if enabled:
        val bracketed = isDecsetInternalBitSet(DECSET_BIT_BRACKETED_PASTE_MODE)
        if (bracketed) session.write("\u001b[200~")
        session.write(processedText)
        if (bracketed) session.write("\u001b[201~")
    }

    /** http://www.vt100.net/docs/vt510-rm/DECSC */
    class SavedScreenState {
        /** Saved state of the cursor position, Used to implement the save/restore cursor position escape sequences. */
        var savedCursorRow: Int = 0
        var savedCursorCol: Int = 0
        var savedEffect: Int = 0
        var savedForeColor: Int = 0
        var savedBackColor: Int = 0
        var savedDecFlags: Int = 0
        var useLineDrawingG0: Boolean = false
        var useLineDrawingG1: Boolean = false
        var useLineDrawingUsesG0: Boolean = true
    }

    override fun toString(): String {
        return "TerminalEmulator[size=${screen.columns}x${screen.screenRows}, margins={$topMargin,$rightMargin,$bottomMargin,$leftMargin}]"
    }
}
