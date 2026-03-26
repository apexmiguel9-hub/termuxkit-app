package com.termux.shared.android

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.os.Build
import com.google.common.base.Joiner
import com.termux.shared.R
import com.termux.shared.data.DataUtils
import com.termux.shared.logger.Logger
import com.termux.shared.markdown.MarkdownUtils
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties
import java.util.TimeZone
import java.util.regex.Pattern

object AndroidUtils {

    /**
     * Get a markdown [String] for the app info for the package associated with the [context].
     * This will contain additional info about the app in addition to the one returned by
     * [getAppInfoMarkdownString], which will be got via the [context]
     * object.
     *
     * @param context The context for operations for the package.
     * @return Returns the markdown [String].
     */
    fun getAppInfoMarkdownString(context: Context): String {
        val markdownString = StringBuilder()

        val appInfo = getAppInfoMarkdownString(context, context.packageName)
        if (appInfo == null)
            return markdownString.toString()
        else
            markdownString.append(appInfo)

        val filesDir = context.filesDir.absolutePath
        if (filesDir != "/data/user/0/${context.packageName}/files" &&
            filesDir != "/data/data/${context.packageName}/files")
            appendPropertyToMarkdown(markdownString, "FILES_DIR", filesDir)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val userId = PackageUtils.getUserIdForPackage(context)
            if (userId == null || userId != 0L)
                appendPropertyToMarkdown(markdownString, "USER_ID", userId ?: "null")
        }

        appendPropertyToMarkdownIfSet(markdownString, "PROFILE_OWNER", PackageUtils.getProfileOwnerPackageNameForUser(context) ?: "null")

