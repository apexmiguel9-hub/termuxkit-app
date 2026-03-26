package com.termux.shared.activity

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.appcompat.app.AppCompatActivity
import com.termux.shared.errors.ActivityErrno
import com.termux.shared.errors.Error
import com.termux.shared.errors.FunctionErrno
import com.termux.shared.logger.Logger

object ActivityUtils {

    private const val LOG_TAG = "ActivityUtils"

    /**
     * Wrapper for [startActivity].
     */
    fun startActivity(context: Context, intent: Intent): Error? {
        return startActivity(context, intent, true, true)
    }

    /**
     * Start an [Activity].
     *
     * @param context The context for operations.
     * @param intent The [Intent] to send to start the activity.
     * @param logErrorMessage If an error message should be logged if failed to start activity.
     * @param showErrorMessage If an error message toast should be shown if failed to start activity
     *                         in addition to logging a message. The `context` must not be
     *                         `null`.
     * @return Returns the `error` if starting activity was not successful, otherwise `null`.
     */
    fun startActivity(
        context: Context?,
        intent: Intent,
        logErrorMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ): Error? {
        val activityName = intent.component?.className ?: "Unknown"

        if (context == null) {
            val error = ActivityErrno.ERRNO_STARTING_ACTIVITY_WITH_NULL_CONTEXT.getError(activityName)
            if (logErrorMessage) error.logErrorAndShowToast(null, LOG_TAG)
            return error
        }

        return try {
            context.startActivity(intent)
            null
        } catch (e: Exception) {
            val error = ActivityErrno.ERRNO_START_ACTIVITY_FAILED_WITH_EXCEPTION.getError(e, activityName, e.message)
            if (logErrorMessage) error.logErrorAndShowToast(if (showErrorMessage) context else null, LOG_TAG)
            error
        }
    }

    /**
     * Wrapper for [startActivityForResult].
     */
    fun startActivityForResult(context: Context, requestCode: Int, intent: Intent): Error? {
        return startActivityForResult(context, requestCode, intent, true, true, null)
    }

    /**
     * Wrapper for [startActivityForResult].
     */
    fun startActivityForResult(
        context: Context,
        requestCode: Int,
        intent: Intent,
        logErrorMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ): Error? {
        return startActivityForResult(context, requestCode, intent, logErrorMessage, showErrorMessage, null)
    }

    /**
     * Start an [Activity] for result.
     *
     * @param context The context for operations. It must be an instance of [Activity] or
     *               [AppCompatActivity]. It is ignored if `activityResultLauncher`
     *                is not `null`.
     * @param requestCode The request code to use while sending intent. This must be >= 0, otherwise
     *                    exception will be raised. This is ignored if `activityResultLauncher`
     *                    is `null`.
     * @param intent The [Intent] to send to start the activity.
     * @param logErrorMessage If an error message should be logged if failed to start activity.
     * @param showErrorMessage If an error message toast should be shown if failed to start activity
     *                         in addition to logging a message. The `context` must not be
     *                         `null`.
     * @param activityResultLauncher The [ActivityResultLauncher] to use for start the
     *                               activity. If this is `null`, then
     *                               `Activity.startActivityForResult` will be
     *                               used instead.
     *                               Note that later is deprecated.
     * @return Returns the `error` if starting activity was not successful, otherwise `null`.
     */
    fun startActivityForResult(
        context: Context?,
        requestCode: Int,
        intent: Intent,
        logErrorMessage: Boolean = true,
        showErrorMessage: Boolean = true,
        activityResultLauncher: ActivityResultLauncher<Intent>? = null
    ): Error? {
        val activityName = intent.component?.className ?: "Unknown"

        return try {
            if (activityResultLauncher != null) {
                activityResultLauncher.launch(intent)
            } else {
                when (context) {
                    null -> {
                        val error = ActivityErrno.ERRNO_STARTING_ACTIVITY_WITH_NULL_CONTEXT.getError(activityName)
                        if (logErrorMessage) error.logErrorAndShowToast(null, LOG_TAG)
                        return error
                    }
                    is AppCompatActivity -> context.startActivityForResult(intent, requestCode)
                    is Activity -> context.startActivityForResult(intent, requestCode)
                    else -> {
                        val error = FunctionErrno.ERRNO_PARAMETER_NOT_INSTANCE_OF.getError(
                            "context", "startActivityForResult", "Activity or AppCompatActivity"
                        )
                        if (logErrorMessage) error.logErrorAndShowToast(if (showErrorMessage) context else null, LOG_TAG)
                        return error
                    }
                }
            }
            null
        } catch (e: Exception) {
            val error = ActivityErrno.ERRNO_START_ACTIVITY_FOR_RESULT_FAILED_WITH_EXCEPTION.getError(e, activityName, e.message)
            if (logErrorMessage) error.logErrorAndShowToast(if (showErrorMessage) context else null, LOG_TAG)
            error
        }
    }

    /**
     * Wrapper for [startActivityForResult].
     */
    fun <T> startActivityForResult(
        context: Context,
        activityResultLauncher: ActivityResultLauncher<T>,
        input: T
    ): Error? {
        return startActivityForResult(context, activityResultLauncher, input, true, true)
    }

    /**
     * Generic method to start an [Activity] for result.
     * @param context The context for operations. It must be an instance of [Activity] or
     *                [AppCompatActivity]. It is ignored if `activityResultLauncher`
     *                is not `null`.
     * @param activityResultLauncher A launcher for start the process of executing an [ActivityResultContract].
     * @param input The data required to [ActivityResultLauncher.launch] Activity.
     * @param logErrorMessage If an error message should be logged if failed to start activity.
     * @param showErrorMessage If an error message toast should be shown if failed to start activity
     *                         in addition to logging a message. The `context` must not be
     *                         `null`.
     * @param <T> Type of the input required to [ActivityResultLauncher.launch].
     * @return Returns the `error` if starting activity was not successful, otherwise `null`.
     */
    fun <T> startActivityForResult(
        context: Context?,
        activityResultLauncher: ActivityResultLauncher<T>,
        input: T,
        logErrorMessage: Boolean = true,
        showErrorMessage: Boolean = true
    ): Error? {
        var activityName = "Unknown"

        if (input is Intent && input.component != null) {
        // Fixed: Smart cast issue
            activityName = input.component.className
        }

        return try {
            activityResultLauncher.launch(input)
            null
        } catch (e: Exception) {
            val error = ActivityErrno.ERRNO_START_ACTIVITY_FOR_RESULT_FAILED_WITH_EXCEPTION.getError(e, activityName, e.message)
            if (logErrorMessage) error.logErrorAndShowToast(if (showErrorMessage) context else null, LOG_TAG)
            error
        }
    }

}
