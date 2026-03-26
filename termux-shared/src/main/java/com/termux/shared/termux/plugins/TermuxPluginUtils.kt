package com.termux.shared.termux.plugins
import java.util.*

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import android.os.Environment
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import com.termux.shared.R
import com.termux.shared.activities.ReportActivity
import com.termux.shared.android.AndroidUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.errors.Errno
import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.models.ReportInfo
import com.termux.shared.notification.NotificationUtils
import com.termux.shared.shell.ShellUtils
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.result.ResultConfig
import com.termux.shared.shell.command.result.ResultData
import com.termux.shared.shell.command.result.ResultSender
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.TermuxUtils.AppInfoMode
import com.termux.shared.termux.file.TermuxFileUtils
import com.termux.shared.termux.models.UserAction
import com.termux.shared.termux.notification.TermuxNotificationUtils
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP

object TermuxPluginUtils {

    private const val LOG_TAG = "TermuxPluginUtils"

    /**
     * Process [ExecutionCommand] result.
     *
     * The ExecutionCommand currentState must be greater or equal to
     * [ExecutionCommand.ExecutionState.EXECUTED].
     * If the [ExecutionCommand.isPluginExecutionCommand] is `true` and
     * [ResultConfig.resultPendingIntent] or [ResultConfig.resultDirectoryPath]
     * is not `null`, then the result of commands are sent back to the command caller.
     *
     * @param context The [Context] that will be used to send result intent to the [PendingIntent] creator.
     * @param logTag The log tag to use for logging.
     * @param executionCommand The [ExecutionCommand] to process.
     */
    fun processPluginExecutionCommandResult(context: Context?, logTag: String?, executionCommand: ExecutionCommand?) {
        if (executionCommand == null) return

        val safeLogTag = DataUtils.getDefaultIfNull(logTag, LOG_TAG)
        var error: Error? = null
        val resultData = executionCommand.resultData

        if (!executionCommand.hasExecuted()) {
        // Fixed: null safety
            Logger.logWarn(safeLogTag, "${executionCommand.getCommandIdAndLabelLogString() ?: "" ?: ""}: Ignoring call to processPluginExecutionCommandResult() since the execution command state is not higher than the ExecutionState.EXECUTED")
            return
        }

        val isPluginExecutionCommandWithPendingResult = executionCommand.isPluginExecutionCommandWithPendingResult()
        val isExecutionCommandLoggingEnabled = Logger.shouldEnableLoggingForCustomLogLevel(executionCommand.backgroundCustomLogLevel)

        // Log the output. ResultData should not be logged if pending result since ResultSender will do it
        // or if logging is disabled
        Logger.logDebugExtended(safeLogTag, ExecutionCommand.getExecutionOutputLogString(executionCommand, true,
            !isPluginExecutionCommandWithPendingResult, isExecutionCommandLoggingEnabled) ?: "")

        // If execution command was started by a plugin which expects the result back
        if (isPluginExecutionCommandWithPendingResult) {
            // Set variables which will be used by sendCommandResultData to send back the result
            executionCommand.resultConfig.resultPendingIntent?.let {
                setPluginResultPendingIntentVariables(executionCommand)
            }
            executionCommand.resultConfig.resultDirectoryPath?.let {
                setPluginResultDirectoryVariables(executionCommand)
            }

            // Send result to caller
            error = ResultSender.sendCommandResultData(context, safeLogTag, executionCommand.getCommandIdAndLabelLogString() ?: "",
                executionCommand.resultConfig, executionCommand.resultData, isExecutionCommandLoggingEnabled)
            if (error != null) {
                // error will be added to existing Errors
                resultData.setStateFailed(error)
                Logger.logDebugExtended(safeLogTag, ExecutionCommand.getExecutionOutputLogString(executionCommand, true, true, isExecutionCommandLoggingEnabled) ?: "")

                // Flash and send notification for the error
                sendPluginCommandErrorNotification(context, safeLogTag, "",
                    ResultData.getErrorsListMinimalString(resultData),
                    ExecutionCommand.getExecutionCommandMarkdownString(executionCommand) ?: "" ?: "",
                    false, true, AppInfoMode.TERMUX_AND_CALLING_PACKAGE, true,
                    executionCommand.resultConfig.resultPendingIntent?.creatorPackage ?: "")
            }
        }

        if (!executionCommand.isStateFailed() && error == null)
            executionCommand.setState(ExecutionCommand.ExecutionState.SUCCESS)
    }

