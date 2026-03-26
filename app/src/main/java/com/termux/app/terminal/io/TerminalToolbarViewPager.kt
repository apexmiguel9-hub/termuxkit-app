package com.termux.app.terminal.io

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText

import androidx.annotation.NonNull
import androidx.viewpager.widget.PagerAdapter
import androidx.viewpager.widget.ViewPager

import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.shared.termux.extrakeys.ExtraKeysView
import com.termux.terminal.TerminalSession

class TerminalToolbarViewPager {

    class PageAdapter(
        private val activity: TermuxActivity,
        private var savedTextInput: String?
    ) : PagerAdapter() {

        override fun getCount(): Int = 2

        override fun isViewFromObject(view: View, `object`: Any): Boolean = view == `object`

        @NonNull
        override fun instantiateItem(@NonNull collection: ViewGroup, position: Int): Any {
            val inflater = LayoutInflater.from(activity)
            val layout: View
            if (position == 0) {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_extra_keys, collection, false)
                val extraKeysView = layout as ExtraKeysView
                extraKeysView.setExtraKeysViewClient(activity.getTermuxTerminalExtraKeys())
                extraKeysView.setButtonTextAllCaps(activity.getProperties().shouldExtraKeysTextBeAllCaps())
                activity.setExtraKeysView(extraKeysView)
                extraKeysView.reload(activity.getTermuxTerminalExtraKeys().getExtraKeysInfo(),
                    activity.getTerminalToolbarDefaultHeight())

                // apply extra keys fix if enabled in prefs
                if (activity.getProperties().isUsingFullScreen() && activity.getProperties().isUsingFullScreenWorkAround()) {
                    FullScreenWorkAround.apply(activity)
                }

                // Update toolbar background corresponding to prefs
                activity.getmTermuxBackgroundManager().updateToolbarBackground()

            } else {
                layout = inflater.inflate(R.layout.view_terminal_toolbar_text_input, collection, false)
                val editText = layout.findViewById<EditText>(R.id.terminal_toolbar_text_input)

                savedTextInput?.let {
                    editText.setText(it)
                    savedTextInput = null
                }

                editText.setOnEditorActionListener { v, actionId, event ->
                    val session = activity.getCurrentSession()
                    if (session != null) {
                        if (session.isRunning) {
                            val textToSend = editText.text.toString()
                            if (textToSend.isEmpty()) {
                                session.write("\r")
                            } else {
                                session.write(textToSend)
                            }
                        } else {
                            activity.getTermuxTerminalSessionClient().removeFinishedSession(session)
                        }
                        editText.setText("")
                    }
                    true
                }
            }
            collection.addView(layout)
            return layout
        }

        override fun destroyItem(@NonNull collection: ViewGroup, position: Int, @NonNull view: Any) {
            collection.removeView(view as View)
        }
    }

    class OnPageChangeListener(
        private val activity: TermuxActivity,
        private val terminalToolbarViewPager: ViewPager
    ) : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            if (position == 0) {
                activity.getTerminalView().requestFocus()
            } else {
                val editText = terminalToolbarViewPager.findViewById<EditText>(R.id.terminal_toolbar_text_input)
                editText?.requestFocus()
            }
        }
    }
}
