package com.termux.shared.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.annotation.Nullable
import com.termux.shared.logger.Logger

object NotificationUtils {

    /** Do not show notification */
    @JvmField
    val NOTIFICATION_MODE_NONE = 0

    /** Show notification without sound, vibration or lights */
    @JvmField
    val NOTIFICATION_MODE_SILENT = 1

    /** Show notification with sound */
    @JvmField
    val NOTIFICATION_MODE_SOUND = 2

    /** Show notification with vibration */
    @JvmField
    val NOTIFICATION_MODE_VIBRATE = 3

    /** Show notification with lights */
    @JvmField
    val NOTIFICATION_MODE_LIGHTS = 4

    /** Show notification with sound and vibration */
    @JvmField
    val NOTIFICATION_MODE_SOUND_AND_VIBRATE = 5

    /** Show notification with sound and lights */
    @JvmField
    val NOTIFICATION_MODE_SOUND_AND_LIGHTS = 6

    /** Show notification with vibration and lights */
    @JvmField
    val NOTIFICATION_MODE_VIBRATE_AND_LIGHTS = 7

    /** Show notification with sound, vibration and lights */
    @JvmField
    val NOTIFICATION_MODE_ALL = 8

    private const val LOG_TAG = "NotificationUtils"

    /**
     * Get the [NotificationManager].
     *
     * @param context The [Context] for operations.
     * @return Returns the [NotificationManager].
     */
    @JvmStatic
    @Nullable
    fun getNotificationManager(context: Context?): NotificationManager? {
        if (context == null) return null
        return context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }

    /**
     * Get [Notification.Builder].
     *
     * @param context The [Context] for operations.
     * @param title The title for the notification.
     * @param channelId The channel id for the notification.
     * @param priority The priority for the notification.
     * @param notificationText The second line text of the notification.
     * @param notificationBigText The full text of the notification that may optionally be styled.
     * @param contentIntent The [PendingIntent] which should be sent when notification is clicked.
     * @param deleteIntent The [PendingIntent] which should be sent when notification is deleted.
     * @param notificationMode The notification mode. It must be one of `NotificationUtils.NOTIFICATION_MODE_*`.
     *                         The builder returned will be `null` if [NOTIFICATION_MODE_NONE]
     *                         is passed. That case should ideally be handled before calling this function.
     * @return Returns the [Notification.Builder].
     */
    @JvmStatic
    @Nullable
    fun geNotificationBuilder(
        context: Context?,
        channelId: String?,
        priority: Int,
        title: CharSequence,
        notificationText: CharSequence,
        notificationBigText: CharSequence,
        contentIntent: PendingIntent?,
        deleteIntent: PendingIntent?,
        notificationMode: Int
    ): Notification.Builder? {
        if (context == null) return null

        val builder = Notification.Builder(context)
        builder.setContentTitle(title)
        builder.setContentText(notificationText)
        builder.setStyle(Notification.BigTextStyle().bigText(notificationBigText))
        builder.setContentIntent(contentIntent)
        builder.setDeleteIntent(deleteIntent)

        builder.setPriority(priority)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            builder.setChannelId(channelId)

        return setNotificationDefaults(builder, notificationMode)
    }

    /**
     * Setup the notification channel if Android version is greater than or equal to
     * [Build.VERSION_CODES.O].
     *
     * @param context The [Context] for operations.
     * @param channelId The id of the channel. Must be unique per package.
     * @param channelName The user visible name of the channel.
     * @param importance The importance of the channel. This controls how interruptive notifications
     *                   posted to this channel are.
     */
    fun setupNotificationChannel(
        context: Context?,
        channelId: String?,
        channelName: CharSequence,
        importance: Int
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (channelId == null || channelName == null) return

        val channel = NotificationChannel(channelId, channelName, importance)

        val notificationManager = getNotificationManager(context)
        notificationManager?.createNotificationChannel(channel)
    }

    fun setNotificationDefaults(builder: Notification.Builder, notificationMode: Int): Notification.Builder? {
        // TODO: setDefaults() is deprecated and should also implement setting notification mode via notification channel
        when (notificationMode) {
            NOTIFICATION_MODE_NONE -> {
                Logger.logWarn(LOG_TAG, "The NOTIFICATION_MODE_NONE passed to setNotificationDefaults(), force setting builder to null.")
                return null // return null since notification is not supposed to be shown
            }
            NOTIFICATION_MODE_SILENT -> {
                // No defaults set for silent notifications
            }
            NOTIFICATION_MODE_SOUND -> {
                builder.setDefaults(Notification.DEFAULT_SOUND)
            }
            NOTIFICATION_MODE_VIBRATE -> {
                builder.setDefaults(Notification.DEFAULT_VIBRATE)
            }
            NOTIFICATION_MODE_LIGHTS -> {
                builder.setDefaults(Notification.DEFAULT_LIGHTS)
            }
            NOTIFICATION_MODE_SOUND_AND_VIBRATE -> {
                builder.setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_VIBRATE)
            }
            NOTIFICATION_MODE_SOUND_AND_LIGHTS -> {
                builder.setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_LIGHTS)
            }
            NOTIFICATION_MODE_VIBRATE_AND_LIGHTS -> {
                builder.setDefaults(Notification.DEFAULT_VIBRATE or Notification.DEFAULT_LIGHTS)
            }
            NOTIFICATION_MODE_ALL -> {
                builder.setDefaults(Notification.DEFAULT_ALL)
            }
            else -> {
                Logger.logError(LOG_TAG, "Invalid notificationMode: \"$notificationMode\" passed to setNotificationDefaults()")
            }
        }

        return builder
    }
}
