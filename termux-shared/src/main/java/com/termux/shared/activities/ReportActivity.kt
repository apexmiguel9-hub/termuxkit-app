package com.termux.shared.activities

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import androidx.annotation.NonNull
import androidx.appcompat.app.ActionBar
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.termux.shared.R
import com.termux.shared.activity.media.AppCompatActivityUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.errors.Error
import com.termux.shared.file.FileUtils
import com.termux.shared.file.filesystem.FileType
import com.termux.shared.interact.ShareUtils
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import com.termux.shared.models.ReportInfo
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.theme.NightMode
import io.noties.markwon.Markwon
import io.noties.markwon.recycler.MarkwonAdapter
import java.io.File
import io.noties.markwon.recycler.SimpleEntry
import org.commonmark.node.FencedCodeBlock

/**
 * An activity to show reports in markdown format as per CommonMark spec based on config passed as [ReportInfo].
 * Add Following to `AndroidManifest.xml` to use in an app:
 * ```xml
 * <activity android:name="com.termux.shared.activities.ReportActivity" android:theme="@style/Theme.AppCompat.TermuxReportActivity" android:documentLaunchMode="intoExisting" />
 * ```
 * and
 * ```xml
 * <receiver android:name="com.termux.shared.activities.ReportActivity.ReportActivityBroadcastReceiver"  android:exported="false" />
 * ```
 * Receiver **must not** be `exported="true"`!!!
 *
 * Also make an incremental call to [deleteReportInfoFilesOlderThanXDays]
 * in the app to cleanup cached files.
 */
open class ReportActivity : AppCompatActivity() {

    companion object {
        const val REQUEST_GRANT_STORAGE_PERMISSION_FOR_SAVE_FILE = 1000
        const val ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES = 1000 * 1024 // 1MB

        private const val LOG_TAG = "ReportActivity"
        private const val CACHE_FILE_BASENAME_PREFIX = "report_info_"
        private val EXTRA_REPORT_INFO_OBJECT = "${ReportActivity::class.java.canonicalName}.EXTRA_REPORT_INFO_OBJECT"
        private val EXTRA_REPORT_INFO_OBJECT_FILE_PATH = "${ReportActivity::class.java.canonicalName}.EXTRA_REPORT_INFO_OBJECT_FILE_PATH"
        private val ACTION_DELETE_REPORT_INFO_OBJECT_FILE = "${ReportActivity::class.java.canonicalName}.ACTION_DELETE_REPORT_INFO_OBJECT_FILE"
        private const val CACHE_DIR_BASENAME = "report_activity"

        /**
         * Start the [ReportActivity].
         *
         * @param context The [Context] for operations.
         * @param reportInfo The [ReportInfo] containing info that needs to be displayed.
         */
        @JvmStatic
        fun startReportActivity(@NonNull context: Context, @NonNull reportInfo: ReportInfo) {
            val result = newInstance(context, reportInfo)
            if (result.contentIntent != null)
                context.startActivity(result.contentIntent)
        }

        /**
         * Get content and delete intents for the [ReportActivity] that can be used to start it
         * and do cleanup.
         *
         * If [ReportInfo] size is too large, then a TransactionTooLargeException will be thrown
         * so its object may be saved to a file in the [Context.getCacheDir()]. Then when activity
         * starts, its read back and the file is deleted in [onDestroy].
         * Note that files may still be left if [onDestroy] is not called or doesn't finish.
         * A separate cleanup routine is implemented from that case by
         * [deleteReportInfoFilesOlderThanXDays] which should be called
         * incrementally or at app startup.
         *
         * @param context The [Context] for operations.
         * @param reportInfo The [ReportInfo] containing info that needs to be displayed.
         * @return Returns [NewInstanceResult].
         */
        @JvmStatic
        @NonNull
        fun newInstance(@NonNull context: Context, @NonNull reportInfo: ReportInfo): NewInstanceResult {
            val size = DataUtils.getSerializedSize(reportInfo)
            if (size > DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES) {
                val reportInfoDirectoryPath = getReportInfoDirectoryPath(context)
                val reportInfoFilePath = "$reportInfoDirectoryPath/$CACHE_FILE_BASENAME_PREFIX${reportInfo.reportTimestamp}"
                Logger.logVerbose(LOG_TAG, "${reportInfo.reportTitle} ${ReportInfo::class.java.simpleName} serialized object size $size is greater than ${DataUtils.TRANSACTION_SIZE_LIMIT_IN_BYTES} and it will be written to file at path \"$reportInfoFilePath\"")
                val error = FileUtils.writeSerializableObjectToFile(ReportInfo::class.java.simpleName, reportInfoFilePath, reportInfo)
                if (error != null) {
                    Logger.logErrorExtended(LOG_TAG, error.toString())
                    Logger.showToast(context, Error.getMinimalErrorString(error), true)
                    return NewInstanceResult(null, null)
                }

                return NewInstanceResult(createContentIntent(context, null, reportInfoFilePath),
                    createDeleteIntent(context, reportInfoFilePath))
            } else {
                return NewInstanceResult(createContentIntent(context, reportInfo, null),
                    null)
            }
        }

        private fun createContentIntent(@NonNull context: Context, reportInfo: ReportInfo?, reportInfoFilePath: String?): Intent {
            val intent = Intent(context, ReportActivity::class.java)
            val bundle = Bundle()

            if (reportInfoFilePath != null) {
                bundle.putString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH, reportInfoFilePath)
            } else {
                bundle.putSerializable(EXTRA_REPORT_INFO_OBJECT, reportInfo)
            }

            intent.putExtras(bundle)

            // Note that ReportActivity should have `documentLaunchMode="intoExisting"` set in `AndroidManifest.xml`
            // which has equivalent behaviour to FLAG_ACTIVITY_NEW_DOCUMENT.
            // FLAG_ACTIVITY_SINGLE_TOP must also be passed for onNewIntent to be called.
            intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NEW_DOCUMENT
            return intent
        }