        return markdownString.toString()
    }

    /**
     * Get a markdown [String] for the app info for the [packageName].
     *
     * @param context The [Context] for operations.
     * @param packageName The package name of the package.
     * @return Returns the markdown [String].
     */
    fun getAppInfoMarkdownString(context: Context, packageName: String): String {
        val packageInfo = PackageUtils.getPackageInfoForPackage(context, packageName) ?: return ""
        val applicationInfo = PackageUtils.getApplicationInfoForPackage(context, packageName) ?: return ""

        val markdownString = StringBuilder()

        appendPropertyToMarkdown(markdownString, "APP_NAME", PackageUtils.getAppNameForPackage(context, applicationInfo) ?: "null")
        appendPropertyToMarkdown(markdownString, "PACKAGE_NAME", PackageUtils.getPackageNameForPackage(applicationInfo))
        appendPropertyToMarkdown(markdownString, "VERSION_NAME", PackageUtils.getVersionNameForPackage(packageInfo) ?: "null")
        appendPropertyToMarkdown(markdownString, "VERSION_CODE", PackageUtils.getVersionCodeForPackage(packageInfo) ?: 0)
        appendPropertyToMarkdown(markdownString, "UID", PackageUtils.getUidForPackage(applicationInfo))
        appendPropertyToMarkdown(markdownString, "TARGET_SDK", PackageUtils.getTargetSDKForPackage(applicationInfo))
        appendPropertyToMarkdown(markdownString, "IS_DEBUGGABLE_BUILD", PackageUtils.isAppForPackageADebuggableBuild(applicationInfo))

        if (PackageUtils.isAppInstalledOnExternalStorage(applicationInfo)) {
            appendPropertyToMarkdown(markdownString, "APK_PATH", PackageUtils.getBaseAPKPathForPackage(applicationInfo))
            appendPropertyToMarkdown(markdownString, "IS_INSTALLED_ON_EXTERNAL_STORAGE", true)
        }

        appendPropertyToMarkdown(markdownString, "SE_PROCESS_CONTEXT", SELinuxUtils.getContext() ?: "")
        appendPropertyToMarkdown(markdownString, "SE_FILE_CONTEXT", SELinuxUtils.getFileContext(context.filesDir.absolutePath) ?: "")

        val seInfoUser = PackageUtils.getApplicationInfoSeInfoUserForPackage(applicationInfo)
        appendPropertyToMarkdown(markdownString, "SE_INFO", PackageUtils.getApplicationInfoSeInfoForPackage(applicationInfo) +
            (if (DataUtils.isNullOrEmpty(seInfoUser)) "" else seInfoUser))

        return markdownString.toString()
    }

    fun getDeviceInfoMarkdownString(context: Context): String {
        return getDeviceInfoMarkdownString(context, false)
    }

    /**
     * Get a markdown [String] for the device info.
     *
     * @param context The context for operations.
     * @param addPhantomProcessesInfo If phantom processes info should be added on Android >= 12.
     * @return Returns the markdown [String].
     */
    fun getDeviceInfoMarkdownString(context: Context, addPhantomProcessesInfo: Boolean): String {
        // Some properties cannot be read with [System.getProperty] but can be read
        // directly by running getprop command
        val systemProperties = getSystemProperties()

        val markdownString = StringBuilder()

        markdownString.append("## Device Info")

        markdownString.append("\n\n### Software\n")
        appendPropertyToMarkdownIfSet(markdownString, "OS_VERSION", getSystemPropertyWithAndroidAPI("os.version"))
        appendPropertyToMarkdown(markdownString, "SDK_INT", Build.VERSION.SDK_INT)
        // If its a release version
        if ("REL" == Build.VERSION.CODENAME)
            appendPropertyToMarkdown(markdownString, "RELEASE", Build.VERSION.RELEASE)
        else
            appendPropertyToMarkdown(markdownString, "CODENAME", Build.VERSION.CODENAME)
        appendPropertyToMarkdown(markdownString, "ID", Build.ID)
        appendPropertyToMarkdown(markdownString, "DISPLAY", Build.DISPLAY)
        appendPropertyToMarkdown(markdownString, "INCREMENTAL", Build.VERSION.INCREMENTAL)
        appendPropertyToMarkdownIfSet(markdownString, "SECURITY_PATCH", systemProperties.getProperty("ro.build.version.security_patch"))
        appendPropertyToMarkdownIfSet(markdownString, "IS_DEBUGGABLE", systemProperties.getProperty("ro.debuggable"))
        appendPropertyToMarkdownIfSet(markdownString, "IS_EMULATOR", systemProperties.getProperty("ro.boot.qemu"))
        appendPropertyToMarkdownIfSet(markdownString, "IS_TREBLE_ENABLED", systemProperties.getProperty("ro.treble.enabled"))
        appendPropertyToMarkdown(markdownString, "TYPE", Build.TYPE)
        appendPropertyToMarkdown(markdownString, "TAGS", Build.TAGS)

        // If on Android >= 12
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.R) {
            val maxPhantomProcesses = PhantomProcessUtils.getActivityManagerMaxPhantomProcesses(context)
            if (maxPhantomProcesses != null)
                appendPropertyToMarkdown(markdownString, "MAX_PHANTOM_PROCESSES", maxPhantomProcesses)
            else
                appendLiteralPropertyToMarkdown(markdownString, "MAX_PHANTOM_PROCESSES", "- (*${context.getString(R.string.msg_requires_dump_and_package_usage_stats_permissions)}*)")

            appendPropertyToMarkdown(markdownString, "MONITOR_PHANTOM_PROCS", PhantomProcessUtils.getFeatureFlagMonitorPhantomProcsValueString(context).name)
            appendPropertyToMarkdownIfSet(markdownString, "DEVICE_CONFIG_SYNC_DISABLED", PhantomProcessUtils.getSettingsGlobalDeviceConfigSyncDisabled(context))
        }

        markdownString.append("\n\n### Hardware\n")
        appendPropertyToMarkdown(markdownString, "MANUFACTURER", Build.MANUFACTURER)
        appendPropertyToMarkdown(markdownString, "BRAND", Build.BRAND)
        appendPropertyToMarkdown(markdownString, "MODEL", Build.MODEL)
        appendPropertyToMarkdown(markdownString, "PRODUCT", Build.PRODUCT)
        appendPropertyToMarkdown(markdownString, "BOARD", Build.BOARD)
        appendPropertyToMarkdown(markdownString, "HARDWARE", Build.HARDWARE)
        appendPropertyToMarkdown(markdownString, "DEVICE", Build.DEVICE)
        appendPropertyToMarkdown(markdownString, "SUPPORTED_ABIS", Joiner.on(", ").skipNulls().join(Build.SUPPORTED_ABIS))

        markdownString.append("\n##\n")

        return markdownString.toString()
    }

    fun getSystemProperties(): Properties {
        val systemProperties = Properties()

        // getprop commands returns values in the format `[key]: [value]`
        // Regex matches string starting with a literal `[`,
        // followed by one or more characters that do not match a closing square bracket as the key,
        // followed by a literal `]: [`,
        // followed by one or more characters as the value,
        // followed by string ending with literal `]`
        // multiline values will be ignored
        val propertiesPattern = Pattern.compile("^\\[([^]]+)]: \\[(.+)]$")

        try {
            val process = ProcessBuilder()
                .command("/system/bin/getprop")
                .redirectErrorStream(true)
                .start()

            val inputStream = process.inputStream
            val bufferedReader = BufferedReader(InputStreamReader(inputStream))
            var line: String?
            var key: String?
            var value: String?

            while (bufferedReader.readLine().also { line = it } != null) {
                val matcher = propertiesPattern.matcher(line)
                if (matcher.matches()) {
                    key = matcher.group(1)
                    value = matcher.group(2)
                    if (key != null && value != null && key.isNotEmpty() && value.isNotEmpty())
                        systemProperties[key] = value
                }
            }

            bufferedReader.close()
            process.destroy()

        } catch (e: IOException) {
            Logger.logStackTraceWithMessage("Failed to get run \"/system/bin/getprop\" to get system properties.", e)
        }

        //for (key in systemProperties.stringPropertyNames()) {
        //    Logger.logVerbose("$key: ${systemProperties[key]}")
        //}

        return systemProperties
    }

    fun getSystemPropertyWithAndroidAPI(property: String): String? {
        return try {
            System.getProperty(property)
        } catch (e: Exception) {
            Logger.logVerbose("Failed to get system property \"$property\":" + e.message)
            null
        }
    }

    fun appendPropertyToMarkdownIfSet(markdownString: StringBuilder, label: String, value: Any?) {
        if (value == null) return
        if (value is String && (value.isEmpty() || "REL" == value)) return
        markdownString.append("\n").append(getPropertyMarkdown(label, value))
    }

    fun appendPropertyToMarkdown(markdownString: StringBuilder, label: String, value: Any) {
        markdownString.append("\n").append(getPropertyMarkdown(label, value))
    }

    fun getPropertyMarkdown(label: String, value: Any): String {
        return MarkdownUtils.getSingleLineMarkdownStringEntry(label, value, "-")
    }

    fun appendLiteralPropertyToMarkdown(markdownString: StringBuilder, label: String, value: Any) {
        markdownString.append("\n").append(getLiteralPropertyMarkdown(label, value))
    }

    fun getLiteralPropertyMarkdown(label: String, value: Any): String {
        return MarkdownUtils.getLiteralSingleLineMarkdownStringEntry(label, value, "-")
    }

    fun getCurrentTimeStamp(): String {
        @SuppressLint("SimpleDateFormat")
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss z")
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(Date())
    }

    fun getCurrentMilliSecondUTCTimeStamp(): String {
        @SuppressLint("SimpleDateFormat")
        val df = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS z")
        df.timeZone = TimeZone.getTimeZone("UTC")
        return df.format(Date())
    }

    fun getCurrentMilliSecondLocalTimeStamp(): String {
        @SuppressLint("SimpleDateFormat")
        val df = SimpleDateFormat("yyyy-MM-dd_HH.mm.ss.SSS")
        df.timeZone = TimeZone.getDefault()
        return df.format(Date())
    }
}
