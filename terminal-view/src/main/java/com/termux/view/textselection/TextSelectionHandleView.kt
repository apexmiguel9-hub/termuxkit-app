package com.termux.view.textselection

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewParent
import android.view.WindowManager
import android.widget.PopupWindow
import com.termux.view.R
import com.termux.view.TerminalView
import com.termux.view.support.PopupWindowCompatGingerbread

@SuppressLint("ViewConstructor")
class TextSelectionHandleView(
    private val terminalView: TerminalView,
    private val cursorController: CursorController,
    private val mInitialOrientation: Int
) : View(terminalView.context) {

    private var handle: PopupWindow? = null

    private var handleLeftDrawable: Drawable
    private var handleRightDrawable: Drawable
    private var handleDrawable: Drawable? = null

    private var _isDragging = false

    private val mTempCoords = IntArray(2)
    private var tempRect: Rect? = null

    private var pointX = 0
    private var pointY = 0
    private var touchToWindowOffsetX = 0f
    private var touchToWindowOffsetY = 0f
    private var hotspotX = 0f
    private var hotspotY = 0f
    private var touchOffsetY = 0f
    private var lastParentX = 0
    private var lastParentY = 0

    private var handleHeight = 0
    private var handleWidth = 0

    private var orientation = 0

    private var lastTime: Long = 0

    companion object {
        const val LEFT = 0
        const val RIGHT = 2
    }

    init {
        handleLeftDrawable = context.getDrawable(R.drawable.text_select_handle_left_material)
            ?: throw IllegalStateException("Left handle drawable not found")
        handleRightDrawable = context.getDrawable(R.drawable.text_select_handle_right_material)
            ?: throw IllegalStateException("Right handle drawable not found")
        setOrientation(mInitialOrientation)
    }

    private fun initHandle() {
        handle = PopupWindow(terminalView.context, null, android.R.attr.textSelectHandleWindowStyle).apply {
            isSplitTouchEnabled = true
            isClippingEnabled = false
            width = ViewGroup.LayoutParams.WRAP_CONTENT
            height = ViewGroup.LayoutParams.WRAP_CONTENT
            setBackgroundDrawable(null)
            animationStyle = 0

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                windowLayoutType = WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL
                enterTransition = null
                exitTransition = null
            } else {
                PopupWindowCompatGingerbread.setWindowLayoutType(this, WindowManager.LayoutParams.TYPE_APPLICATION_SUB_PANEL)
            }
            contentView = this@TextSelectionHandleView
        }
    }

    fun setOrientation(orientation: Int) {
        this.orientation = orientation
        var handleWidth = 0
        when (orientation) {
            LEFT -> {
                handleDrawable = handleLeftDrawable
                handleWidth = handleDrawable?.intrinsicWidth ?: 0
                hotspotX = (handleWidth * 3) / 4f
            }

            RIGHT -> {
                handleDrawable = handleRightDrawable
                handleWidth = handleDrawable?.intrinsicWidth ?: 0
                hotspotX = handleWidth / 4f
            }
        }

        handleHeight = handleDrawable?.intrinsicHeight ?: 0
        handleWidth = handleWidth
        touchOffsetY = -handleHeight * 0.3f
        hotspotY = 0f
        invalidate()
    }

    fun show() {
        if (!isPositionVisible) {
            hide()
            return
        }

        // We remove handle from its parent first otherwise the following exception may be thrown
        // java.lang.IllegalStateException: The specified child already has a parent. You must call removeView() on the child's parent first.
        removeFromParent()

        initHandle() // init the handle
        invalidate() // invalidate to make sure onDraw is called

        val coords = mTempCoords
        terminalView.getLocationInWindow(coords)
        coords[0] += pointX
        coords[1] += pointY

        handle?.showAtLocation(terminalView, 0, coords[0], coords[1])
    }

    fun hide() {
        _isDragging = false

        handle?.let {
            it.dismiss()

            // We remove handle from its parent, otherwise it may still be shown in some cases even after the dismiss call
            removeFromParent()
            handle = null // garbage collect the handle
        }
        invalidate()
    }

    fun removeFromParent() {
        if (!isParentNull) {
            (this.parent as ViewGroup).removeView(this)
        }
    }

    fun positionAtCursor(cx: Int, cy: Int, forceOrientationCheck: Boolean) {
        val x = terminalView.getPointX(cx)
        val y = terminalView.getPointY(cy + 1)
        moveTo(x, y, forceOrientationCheck)
    }

    private fun moveTo(x: Int, y: Int, forceOrientationCheck: Boolean) {
        val oldHotspotX = hotspotX
        checkChangedOrientation(x, forceOrientationCheck)
        pointX = (x - (if (isShowing()) oldHotspotX else hotspotX)).toInt()
        pointY = y

        if (isPositionVisible) {
            var coords: IntArray? = null

            if (isShowing()) {
                coords = mTempCoords
                terminalView.getLocationInWindow(coords)
                val x1 = coords[0] + pointX
                val y1 = coords[1] + pointY
                handle?.update(x1, y1, width, height)
            } else {
                show()
            }

            if (_isDragging) {
                if (coords == null) {
                    coords = mTempCoords
                    terminalView.getLocationInWindow(coords)
                }
                if (coords[0] != lastParentX || coords[1] != lastParentY) {
                    touchToWindowOffsetX += coords[0] - lastParentX
                    touchToWindowOffsetY += coords[1] - lastParentY
                    lastParentX = coords[0]
                    lastParentY = coords[1]
                }
            }
        } else {
            hide()
        }
    }

    fun changeOrientation(orientation: Int) {
        if (this.orientation != orientation) {
            setOrientation(orientation)
        }
    }

    private fun checkChangedOrientation(posX: Int, force: Boolean) {
        if (!_isDragging && !force) {
            return
        }
        val millis = SystemClock.currentThreadTimeMillis()
        if (millis - lastTime < 50 && !force) {
            return
        }
        lastTime = millis

        val hostView = terminalView
        val left = hostView.left
        val right = hostView.width
        val top = hostView.top
        val bottom = hostView.height

        if (tempRect == null) {
            tempRect = Rect()
        }
        val clip = tempRect ?: return
        clip.left = left + terminalView.paddingLeft
        clip.top = top + terminalView.paddingTop
        clip.right = right - terminalView.paddingRight
        clip.bottom = bottom - terminalView.paddingBottom

        val parent = hostView.parent
        if (parent == null || !parent.getChildVisibleRect(hostView, clip, null)) {
            return
        }

        if (posX - handleWidth < clip.left) {
            changeOrientation(RIGHT)
        } else if (posX + handleWidth > clip.right) {
            changeOrientation(LEFT)
        } else {
            changeOrientation(mInitialOrientation)
        }
    }

    private val isPositionVisible: Boolean
        get() {
            // Always show a dragging handle.
            if (_isDragging) {
                return true
            }

            val hostView = terminalView
            val left = 0
            val right = hostView.width
            val top = 0
            val bottom = hostView.height

            if (tempRect == null) {
                tempRect = Rect()
            }
            val clip = tempRect ?: return false
            clip.left = left + terminalView.paddingLeft
            clip.top = top + terminalView.paddingTop
            clip.right = right - terminalView.paddingRight
            clip.bottom = bottom - terminalView.paddingBottom

            val parent = hostView.parent
            if (parent == null || !parent.getChildVisibleRect(hostView, clip, null)) {
                return false
            }

            val coords = mTempCoords
            hostView.getLocationInWindow(coords)
            val posX = coords[0] + pointX + hotspotX.toInt()
            val posY = coords[1] + pointY + hotspotY.toInt()

            return posX >= clip.left && posX <= clip.right &&
                posY >= clip.top && posY <= clip.bottom
        }

    override fun onDraw(c: Canvas) {
        val width = handleDrawable?.intrinsicWidth ?: 0
        val height = handleDrawable?.intrinsicHeight ?: 0
        handleDrawable?.setBounds(0, 0, width, height)
        handleDrawable?.draw(c)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        terminalView.updateFloatingToolbarVisibility(event)
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val rawX = event.rawX
                val rawY = event.rawY
                touchToWindowOffsetX = rawX - pointX
                touchToWindowOffsetY = rawY - pointY
                val coords = mTempCoords
                terminalView.getLocationInWindow(coords)
                lastParentX = coords[0]
                lastParentY = coords[1]
                _isDragging = true
            }

            MotionEvent.ACTION_MOVE -> {
                val rawX = event.rawX
                val rawY = event.rawY

                val newPosX = rawX - touchToWindowOffsetX + hotspotX
                val newPosY = rawY - touchToWindowOffsetY + hotspotY + touchOffsetY

                cursorController.updatePosition(this, Math.round(newPosX), Math.round(newPosY))
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                _isDragging = false
            }
        }
        return true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            handleDrawable?.intrinsicWidth ?: 0,
            handleDrawable?.intrinsicHeight ?: 0
        )
    }

    fun getHandleHeight(): Int = handleHeight

    fun getHandleWidth(): Int = handleWidth

    fun isShowing(): Boolean = handle?.isShowing ?: false

    val isParentNull: Boolean
        get() = this.parent == null

    val isDragging: Boolean
        get() = _isDragging
}