        private fun createDeleteIntent(@NonNull context: Context, reportInfoFilePath: String?): Intent? {
            if (reportInfoFilePath == null) return null

            val intent = Intent(context, ReportActivityBroadcastReceiver::class.java)
            intent.action = ACTION_DELETE_REPORT_INFO_OBJECT_FILE
            intent.putExtra(EXTRA_REPORT_INFO_OBJECT_FILE_PATH, reportInfoFilePath)
            return intent
        }

        /**
         * Get the directory path where [ReportInfo] files are cached.
         *
         * @param context The [Context] for operations.
         * @return Returns the directory path.
         */
        @NonNull
        private fun getReportInfoDirectoryPath(@NonNull context: Context): String {
            val reportInfoDirectory = File(context.cacheDir, CACHE_DIR_BASENAME)
            if (!reportInfoDirectory.exists())
                reportInfoDirectory.mkdirs()
            return reportInfoDirectory.absolutePath
        }

        /**
         * Delete [ReportInfo] files older than X days from the cache directory.
         *
         * @param context The [Context] for operations.
         * @param days The number of days. Files older than this will be deleted.
         * @return Returns the number of files deleted.
         */
        @JvmStatic
        fun deleteReportInfoFilesOlderThanXDays(@NonNull context: Context, days: Int): Int {
            var filesDeleted = 0
            val reportInfoDirectory = File(context.cacheDir, CACHE_DIR_BASENAME)
            if (reportInfoDirectory.exists() && reportInfoDirectory.isDirectory) {
                val files = reportInfoDirectory.listFiles()
                if (files != null) {
                    for (file in files) {
                        if (file.isFile && file.name.startsWith(CACHE_FILE_BASENAME_PREFIX)) {
                            val fileAgeInDays = (System.currentTimeMillis() - file.lastModified()) / (24 * 60 * 60 * 1000)
                            if (fileAgeInDays > days) {
                                FileUtils.deleteRegularFile("ReportInfo cache file", file.absolutePath, true)
                                filesDeleted++
                            }
                        }
                    }
                }
            }
            return filesDeleted
        }

