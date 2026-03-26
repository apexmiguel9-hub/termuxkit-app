package com.termux.shared.image

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Bitmap.CompressFormat
import android.graphics.BitmapFactory
import android.graphics.BlendMode
import android.graphics.BlendModeColorFilter
import android.graphics.ImageDecoder
import android.graphics.Point
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.file.FileUtilsErrno
import com.termux.shared.logger.Logger
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object ImageUtils {

    /**
     * Request code that can be used to distinguish Activity result.
     */
    const val REQUEST_CODE_IMAGE = 100

    /**
     * Compression quality used to compress image. The value is interpreted differently depending on the [CompressFormat].
     */
    const val COMPRESS_QUALITY = 80

    /**
     * Tolerance for diffrence in original image required optimized image.
     */
    const val OPTIMALITY_TOLERANCE = 50

    const val IMAGE_TYPE = "image"

    const val ANY_IMAGE_TYPE = "$IMAGE_TYPE/*"

    private const val LOG_TAG = "ImageUtils"

    /**
     * Get an [Bitmap] image from the [Uri].
     *
     * @param context The context for the operations.
     * @param uri     The uri from where image content will be loaded.
     * @return Bitmap containing the image, or return `null` if failed to
     * load bitmap content.
     */
    fun getBitmap(context: Context, uri: Uri?): Bitmap? {
        var bitmap: Bitmap? = null

        uri?.let {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, it))
                } else {
                    bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
                }
                bitmap = MediaStore.Images.Media.getBitmap(context.contentResolver, it)
            } catch (e: IOException) {
                Logger.logStackTraceWithMessage(LOG_TAG, "Failed to load bitmap from $it", e)
            }
        }
        return bitmap
    }

    /**
     * Generate image bitmap from the path.
     *
     * @param path The path for image file
     * @return Bitmap generated from image path, if fails to
     * to generate returns `null`
     */
    fun getBitmap(path: String?): Bitmap? {
        return BitmapFactory.decodeFile(path)
    }

    /**
     * Creates an centered and resized [Bitmap] according to given size.
     *
     * @param bitmap Original bitmap source to resize.
     * @param point  Target size containing width and height to resize.
     * @return Returns the resized bitmap for given size.
     */
    fun resizeBitmap(bitmap: Bitmap?, point: Point?): Bitmap? {
        if (point == null) return null
        return ThumbnailUtils.extractThumbnail(bitmap, point.x, point.y)
    }

    /**
     * Wrapper for [compressAndSaveBitmap] with `[COMPRESS_QUALITY] `quality`.
     */
    fun compressAndSaveBitmap(bitmap: Bitmap, path: String): Error? {
        return compressAndSaveBitmap(bitmap, COMPRESS_QUALITY, path)
    }

    /**
     * Wrapper for [compressAndSaveBitmap] with `Bitmap.CompressFormat.JPEG` `format`.
     */
    fun compressAndSaveBitmap(bitmap: Bitmap, quality: Int, path: String): Error? {
        return compressAndSaveBitmap(bitmap, CompressFormat.JPEG, quality, path)
    }

    /**
     * Compress the given bitmap image file for given format and quality.
     *
     * @param bitmap  Original source bitmap.
     * @param format  The format for image compression.
     * @param quality Hint to the compressor, 0-100. The value is interpreted differently
     *               depending on the [Bitmap.CompressFormat].
     * @param path    The path to store compressed bitmap.
     * @return Returns the `error` if compression and save operation was not successful,
     * otherwise `null`.
     */
    fun compressAndSaveBitmap(bitmap: Bitmap, format: CompressFormat, quality: Int, path: String): Error? {
        FileUtils.deleteRegularFile(null, path, true)
        var error = FileUtils.createRegularFile(path)

        if (error != null)
            return error

        try {
            FileOutputStream(path).use { out ->
                bitmap.compress(format, quality, out)
            }
        } catch (e: Exception) {
            FileUtils.deleteRegularFile(null, path, true)
            error = FileUtilsErrno.ERRNO_CREATING_FILE_FAILED_WITH_EXCEPTION.getError(e, e.message)
        }

        return error
    }

    /**
     * Wrapper for [getDrawable] with `file.getAbsolutePath()` `path` of file.
     */
    fun getDrawable(file: File?): Drawable? {
        val path = file?.absolutePath

        return getDrawable(path)
    }

    /**
     * Create [BitmapDrawable] from specified file path.
     *
     * @param path The path file to load image bitmap drawable.
     * @return Drawable created from image file path.
     */
    fun getDrawable(path: String?): Drawable? {
        return BitmapDrawable.createFromPath(path)
    }

    /**
     * Add an overlay color/tint on image with [BlendMode.MULTIPLY].
     *
     * @param drawable The source image bitmap drawable.
     * @param color    Overlay color for image.
     */
    fun addOverlay(drawable: Drawable?, color: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            drawable?.colorFilter = BlendModeColorFilter(color, BlendMode.DARKEN)
        } else {
            drawable?.colorFilter = PorterDuffColorFilter(color, PorterDuff.Mode.DARKEN)
        }
    }

    /**
     * Wrapper for [isImageOptimized] with `[OPTIMALITY_TOLERANCE]` `tolerance`.
     */
    fun isImageOptimized(path: String?, point: Point?): Boolean {
        return isImageOptimized(path, point, OPTIMALITY_TOLERANCE)
    }

    /**
     * Wrapper for [isImageOptimized] with `width` and `height` obtained from [Point].
     */
    fun isImageOptimized(path: String?, point: Point?, tolerance: Int): Boolean {
        if (point == null) return false
        return isImageOptimized(path, point.x, point.y, tolerance)
    }

    /**
     * Check whether the image file present at file location is optimized corresponding
     * to given width and height. It can tolorent error upto given value.
     *
     * @param path      The file path of image.
     * @param width     The required width of image file.
     * @param height    The required width of image file.
     * @param tolerance The tolerance value upto which diffrence is ignored.
     * @return Returns whether the given image is optimized or not.
     */
    fun isImageOptimized(path: String?, width: Int, height: Int, tolerance: Int): Boolean {
        if (!FileUtils.regularFileExists(path, false)) {
            Logger.logInfo(LOG_TAG, "Image file $path does not exist.")
            return false
        }

        val opt = BitmapFactory.Options()
        opt.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, opt)

        val imgWidth = opt.outWidth
        val imgHeight = opt.outHeight

        return Math.abs(imgWidth - width) <= tolerance && Math.abs(imgHeight - height) <= tolerance
    }

    fun isImage(path: String?): Boolean {
        if (!FileUtils.regularFileExists(path, false)) {
            Logger.logInfo(LOG_TAG, "Image file $path does not exist.")
            return false
        }

        val opt = BitmapFactory.Options()
        opt.inJustDecodeBounds = true
        BitmapFactory.decodeFile(path, opt)

        return opt.outWidth != -1 && opt.outHeight != -1
    }

    /**
     * Resize bitmap for [Configuration.ORIENTATION_PORTRAIT Portrait] and [
     * Configuration.ORIENTATION_LANDSCAPE Landscape] display view.
     * Also Compress the image bitmap before saving it to given path.
     *
     * @param bitmap The original bitmap image to resize and store.
     * @param point  Display resolution containing width and height.
     * @param path1  The path for storing image with width point.x and height point.y
     * @param path2  The path for storing image with width point.y and height point.x
     * @return Returns the `error` if save operation was not successful,
     * otherwise `null`.
     */
    fun saveForDisplayResolution(bitmap: Bitmap, point: Point, path1: String, path2: String): Error? {
        var error: Error?
        val bitmap1 = resizeBitmap(bitmap, point) ?: return null
        error = compressAndSaveBitmap(bitmap1, path1)

        if (error != null) {
            return error
        }

        val bitmap2 = resizeBitmap(bitmap, Point(point.y, point.x)) ?: return null
        error = compressAndSaveBitmap(bitmap2, path2)

        return error
    }

    /**
     * Check for the given [Drawable] whether it is instance of [
     * BitmapDrawable] or not.
     *
     * @param drawable The drawable to check.
     * @return Retruns whether drawable is bitmap drawable.
     */
    fun isBitmapDrawable(drawable: Drawable?): Boolean {
        return drawable is BitmapDrawable
    }
}