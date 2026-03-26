package com.termux.view.textselection

import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.text.TextUtils
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import androidx.annotation.Nullable
import kotlin.math.roundToInt
import com.termux.terminal.TerminalBuffer
import com.termux.terminal.WcWidth
import com.termux.view.R
import com.termux.view.TerminalView

class TextSelectionCursorController(
    private val terminalView: TerminalView
) : CursorController {

    private val mStartHandle: TextSelectionHandleView
    private val mEndHandle: TextSelectionHandleView
    private var storedSelectedText: String? = null
    private var isSelectingText = false
    private var showStartTime = System.currentTimeMillis()

    private val mHandleHeight: Int
    private var selX1 = -1
    private var selX2 = -1
    private var selY1 = -1
    private var selY2 = -1

    private var actionMode: ActionMode? = null

    val ACTION_COPY = 1
    val ACTION_PASTE = 2
    val ACTION_MORE = 3

    init {
        mStartHandle = TextSelectionHandleView(terminalView, this, TextSelectionHandleView.LEFT)
        mEndHandle = TextSelectionHandleView(terminalView, this, TextSelectionHandleView.RIGHT)
        mHandleHeight = maxOf(mStartHandle.getHandleHeight(), mEndHandle.getHandleWidth())
    }

    override fun show(event: MotionEvent) {
        setInitialTextSelectionPosition(event)
        mStartHandle.positionAtCursor(selX1, selY1, true)
        mEndHandle.positionAtCursor(selX2 + 1, selY2, true)

        setActionModeCallBacks()
        showStartTime = System.currentTimeMillis()
        isSelectingText = true
    }

    override fun hide(): Boolean {
        if (!isActive()) return false

        // prevent hide calls right after a show call, like long pressing the down key
        // 300ms seems long enough that it wouldn't cause hide problems if action button
        // is quickly clicked after the show, otherwise decrease it
        if (System.currentTimeMillis() - showStartTime < 300) {
            return false
        }

        mStartHandle.hide()
        mEndHandle.hide()

        actionMode?.let {
            // This will hide the TextSelectionCursorController
            it.finish()
        }

        selX1 = -1
        selY1 = -1
        selX2 = -1
        selY2 = -1
        isSelectingText = false

        return true
    }

    override fun render() {
        if (!isActive()) return

        mStartHandle.positionAtCursor(selX1, selY1, false)
        mEndHandle.positionAtCursor(selX2 + 1, selY2, false)

        actionMode?.invalidate()
    }

    private fun setInitialTextSelectionPosition(event: MotionEvent) {
        val columnAndRow = terminalView.getColumnAndRow(event, true)
        selX1 = columnAndRow[0]
        selX2 = columnAndRow[0]
        selY1 = columnAndRow[1]
        selY2 = columnAndRow[1]

        val screen = terminalView.emulator?.getScreen() ?: return
        if (" " != screen.getSelectedText(selX1, selY1, selX1, selY1)) {
            // Selecting something other than whitespace. Expand to word.
            while (selX1 > 0 && "" != screen.getSelectedText(selX1 - 1, selY1, selX1 - 1, selY1)) {
                selX1--
            }
            while (selX2 < (terminalView.emulator?.columns ?: 80) - 1 &&
                   "" != screen.getSelectedText(selX2 + 1, selY1, selX2 + 1, selY1)) {
                selX2++
            }
        }
    }

    private fun setActionModeCallBacks() {
        val callback = object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                val show = MenuItem.SHOW_AS_ACTION_IF_ROOM or MenuItem.SHOW_AS_ACTION_WITH_TEXT

                val clipboard = terminalView.context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                menu.add(Menu.NONE, ACTION_COPY, Menu.NONE, R.string.copy_text).setShowAsAction(show)
                menu.add(Menu.NONE, ACTION_PASTE, Menu.NONE, R.string.paste_text)
                    .setEnabled(clipboard != null && clipboard.hasPrimaryClip())
                    .setShowAsAction(show)
                menu.add(Menu.NONE, ACTION_MORE, Menu.NONE, R.string.text_selection_more)
                return true
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                if (!isActive()) {
                    // Fix issue where the dialog is pressed while being dismissed.
                    return true
                }

                when (item.itemId) {
                    ACTION_COPY -> {
                        val selectedText = selectedText
                        terminalView.termSession?.onCopyTextToClipboard(selectedText)
                        terminalView.stopTextSelectionMode()
                    }
                    ACTION_PASTE -> {
                        terminalView.stopTextSelectionMode()
                        terminalView.termSession?.onPasteTextFromClipboard()
                    }
                    ACTION_MORE -> {
                        // We first store the selected text in case TerminalViewClient needs the
                        // selected text before MORE button was pressed since we are going to
                        // stop selection mode
                        storedSelectedText = selectedText
                        // The text selection needs to be stopped before showing context menu,
                        // otherwise handles will show above popup
                        terminalView.stopTextSelectionMode()
                        terminalView.showContextMenu()
                    }
                }

                return true
            }

            override fun onDestroyActionMode(mode: ActionMode) {
            }
        }

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            actionMode = terminalView.startActionMode(callback)
            return
        }

        //noinspection NewApi
        actionMode = terminalView.startActionMode(object : ActionMode.Callback2() {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                return callback.onCreateActionMode(mode, menu)
            }

            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false

            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                return callback.onActionItemClicked(mode, item)
            }

            override fun onDestroyActionMode(mode: ActionMode) {
                // Ignore.
            }

            override fun onGetContentRect(mode: ActionMode, view: View, outRect: Rect) {
                val fontWidth = terminalView.renderer?.fontWidth ?: 1f
                val fontLineSpacing = (terminalView.renderer?.fontLineSpacing ?: 1).toFloat()
                var x1 = ((selX1.toFloat() * fontWidth).toDouble()).roundToInt()
                var x2 = ((selX2.toFloat() * fontWidth).toDouble()).roundToInt()
                val y1 = (((selY1 - 1 - terminalView.topRow).toFloat() * fontLineSpacing).toDouble()).roundToInt()
                val y2 = (((selY2 + 1 - terminalView.topRow).toFloat() * fontLineSpacing).toDouble()).roundToInt()

                if (x1 > x2) {
                    val tmp = x1
                    x1 = x2
                    x2 = tmp
                }

                val terminalBottom = terminalView.bottom
                var top = y1 + mHandleHeight
                var bottom = y2 + mHandleHeight
                if (top > terminalBottom) top = terminalBottom
                if (bottom > terminalBottom) bottom = terminalBottom

                outRect.set(x1, top, x2, bottom)
            }
        }, ActionMode.TYPE_FLOATING)
    }

    override fun updatePosition(handle: TextSelectionHandleView, x: Int, y: Int) {
        val screen = terminalView.emulator?.getScreen() ?: return
        val emulatorRows = terminalView.emulator?.rows ?: 24
        val scrollRows = screen.activeRows - emulatorRows

        if (handle == mStartHandle) {
            selX1 = terminalView.getCursorX(x.toFloat())
            selY1 = terminalView.getCursorY(y.toFloat())
            if (selX1 < 0) {
                selX1 = 0
            }

            if (selY1 < -scrollRows) {
                selY1 = -scrollRows
            } else if (selY1 > (terminalView.emulator?.rows ?: 24) - 1) {
                selY1 = (terminalView.emulator?.rows ?: 24) - 1
            }

            if (selY1 > selY2) {
                selY1 = selY2
            }
            if (selY1 == selY2 && selX1 > selX2) {
                selX1 = selX2
            }

            if (!(terminalView.emulator?.isAlternateBufferActive() ?: false)) {
                var topRow = terminalView.topRow

                if (selY1 <= topRow) {
                    topRow--
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows
                    }
                } else if (selY1 >= topRow + (terminalView.emulator?.rows ?: 24)) {
                    topRow++
                    if (topRow > 0) {
                        topRow = 0
                    }
                }

                terminalView.setTopRow(topRow)
            }

            selX1 = getValidCurX(screen, selY1, selX1)

        } else {
            selX2 = terminalView.getCursorX(x.toFloat())
            selY2 = terminalView.getCursorY(y.toFloat())
            if (selX2 < 0) {
                selX2 = 0
            }

            if (selY2 < -scrollRows) {
                selY2 = -scrollRows
            } else if (selY2 > (terminalView.emulator?.rows ?: 24) - 1) {
                selY2 = (terminalView.emulator?.rows ?: 24) - 1
            }

            if (selY1 > selY2) {
                selY2 = selY1
            }
            if (selY1 == selY2 && selX1 > selX2) {
                selX2 = selX1
            }

            if (!(terminalView.emulator?.isAlternateBufferActive() ?: false)) {
                var topRow = terminalView.topRow

                if (selY2 <= topRow) {
                    topRow--
                    if (topRow < -scrollRows) {
                        topRow = -scrollRows
                    }
                } else if (selY2 >= topRow + (terminalView.emulator?.rows ?: 24)) {
                    topRow++
                    if (topRow > 0) {
                        topRow = 0
                    }
                }

                terminalView.setTopRow(topRow)
            }

            selX2 = getValidCurX(screen, selY2, selX2)
        }

        terminalView.invalidate()
    }

    private fun getValidCurX(screen: TerminalBuffer, cy: Int, cx: Int): Int {
        val line = screen.getSelectedText(0, cy, cx, cy)
        if (!TextUtils.isEmpty(line)) {
            var col = 0
            var i = 0
            val len = line.length
            while (i < len) {
                val ch1 = line[i]
                if (ch1 == 0.toChar()) {
                    break
                }

                val wc: Int
                if (Character.isHighSurrogate(ch1) && i + 1 < len) {
                    val ch2 = line[++i]
                    wc = WcWidth.width(Character.toCodePoint(ch1, ch2))
                } else {
                    wc = WcWidth.width(ch1.code)
                }

                val cend = col + wc
                if (cx > col && cx < cend) {
                    return cend
                }
                if (cend == col) {
                    return col
                }
                col = cend
                i++
            }
        }
        return cx
    }

    fun decrementYTextSelectionCursors(decrement: Int) {
        selY1 -= decrement
        selY2 -= decrement
    }

    override fun onTouchEvent(event: MotionEvent): Boolean = false

    override fun onTouchModeChanged(isInTouchMode: Boolean) {
        if (!isInTouchMode) {
            terminalView.stopTextSelectionMode()
        }
    }

    override fun onDetached() {
    }

    override fun isActive(): Boolean = isSelectingText

    fun getSelectors(sel: IntArray?) {
        if (sel == null || sel.size != 4) {
            return
        }

        sel[0] = selY1
        sel[1] = selY2
        sel[2] = selX1
        sel[3] = selX2
    }

    /** Get the currently selected text. */
    val selectedText: String
        get() = terminalView.emulator?.getSelectedText(selX1, selY1, selX2, selY2) ?: ""

    /** Get the selected text stored before "MORE" button was pressed on the context menu. */
    @Nullable
    fun getStoredSelectedText(): String? = storedSelectedText

    /** Unset the selected text stored before "MORE" button was pressed on the context menu. */
    fun unsetStoredSelectedText() {
        storedSelectedText = null
    }

    fun getActionMode(): ActionMode? = actionMode

    /**
     * @return true if this controller is currently used to move the start selection.
     */
    fun isSelectionStartDragged(): Boolean = mStartHandle.isDragging

    /**
     * @return true if this controller is currently used to move the end selection.
     */
    fun isSelectionEndDragged(): Boolean = mEndHandle.isDragging
}
