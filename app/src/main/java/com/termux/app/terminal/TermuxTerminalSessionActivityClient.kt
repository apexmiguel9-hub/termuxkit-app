package com.termux.app.terminal
import java.util.*

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.SoundPool
import android.text.TextUtils
import android.widget.ListView
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.app.TermuxService
import com.termux.shared.interact.ShareUtils
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.interact.TextInputDialogUtils
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants
import com.termux.shared.termux.shell.command.runner.terminal.TermuxSession
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.shared.termux.terminal.io.BellHandler
import com.termux.terminal.TerminalColors
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient
import java.io.FileInputStream
import java.io.InputStream

/** The [TerminalSessionClient] implementation that may require an [Activity] for its interface methods. */
class TermuxTerminalSessionActivityClient(
    private val activity: TermuxActivity
) : TermuxTerminalSessionClientBase() {

    private var bellSoundPool: SoundPool? = null
    private var bellSoundId: Int = 0

    companion object {
        private const val MAX_SESSIONS = 8
        private const val LOG_TAG = "TermuxTerminalSessionActivityClient"
    }

    /**
     * Should be called when activity.onCreate() is called
     */
    fun onCreate() {
        // Set terminal fonts and colors
        checkForFontAndColors()
    }

    /**
     * Should be called when activity.onStart() is called
     */
    fun onStart() {
        // The service has connected, but data may have changed since we were last in the foreground.
        // Get the session stored in shared preferences stored by [onStop] if its valid,
        // otherwise get the last session currently running.
        activity.getTermuxService()?.let { service ->
            setCurrentSession(getCurrentStoredSessionOrLast())
            termuxSessionListNotifyUpdated()
        }

        // The current terminal session may have changed while being away, force
        // a refresh of the displayed terminal.
        activity.getTerminalView().onScreenUpdated()

        // Set background image or color. The display orientation may have changed
        // while being away, force a background update.
        activity.getmTermuxBackgroundManager().updateBackground(true)
    }

    /**
     * Should be called when activity.onResume() is called
     */
    fun onResume() {
        // Just initialize the bellSoundPool and load the sound, otherwise bell might not run
        // the first time bell key is pressed and play() is called, since sound may not be loaded
        // quickly enough before the call to play(). https://stackoverflow.com/questions/35435625
        loadBellSoundPool()
    }

    /**
     * Should be called when activity.onStop() is called
     */
    fun onStop() {
        // Store current session in shared preferences so that it can be restored later in
        // [onStart] if needed.
        setCurrentStoredSession()

        // Release bellSoundPool resources, specially to prevent exceptions like the following to be thrown
        // java.util.concurrent.TimeoutException: android.media.SoundPool.finalize() timed out after 10 seconds
        // Bell is not played in background anyways
        // Related: https://stackoverflow.com/a/28708351/14686958
        releaseBellSoundPool()
    }

    fun onConfigurationChanged(@NonNull newConfig: Configuration) {
        // Display orientation may have changed, force a background update.
        activity.getmTermuxBackgroundManager().updateBackground(true)
    }

    /**
     * Should be called when activity.reloadActivityStyling() is called
     */
    fun onReloadActivityStyling() {
        // Set terminal fonts and colors
        checkForFontAndColors()

        // Set background image or color
        activity.getmTermuxBackgroundManager().updateBackground(true)
    }

    override fun onTextChanged(@NonNull changedSession: TerminalSession) {
        if (!activity.isVisible) return

        if (activity.getCurrentSession() == changedSession) activity.getTerminalView().onScreenUpdated()
    }

    override fun onTitleChanged(@NonNull updatedSession: TerminalSession) {
        if (!activity.isVisible) return

        if (updatedSession != activity.getCurrentSession()) {
            // Only show toast for other sessions than the current one, since the user
            // probably consciously caused the title change to change in the current session
            // and don't want an annoying toast for that.
            activity.showToast(toToastTitle(updatedSession), true)
        }

        termuxSessionListNotifyUpdated()
    }

    override fun onSessionFinished(@NonNull finishedSession: TerminalSession) {
        val service = activity.getTermuxService()

        if (service == null || service.wantsToStop()) {
            // The service wants to stop as soon as possible.
            activity.finishActivityIfNotFinishing()
            return
        }

        val index = service.getIndexOfSession(finishedSession)

        // For plugin commands that expect the result back, we should immediately close the session
        // and send the result back instead of waiting fo the user to press enter.
        // The plugin can handle/show errors itself.
        var isPluginExecutionCommandWithPendingResult = false
        val termuxSession = service.getTermuxSession(index)
        if (termuxSession != null) {
            isPluginExecutionCommandWithPendingResult = termuxSession.executionCommand.isPluginExecutionCommandWithPendingResult()
            if (isPluginExecutionCommandWithPendingResult)
                Logger.logVerbose(LOG_TAG, "The \"${finishedSession.sessionName}\" session will be force finished automatically since result in pending.")
        }

        if (activity.isVisible && finishedSession != activity.getCurrentSession()) {
            // Show toast for non-current sessions that exit.
            // Verify that session was not removed before we got told about it finishing:
            if (index >= 0)
                activity.showToast("${toToastTitle(finishedSession)} - exited", true)
        }

        if (activity.packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)) {
            // On Android TV devices we need to use older behaviour because we may
            // not be able to have multiple launcher icons.
            if (service.termuxSessionsSize > 1 || isPluginExecutionCommandWithPendingResult) {
                removeFinishedSession(finishedSession)
            }
        } else {
            // Once we have a separate launcher icon for the failsafe session, it
            // should be safe to auto-close session on exit code '0' or '130'.
            if (finishedSession.exitStatus == 0 || finishedSession.exitStatus == 130 || isPluginExecutionCommandWithPendingResult) {
                removeFinishedSession(finishedSession)
            }
        }
    }

    override fun onCopyTextToClipboard(@NonNull session: TerminalSession, text: String) {
        if (!activity.isVisible) return

        ShareUtils.copyTextToClipboard(activity, text)
    }

    override fun onPasteTextFromClipboard(@Nullable session: TerminalSession?) {
        if (!activity.isVisible) return

        val text = ShareUtils.getTextStringFromClipboardIfSet(activity, true)
        if (text != null)
            activity.getTerminalView().emulator.paste(text)
    }

    override fun onBell(@NonNull session: TerminalSession) {
        if (!activity.isVisible) return

        when (activity.properties.bellBehaviour) {
            TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_VIBRATE ->
                BellHandler.getInstance(activity).doBell()
            TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_BEEP -> {
                loadBellSoundPool()
                bellSoundPool?.play(bellSoundId, 1f, 1f, 1, 0, 1f)
            }
            TermuxPropertyConstants.IVALUE_BELL_BEHAVIOUR_IGNORE -> {
                // Ignore the bell character.
            }
        }
    }

    override fun onColorsChanged(@NonNull changedSession: TerminalSession) {
        if (activity.getCurrentSession() == changedSession)
            // Background color may have changed. If the background is image and already set,
            // no need for update.
            activity.getmTermuxBackgroundManager().updateBackground(false)
    }

    override fun onTerminalCursorStateChange(enabled: Boolean) {
        // Do not start cursor blinking thread if activity is not visible
        if (enabled && !activity.isVisible) {
            Logger.logVerbose(LOG_TAG, "Ignoring call to start cursor blinking since activity is not visible")
            return
        }

        // If cursor is to enabled now, then start cursor blinking if blinking is enabled
        // otherwise stop cursor blinking
        activity.getTerminalView().setTerminalCursorBlinkerState(enabled, false)
    }

    override fun setTerminalShellPid(@NonNull terminalSession: TerminalSession, pid: Int) {
        val service = activity.getTermuxService() ?: return

        val termuxSession = service.getTermuxSessionForTerminalSession(terminalSession)
        termuxSession?.executionCommand?.pid = pid
    }

    /**
     * Should be called when activity.onResetTerminalSession() is called
     */
    fun onResetTerminalSession() {
        // Ensure blinker starts again after reset if cursor blinking was disabled before reset like
        // with "tput civis" which would have called onTerminalCursorStateChange()
        activity.getTerminalView().setTerminalCursorBlinkerState(true, true)
    }

    override fun getTerminalCursorStyle(): Int? {
        return activity.properties.terminalCursorStyle
    }

    /** Load bellSoundPool */
    @Synchronized
    private fun loadBellSoundPool() {
        if (bellSoundPool == null) {
            bellSoundPool = SoundPool.Builder().setMaxStreams(1).setAudioAttributes(
                AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION).build()
            ).build()

            try {
                bellSoundId = bellSoundPool?.load(activity, com.termux.shared.R.raw.bell, 1)
            } catch (e: Exception) {
                // Catch java.lang.RuntimeException: Unable to resume activity {com.termux/com.termux.app.TermuxActivity}: android.content.res.Resources$NotFoundException: File res/raw/bell.ogg from drawable resource ID
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load bell sound pool", e)
            }
        }
    }

    /** Release bellSoundPool resources */
    @Synchronized
    private fun releaseBellSoundPool() {
        bellSoundPool?.let {
            it.release()
            bellSoundPool = null
        }
    }

    /** Try switching to session. */
    fun setCurrentSession(session: TerminalSession?) {
        if (session == null) return

        if (activity.getTerminalView().attachSession(session)) {
            // notify about switched session if not already displaying the session
            notifyOfSessionChange()
        }

        // We call the following even when the session is already being displayed since config may
        // be stale, like current session not selected or scrolled to.
        checkAndScrollToSession(session)

        // Background color may have changed. If the background is image and already set,
        // no need for update.
        activity.getmTermuxBackgroundManager().updateBackground(false)
    }

    private fun notifyOfSessionChange() {
        if (!activity.isVisible) return

        if (!activity.properties.areTerminalSessionChangeToastsDisabled) {
            val session = activity.getCurrentSession()
            activity.showToast(toToastTitle(session), false)
        }
    }

    fun switchToSession(forward: Boolean) {
        val service = activity.getTermuxService() ?: return

        val currentTerminalSession = activity.getCurrentSession()
        var index = service.getIndexOfSession(currentTerminalSession)
        val size = service.termuxSessionsSize
        index = if (forward) {
            if (++index >= size) 0 else index
        } else {
            if (--index < 0) size - 1 else index
        }

        val termuxSession = service.getTermuxSession(index)
        termuxSession?.let { setCurrentSession(it.terminalSession) }
    }

    fun switchToSession(index: Int) {
        val service = activity.getTermuxService() ?: return

        val termuxSession = service.getTermuxSession(index)
        termuxSession?.let { setCurrentSession(it.terminalSession) }
    }

    @SuppressLint("InflateParams")
    fun renameSession(sessionToRename: TerminalSession?) {
        if (sessionToRename == null) return

        TextInputDialogUtils.textInput(
            activity,
            R.string.title_rename_session,
            sessionToRename.sessionName,
            R.string.action_rename_session_confirm,
            { text ->
                renameSession(sessionToRename, text)
                termuxSessionListNotifyUpdated()
            },
            -1, null, -1, null, null
        )
    }

    private fun renameSession(sessionToRename: TerminalSession?, text: String) {
        if (sessionToRename == null) return
        sessionToRename.sessionName = text
        val service = activity.getTermuxService()
        if (service != null) {
            val termuxSession = service.getTermuxSessionForTerminalSession(sessionToRename)
            termuxSession?.executionCommand?.shellName = text
        }
    }

    fun addNewSession(isFailSafe: Boolean, sessionName: String?) {
        val service = activity.getTermuxService() ?: return

        if (service.termuxSessionsSize >= MAX_SESSIONS) {
            AlertDialog.Builder(activity)
                .setTitle(R.string.title_max_terminals_reached)
                .setMessage(R.string.msg_max_terminals_reached)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        } else {
            val currentSession = activity.getCurrentSession()

            val workingDirectory = if (currentSession == null) {
                activity.properties.defaultWorkingDirectory
            } else {
                currentSession.cwd
            }

            val newTermuxSession = service.createTermuxSession(null, null, null, workingDirectory, isFailSafe, sessionName)
            if (newTermuxSession == null) return

            val newTerminalSession = newTermuxSession.terminalSession
            setCurrentSession(newTerminalSession)

            activity.getDrawer().closeDrawers()
        }
    }

    fun setCurrentStoredSession() {
        val currentSession = activity.getCurrentSession()
        activity.preferences.currentSession = currentSession?.handle
    }

    /** The current session as stored or the last one if that does not exist. */
    fun getCurrentStoredSessionOrLast(): TerminalSession? {
        val stored = getCurrentStoredSession()

        if (stored != null) {
            // If a stored session is in the list of currently running sessions, then return it
            return stored
        } else {
            // Else return the last session currently running
            val service = activity.getTermuxService() ?: return null

            val termuxSession = service.lastTermuxSession
            return termuxSession?.terminalSession
        }
    }

    private fun getCurrentStoredSession(): TerminalSession? {
        val sessionHandle = activity.preferences.currentSession

        // If no session is stored in shared preferences
        if (sessionHandle == null)
            return null

        // Check if the session handle found matches one of the currently running sessions
        val service = activity.getTermuxService() ?: return null

        return service.getTerminalSessionForHandle(sessionHandle)
    }

    fun removeFinishedSession(finishedSession: TerminalSession) {
        // Return pressed with finished session - remove it.
        val service = activity.getTermuxService() ?: return

        val index = service.removeTermuxSession(finishedSession)

        val size = service.termuxSessionsSize
        if (size == 0) {
            // There are no sessions to show, so finish the activity.
            activity.finishActivityIfNotFinishing()
        } else {
            var idx = index
            if (idx >= size) {
                idx = size - 1
            }
            val termuxSession = service.getTermuxSession(idx)
            termuxSession?.let { setCurrentSession(it.terminalSession) }
        }
    }

    fun termuxSessionListNotifyUpdated() {
        activity.termuxSessionListNotifyUpdated()
    }

    fun checkAndScrollToSession(session: TerminalSession?) {
        if (!activity.isVisible) return
        val service = activity.getTermuxService() ?: return

        val indexOfSession = service.getIndexOfSession(session)
        if (indexOfSession < 0) return
        val termuxSessionsListView: ListView? = activity.findViewById(R.id.terminal_sessions_list)
        if (termuxSessionsListView == null) return

        termuxSessionsListView.setItemChecked(indexOfSession, true)
        // Delay is necessary otherwise sometimes scroll to newly added session does not happen
        termuxSessionsListView.postDelayed({ termuxSessionsListView.smoothScrollToPosition(indexOfSession) }, 1000)
    }

    private fun toToastTitle(session: TerminalSession?): String? {
        val service = activity.getTermuxService() ?: return null

        val indexOfSession = service.getIndexOfSession(session)
        if (indexOfSession < 0) return null
        val toastTitle = StringBuilder("[$indexOfSession]")
        if (!TextUtils.isEmpty(session?.sessionName)) {
            toastTitle.append(" ").append(session.sessionName)
        }
        val title = session?.title
        if (!TextUtils.isEmpty(title)) {
            // Space to "[${NR}] or newline after session name:
            toastTitle.append(if (session.sessionName == null) " " else "\n")
            toastTitle.append(title)
        }
        return toastTitle.toString()
    }

    fun checkForFontAndColors() {
        try {
            val colorsFile = TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE
            val fontFile = TermuxConstants.TERMUX_FONT_FILE

            val props = Properties()
            if (colorsFile.isFile) {
                FileInputStream(colorsFile).use { inStream ->
                    props.load(inStream)
                }
            }

            TerminalColors.COLOR_SCHEME.updateWith(props)
            val session = activity.getCurrentSession()
            session?.emulator?.colors?.reset()

            val newTypeface = if (fontFile.exists() && fontFile.length() > 0) {
                Typeface.createFromFile(fontFile)
            } else {
                Typeface.MONOSPACE
            }
            activity.getTerminalView().typeface = newTypeface
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Error in checkForFontAndColors()", e)
        }
    }
}