    /**
     * Set [ExecutionCommand] state to [Errno.ERRNO_FAILED] with `errmsg` and
     * process error with [processPluginExecutionCommandError].
     *
     *
     * @param context The [Context] for operations.
     * @param logTag The log tag to use for logging.
     * @param executionCommand The [ExecutionCommand] that failed.
     * @param forceNotification If set to `true`, then a flash and notification will be shown
     *                          regardless of if pending intent is `null` or
     *                          [TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED]
     *                          is `false`.
     * @param errmsg The error message to set.
     */
    fun setAndProcessPluginExecutionCommandError(context: Context?, logTag: String?,
                                                 executionCommand: ExecutionCommand?,
                                                 forceNotification: Boolean,
                                                 @NonNull errmsg: String) {
        executionCommand?.setStateFailed(Errno.ERRNO_FAILED.code, errmsg)
        processPluginExecutionCommandError(context, logTag, executionCommand, forceNotification)
    }

    /**
     * Process [ExecutionCommand] error.
     *
     * The ExecutionCommand currentState must be equal to [ExecutionCommand.ExecutionState.FAILED].
     * The [ResultData.errCode] must have been set to a value greater than
     * [Errno.ERRNO_SUCCESS].
     * The [ResultData.errorsList] must also be set with appropriate error info.
     *
     * If the [ExecutionCommand.isPluginExecutionCommand] is `true` and
     * [ResultConfig.resultPendingIntent] or [ResultConfig.resultDirectoryPath]
     * is not `null`, then the errors of commands are sent back to the command caller.
     *
     * Otherwise if the [TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED] is
     * enabled, then a flash and a notification will be shown for the error as well
     * on the [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_NAME] channel instead of just logging
     * the error.
     *
     * @param context The [Context] for operations.
     * @param logTag The log tag to use for logging.
     * @param executionCommand The [ExecutionCommand] that failed.
     * @param forceNotification If set to `true`, then a flash and notification will be shown
     *                          regardless of if pending intent is `null` or
     *                          [TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED]
     *                          is `false`.
     */
    fun processPluginExecutionCommandError(context: Context?, logTag: String?,
                                           executionCommand: ExecutionCommand?,
                                           forceNotification: Boolean) {
        if (context == null || executionCommand == null) return

        val safeLogTag = DataUtils.getDefaultIfNull(logTag, LOG_TAG)
        val resultData = executionCommand.resultData
        var shouldForceNotification = forceNotification

        if (!executionCommand.isStateFailed()) {
            Logger.logWarn(safeLogTag, "${executionCommand.getCommandIdAndLabelLogString() ?: "" ?: ""}: Ignoring call to processPluginExecutionCommandError() since the execution command is not in ExecutionState.FAILED")
            return
        }

        val isPluginExecutionCommandWithPendingResult = executionCommand.isPluginExecutionCommandWithPendingResult()
        val isExecutionCommandLoggingEnabled = Logger.shouldEnableLoggingForCustomLogLevel(executionCommand.backgroundCustomLogLevel)

        // Log the error and any exception. ResultData should not be logged if pending result since ResultSender will do it
        Logger.logError(safeLogTag, "Processing plugin execution error for:\n${executionCommand.getCommandIdAndLabelLogString() ?: "" ?: ""}")
        Logger.logError(safeLogTag, "Set log level to debug or higher to see error in logs")
        Logger.logErrorPrivateExtended(safeLogTag, ExecutionCommand.getExecutionOutputLogString(executionCommand, true,
            !isPluginExecutionCommandWithPendingResult, isExecutionCommandLoggingEnabled) ?: "")

        // If execution command was started by a plugin which expects the result back
        if (isPluginExecutionCommandWithPendingResult) {
            // Set variables which will be used by sendCommandResultData to send back the result
            executionCommand.resultConfig.resultPendingIntent?.let {
                setPluginResultPendingIntentVariables(executionCommand)
            }
            executionCommand.resultConfig.resultDirectoryPath?.let {
                setPluginResultDirectoryVariables(executionCommand)
            }

            // Send result to caller
            val error = ResultSender.sendCommandResultData(context, safeLogTag, executionCommand.getCommandIdAndLabelLogString() ?: "",
                executionCommand.resultConfig, executionCommand.resultData, isExecutionCommandLoggingEnabled)
            if (error != null) {
                // error will be added to existing Errors
                resultData.setStateFailed(error)
                Logger.logErrorPrivateExtended(safeLogTag, ExecutionCommand.getExecutionOutputLogString(executionCommand, true, true, isExecutionCommandLoggingEnabled) ?: "")
                shouldForceNotification = true
            }

            // No need to show notifications if a pending intent was sent, let the caller handle the result himself
            if (!shouldForceNotification) return
        }

        // Flash and send notification for the error
        sendPluginCommandErrorNotification(context, safeLogTag, "",
            ResultData.getErrorsListMinimalString(resultData),
            ExecutionCommand.getExecutionCommandMarkdownString(executionCommand) ?: "",
            shouldForceNotification, true, AppInfoMode.TERMUX_AND_CALLING_PACKAGE, true,
            executionCommand.resultConfig.resultPendingIntent?.creatorPackage ?: "")
    }

