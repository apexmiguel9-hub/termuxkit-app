package com.termux.app.style

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.NonNull
import androidx.appcompat.app.AlertDialog
import androidx.viewpager.widget.ViewPager
import com.termux.R
import com.termux.app.TermuxActivity
import com.termux.shared.activity.ActivityUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.image.ImageUtils
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.extrakeys.ExtraKeysView
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.theme.ThemeUtils
import com.termux.shared.view.ViewUtils
import com.termux.terminal.TerminalSession
import com.termux.terminal.TextStyle
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class TermuxBackgroundManager(private val activity: TermuxActivity) {

    /** A launcher for start the process of executing an {@link ActivityResultContract}. */
    private val mActivityResultLauncher: ActivityResultLauncher<String>

    /** Termux app shared preferences manager. */
    private val preferences: TermuxAppSharedPreferences

    /** ExecutorService to execute task in background. */
    private val executor: ExecutorService

    /** Handler allows to send and process {@link  android.os.Message Message}. */
    private val handler: Handler

    companion object {
        private const val LOG_TAG = "TermuxBackgroundManager"
    }

    init {
        this.preferences = activity.preferences
        this.mActivityResultLauncher = registerActivityResultLauncher()
        this.executor = Executors.newSingleThreadExecutor()
        this.handler = Handler(Looper.getMainLooper())
    }

    /**
     * Registers for activity result launcher. It's safe to call before fragment
     * or activity is created.
     *
     * @return A launcher for executing an {@link ActivityResultContract}.
     */
    private fun registerActivityResultLauncher(): ActivityResultLauncher<String> {
        return activity.registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->

            if (uri != null) {
                try {
                    executor.execute {
                        val bitmap = ImageUtils.getBitmap(activity, uri)

                        if (bitmap == null) {
                            Logger.logErrorAndShowToast(activity, LOG_TAG, activity.getString(R.string.error_background_image_loading_from_gallery_failed))
                            return@execute
                        }

                        ImageUtils.compressAndSaveBitmap(bitmap, TermuxConstants.TERMUX_BACKGROUND_IMAGE_PATH)
                        val success = generateImageFiles(activity, bitmap)

                        if (success) {
                            notifyBackgroundUpdated(true)

                            Logger.logInfo(LOG_TAG, "Image received successfully from the gallary.")
                            Logger.logDebug(LOG_TAG, "Storing background original image to " + TermuxConstants.TERMUX_BACKGROUND_IMAGE_PATH)
                            Logger.logDebug(LOG_TAG, "Storing background portrait image to " + TermuxConstants.TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH)
                            Logger.logDebug(LOG_TAG, "Storing background landscape image to " + TermuxConstants.TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH)

                        } else {
                            Logger.logErrorAndShowToast(activity, LOG_TAG, activity.getString(R.string.error_background_image_loading_from_gallery_failed))
                        }
                    }

                } catch (e: Exception) {
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load image", e)
                    Logger.showToast(activity, activity.getString(R.string.error_background_image_loading_from_gallery_failed), true)
                }
            }
        }
    }

    /**
     * Check whether the optimized background image for {@link Configuration#ORIENTATION_PORTRAIT
     * Portrait} and {@link Configuration#ORIENTATION_LANDSCAPE Landscape} display view exist.
     *
     * @param context The context for operation.
     * @return Returns whether the optimized background image exist or not.
     */
    fun isImageFilesExist(@NonNull context: Context, shouldGenerate: Boolean): Boolean {
        val isLandscape = ViewUtils.getDisplayOrientation(context) == Configuration.ORIENTATION_LANDSCAPE
        val size = ViewUtils.getDisplaySize(context, true)

        val imagePath1 = if (isLandscape) TermuxConstants.TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH else TermuxConstants.TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH

        val imagePath2 = if (isLandscape) TermuxConstants.TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH else TermuxConstants.TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH

        var exist = ImageUtils.isImageOptimized(imagePath1, size)
                && ImageUtils.isImageOptimized(imagePath2, DataUtils.swap(size))

        if (!exist && shouldGenerate && ImageUtils.isImage(TermuxConstants.TERMUX_BACKGROUND_IMAGE_PATH)) {
            val bitmap = ImageUtils.getBitmap(TermuxConstants.TERMUX_BACKGROUND_IMAGE_PATH)
            return generateImageFiles(context, bitmap)
        }

        return exist
    }

    /**
     * Enable background image loading. If the image already exist then ask for restore otherwise pick from gallery.
     */
    fun setBackgroundImage() {
        if (!preferences.isBackgroundImageEnabled() && isImageFilesExist(activity, true)) {
            restoreBackgroundImages()

        } else {
            pickImageFromGallery()
        }
    }

    /**
     * Disable background image loading and notify about the changes.
     * If image files are not deleted then it can be used to restore
     * when resetting background to image.
     *
     * @param deleteFiles The {@code boolean} that decides if it should delete the image files.
     */
    fun removeBackgroundImage(deleteFiles: Boolean) {
        if (deleteFiles) {
            FileUtils.deleteDirectoryFile(null, TermuxConstants.TERMUX_BACKGROUND_DIR_PATH, true)
        }

        notifyBackgroundUpdated(false)
    }

    /** {@link ActivityResultLauncher#launch(Object) Launch} Activity for result to pick image from gallery. */
    private fun pickImageFromGallery() {
        ActivityUtils.startActivityForResult(activity, mActivityResultLauncher, ImageUtils.ANY_IMAGE_TYPE)
    }

    /**
     * If the background images exist then ask user whether to restore them or not.
     * If denied pick from gallery.
     */
    private fun restoreBackgroundImages() {
        val b = AlertDialog.Builder(activity)

        b.setMessage(R.string.title_restore_background_image)
        b.setPositiveButton(R.string.action_yes) { dialog, id ->
            notifyBackgroundUpdated(true)
        }

        b.setNegativeButton(R.string.action_no) { dialog, id ->
            pickImageFromGallery()
        }

        b.show()
    }

    /**
     * Generate background image files using original image. {@link Context}
     * passed to this method must be of an {@link Activity} to determine the size
     * of display.
     *
     * @param context The context require for the operations.
     * @param bitmap Image bitmap to save as background.
     * @return Returns whether the images generated successfully.
     */
    fun generateImageFiles(@NonNull context: Context, bitmap: Bitmap?): Boolean {

        if (bitmap == null || context !is Activity) {
            return false
        }

        val size = ViewUtils.getDisplaySize(context, true)
        val isLandscape = ViewUtils.getDisplayOrientation(context) == Configuration.ORIENTATION_LANDSCAPE
        var error: Error? = null

        if (isLandscape) {
            error = ImageUtils.saveForDisplayResolution(bitmap, size, TermuxConstants.TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH, TermuxConstants.TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH)

        } else {
            error = ImageUtils.saveForDisplayResolution(bitmap, DataUtils.swap(size), TermuxConstants.TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH, TermuxConstants.TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH)
        }

        return error == null
    }

    /**
     * Updates background to image or solid color. If forced then load again even if
     * the background is already set. Forced update is require when the display orientation
     * is changed.
     *
     * @param forced Force background update task.
     */
    fun updateBackground(forced: Boolean) {
        if (!activity.isVisible) return

        if (activity.preferences.isBackgroundImageEnabled) {

            val drawable = activity.window.decorView.background

            // If it's not forced update and background is already drawn,
            // then avoid reloading of image.
            if (!forced && ImageUtils.isBitmapDrawable(drawable)) {
                return
            }

            updateBackgroundImage()
        } else {
            updateBackgroundColor()
        }

        updateToolbarBackground()
    }

    /**
     * Set background to color.
     */
    fun updateBackgroundColor() {
        if (!activity.isVisible) return

        val session = activity.currentSession

        if (session != null && session.emulator != null) {
            activity.window.decorView.setBackgroundColor(session.emulator.colors.mCurrentColors[TextStyle.COLOR_INDEX_BACKGROUND])
        }
    }

    /**
     * Set background to image corresponding to display orientation.
     */
    fun updateBackgroundImage() {
        val isLandscape = ViewUtils.getDisplayOrientation(activity) == Configuration.ORIENTATION_LANDSCAPE
        val imagePath = if (isLandscape) TermuxConstants.TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH else TermuxConstants.TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH

        try {
            // Performing on main Thread may cause ANR and lag.
            executor.execute {
                if (isImageFilesExist(activity, true)) {
                    val drawable = ImageUtils.getDrawable(imagePath)
                    ImageUtils.addOverlay(drawable, activity.properties.backgroundOverlayColor)

                    handler.post { activity.window.decorView.background = drawable }
                } else {
                    Logger.logErrorAndShowToast(activity, LOG_TAG, activity.getString(R.string.error_background_image_loading_failed))

                    // Image files are unable to load so set background to solid color and notify update.
                    handler.post { this@TermuxBackgroundManager.updateBackgroundColor() }
                    notifyBackgroundUpdated(false)
                }
            }

        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load image", e)
            Logger.showToast(activity, activity.getString(R.string.error_background_image_loading_failed), true)

            // Since loading of image is failed, Set background to solid color.
            updateBackgroundColor()
            notifyBackgroundUpdated(false)
        }
    }

    /**
     * Set backgroudn of the ExtraKey toolbar and buttons.
     * Must be called when background preference is changed.
     */
    fun updateToolbarBackground() {
        val viewPager = activity.terminalToolbarViewPager
        val extraKeysView = activity.extraKeysView

        if (viewPager == null || extraKeysView == null) {
            return
        }

        if (preferences.isBackgroundImageEnabled) {
            // Set overlay background to ToolbarViewPager and make button transparent.
            activity.terminalToolbarViewPager.setBackgroundColor(activity.properties.backgroundOverlayColor)
            extraKeysView.setButtonBackgroundColor(Color.TRANSPARENT)

        } else {
            // Use default background color of ToolbarViewPager and button.
            viewPager.setBackgroundColor(Color.BLACK)
            extraKeysView.setButtonBackgroundColor(ThemeUtils.getSystemAttrColor(activity, ExtraKeysView.ATTR_BUTTON_BACKGROUND_COLOR, ExtraKeysView.DEFAULT_BUTTON_BACKGROUND_COLOR))
        }
    }

    /**
     * Notify that the background is changed. New background can be image or solid color.
     *
     * @param isImage The {@code boolean} indicates that new background is image or not.
     */
    fun notifyBackgroundUpdated(isImage: Boolean) {
        preferences.setBackgroundImageEnabled(isImage)
        TermuxActivity.updateTermuxActivityStyling(activity, false)
    }
}