        private fun deleteReportInfoFile(context: Context?, reportInfoFilePath: String?) {
            if (context == null || reportInfoFilePath == null) return

            // Extra protection for mainly if someone set `exported="true"` for ReportActivityBroadcastReceiver
            val reportInfoDirectoryPath = getReportInfoDirectoryPath(context)
            val canonicalFilePath = FileUtils.getCanonicalPath(reportInfoFilePath, null)
            if (canonicalFilePath != reportInfoDirectoryPath && canonicalFilePath.startsWith("$reportInfoDirectoryPath/")) {
                Logger.logVerbose(LOG_TAG, "Deleting ${ReportInfo::class.java.simpleName} serialized object file at path \"$reportInfoFilePath\"")
                val error = FileUtils.deleteRegularFile(ReportInfo::class.java.simpleName, reportInfoFilePath, true)
                if (error != null) {
                    Logger.logErrorExtended(LOG_TAG, error.toString())
                }
            } else {
                Logger.logError(LOG_TAG, "Not deleting ${ReportInfo::class.java.simpleName} serialized object file at path \"$reportInfoFilePath\" since its not under \"$reportInfoDirectoryPath\"")
            }
        }
    }

    private var reportInfo: ReportInfo? = null
    private var reportInfoFilePath: String? = null
    private var reportActivityMarkdownString: String? = null
    private var bundle: Bundle? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Logger.logVerbose(LOG_TAG, "onCreate")

        AppCompatActivityUtils.setNightMode(this, NightMode.getAppNightMode().name, true)

        setContentView(R.layout.activity_report)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        if (toolbar != null) {
            setSupportActionBar(toolbar)
        }

        bundle = null
        val intent = intent
        if (intent != null)
            bundle = intent.extras
        else if (savedInstanceState != null)
            bundle = savedInstanceState

        updateUI()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Logger.logVerbose(LOG_TAG, "onNewIntent")

        setIntent(intent)

        if (intent != null) {
            deleteReportInfoFile(this, reportInfoFilePath)
            bundle = intent.extras
            updateUI()
        }
    }

    private fun updateUI() {
        if (bundle == null) {
            finish()
            return
        }

        reportInfo = null
        reportInfoFilePath = null

        if ((bundle?.containsKey(EXTRA_REPORT_INFO_OBJECT_FILE_PATH)) == true) {
            reportInfoFilePath = bundle?.getString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH)
            Logger.logVerbose(LOG_TAG, "${ReportInfo::class.java.simpleName} serialized object will be read from file at path \"$reportInfoFilePath\"")
            if (reportInfoFilePath != null) {
                try {
                    val result = FileUtils.readSerializableObjectFromFile(ReportInfo::class.java.simpleName, reportInfoFilePath, ReportInfo::class.java, false)
                    if (result.error != null) {
                        Logger.logErrorExtended(LOG_TAG, result.error.toString())
                        Logger.showToast(this, Error.getMinimalErrorString(result.error), true)
                        finish()
                        return
                    } else {
                        if (result.serializableObject != null)
                            reportInfo = result.serializableObject as ReportInfo
                    }
                } catch (e: Exception) {
                    Logger.logErrorAndShowToast(this, LOG_TAG, e.message ?: "")
                    Logger.logStackTraceWithMessage(LOG_TAG, "Failure while getting ${ReportInfo::class.java.simpleName} serialized object from file at path \"$reportInfoFilePath\"", e)
                }
            }
        } else {
            reportInfo = bundle?.getSerializable(EXTRA_REPORT_INFO_OBJECT) as ReportInfo?
        }

        if (reportInfo == null) {
            finish()
            return
        }

        val actionBar: ActionBar? = supportActionBar
        if (actionBar != null) {
            actionBar.title = reportInfo?.reportTitle ?: "${TermuxConstants.TERMUX_APP_NAME} App Report"
        }

        val recyclerView = findViewById<RecyclerView>(R.id.recycler_view)

        val markwon = MarkdownUtils.getRecyclerMarkwonBuilder(this)

        val adapter = MarkwonAdapter.builderTextViewIsRoot(R.layout.markdown_adapter_node_default)
            .include(FencedCodeBlock::class.java, SimpleEntry.create(R.layout.markdown_adapter_node_code_block, R.id.code_text_view))
            .build()

        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        generateReportActivityMarkdownString()
        adapter.setMarkdown(markwon, reportActivityMarkdownString ?: "")
        adapter.notifyDataSetChanged()
    }

    override fun onSaveInstanceState(@NonNull outState: Bundle) {
        super.onSaveInstanceState(outState)
        if ((bundle?.containsKey(EXTRA_REPORT_INFO_OBJECT_FILE_PATH)) == true) {
            outState.putString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH, reportInfoFilePath)
        } else {
            outState.putSerializable(EXTRA_REPORT_INFO_OBJECT, reportInfo)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Logger.logVerbose(LOG_TAG, "onDestroy")

        deleteReportInfoFile(this, reportInfoFilePath)
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.menu_report, menu)

        if (reportInfo?.reportSaveFilePath == null) {
            val item = menu.findItem(R.id.menu_item_save_report_to_file)
            if (item != null)
                item.isEnabled = false
        }

        return true
    }

    override fun onBackPressed() {
        // Remove activity from recents menu on back button press
        finishAndRemoveTask()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.menu_item_share_report -> {
                ShareUtils.shareText(this, getString(R.string.title_report_text), ReportInfo.getReportInfoMarkdownString(reportInfo))
                true
            }
            R.id.menu_item_copy_report -> {
                ShareUtils.copyTextToClipboard(this, ReportInfo.getReportInfoMarkdownString(reportInfo), null)
                true
            }
            R.id.menu_item_save_report_to_file -> {
                ShareUtils.saveTextToFile(this, reportInfo?.reportSaveFileLabel,
                    reportInfo?.reportSaveFilePath, ReportInfo.getReportInfoMarkdownString(reportInfo),
                    true, REQUEST_GRANT_STORAGE_PERMISSION_FOR_SAVE_FILE)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Logger.logInfo(LOG_TAG, "Storage permission granted by user on request.")
            if (requestCode == REQUEST_GRANT_STORAGE_PERMISSION_FOR_SAVE_FILE) {
                ShareUtils.saveTextToFile(this, reportInfo?.reportSaveFileLabel ?: "",
                    reportInfo?.reportSaveFilePath ?: "", ReportInfo.getReportInfoMarkdownString(reportInfo) ?: "",
                    true, -1)
            }
        } else {
            Logger.logInfo(LOG_TAG, "Storage permission denied by user on request.")
        }
    }

    /**
     * Generate the markdown [String] to be shown in [ReportActivity].
     */
    private fun generateReportActivityMarkdownString() {
        // We need to reduce chances of OutOfMemoryError happening so reduce new allocations and
        // do not keep output of getReportInfoMarkdownString in memory
        val reportString = StringBuilder()

        if (reportInfo?.reportStringPrefix != null)
            reportString.append(reportInfo?.reportStringPrefix)

        val reportMarkdownString = ReportInfo.getReportInfoMarkdownString(reportInfo)
        val reportMarkdownStringSize = reportMarkdownString.toByteArray().size
        var truncated = false
        if (reportMarkdownStringSize > ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES) {
            Logger.logVerbose(LOG_TAG, "${reportInfo?.reportTitle} report string size $reportMarkdownStringSize is greater than $ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES and will be truncated")
            reportString.append(DataUtils.getTruncatedCommandOutput(reportMarkdownString, ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES, true, false, true))
            truncated = true
        } else {
            reportString.append(reportMarkdownString)
        }

        // Free reference
        val reportMarkdownStringRef: String? = null

        if (reportInfo?.reportStringSuffix != null)
            reportString.append(reportInfo?.reportStringSuffix)

        val reportStringSize = reportString.length
        reportActivityMarkdownString = if (reportStringSize > ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES) {
            // This may break markdown formatting
            Logger.logVerbose(LOG_TAG, "${reportInfo?.reportTitle} report string total size $reportStringSize is greater than $ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES and will be truncated")
            getString(R.string.msg_report_truncated) + DataUtils.getTruncatedCommandOutput(reportString.toString(), ACTIVITY_TEXT_SIZE_LIMIT_IN_BYTES, true, false, false)
        } else if (truncated) {
            getString(R.string.msg_report_truncated) + reportString.toString()
        } else {
            reportString.toString()
        }
    }

    class NewInstanceResult(
        /** An intent that can be used to start the [ReportActivity]. */
        val contentIntent: Intent?,
        /** An intent that can should be adding as the `android.app.Notification.deleteIntent`
         * by a call to `android.app.PendingIntent.getBroadcast(Context, Int, Intent, Int)`
         * so that [ReportActivityBroadcastReceiver] can do cleanup of [EXTRA_REPORT_INFO_OBJECT_FILE_PATH]. */
        val deleteIntent: Intent?
    )

    /**
     * The [BroadcastReceiver] for [ReportActivity] that currently does cleanup when
     * `android.app.Notification.deleteIntent` is called. It must be registered in `AndroidManifest.xml`.
     */
    class ReportActivityBroadcastReceiver : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (intent == null) return

            val action = intent.action
            Logger.logVerbose(LOG_TAG, "onReceive: \"$action\" action")

            if (ACTION_DELETE_REPORT_INFO_OBJECT_FILE == action) {
                val bundle = intent.extras ?: return
                if (bundle.containsKey(EXTRA_REPORT_INFO_OBJECT_FILE_PATH)) {
                    ReportActivity.deleteReportInfoFile(context, bundle.getString(EXTRA_REPORT_INFO_OBJECT_FILE_PATH))
                }
            }
        }

        companion object {
            private const val LOG_TAG = "ReportActivityBroadcastReceiver"
        }
    }
}