    /** Set variables which will be used by [ResultSender.sendCommandResultData]
     * to send back the result via [ResultConfig.resultPendingIntent]. */
    fun setPluginResultPendingIntentVariables(executionCommand: ExecutionCommand) {
        val resultConfig = executionCommand.resultConfig

        resultConfig.resultBundleKey = TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE
        resultConfig.resultStdoutKey = TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT
        resultConfig.resultStdoutOriginalLengthKey = TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT_ORIGINAL_LENGTH
        resultConfig.resultStderrKey = TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR
        resultConfig.resultStderrOriginalLengthKey = TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_STDERR_ORIGINAL_LENGTH
        resultConfig.resultExitCodeKey = TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE
        resultConfig.resultErrCodeKey = TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERR
        resultConfig.resultErrmsgKey = TERMUX_SERVICE.EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG
    }

    /** Set variables which will be used by [ResultSender.sendCommandResultData]
     * to send back the result by writing it to files in [ResultConfig.resultDirectoryPath]. */
    fun setPluginResultDirectoryVariables(executionCommand: ExecutionCommand) {
        val resultConfig = executionCommand.resultConfig

        resultConfig.resultDirectoryPath = TermuxFileUtils.getCanonicalPath(resultConfig.resultDirectoryPath, null, true)
        resultConfig.resultDirectoryAllowedParentPath = TermuxFileUtils.getMatchedAllowedTermuxWorkingDirectoryParentPathForPath(resultConfig.resultDirectoryPath)

        // Set default resultFileBasename if resultSingleFile is true to `<executable_basename>-<timestamp>.log`
        if (resultConfig.resultSingleFile && resultConfig.resultFileBasename == null)
            resultConfig.resultFileBasename = ShellUtils.getExecutableBasename(executionCommand.executable) + "-" + AndroidUtils.getCurrentMilliSecondLocalTimeStamp() + ".log"
    }

    /**
     * Send a plugin error report notification for [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_ID]
     * and [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_NAME].
     *
     * @param currentPackageContext The [Context] of current package.
     * @param logTag The log tag to use for logging.
     * @param title The title for the error report and notification.
     * @param message The message for the error report.
     * @param throwable The [Throwable] for the error report.
     */
    fun sendPluginCommandErrorNotification(currentPackageContext: Context?, logTag: String?,
                                           title: CharSequence, message: String?, throwable: Throwable?) {
        sendPluginCommandErrorNotification(currentPackageContext, logTag,
            title, message,
            MarkdownUtils.getMarkdownCodeForString(Logger.getMessageAndStackTraceString(message, throwable), true),
            false, false, true)
    }

    /**
     * Send a plugin error report notification for [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_ID]
     * and [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_NAME].
     *
     * @param currentPackageContext The [Context] of current package.
     * @param logTag The log tag to use for logging.
     * @param title The title for the error report and notification.
     * @param notificationTextString The text of the notification.
     * @param message The message for the error report.
     */
    fun sendPluginCommandErrorNotification(currentPackageContext: Context?, logTag: String?,
                                           title: CharSequence, notificationTextString: String?,
                                           message: String?) {
        sendPluginCommandErrorNotification(currentPackageContext, logTag,
            title, notificationTextString, message,
            false, false, true)
    }

