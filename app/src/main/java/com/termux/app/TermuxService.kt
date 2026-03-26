package com.termux.app

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.wifi.WifiManager
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.termux.R
import com.termux.app.event.SystemEventReceiver
import com.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.termux.shared.android.PermissionUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.data.IntentUtils
import com.termux.shared.errors.Errno
import com.termux.shared.logger.Logger
import com.termux.shared.net.uri.UriUtils
import com.termux.shared.notification.NotificationUtils
import com.termux.shared.shell.ShellUtils
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.ExecutionCommand.Runner
import com.termux.shared.shell.command.ExecutionCommand.ShellCreateMode
import com.termux.shared.shell.command.runner.app.AppShell
import com.termux.shared.shell.command.runner.terminal.TermuxSession
import com.termux.shared.shell.command.environment.TermuxShellEnvironment
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import com.termux.shared.termux.plugins.TermuxPluginUtils
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties
import com.termux.shared.termux.shell.TermuxShellManager
import com.termux.shared.termux.shell.TermuxShellUtils
import com.termux.shared.termux.shell.command.environment.TermuxShellEnvironment
import com.termux.shared.termux.terminal.TermuxTerminalSessionClientBase
import com.termux.terminal.TerminalEmulator
import com.termux.terminal.TerminalSession
import java.util.ArrayList

/**
 * A service holding a list of [TermuxSession] in [TermuxShellManager.mTermuxSessions] and background [AppShell]
 * in [TermuxShellManager.mTermuxTasks], showing a foreground notification while running so that it is not terminated.
 * The user interacts with the session through [TermuxActivity], but this service may outlive
 * the activity when the user or the system disposes of the activity. In that case the user may
 * restart [TermuxActivity] later to yet again access the sessions.
 *
 * In order to keep both terminal sessions and spawned processes (who may outlive the terminal sessions) alive as long
 * as wanted by the user this service is a foreground service, [Service.startForeground].
 *
 * Optionally may hold a wake and a wifi lock, in which case that is shown in the notification - see
 * [buildNotification].
 */
class TermuxService : Service(), AppShell.AppShellClient, TermuxSession.TermuxSessionClient {

    /** This service is only bound from inside the same process and never uses IPC. */
    inner class LocalBinder : Binder() {
        val service: TermuxService = this@TermuxService
    }

    private val mBinder: IBinder = LocalBinder()

    private val handler = Handler()

    /**
     * The full implementation of the [TerminalSessionClient] interface to be used by [TerminalSession]
     * that holds activity references for activity related functions.
     * Note that the service may often outlive the activity, so need to clear this reference.
     */
    private var termuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient? = null

    /**
     * The basic implementation of the [TerminalSessionClient] interface to be used by [TerminalSession]
     * that does not hold activity references and only a service reference.
     */
    private val mTermuxTerminalSessionServiceClient = TermuxTerminalSessionServiceClient(this)

    /**
     * Termux app shared properties manager, loaded from termux.properties
     */
    private lateinit var properties: TermuxAppSharedProperties

    /**
     * Termux app shell manager
     */
    private lateinit var shellManager: TermuxShellManager

    /** The wake lock and wifi lock are always acquired and released together. */
    private var wakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    /** If the user has executed the [TERMUX_SERVICE.ACTION_STOP_SERVICE] intent. */
    var wantsToStop = false
        private set

    companion object {
        private const val LOG_TAG = "TermuxService"
    }

    override fun onCreate() {
        Logger.logVerbose(LOG_TAG, "onCreate")

        // Get Termux app SharedProperties without loading from disk since TermuxApplication handles
        // load and TermuxActivity handles reloads
        properties = TermuxAppSharedProperties.getProperties()

        shellManager = TermuxShellManager.getShellManager()

        runStartForeground()

        SystemEventReceiver.registerPackageUpdateEvents(this)
    }

    @SuppressLint("Wakelock")
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Logger.logDebug(LOG_TAG, "onStartCommand")

        // Run again in case service is already started and onCreate() is not called
        runStartForeground()

        val action = intent?.action

        if (action != null) {
            Logger.logVerboseExtended(LOG_TAG, "Intent Received:\n${IntentUtils.getIntentString(intent)}")
            when (action) {
                TERMUX_SERVICE.ACTION_STOP_SERVICE -> {
                    Logger.logDebug(LOG_TAG, "ACTION_STOP_SERVICE intent received")
                    actionStopService()
                }
                TERMUX_SERVICE.ACTION_WAKE_LOCK -> {
                    Logger.logDebug(LOG_TAG, "ACTION_WAKE_LOCK intent received")
                    actionAcquireWakeLock()
                }
                TERMUX_SERVICE.ACTION_WAKE_UNLOCK -> {
                    Logger.logDebug(LOG_TAG, "ACTION_WAKE_UNLOCK intent received")
                    actionReleaseWakeLock(true)
                }
                TERMUX_SERVICE.ACTION_SERVICE_EXECUTE -> {
                    Logger.logDebug(LOG_TAG, "ACTION_SERVICE_EXECUTE intent received")
                    actionServiceExecute(intent)
                }
                else -> {
                    Logger.logError(LOG_TAG, "Invalid action: \"$action\"")
                }
            }
        }

