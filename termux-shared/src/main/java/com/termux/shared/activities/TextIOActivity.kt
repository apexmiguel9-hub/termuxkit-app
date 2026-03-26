package com.termux.shared.activities

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputFilter
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.termux.shared.R
import com.termux.shared.interact.ShareUtils
import com.termux.shared.logger.Logger
import com.termux.shared.models.TextIOInfo
import com.termux.shared.view.KeyboardUtils
import java.util.Locale

/**
 * An activity to edit or view text based on config passed as [TextIOInfo].
 *
 * Add Following to `AndroidManifest.xml` to use in an app:
 *
 * `<activity android:name="com.termux.shared.activities.TextIOActivity" android:theme="@style/Theme.AppCompat.TermuxTextIOActivity" />`
 */
class TextIOActivity : AppCompatActivity() {

    private var mTextIOLabel: TextView? = null
    private var mTextIOLabelSeparator: View? = null
    private var mTextIOText: EditText? = null
    private var mTextIOHorizontalScrollView: HorizontalScrollView? = null
    private var mTextIOTextLinearLayout: LinearLayout? = null
    private var mTextIOTextCharacterUsage: TextView? = null

    private var mTextIOInfo: TextIOInfo? = null
    private var mBundle: Bundle? = null

    companion object {
        private const val CLASS_NAME = "com.termux.shared.activities.TextIOActivity"
        const val EXTRA_TEXT_IO_INFO_OBJECT = "$CLASS_NAME.EXTRA_TEXT_IO_INFO_OBJECT"
        private const val LOG_TAG = "TextIOActivity"

        /**
         * Get the [Intent] that can be used to start the [TextIOActivity].
         *
         * @param context The [Context] for operations.
         * @param mTextIOInfo The [TextIOInfo] containing info for the edit text.
         */
        @JvmStatic
        fun newInstance(context: Context, mTextIOInfo: TextIOInfo): Intent {
            val intent = Intent(context, TextIOActivity::class.java)
            val mBundle = Bundle()
            mBundle.putSerializable(EXTRA_TEXT_IO_INFO_OBJECT, mTextIOInfo)
            intent.putExtras(mBundle)
            return intent
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.logVerbose(LOG_TAG, "onCreate")

        setContentView(R.layout.activity_text_io)

        mTextIOLabel = findViewById(R.id.text_io_label)
        mTextIOLabelSeparator = findViewById(R.id.text_io_label_separator)
        mTextIOText = findViewById(R.id.text_io_text)
        mTextIOHorizontalScrollView = findViewById(R.id.text_io_horizontal_scroll_view)
        mTextIOTextLinearLayout = findViewById(R.id.text_io_text_linear_layout)
        mTextIOTextCharacterUsage = findViewById(R.id.text_io_text_character_usage)

        val toolbar: Toolbar? = findViewById(R.id.toolbar)
        toolbar?.let { setSupportActionBar(it) }

        mBundle = intent?.extras ?: savedInstanceState

        updateUI()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Logger.logVerbose(LOG_TAG, "onNewIntent")

        // Views must be re-created since different configs for editingTextDisabled() and
        // textHorizontallyScrolling() will not work or at least reliably
        finish()
        intent?.let { startActivity(it) }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun updateUI() {
        val mBundle = mBundle ?: run { finish(); return }

        mTextIOInfo = mBundle.getSerializable(EXTRA_TEXT_IO_INFO_OBJECT) as? TextIOInfo
        if (mTextIOInfo == null) { finish(); return }

        val actionBar: ActionBar? = supportActionBar
        actionBar?.let {
            it.title = mTextIOInfo?.title ?: "Text Input"

            if (mTextIOInfo?.showBackButtonInActionBar == true) {
                it.setDisplayHomeAsUpEnabled(true)
                it.setDisplayShowHomeEnabled(true)
            }
        }

        mTextIOLabel?.visibility = View.GONE
        mTextIOLabelSeparator?.visibility = View.GONE
        if (mTextIOInfo?.labelEnabled == true) {
            mTextIOLabel?.visibility = View.VISIBLE
            mTextIOLabelSeparator?.visibility = View.VISIBLE
            mTextIOLabel?.text = mTextIOInfo?.label
            mTextIOLabel?.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(TextIOInfo.LABEL_SIZE_LIMIT_IN_BYTES))
            mTextIOLabel?.textSize = mTextIOInfo?.labelSize?.toFloat() ?: 0f
            mTextIOLabel?.setTextColor(mTextIOInfo?.labelColor ?: 0)
            mTextIOLabel?.typeface = Typeface.create(mTextIOInfo?.labelTypeFaceFamily, mTextIOInfo?.labelTypeFaceStyle ?: Typeface.NORMAL)
        }

        if (mTextIOInfo?.textHorizontallyScrolling == true) {
            mTextIOHorizontalScrollView?.isEnabled = true
            mTextIOText?.setHorizontallyScrolling(true)
        } else {
            // Remove mTextIOHorizontalScrollView and add mTextIOText in its place
            val parent = mTextIOHorizontalScrollView?.parent as? ViewGroup
            if (parent != null && parent.indexOfChild(mTextIOText) < 0) {
                val params = mTextIOHorizontalScrollView?.layoutParams
                val index = parent.indexOfChild(mTextIOHorizontalScrollView)
                mTextIOTextLinearLayout?.removeAllViews()
                mTextIOHorizontalScrollView?.removeAllViews()
                parent.removeView(mTextIOHorizontalScrollView)
                parent.addView(mTextIOText, index, params)
                mTextIOText?.setHorizontallyScrolling(false)
            }
        }

        mTextIOText?.setText(mTextIOInfo?.text ?: "")
        mTextIOText?.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(mTextIOInfo?.textLengthLimit ?: 0))
        mTextIOText?.textSize = mTextIOInfo?.textSize?.toFloat() ?: 0f
        mTextIOText?.setTextColor(mTextIOInfo?.textColor ?: 0)
        mTextIOText?.typeface = Typeface.create(mTextIOInfo?.textTypeFaceFamily ?: "sans-serif", mTextIOInfo?.textTypeFaceStyle ?: Typeface.NORMAL)

        // setTextIsSelectable must be called after changing KeyListener to regain focusability and selectivity
        if (mTextIOInfo?.editingTextDisabled == true) {
            mTextIOText?.isCursorVisible = false
            mTextIOText?.keyListener = null
            mTextIOText?.setTextIsSelectable(true)
        }

        if (mTextIOInfo?.showTextCharacterUsage == true) {
            mTextIOTextCharacterUsage?.visibility = View.VISIBLE
            updateTextIOTextCharacterUsage(mTextIOInfo?.text)

            mTextIOText?.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(editable: android.text.Editable?) {
                    if (editable != null) {
                        updateTextIOTextCharacterUsage(editable.toString())
                    }
                }
            })
        } else {
            mTextIOTextCharacterUsage?.visibility = View.GONE
            mTextIOText?.addTextChangedListener(null)
        }
    }

    private fun updateTextIOInfoText() {
        val text = mTextIOText?.text?.toString()
        if (text != null) {
            mTextIOInfo?.text = text
        }
    }

    private fun updateTextIOTextCharacterUsage(text: String?) {
        val safeText = text ?: ""
        val limit = mTextIOInfo?.textLengthLimit ?: 0
        mTextIOTextCharacterUsage?.text = String.format(Locale.getDefault(), "%d/%d", safeText.length, limit)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        updateTextIOInfoText()
        outState.putSerializable(EXTRA_TEXT_IO_INFO_OBJECT, mTextIOInfo)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_text_io, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val text = mTextIOText?.text?.toString() ?: ""

        when (item.itemId) {
            android.R.id.home -> confirm()
            R.id.menu_item_cancel -> cancel()
            R.id.menu_item_share_text -> ShareUtils.shareText(this, mTextIOInfo?.title, text)
            R.id.menu_item_copy_text -> ShareUtils.copyTextToClipboard(this, text, null)
        }

        return false
    }

    override fun onBackPressed() {
        confirm()
    }

    /** Confirm current text and send it back to calling [Activity]. */
    private fun confirm() {
        updateTextIOInfoText()
        KeyboardUtils.hideSoftKeyboard(this, mTextIOText)
        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    /** Cancel current text and notify calling [Activity]. */
    private fun cancel() {
        KeyboardUtils.hideSoftKeyboard(this, mTextIOText)
        setResult(Activity.RESULT_CANCELED, resultIntent)
        finish()
    }

    private val resultIntent: Intent
        get() {
            val intent = Intent()
            val mBundle = Bundle()
            mBundle.putSerializable(EXTRA_TEXT_IO_INFO_OBJECT, mTextIOInfo)
            intent.putExtras(mBundle)
            return intent
        }
}