    /**
     * Send a plugin error report notification for [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_ID]
     * and [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_NAME].
     *
     * @param currentPackageContext The [Context] of current package.
     * @param logTag The log tag to use for logging.
     * @param title The title for the error report and notification.
     * @param notificationTextString The text of the notification.
     * @param message The message for the error report.
     * @param forceNotification If set to `true`, then a notification will be shown
     *                          regardless of if pending intent is `null` or
     *                          [TermuxPreferenceConstants.TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED]
     *                          is `false`.
     * @param showToast If set to `true`, then a toast will be shown for `notificationTextString`.
     * @param addDeviceInfo If set to `true`, then device info should be appended to the message.
     */
    fun sendPluginCommandErrorNotification(currentPackageContext: Context?, logTag: String?,
                                           title: CharSequence, notificationTextString: String?,
                                           message: String?, forceNotification: Boolean,
                                           showToast: Boolean,
                                           addDeviceInfo: Boolean) {
        sendPluginCommandErrorNotification(currentPackageContext, logTag,
            title, notificationTextString, "## $title\n\n$message\n\n",
            forceNotification, showToast, AppInfoMode.TERMUX_AND_PLUGIN_PACKAGE, addDeviceInfo, null)
    }

    /**
     * Send a plugin error notification for [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_ID]
     * and [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_NAME].
     *
     * @param currentPackageContext The [Context] of current package.
     * @param logTag The log tag to use for logging.
     * @param title The title for the error report and notification.
     * @param notificationTextString The text of the notification.
     * @param message The message for the error report.
     * @param forceNotification If set to `true`, then a notification will be shown
     *                          regardless of if pending intent is `null` or
     *                          [TermuxPreferenceConstants.TERMUX_APP.KEY_PLUGIN_ERROR_NOTIFICATIONS_ENABLED]
     *                          is `false`.
     * @param showToast If set to `true`, then a toast will be shown for `notificationTextString`.
     * @param appInfoMode The [AppInfoMode] to use to add app info to the message.
     *                    Set to `null` if app info should not be appended to the message.
     * @param addDeviceInfo If set to `true`, then device info should be appended to the message.
     * @param callingPackageName The optional package name of the app for which the plugin command
     *                           was run.
     */
    fun sendPluginCommandErrorNotification(currentPackageContext: Context?, logTag: String?,
                                           title: CharSequence,
                                           notificationTextString: String?,
                                           message: String?, forceNotification: Boolean,
                                           showToast: Boolean,
                                           appInfoMode: AppInfoMode?,
                                           addDeviceInfo: Boolean,
                                           callingPackageName: String?) {
        // Note: Do not change currentPackageContext or termuxPackageContext passed to functions or things will break

        val safeContext = currentPackageContext ?: return
        val currentPackageName = safeContext.packageName

        val termuxPackageContext = TermuxUtils.getTermuxPackageContext(safeContext)
            ?: run {
                Logger.logWarn(LOG_TAG, "Ignoring call to sendPluginCommandErrorNotification() since failed to get \"${TermuxConstants.TERMUX_PACKAGE_NAME}\" package context from \"$currentPackageName\" context")
                return
            }

        val preferences = TermuxAppSharedPreferences.build(termuxPackageContext) ?: return

        // If user has disabled notifications for plugin commands, then just return
        if (!preferences.arePluginErrorNotificationsEnabled(true) && !forceNotification)
            return

        val safeLogTag = DataUtils.getDefaultIfNull(logTag, LOG_TAG)

        if (showToast)
            Logger.showToast(safeContext, notificationTextString ?: "", true)

        // Send a notification to show the error which when clicked will open the ReportActivity
        // to show the details of the error
        val safeTitle: CharSequence = title?.takeUnless { it.toString().isEmpty() }
            ?: "${TermuxConstants.TERMUX_APP_NAME} Plugin Execution Command Error"

        Logger.logDebug(safeLogTag, "Sending \"$safeTitle\" notification.")

        val reportString = StringBuilder(message ?: "")

        if (appInfoMode != null)
            reportString.append("\n\n").append(TermuxUtils.getAppInfoMarkdownString(safeContext, appInfoMode, callingPackageName ?: currentPackageName) ?: "")

        if (addDeviceInfo)
            reportString.append("\n\n").append(AndroidUtils.getDeviceInfoMarkdownString(safeContext, true) ?: "")

        val userActionName = UserAction.PLUGIN_EXECUTION_COMMAND.getName() ?: "UNKNOWN"
        val titleString: String = safeTitle.toString().let { if (it.isEmpty()) "Error" else it }
        val reportInfo = ReportInfo(userActionName, safeLogTag, titleString)
        reportInfo.reportString = reportString.toString()
        reportInfo.setReportStringSuffix("\n\n${TermuxUtils.getReportIssueMarkdownString(safeContext) ?: ""}")
        reportInfo.setAddReportInfoHeaderToMarkdown(true)
        reportInfo.setReportSaveFileLabelAndPath(userActionName,
            Environment.getExternalStorageDirectory().absolutePath + "/" +
                FileUtils.sanitizeFileName("${TermuxConstants.TERMUX_APP_NAME}-$userActionName.log", true, true))

        val result = ReportActivity.newInstance(termuxPackageContext, reportInfo)
        val contentIntent = result.contentIntent ?: return

        // Must ensure result code for PendingIntents and id for notification are unique otherwise will override previous
        val nextNotificationId = TermuxNotificationUtils.getNextNotificationId(termuxPackageContext)

        val contentPendingIntent = PendingIntent.getActivity(termuxPackageContext, nextNotificationId, contentIntent, PendingIntent.FLAG_UPDATE_CURRENT)

        var deleteIntent: PendingIntent? = null
        result.deleteIntent?.let {
            deleteIntent = PendingIntent.getBroadcast(termuxPackageContext, nextNotificationId, it, PendingIntent.FLAG_UPDATE_CURRENT)
        }

        // Setup the notification channel if not already set up
        setupPluginCommandErrorsNotificationChannel(termuxPackageContext)

        // Use markdown in notification
        val notificationTextCharSequence: CharSequence = MarkdownUtils.getSpannedMarkdownText(termuxPackageContext, notificationTextString) ?: notificationTextString ?: ""

        // Build the notification
        val builder = getPluginCommandErrorsNotificationBuilder(safeContext, termuxPackageContext,
            safeTitle, notificationTextCharSequence, notificationTextCharSequence, contentPendingIntent, deleteIntent,
            NotificationUtils.NOTIFICATION_MODE_VIBRATE)
            ?: return

        // Send the notification
        val notificationManager = NotificationUtils.getNotificationManager(termuxPackageContext)
        notificationManager?.notify(nextNotificationId, builder.build())
    }

