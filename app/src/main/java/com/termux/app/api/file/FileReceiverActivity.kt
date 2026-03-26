package com.termux.app.api.file

import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Patterns
import androidx.appcompat.app.AppCompatActivity
import com.termux.R
import com.termux.shared.android.PackageUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.data.IntentUtils
import com.termux.shared.net.uri.UriUtils
import com.termux.shared.interact.MessageDialogUtils
import com.termux.shared.net.uri.UriScheme
import com.termux.shared.termux.interact.TextInputDialogUtils
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_APP
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_SERVICE
import com.termux.app.TermuxService
import com.termux.shared.logger.Logger
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties
import com.termux.shared.termux.settings.properties.TermuxPropertyConstants
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class FileReceiverActivity : AppCompatActivity() {

    var finishOnDismissNameDialog = true

    companion object {
        const val TERMUX_RECEIVEDIR = TermuxConstants.TERMUX_FILES_DIR_PATH + "/home/downloads"
        const val EDITOR_PROGRAM = TermuxConstants.TERMUX_HOME_DIR_PATH + "/bin/termux-file-editor"
        const val URL_OPENER_PROGRAM = TermuxConstants.TERMUX_HOME_DIR_PATH + "/bin/termux-url-opener"

        private const val API_TAG = TermuxConstants.TERMUX_APP_NAME + "FileReceiver"
        private const val LOG_TAG = "FileReceiverActivity"

        fun isSharedTextAnUrl(sharedText: String?): Boolean {
            if (sharedText == null || sharedText.isEmpty()) return false

            return Patterns.WEB_URL.matcher(sharedText).matches()
                || Pattern.matches("magnet:\\?xt=urn:btih:.*?", sharedText)
        }

        /**
         * Update [TERMUX_APP.FILE_SHARE_RECEIVER_ACTIVITY_CLASS_NAME] component state depending on
         * [TermuxPropertyConstants.KEY_DISABLE_FILE_SHARE_RECEIVER] value and
         * [TERMUX_APP.FILE_VIEW_RECEIVER_ACTIVITY_CLASS_NAME] component state depending on
         * [TermuxPropertyConstants.KEY_DISABLE_FILE_VIEW_RECEIVER] value.
         */
        @JvmStatic
        fun updateFileReceiverActivityComponentsState(context: Context) {
            Thread {
                val properties = TermuxAppSharedProperties.getProperties()

                var errmsg: String?
                var state: Boolean

                state = !properties.isFileShareReceiverDisabled()
                Logger.logVerbose(LOG_TAG, "Setting ${TERMUX_APP.FILE_SHARE_RECEIVER_ACTIVITY_CLASS_NAME} component state to $state")
                errmsg = PackageUtils.setComponentState(
                    context, TermuxConstants.TERMUX_PACKAGE_NAME,
                    TERMUX_APP.FILE_SHARE_RECEIVER_ACTIVITY_CLASS_NAME,
                    state, null, false, false
                )
                if (errmsg != null)
                    Logger.logError(LOG_TAG, errmsg)

                state = !properties.isFileViewReceiverDisabled()
                Logger.logVerbose(LOG_TAG, "Setting ${TERMUX_APP.FILE_VIEW_RECEIVER_ACTIVITY_CLASS_NAME} component state to $state")
                errmsg = PackageUtils.setComponentState(
                    context, TermuxConstants.TERMUX_PACKAGE_NAME,
                    TERMUX_APP.FILE_VIEW_RECEIVER_ACTIVITY_CLASS_NAME,
                    state, null, false, false
                )
                if (errmsg != null)
                    Logger.logError(LOG_TAG, errmsg)
            }.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_receiver)
    }

    override fun onResume() {
        super.onResume()

        val intent = intent
        val action = intent.action
        val type = intent.type
        val scheme = intent.scheme

        Logger.logVerbose(LOG_TAG, "Intent Received:\n${IntentUtils.getIntentString(intent)}")

        val sharedTitle = IntentUtils.getStringExtraIfSet(intent, Intent.EXTRA_TITLE, null)

        if (Intent.ACTION_SEND == action && type != null) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            val sharedUri = intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM)

            if (sharedUri != null) {
                handleContentUri(sharedUri, sharedTitle)
            } else if (sharedText != null) {
                if (isSharedTextAnUrl(sharedText)) {
                    handleUrlAndFinish(sharedText)
                } else {
                    var subject = IntentUtils.getStringExtraIfSet(intent, Intent.EXTRA_SUBJECT, null)
                    if (subject == null) subject = sharedTitle
                    if (subject != null) subject += ".txt"
                    promptNameAndSave(ByteArrayInputStream(sharedText.toByteArray(StandardCharsets.UTF_8)), subject)
                }
            } else {
                showErrorDialogAndQuit("Send action without content - nothing to save.")
            }
        } else {
            val dataUri = intent.data

            if (dataUri == null) {
                showErrorDialogAndQuit("Data uri not passed.")
                return
            }

            when (scheme) {
                UriScheme.SCHEME_CONTENT -> handleContentUri(dataUri, sharedTitle)
                UriScheme.SCHEME_FILE -> {
                    Logger.logVerbose(LOG_TAG, "uri: \"$dataUri\", path: \"${dataUri.path}\", fragment: \"${dataUri.fragment}\"")

                    // Get full path including fragment (anything after last "#")
                    val path = UriUtils.getUriFilePathWithFragment(dataUri)
                    if (DataUtils.isNullOrEmpty(path)) {
                        showErrorDialogAndQuit("File path from data uri is null, empty or invalid.")
                        return
                    }

                    val file = File(path)
                    try {
                        val `in` = FileInputStream(file)
                        promptNameAndSave(`in`, file.name)
                    } catch (e: FileNotFoundException) {
                        showErrorDialogAndQuit("Cannot open file: ${e.message}.")
                    }
                }
                else -> showErrorDialogAndQuit("Unable to receive any file or URL.")
            }
        }
    }

    private fun showErrorDialogAndQuit(message: String) {
        finishOnDismissNameDialog = false
        MessageDialogUtils.showMessage(
            this,
            API_TAG, message,
            null, { _, _ -> finish() },
            null, null
        ) { finish() }
    }

    private fun handleContentUri(uri: Uri, subjectFromIntent: String?) {
        try {
            Logger.logVerbose(LOG_TAG, "uri: \"$uri\", path: \"${uri.path}\", fragment: \"${uri.fragment}\"")

            var attachmentFileName: String? = null

            val projection = arrayOf(OpenableColumns.DISPLAY_NAME)
            contentResolver.query(uri, projection, null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val fileNameColumnId = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (fileNameColumnId >= 0) attachmentFileName = c.getString(fileNameColumnId)
                }
            }

            if (attachmentFileName == null) attachmentFileName = subjectFromIntent
            if (attachmentFileName == null) attachmentFileName = UriUtils.getUriFileBasename(uri, true)

            val `in` = contentResolver.openInputStream(uri)
            promptNameAndSave(`in`, attachmentFileName)
        } catch (e: Exception) {
            showErrorDialogAndQuit("Unable to handle shared content:\n\n${e.message}")
            Logger.logStackTraceWithMessage(LOG_TAG, "handleContentUri(uri=$uri) failed", e)
        }
    }

    private fun promptNameAndSave(`in`: InputStream?, attachmentFileName: String) {
        TextInputDialogUtils.textInput(
            this, R.string.title_file_received, attachmentFileName,
            R.string.action_file_received_edit
        ) { text ->
            val outFile = saveStreamWithName(`in`, text) ?: return@textInput

            val editorProgramFile = File(EDITOR_PROGRAM)
            if (!editorProgramFile.isFile) {
                showErrorDialogAndQuit(
                    "The following file does not exist:\n\$HOME/bin/termux-file-editor\n\n"
                            + "Create this file as a script or a symlink - it will be called with the received file as only argument."
                )
                return@textInput
            }

            // Do this for the user if necessary:
            editorProgramFile.setExecutable(true)

            val scriptUri = UriUtils.getFileUri(EDITOR_PROGRAM)

            val executeIntent = Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, scriptUri)
            executeIntent.setClass(this@FileReceiverActivity, TermuxService::class.java)
            executeIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, arrayOf(outFile.absolutePath))
            startService(executeIntent)
            finish()
        }
            .setNegativeButton(R.string.action_file_received_open_directory) { _, _ ->
                if (saveStreamWithName(`in`, attachmentFileName) == null) return@setNegativeButton

                val executeIntent = Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE)
                executeIntent.putExtra(TERMUX_SERVICE.EXTRA_WORKDIR, TERMUX_RECEIVEDIR)
                executeIntent.setClass(this@FileReceiverActivity, TermuxService::class.java)
                startService(executeIntent)
                finish()
            }
            .setNeutralButton(android.R.string.cancel) { _, _ -> finish() }
            .setOnDismissListener {
                if (finishOnDismissNameDialog) finish()
            }
            .show()
    }

    private fun saveStreamWithName(`in`: InputStream?, attachmentFileName: String): File? {
        val receiveDir = File(TERMUX_RECEIVEDIR)

        if (DataUtils.isNullOrEmpty(attachmentFileName)) {
            showErrorDialogAndQuit("File name cannot be null or empty")
            return null
        }

        if (!receiveDir.isDirectory && !receiveDir.mkdirs()) {
            showErrorDialogAndQuit("Cannot create directory: ${receiveDir.absolutePath}")
            return null
        }

        return try {
            val outFile = File(receiveDir, attachmentFileName)
            FileOutputStream(outFile).use { f ->
                val buffer = ByteArray(4096)
                var readBytes: Int
                while (`in`?.read(buffer).also { readBytes = it } > 0) {
                    f.write(buffer, 0, readBytes)
                }
            }
            outFile
        } catch (e: IOException) {
            showErrorDialogAndQuit("Error saving file:\n\n$e")
            Logger.logStackTraceWithMessage(LOG_TAG, "Error saving file", e)
            null
        }
    }

    private fun handleUrlAndFinish(url: String) {
        val urlOpenerProgramFile = File(URL_OPENER_PROGRAM)
        if (!urlOpenerProgramFile.isFile) {
            showErrorDialogAndQuit(
                "The following file does not exist:\n\$HOME/bin/termux-url-opener\n\n"
                        + "Create this file as a script or a symlink - it will be called with the shared URL as the first argument."
            )
            return
        }

        // Do this for the user if necessary:
        urlOpenerProgramFile.setExecutable(true)

        val urlOpenerProgramUri = UriUtils.getFileUri(URL_OPENER_PROGRAM)

        val executeIntent = Intent(TERMUX_SERVICE.ACTION_SERVICE_EXECUTE, urlOpenerProgramUri)
        executeIntent.setClass(this@FileReceiverActivity, TermuxService::class.java)
        executeIntent.putExtra(TERMUX_SERVICE.EXTRA_ARGUMENTS, arrayOf(url))
        startService(executeIntent)
        finish()
    }
}