        // If this service really do get killed, there is no point restarting it automatically - let the user do on next
        // start of [TermuxActivity]:
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Logger.logVerbose(LOG_TAG, "onDestroy")

        TermuxShellUtils.clearTermuxTMPDIR(true)

        actionReleaseWakeLock(false)
        if (!wantsToStop)
            killAllTermuxExecutionCommands()

        TermuxShellManager.onAppExit(this)

        SystemEventReceiver.unregisterPackageUpdateEvents(this)

        runStopForeground()
    }

    override fun onBind(intent: Intent?): IBinder {
        Logger.logVerbose(LOG_TAG, "onBind")
        return mBinder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Logger.logVerbose(LOG_TAG, "onUnbind")

        // Since we cannot rely on [TermuxActivity.onDestroy()] to always complete,
        // we unset clients here as well if it failed, so that we do not leave service and session
        // clients with references to the activity.
        termuxTerminalSessionActivityClient?.let {
            unsetTermuxTerminalSessionClient()
        }
        return false
    }

    /** Make service run in foreground mode. */
    private fun runStartForeground() {
        setupNotificationChannel()
        startForeground(TermuxConstants.TERMUX_APP_NOTIFICATION_ID, buildNotification())
    }

    /** Make service leave foreground mode. */
    private fun runStopForeground() {
        stopForeground(true)
    }

    /** Request to stop service. */
    private fun requestStopService() {
        Logger.logDebug(LOG_TAG, "Requesting to stop service")
        runStopForeground()
        stopSelf()
    }

    /** Process action to stop service. */
    private fun actionStopService() {
        wantsToStop = true
        killAllTermuxExecutionCommands()
        requestStopService()
    }

    /**
     * Kill all TermuxSessions and TermuxTasks by sending SIGKILL to their processes.
     *
     * For TermuxSessions, all sessions will be killed, whether user manually exited Termux or if
     * onDestroy() was directly called because of unintended shutdown. The processing of results
     * will only be done if user manually exited termux or if the session was started by a plugin
     * which **expects** the result back via a pending intent.
     *
     * For TermuxTasks, only tasks that were started by a plugin which **expects** the result
     * back via a pending intent will be killed, whether user manually exited Termux or if
     * onDestroy() was directly called because of unintended shutdown. The processing of results
     * will always be done for the tasks that are killed. The remaining processes will keep on
     * running until the termux app process is killed by android, like by OOM, so we let them run
     * as long as they can.
     *
     * Some plugin execution commands may not have been processed and added to mTermuxSessions and
     * mTermuxTasks lists before the service is killed, so we maintain a separate
     * mPendingPluginExecutionCommands list for those, so that we can notify the pending intent
     * creators that execution was cancelled.
     *
     * Note that if user didn't manually exit Termux and if onDestroy() was directly called because
     * of unintended shutdown, like android deciding to kill the service, then there will be no
     * guarantee that onDestroy() will be allowed to finish and termux app process may be killed before
     * it has finished. This means that in those cases some results may not be sent back to their
     * creators for plugin commands but we still try to process whatever results can be processed
     * despite the unreliable behaviour of onDestroy().
     *
     * Note that if don't kill the processes started by plugins which **expect** the result back
     * and notify their creators that they have been killed, then they may get stuck waiting for
     * the results forever like in case of commands started by Termux:Tasker or RUN_COMMAND intent,
     * since once TermuxService has been killed, no result will be sent back. They may still get
     * stuck if termux app process gets killed, so for this case reasonable timeout values should
     * be used, like in Tasker for the Termux:Tasker actions.
     *
     * We make copies of each list since items are removed inside the loop.
     */
    @Synchronized
    private fun killAllTermuxExecutionCommands() {
        Logger.logDebug(LOG_TAG, "Killing TermuxSessions=${shellManager.mTermuxSessions.size}" +
            ", TermuxTasks=${shellManager.mTermuxTasks.size}" +
            ", PendingPluginExecutionCommands=${shellManager.mPendingPluginExecutionCommands.size}")

        val termuxSessions = ArrayList(shellManager.mTermuxSessions)
        val termuxTasks = ArrayList(shellManager.mTermuxTasks)
        val pendingPluginExecutionCommands = ArrayList(shellManager.mPendingPluginExecutionCommands)

        for (i in termuxSessions.indices) {
            val executionCommand = termuxSessions[i].executionCommand
            val processResult = wantsToStop || executionCommand.isPluginExecutionCommandWithPendingResult()
            termuxSessions[i].killIfExecuting(this, processResult)
            if (!processResult)
                shellManager.mTermuxSessions.remove(termuxSessions[i])
        }

        for (i in termuxTasks.indices) {
            val executionCommand = termuxTasks[i].executionCommand
            if (executionCommand.isPluginExecutionCommandWithPendingResult())
                termuxTasks[i].killIfExecuting(this, true)
            else
                shellManager.mTermuxTasks.remove(termuxTasks[i])
        }

        for (i in pendingPluginExecutionCommands.indices) {
            val executionCommand = pendingPluginExecutionCommands[i]
            if (!executionCommand.shouldNotProcessResults() && executionCommand.isPluginExecutionCommandWithPendingResult()) {
                if (executionCommand.setStateFailed(Errno.ERRNO_CANCELLED.code, getString(com.termux.shared.R.string.error_execution_cancelled))) {
                    TermuxPluginUtils.processPluginExecutionCommandResult(this, LOG_TAG, executionCommand)
                }
            }
        }
    }

    /** Process action to acquire Power and Wi-Fi WakeLocks. */
    @SuppressLint({"WakelockTimeout", "BatteryLife"})
    private fun actionAcquireWakeLock() {
        if (wakeLock != null) {
            Logger.logDebug(LOG_TAG, "Ignoring acquiring WakeLocks since they are already held")
            return
        }

        Logger.logDebug(LOG_TAG, "Acquiring WakeLocks")

        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "${TermuxConstants.TERMUX_APP_NAME.lowercase()}:service-wakelock")
        wakeLock.acquire()

        // http://tools.android.com/tech-docs/lint-in-studio-2-3#TOC-WifiManager-Leak
        val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        wifiLock = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, TermuxConstants.TERMUX_APP_NAME.lowercase())
        wifiLock.acquire()

        if (!PermissionUtils.checkIfBatteryOptimizationsDisabled(this)) {
            PermissionUtils.requestDisableBatteryOptimizations(this)
        }

        updateNotification()

        Logger.logDebug(LOG_TAG, "WakeLocks acquired successfully")
    }

    /** Process action to release Power and Wi-Fi WakeLocks. */
    private fun actionReleaseWakeLock(updateNotification: Boolean) {
        if (wakeLock == null && wifiLock == null) {
            Logger.logDebug(LOG_TAG, "Ignoring releasing WakeLocks since none are already held")
            return
        }

        Logger.logDebug(LOG_TAG, "Releasing WakeLocks")

        wakeLock?.let {
            it.release()
            wakeLock = null
        }

        wifiLock?.let {
            it.release()
            wifiLock = null
        }

        if (updateNotification)
            updateNotification()

        Logger.logDebug(LOG_TAG, "WakeLocks released successfully")
    }

    /** Process [TERMUX_SERVICE.ACTION_SERVICE_EXECUTE] intent to execute a shell command in
     * a foreground TermuxSession or in a background TermuxTask. */
    private fun actionServiceExecute(intent: Intent) {
        val executionCommand = ExecutionCommand(TermuxShellManager.getNextShellId()).apply {
            executableUri = intent.data
            isPluginExecutionCommand = true

            // If EXTRA_RUNNER is passed, use that, otherwise check EXTRA_BACKGROUND and default to Runner.TERMINAL_SESSION
            runner = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RUNNER,
                if (intent.getBooleanExtra(TERMUX_SERVICE.EXTRA_BACKGROUND, false)) Runner.APP_SHELL.name else Runner.TERMINAL_SESSION.name)
            if (Runner.runnerOf(runner) == null) {
                val errmsg = getString(R.string.error_termux_service_invalid_execution_command_runner, runner)
                setStateFailed(Errno.ERRNO_FAILED.code, errmsg)
                TermuxPluginUtils.processPluginExecutionCommandError(this@TermuxService, LOG_TAG, this, false)
                return
            }

            if (executableUri != null) {
                Logger.logVerbose(LOG_TAG, "uri: \"$executableUri\", path: \"${executableUri.path}\", fragment: \"${executableUri.fragment}\"")

                // Get full path including fragment (anything after last "#")
                executable = UriUtils.getUriFilePathWithFragment(executableUri)
                arguments = IntentUtils.getStringArrayExtraIfSet(intent, TERMUX_SERVICE.EXTRA_ARGUMENTS, null)
                if (Runner.APP_SHELL.equalsRunner(runner))
                    stdin = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_STDIN, null)
                backgroundCustomLogLevel = IntentUtils.getIntegerExtraIfSet(intent, TERMUX_SERVICE.EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL, null)
            }

            workingDirectory = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_WORKDIR, null)
            isFailsafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false)
            sessionAction = intent.getStringExtra(TERMUX_SERVICE.EXTRA_SESSION_ACTION)
            shellName = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_SHELL_NAME, null)
            shellCreateMode = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_SHELL_CREATE_MODE, null)
            commandLabel = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_COMMAND_LABEL, "Execution Intent Command")
            commandDescription = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_COMMAND_DESCRIPTION, null)
            commandHelp = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_COMMAND_HELP, null)
            pluginAPIHelp = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_PLUGIN_API_HELP, null)
            resultConfig.resultPendingIntent = intent.getParcelableExtra(TERMUX_SERVICE.EXTRA_PENDING_INTENT)
            resultConfig.resultDirectoryPath = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_DIRECTORY, null)
            if (resultConfig.resultDirectoryPath != null) {
                resultConfig.resultSingleFile = intent.getBooleanExtra(TERMUX_SERVICE.EXTRA_RESULT_SINGLE_FILE, false)
                resultConfig.resultFileBasename = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_FILE_BASENAME, null)
                resultConfig.resultFileOutputFormat = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_FILE_OUTPUT_FORMAT, null)
                resultConfig.resultFileErrorFormat = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_FILE_ERROR_FORMAT, null)
                resultConfig.resultFilesSuffix = IntentUtils.getStringExtraIfSet(intent, TERMUX_SERVICE.EXTRA_RESULT_FILES_SUFFIX, null)
            }

            if (shellCreateMode == null)
                shellCreateMode = ShellCreateMode.ALWAYS.mode

            // Add the execution command to pending plugin execution commands list
            shellManager.mPendingPluginExecutionCommands.add(this)
        }

        when {
            Runner.APP_SHELL.equalsRunner(executionCommand.runner) -> executeTermuxTaskCommand(executionCommand)
            Runner.TERMINAL_SESSION.equalsRunner(executionCommand.runner) -> executeTermuxSessionCommand(executionCommand)
            else -> {
                val errmsg = getString(R.string.error_termux_service_unsupported_execution_command_runner, executionCommand.runner)
                executionCommand.setStateFailed(Errno.ERRNO_FAILED.code, errmsg)
                TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false)
            }
        }
    }

    /** Execute a shell command in background TermuxTask. */
    private fun executeTermuxTaskCommand(executionCommand: ExecutionCommand?) {
        if (executionCommand == null) return

        Logger.logDebug(LOG_TAG, "Executing background \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxTask command")

        // Transform executable path to shell/session name, e.g. "/bin/do-something.sh" => "do-something.sh".
        if (executionCommand.shellName == null && executionCommand.executable != null)
            executionCommand.shellName = ShellUtils.getExecutableBasename(executionCommand.executable)

        var newTermuxTask: AppShell? = null
        val shellCreateMode = processShellCreateMode(executionCommand) ?: return
        if (ShellCreateMode.NO_SHELL_WITH_NAME.equals(shellCreateMode)) {
            newTermuxTask = getTermuxTaskForShellName(executionCommand.shellName)
            if (newTermuxTask != null)
                Logger.logVerbose(LOG_TAG, "Existing TermuxTask with \"${executionCommand.shellName}\" shell name found for shell create mode \"${shellCreateMode.mode}\"")
            else
                Logger.logVerbose(LOG_TAG, "No existing TermuxTask with \"${executionCommand.shellName}\" shell name found for shell create mode \"${shellCreateMode.mode}\"")
        }

        if (newTermuxTask == null)
            newTermuxTask = createTermuxTask(executionCommand)
    }

    /** Create a TermuxTask. */
    @Nullable
    fun createTermuxTask(executablePath: String?, arguments: Array<String>?, stdin: String?, workingDirectory: String?): AppShell? {
        return createTermuxTask(ExecutionCommand(TermuxShellManager.getNextShellId(), executablePath,
            arguments, stdin, workingDirectory, Runner.APP_SHELL.name, false))
    }

    /** Create a TermuxTask. */
    @Nullable
    @Synchronized
    fun createTermuxTask(executionCommand: ExecutionCommand?): AppShell? {
        if (executionCommand == null) return null

        Logger.logDebug(LOG_TAG, "Creating \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxTask")

        if (!Runner.APP_SHELL.equalsRunner(executionCommand.runner)) {
            Logger.logDebug(LOG_TAG, "Ignoring wrong runner \"${executionCommand.runner}\" command passed to createTermuxTask()")
            return null
        }

        executionCommand.setShellCommandShellEnvironment = true

        if (Logger.getLogLevel() >= Logger.LOG_LEVEL_VERBOSE)
            Logger.logVerboseExtended(LOG_TAG, executionCommand.toString())

        val newTermuxTask = AppShell.execute(this, executionCommand, this,
            TermuxShellEnvironment(), null, false)
        if (newTermuxTask == null) {
            Logger.logError(LOG_TAG, "Failed to execute new TermuxTask command for:\n${executionCommand.getCommandIdAndLabelLogString()}")
            // If the execution command was started for a plugin, then process the error
            if (executionCommand.isPluginExecutionCommand)
                TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false)
            else {
                Logger.logError(LOG_TAG, "Set log level to debug or higher to see error in logs")
                Logger.logErrorPrivateExtended(LOG_TAG, executionCommand.toString())
            }
            return null
        }

        shellManager.mTermuxTasks.add(newTermuxTask)

        // Remove the execution command from the pending plugin execution commands list since it has
        // now been processed
        if (executionCommand.isPluginExecutionCommand)
            shellManager.mPendingPluginExecutionCommands.remove(executionCommand)

        updateNotification()

        return newTermuxTask
    }

    /** Callback received when a TermuxTask finishes. */
    override fun onAppShellExited(termuxTask: AppShell?) {
        handler.post {
            if (termuxTask != null) {
                val executionCommand = termuxTask.executionCommand

                Logger.logVerbose(LOG_TAG, "The onTermuxTaskExited() callback called for \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxTask command")

                // If the execution command was started for a plugin, then process the results
                if (executionCommand != null && executionCommand.isPluginExecutionCommand)
                    TermuxPluginUtils.processPluginExecutionCommandResult(this, LOG_TAG, executionCommand)

                shellManager.mTermuxTasks.remove(termuxTask)
            }

            updateNotification()
        }
    }

    /** Execute a shell command in a foreground [TermuxSession]. */
    private fun executeTermuxSessionCommand(executionCommand: ExecutionCommand?) {
        if (executionCommand == null) return

        Logger.logDebug(LOG_TAG, "Executing foreground \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxSession command")

        // Transform executable path to shell/session name, e.g. "/bin/do-something.sh" => "do-something.sh".
        if (executionCommand.shellName == null && executionCommand.executable != null)
            executionCommand.shellName = ShellUtils.getExecutableBasename(executionCommand.executable)

        var newTermuxSession: TermuxSession? = null
        val shellCreateMode = processShellCreateMode(executionCommand) ?: return
        if (ShellCreateMode.NO_SHELL_WITH_NAME.equals(shellCreateMode)) {
            newTermuxSession = getTermuxSessionForShellName(executionCommand.shellName)
            if (newTermuxSession != null)
                Logger.logVerbose(LOG_TAG, "Existing TermuxSession with \"${executionCommand.shellName}\" shell name found for shell create mode \"${shellCreateMode.mode}\"")
            else
                Logger.logVerbose(LOG_TAG, "No existing TermuxSession with \"${executionCommand.shellName}\" shell name found for shell create mode \"${shellCreateMode.mode}\"")
        }

        if (newTermuxSession == null)
            newTermuxSession = createTermuxSession(executionCommand)
        if (newTermuxSession == null) return

        handleSessionAction(DataUtils.getIntFromString(executionCommand.sessionAction,
            TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY),
            newTermuxSession.terminalSession)
    }

    /**
     * Create a [TermuxSession].
     * Currently called by [TermuxTerminalSessionActivityClient.addNewSession] to add a new [TermuxSession].
     */
    @Nullable
    fun createTermuxSession(executablePath: String?, arguments: Array<String>?, stdin: String?,
                            workingDirectory: String?, isFailSafe: Boolean, sessionName: String?): TermuxSession? {
        val executionCommand = ExecutionCommand(TermuxShellManager.getNextShellId(),
            executablePath, arguments, stdin, workingDirectory, Runner.TERMINAL_SESSION.name, isFailSafe).apply {
            this.shellName = sessionName
        }
        return createTermuxSession(executionCommand)
    }

    /** Create a [TermuxSession]. */
    @Nullable
    @Synchronized
    fun createTermuxSession(executionCommand: ExecutionCommand?): TermuxSession? {
        if (executionCommand == null) return null

        Logger.logDebug(LOG_TAG, "Creating \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxSession")

        if (!Runner.TERMINAL_SESSION.equalsRunner(executionCommand.runner)) {
            Logger.logDebug(LOG_TAG, "Ignoring wrong runner \"${executionCommand.runner}\" command passed to createTermuxSession()")
            return null
        }

        executionCommand.setShellCommandShellEnvironment = true
        executionCommand.terminalTranscriptRows = properties.terminalTranscriptRows

        if (Logger.getLogLevel() >= Logger.LOG_LEVEL_VERBOSE)
            Logger.logVerboseExtended(LOG_TAG, executionCommand.toString())

        // If the execution command was started for a plugin, only then will the stdout be set
        // Otherwise if command was manually started by the user like by adding a new terminal session,
        // then no need to set stdout
        val newTermuxSession = TermuxSession.execute(this, executionCommand, termuxTerminalSessionClient,
            this, TermuxShellEnvironment(), null, executionCommand.isPluginExecutionCommand)
        if (newTermuxSession == null) {
            Logger.logError(LOG_TAG, "Failed to execute new TermuxSession command for:\n${executionCommand.getCommandIdAndLabelLogString()}")
            // If the execution command was started for a plugin, then process the error
            if (executionCommand.isPluginExecutionCommand)
                TermuxPluginUtils.processPluginExecutionCommandError(this, LOG_TAG, executionCommand, false)
            else {
                Logger.logError(LOG_TAG, "Set log level to debug or higher to see error in logs")
                Logger.logErrorPrivateExtended(LOG_TAG, executionCommand.toString())
            }
            return null
        }

        shellManager.mTermuxSessions.add(newTermuxSession)

        // Remove the execution command from the pending plugin execution commands list since it has
        // now been processed
        if (executionCommand.isPluginExecutionCommand)
            shellManager.mPendingPluginExecutionCommands.remove(executionCommand)

        // Notify [TermuxSessionsListViewController] that sessions list has been updated if
        // activity in is foreground
        termuxTerminalSessionActivityClient?.termuxSessionListNotifyUpdated()

        updateNotification()

        // No need to recreate the activity since it likely just started and theme should already have applied
        TermuxActivity.updateTermuxActivityStyling(this, false)

        return newTermuxSession
    }

    /** Remove a TermuxSession. */
    @Synchronized
    fun removeTermuxSession(sessionToRemove: TerminalSession): Int {
        val index = getIndexOfSession(sessionToRemove)

        if (index >= 0)
            shellManager.mTermuxSessions[index].finish()

        return index
    }

    /** Callback received when a [TermuxSession] finishes. */
    override fun onTermuxSessionExited(termuxSession: TermuxSession?) {
        if (termuxSession != null) {
            val executionCommand = termuxSession.executionCommand

            Logger.logVerbose(LOG_TAG, "The onTermuxSessionExited() callback called for \"${executionCommand.getCommandIdAndLabelLogString()}\" TermuxSession command")

            // If the execution command was started for a plugin, then process the results
            if (executionCommand != null && executionCommand.isPluginExecutionCommand)
                TermuxPluginUtils.processPluginExecutionCommandResult(this, LOG_TAG, executionCommand)

            shellManager.mTermuxSessions.remove(termuxSession)

            // Notify [TermuxSessionsListViewController] that sessions list has been updated if
            // activity in is foreground
            termuxTerminalSessionActivityClient?.termuxSessionListNotifyUpdated()
        }

        updateNotification()
    }

    private fun processShellCreateMode(@NonNull executionCommand: ExecutionCommand): ShellCreateMode? {
        return when {
            ShellCreateMode.ALWAYS.equalsMode(executionCommand.shellCreateMode) -> ShellCreateMode.ALWAYS // Default
            ShellCreateMode.NO_SHELL_WITH_NAME.equalsMode(executionCommand.shellCreateMode) -> {
                if (DataUtils.isNullOrEmpty(executionCommand.shellName)) {
                    TermuxPluginUtils.setAndProcessPluginExecutionCommandError(this, LOG_TAG, executionCommand, false,
                        getString(R.string.error_termux_service_execution_command_shell_name_unset, executionCommand.shellCreateMode))
                    null
                } else {
                    ShellCreateMode.NO_SHELL_WITH_NAME
                }
            }
            else -> {
                TermuxPluginUtils.setAndProcessPluginExecutionCommandError(this, LOG_TAG, executionCommand, false,
                    getString(R.string.error_termux_service_unsupported_execution_command_shell_create_mode, executionCommand.shellCreateMode))
                null
            }
        }
    }

    /** Process session action for new session. */
    private fun handleSessionAction(sessionAction: Int, newTerminalSession: TerminalSession) {
        Logger.logDebug(LOG_TAG, "Processing sessionAction \"$sessionAction\" for session \"${newTerminalSession.sessionName}\"")

        when (sessionAction) {
            TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY -> {
                setCurrentStoredTerminalSession(newTerminalSession)
                termuxTerminalSessionActivityClient?.setCurrentSession(newTerminalSession)
                startTermuxActivity()
            }
            TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY -> {
                if (termuxSessionsSize == 1)
                    setCurrentStoredTerminalSession(newTerminalSession)
                startTermuxActivity()
            }
            TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY -> {
                setCurrentStoredTerminalSession(newTerminalSession)
                termuxTerminalSessionActivityClient?.setCurrentSession(newTerminalSession)
            }
            TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_DONT_OPEN_ACTIVITY -> {
                if (termuxSessionsSize == 1)
                    setCurrentStoredTerminalSession(newTerminalSession)
            }
            else -> {
                Logger.logError(LOG_TAG, "Invalid sessionAction: \"$sessionAction\". Force using default sessionAction.")
                handleSessionAction(TERMUX_SERVICE.VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY, newTerminalSession)
            }
        }
    }

    /** Launch the [TermuxActivity] to bring it to foreground. */
    private fun startTermuxActivity() {
        // For android >= 10, apps require Display over other apps permission to start foreground activities
        // from background (services). If it is not granted, then TermuxSessions that are started will
        // show in Termux notification but will not run until user manually clicks the notification.
        if (PermissionUtils.validateDisplayOverOtherAppsPermissionForPostAndroid10(this, true)) {
            TermuxActivity.startTermuxActivity(this)
        } else {
            val preferences = TermuxAppSharedPreferences.build(this) ?: return
            if (preferences.arePluginErrorNotificationsEnabled(false))
                Logger.showToast(this, getString(R.string.error_display_over_other_apps_permission_not_granted_to_start_terminal), true)
        }
    }

    /**
     * If [TermuxActivity] has not bound to the [TermuxService] yet or is destroyed, then
     * interface functions requiring the activity should not be available to the terminal sessions,
     * so we just return the [mTermuxTerminalSessionServiceClient]. Once [TermuxActivity] bind
     * callback is received, it should call [setTermuxTerminalSessionClient] to set the
     * [TermuxService.termuxTerminalSessionActivityClient] so that further terminal sessions are directly
     * passed the [TermuxTerminalSessionActivityClient] object which fully implements the
     * [TerminalSessionClient] interface.
     *
     * @return Returns the [TermuxTerminalSessionActivityClient] if [TermuxActivity] has bound with
     * [TermuxService], otherwise [TermuxTerminalSessionServiceClient].
     */
    @Synchronized
    fun getTermuxTerminalSessionClient(): TermuxTerminalSessionClientBase {
        return termuxTerminalSessionActivityClient ?: mTermuxTerminalSessionServiceClient
    }

    /** This should be called when [TermuxActivity.onServiceConnected] is called to set the
     * [TermuxService.termuxTerminalSessionActivityClient] variable and update the [TerminalSession]
     * and [TerminalEmulator] clients in case they were passed [TermuxTerminalSessionServiceClient]
     * earlier.
     *
     * @param termuxTerminalSessionActivityClient The [TermuxTerminalSessionActivityClient] object that fully
     * implements the [TerminalSessionClient] interface.
     */
    @Synchronized
    fun setTermuxTerminalSessionClient(termuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient) {
        termuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient

        for (i in shellManager.mTermuxSessions.indices)
            shellManager.mTermuxSessions[i].terminalSession.updateTerminalSessionClient(termuxTerminalSessionActivityClient)
    }

    /** This should be called when [TermuxActivity] has been destroyed and in [onUnbind]
     * so that the [TermuxService] and [TerminalSession] and [TerminalEmulator]
     * clients do not hold an activity references.
     */
    @Synchronized
    fun unsetTermuxTerminalSessionClient() {
        for (i in shellManager.mTermuxSessions.indices)
            shellManager.mTermuxSessions[i].terminalSession.updateTerminalSessionClient(mTermuxTerminalSessionServiceClient)

        termuxTerminalSessionActivityClient = null
    }

    private fun buildNotification(): Notification? {
        val res = resources

        // Set pending intent to be launched when notification is clicked
        val notificationIntent = TermuxActivity.newInstance(this)
        val contentIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        // Set notification text
        val sessionCount = termuxSessionsSize
        val taskCount = shellManager.mTermuxTasks.size
        var notificationText = "$sessionCount session${if (sessionCount == 1) "" else "s"}"
        if (taskCount > 0) {
            notificationText += ", $taskCount task${if (taskCount == 1) "" else "s"}"
        }

        val wakeLockHeld = wakeLock != null
        if (wakeLockHeld) notificationText += " (wake lock held)"

        // Set notification priority
        // If holding a wake or wifi lock consider the notification of high priority since it's using power,
        // otherwise use a low priority
        val priority = if (wakeLockHeld) Notification.PRIORITY_HIGH else Notification.PRIORITY_LOW

        // Build the notification
        val builder = NotificationUtils.getNotificationBuilder(this,
            TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID, priority,
            TermuxConstants.TERMUX_APP_NAME, notificationText, null,
            contentIntent, null, NotificationUtils.NOTIFICATION_MODE_SILENT)
        if (builder == null) return null

        // No need to show a timestamp:
        builder.setShowWhen(false)

        // Set notification icon
        builder.setSmallIcon(R.drawable.ic_service_notification)

        // Set background color for small notification icon
        builder.setColor(0xFF607D8B.toInt())

        // TermuxSessions are always ongoing
        builder.setOngoing(true)

        // Set Exit button action
        val exitIntent = Intent(this, TermuxService::class.java).setAction(TERMUX_SERVICE.ACTION_STOP_SERVICE)
        builder.addAction(android.R.drawable.ic_delete, res.getString(R.string.notification_action_exit),
            PendingIntent.getService(this, 0, exitIntent, 0))

        // Set Wakelock button actions
        val newWakeAction = if (wakeLockHeld) TERMUX_SERVICE.ACTION_WAKE_UNLOCK else TERMUX_SERVICE.ACTION_WAKE_LOCK
        val toggleWakeLockIntent = Intent(this, TermuxService::class.java).setAction(newWakeAction)
        val actionTitle = res.getString(if (wakeLockHeld) R.string.notification_action_wake_unlock else R.string.notification_action_wake_lock)
        val actionIcon = if (wakeLockHeld) android.R.drawable.ic_lock_idle_lock else android.R.drawable.ic_lock_lock
        builder.addAction(actionIcon, actionTitle, PendingIntent.getService(this, 0, toggleWakeLockIntent, 0))

        return builder.build()
    }

    private fun setupNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        NotificationUtils.setupNotificationChannel(this, TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_ID,
            TermuxConstants.TERMUX_APP_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_LOW)
    }

    /** Update the shown foreground service notification after making any changes that affect it. */
    @Synchronized
    private fun updateNotification() {
        if (wakeLock == null && shellManager.mTermuxSessions.isEmpty() && shellManager.mTermuxTasks.isEmpty()) {
            // Exit if we are updating after the user disabled all locks with no sessions or tasks running.
            requestStopService()
        } else {
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(
                TermuxConstants.TERMUX_APP_NOTIFICATION_ID, buildNotification())
        }
    }

    private fun setCurrentStoredTerminalSession(terminalSession: TerminalSession?) {
        if (terminalSession == null) return
        // Make the newly created session the current one to be displayed
        val preferences = TermuxAppSharedPreferences.build(this) ?: return
        preferences.setCurrentSession(terminalSession.handle)
    }

    @Synchronized
    fun isTermuxSessionsEmpty(): Boolean = shellManager.mTermuxSessions.isEmpty()

    @Synchronized
    fun getTermuxSessionsSize(): Int = shellManager.mTermuxSessions.size

    @Synchronized
    fun getTermuxSessions(): List<TermuxSession> = shellManager.mTermuxSessions

    @Nullable
    @Synchronized
    fun getTermuxSession(index: Int): TermuxSession? {
        return if (index >= 0 && index < shellManager.mTermuxSessions.size)
            shellManager.mTermuxSessions[index]
        else
            null
    }

    @Nullable
    @Synchronized
    fun getTermuxSessionForTerminalSession(terminalSession: TerminalSession?): TermuxSession? {
        if (terminalSession == null) return null

        for (i in shellManager.mTermuxSessions.indices) {
            if (shellManager.mTermuxSessions[i].terminalSession == terminalSession)
                return shellManager.mTermuxSessions[i]
        }

        return null
    }

    @Synchronized
    fun getLastTermuxSession(): TermuxSession? {
        return if (shellManager.mTermuxSessions.isEmpty()) null else shellManager.mTermuxSessions[shellManager.mTermuxSessions.size - 1]
    }

    @Synchronized
    fun getIndexOfSession(terminalSession: TerminalSession?): Int {
        if (terminalSession == null) return -1

        for (i in shellManager.mTermuxSessions.indices) {
            if (shellManager.mTermuxSessions[i].terminalSession == terminalSession)
                return i
        }
        return -1
    }

    @Synchronized
    fun getTerminalSessionForHandle(sessionHandle: String): TerminalSession? {
        for (i in shellManager.mTermuxSessions.indices) {
            val terminalSession = shellManager.mTermuxSessions[i].terminalSession
            if (terminalSession.handle == sessionHandle)
                return terminalSession
        }
        return null
    }

    @Synchronized
    fun getTermuxTaskForShellName(name: String?): AppShell? {
        if (DataUtils.isNullOrEmpty(name)) return null
        for (i in shellManager.mTermuxTasks.indices) {
            val appShell = shellManager.mTermuxTasks[i]
            val shellName = appShell.executionCommand.shellName
            if (shellName != null && shellName == name)
                return appShell
        }
        return null
    }

    @Synchronized
    fun getTermuxSessionForShellName(name: String?): TermuxSession? {
        if (DataUtils.isNullOrEmpty(name)) return null
        for (i in shellManager.mTermuxSessions.indices) {
            val termuxSession = shellManager.mTermuxSessions[i]
            val shellName = termuxSession.executionCommand.shellName
            if (shellName != null && shellName == name)
                return termuxSession
        }
        return null
    }
}