    /**
     * Get [Notification.Builder] for [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_ID]
     * and [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_NAME].
     *
     * @param currentPackageContext The [Context] of current package.
     * @param termuxPackageContext The [Context] of termux package.
     * @param title The title for the notification.
     * @param notificationText The second line text of the notification.
     * @param notificationBigText The full text of the notification that may optionally be styled.
     * @param contentIntent The [PendingIntent] which should be sent when notification is clicked.
     * @param deleteIntent The [PendingIntent] which should be sent when notification is deleted.
     * @param notificationMode The notification mode. It must be one of `NotificationUtils.NOTIFICATION_MODE_*`.
     * @return Returns the [Notification.Builder].
     */
    @JvmStatic
    @Nullable
    fun getPluginCommandErrorsNotificationBuilder(currentPackageContext: Context,
                                                    termuxPackageContext: Context,
                                                    title: CharSequence,
                                                    notificationText: CharSequence,
                                                    notificationBigText: CharSequence,
                                                    contentIntent: PendingIntent?,
                                                    deleteIntent: PendingIntent?,
                                                    notificationMode: Int): Notification.Builder? {
        return TermuxNotificationUtils.getTermuxOrPluginAppNotificationBuilder(
            currentPackageContext, termuxPackageContext,
            TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_ID, Notification.PRIORITY_HIGH,
            title, notificationText, notificationBigText, contentIntent, deleteIntent, notificationMode)
    }

    /**
     * Setup the notification channel for [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_ID] and
     * [TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_NAME].
     *
     * @param context The [Context] for operations.
     */
    fun setupPluginCommandErrorsNotificationChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        NotificationUtils.setupNotificationChannel(context, TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_ID,
            TermuxConstants.TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
    }

    /**
     * Check if [TermuxConstants.PROP_ALLOW_EXTERNAL_APPS] property is not set to "true".
     *
     * @param context The [Context] to get error string.
     * @return Returns the `error` if policy is violated, otherwise `null`.
     */
    fun checkIfAllowExternalAppsPolicyIsViolated(context: Context, apiName: String): String? {
        val properties = TermuxAppSharedProperties.getProperties()
        if (properties == null || !properties.shouldAllowExternalApps()) {
            return context.getString(R.string.error_allow_external_apps_ungranted, apiName,
                TermuxFileUtils.getUnExpandedTermuxPath(TermuxConstants.TERMUX_PROPERTIES_PRIMARY_FILE_PATH))
        }

        return null
    }
}
