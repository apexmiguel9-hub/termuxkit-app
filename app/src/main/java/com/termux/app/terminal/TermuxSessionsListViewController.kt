package com.termux.app.terminal

import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.TextUtils
import android.text.style.StyleSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.TextView

import androidx.annotation.NonNull
import androidx.core.content.ContextCompat

import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.shared.theme.NightMode
import com.termux.shared.theme.ThemeUtils
import com.termux.terminal.TerminalSession

import java.util.List

open class TermuxSessionsListViewController(
    activity: TermuxActivity,
    sessionList: List<TermuxSession>
) : ArrayAdapter<TermuxSession>(activity, R.layout.item_terminal_sessions_list, sessionList),
    AdapterView.OnItemClickListener,
    AdapterView.OnItemLongClickListener {

    val activity: TermuxActivity = activity
    val boldSpan = StyleSpan(Typeface.BOLD)
    val italicSpan = StyleSpan(Typeface.ITALIC)

    @SuppressLint("SetTextI18n")
    @NonNull
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val sessionRowView = convertView ?: activity.layoutInflater.inflate(
            R.layout.item_terminal_sessions_list,
            parent,
            false
        )

        val sessionTitleView = sessionRowView.findViewById<TextView>(R.id.session_title)

        val termuxSession = getItem(position)
        val sessionAtRow = termuxSession?.terminalSession
        if (sessionAtRow == null) {
            sessionTitleView.text = "null session"
            return sessionRowView
        }

        val shouldEnableDarkTheme = ThemeUtils.shouldEnableDarkTheme(
            activity,
            NightMode.getAppNightMode().getName()
        )

        if (shouldEnableDarkTheme) {
            sessionTitleView.background = ContextCompat.getDrawable(
                activity,
                R.drawable.session_background_black_selected
            )
        }

        val name = sessionAtRow.mSessionName
        val sessionTitle = sessionAtRow.getTitle()

        val numberPart = "[${position + 1}] "
        val sessionNamePart = if (TextUtils.isEmpty(name)) "" else name
        val sessionTitlePart = if (TextUtils.isEmpty(sessionTitle)) "" else if (sessionNamePart.isEmpty()) "" else "\n$sessionTitle"

        val fullSessionTitle = numberPart + sessionNamePart + sessionTitlePart
        val fullSessionTitleStyled = SpannableString(fullSessionTitle)
        fullSessionTitleStyled.setSpan(
            boldSpan,
            0,
            numberPart.length + sessionNamePart.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        fullSessionTitleStyled.setSpan(
            italicSpan,
            numberPart.length + sessionNamePart.length,
            fullSessionTitle.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        sessionTitleView.text = fullSessionTitleStyled

        val sessionRunning = sessionAtRow.isRunning()

        if (sessionRunning) {
            sessionTitleView.paintFlags = sessionTitleView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
        } else {
            sessionTitleView.paintFlags = sessionTitleView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
        }

        val defaultColor = if (shouldEnableDarkTheme) Color.WHITE else Color.BLACK
        val color = if (sessionRunning || sessionAtRow.getExitStatus() == 0) defaultColor else Color.RED
        sessionTitleView.setTextColor(color)

        return sessionRowView
    }

    override fun onItemClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
        val clickedSession = getItem(position)
        activity.termuxTerminalSessionClient.currentSession = clickedSession?.terminalSession
        activity.drawer.closeDrawers()
    }

    override fun onItemLongClick(parent: AdapterView<*>?, view: View?, position: Int, id: Long): Boolean {
        val selectedSession = getItem(position)
        activity.termuxTerminalSessionClient.renameSession(selectedSession?.terminalSession)
        return true
    }
}
