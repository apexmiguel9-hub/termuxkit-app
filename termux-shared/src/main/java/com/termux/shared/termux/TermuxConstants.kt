package com.termux.shared.termux

import android.annotation.SuppressLint
import android.content.Intent
import com.termux.shared.shell.command.ExecutionCommand
import com.termux.shared.shell.command.ExecutionCommand.Runner
import java.io.File
import java.util.Arrays.asList
import java.util.Formatter
import java.util.List

/*
 * Version: v0.53.0
 * SPDX-License-Identifier: MIT
 *
 * Changelog
 *
 * - 0.1.0 (2021-03-08)
 *      - Initial Release.
 *
 * - 0.2.0 (2021-03-11)
 *      - Added `_DIR` and `_FILE` substrings to paths.
 *      - Added `INTERNAL_PRIVATE_APP_DATA_DIR*`, `TERMUX_CACHE_DIR*`, `TERMUX_DATABASES_DIR*`,
 *          `TERMUX_SHARED_PREFERENCES_DIR*`, `TERMUX_BIN_PREFIX_DIR*`, `TERMUX_ETC_DIR*`,
 *          `TERMUX_INCLUDE_DIR*`, `TERMUX_LIB_DIR*`, `TERMUX_LIBEXEC_DIR*`, `TERMUX_SHARE_DIR*`,
 *          `TERMUX_TMP_DIR*`, `TERMUX_VAR_DIR*`, `TERMUX_STAGING_PREFIX_DIR*`,
 *          `TERMUX_STORAGE_HOME_DIR*`, `TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME*`,
 *          `TERMUX_DEFAULT_PREFERENCES_FILE`.
 *      - Renamed `DATA_HOME_PATH` to `TERMUX_DATA_HOME_DIR_PATH`.
 *      - Renamed `CONFIG_HOME_PATH` to `TERMUX_CONFIG_HOME_DIR_PATH`.
 *      - Updated javadocs and spacing.
 *
 * - 0.3.0 (2021-03-12)
 *      - Remove `TERMUX_CACHE_DIR_PATH*`, `TERMUX_DATABASES_DIR_PATH*`,
 *          `TERMUX_SHARED_PREFERENCES_DIR_PATH*` since they may not be consistent on all devices.
 *      - Renamed `TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME` to
 *          `TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION`. This should be used for
 *           accessing shared preferences between Termux app and its plugins if ever needed by first
 *           getting shared package context with `Context.createPackageContext(String,int)`.
 *
 * - 0.4.0 (2021-03-16)
 *      - Added `BROADCAST_TERMUX_OPENED`,
 *          `TERMUX_API_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION`
 *          `TERMUX_BOOT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION`,
 *          `TERMUX_FLOAT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION`,
 *          `TERMUX_STYLING_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION`,
 *          `TERMUX_TASKER_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION`,
 *          `TERMUX_WIDGET_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION`.
 *
 * - 0.5.0 (2021-03-16)
 *      - Renamed "Termux Plugin app" labels to "Termux:Tasker app".
 *
 * - 0.6.0 (2021-03-16)
 *      - Added `TERMUX_FILE_SHARE_URI_AUTHORITY`.
 *
 * - 0.7.0 (2021-03-17)
 *      - Fixed javadocs.
 *
 * - 0.8.0 (2021-03-18)
 *      - Fixed Intent extra types javadocs.
 *      - Added following to `TERMUX_SERVICE`:
 *          `EXTRA_PENDING_INTENT`, `EXTRA_RESULT_BUNDLE`,
 *          `EXTRA_STDOUT`, `EXTRA_STDERR`, `EXTRA_EXIT_CODE`,
 *          `EXTRA_ERR`, `EXTRA_ERRMSG`.
 *
 * - 0.9.0 (2021-03-18)
 *      - Fixed javadocs.
 *
 * - 0.10.0 (2021-03-19)
 *      - Added following to `TERMUX_SERVICE`:
 *          `EXTRA_SESSION_ACTION`,
 *          `VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY`,
 *          `VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY`,
 *          `VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY`
 *          `VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_DONT_OPEN_ACTIVITY`.
 *      - Added following to `RUN_COMMAND_SERVICE`:
 *          `EXTRA_SESSION_ACTION`.
 *
 * - 0.11.0 (2021-03-24)
 *      - Added following to `TERMUX_SERVICE`:
 *          `EXTRA_COMMAND_LABEL`, `EXTRA_COMMAND_DESCRIPTION`, `EXTRA_COMMAND_HELP`, `EXTRA_PLUGIN_API_HELP`.
 *      - Added following to `RUN_COMMAND_SERVICE`:
 *          `EXTRA_COMMAND_LABEL`, `EXTRA_COMMAND_DESCRIPTION`, `EXTRA_COMMAND_HELP`.
 *      - Updated `RESULT_BUNDLE` related extras with `PLUGIN_RESULT_BUNDLE` prefixes.
 *
 * - 0.12.0 (2021-03-25)
 *      - Added following to `TERMUX_SERVICE`:
 *          `EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT_ORIGINAL_LENGTH`,
 *          `EXTRA_PLUGIN_RESULT_BUNDLE_STDERR_ORIGINAL_LENGTH`.
 *
 * - 0.13.0 (2021-03-25)
 *      - Added following to `RUN_COMMAND_SERVICE`:
 *          `EXTRA_PENDING_INTENT`.
 *
 * - 0.14.0 (2021-03-25)
 *      - Added `FDROID_PACKAGES_BASE_URL`,
 *          `TERMUX_GITHUB_ORGANIZATION_NAME`, `TERMUX_GITHUB_ORGANIZATION_URL`,
 *          `TERMUX_GITHUB_REPO_NAME`, `TERMUX_GITHUB_REPO_URL`, `TERMUX_FDROID_PACKAGE_URL`,
 *          `TERMUX_API_GITHUB_REPO_NAME`,`TERMUX_API_GITHUB_REPO_URL`, `TERMUX_API_FDROID_PACKAGE_URL`,
 *          `TERMUX_BOOT_GITHUB_REPO_NAME`, `TERMUX_BOOT_GITHUB_REPO_URL`, `TERMUX_BOOT_FDROID_PACKAGE_URL`,
 *          `TERMUX_FLOAT_GITHUB_REPO_NAME`, `TERMUX_FLOAT_GITHUB_REPO_URL`, `TERMUX_FLOAT_FDROID_PACKAGE_URL`,
 *          `TERMUX_STYLING_GITHUB_REPO_NAME`, `TERMUX_STYLING_GITHUB_REPO_URL`, `TERMUX_STYLING_FDROID_PACKAGE_URL`,
 *          `TERMUX_TASKER_GITHUB_REPO_NAME`, `TERMUX_TASKER_GITHUB_REPO_URL`, `TERMUX_TASKER_FDROID_PACKAGE_URL`,
 *          `TERMUX_WIDGET_GITHUB_REPO_NAME`, `TERMUX_WIDGET_GITHUB_REPO_URL` `TERMUX_WIDGET_FDROID_PACKAGE_URL`.
 *
 * - 0.15.0 (2021-04-06)
 *      - Fixed some variables that had `PREFIX_` substring missing in their name.
 *      - Added `TERMUX_CRASH_LOG_FILE_PATH`, `TERMUX_CRASH_LOG_BACKUP_FILE_PATH`,
 *          `TERMUX_GITHUB_ISSUES_REPO_URL`, `TERMUX_API_GITHUB_ISSUES_REPO_URL`,
 *          `TERMUX_BOOT_GITHUB_ISSUES_REPO_URL`, `TERMUX_FLOAT_GITHUB_ISSUES_REPO_URL`,
 *          `TERMUX_STYLING_GITHUB_ISSUES_REPO_URL`, `TERMUX_TASKER_GITHUB_ISSUES_REPO_URL`,
 *          `TERMUX_WIDGET_GITHUB_ISSUES_REPO_URL`,
 *          `TERMUX_GITHUB_WIKI_REPO_URL`, `TERMUX_PACKAGES_GITHUB_WIKI_REPO_URL`,
 *          `TERMUX_PACKAGES_GITHUB_REPO_NAME`, `TERMUX_PACKAGES_GITHUB_REPO_URL`, `TERMUX_PACKAGES_GITHUB_ISSUES_REPO_URL`,
 *          `TERMUX_GAME_PACKAGES_GITHUB_REPO_NAME`, `TERMUX_GAME_PACKAGES_GITHUB_REPO_URL`, `TERMUX_GAME_PACKAGES_GITHUB_ISSUES_REPO_URL`,
 *          `TERMUX_SCIENCE_PACKAGES_GITHUB_REPO_NAME`, `TERMUX_SCIENCE_PACKAGES_GITHUB_REPO_URL`, `TERMUX_SCIENCE_PACKAGES_GITHUB_ISSUES_REPO_URL`,
 *          `TERMUX_ROOT_PACKAGES_GITHUB_REPO_NAME`, `TERMUX_ROOT_PACKAGES_GITHUB_REPO_URL`, `TERMUX_ROOT_PACKAGES_GITHUB_ISSUES_REPO_URL`,
 *          `TERMUX_UNSTABLE_PACKAGES_GITHUB_REPO_NAME`, `TERMUX_UNSTABLE_PACKAGES_GITHUB_REPO_URL`, `TERMUX_UNSTABLE_PACKAGES_GITHUB_ISSUES_REPO_URL`,
 *          `TERMUX_X11_PACKAGES_GITHUB_REPO_NAME`, `TERMUX_X11_PACKAGES_GITHUB_REPO_URL`, `TERMUX_X11_PACKAGES_GITHUB_ISSUES_REPO_URL`.
 *      - Added following to `RUN_COMMAND_SERVICE`:
 *          `RUN_COMMAND_API_HELP_URL`.
 *
 * - 0.16.0 (2021-04-06)
 *      - Added `TERMUX_SUPPORT_EMAIL`, `TERMUX_SUPPORT_EMAIL_URL`, `TERMUX_SUPPORT_EMAIL_MAILTO_URL`,
 *          `TERMUX_REDDIT_SUBREDDIT`, `TERMUX_REDDIT_SUBREDDIT_URL`.
 *      - The `TERMUX_SUPPORT_EMAIL_URL` value must be fixed later when email has been set up.
 *
 * - 0.17.0 (2021-04-07)
 *      - Added `TERMUX_APP_NOTIFICATION_CHANNEL_ID`, `TERMUX_APP_NOTIFICATION_CHANNEL_NAME`, `TERMUX_APP_NOTIFICATION_ID`,
 *          `TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_ID`, `TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_NAME`, `TERMUX_RUN_COMMAND_NOTIFICATION_ID`,
 *          `TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_ID`, `TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_NAME`,
 *          `TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_ID`, `TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_NAME`.
 *      - Updated javadocs.
 *
 * - 0.18.0 (2021-04-11)
 *      - Updated `TERMUX_SUPPORT_EMAIL_URL` to a valid email.
 *      - Removed `TERMUX_SUPPORT_EMAIL`.
 *
 * - 0.19.0 (2021-04-12)
 *      - Added `TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS`.
 *      - Added `TERMUX_SERVICE.EXTRA_STDIN`.
 *      - Added `RUN_COMMAND_SERVICE.EXTRA_STDIN`.
 *      - Deprecated `TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE`.
 *
 * - 0.20.0 (2021-05-13)
 *      - Added `TERMUX_WIKI`, `TERMUX_WIKI_URL`, `TERMUX_PLUGIN_APP_NAMES_LIST`, `TERMUX_PLUGIN_APP_PACKAGE_NAMES_LIST`.
 *      - Added `TERMUX_SETTINGS_ACTIVITY_NAME`.
 *
 * - 0.21.0 (2021-05-13)
 *      - Added `APK_RELEASE_FDROID`, `APK_RELEASE_FDROID_SIGNING_CERTIFICATE_SHA256_DIGEST`,
 *          `APK_RELEASE_GITHUB_DEBUG_BUILD`, `APK_RELEASE_GITHUB_DEBUG_BUILD_SIGNING_CERTIFICATE_SHA256_DIGEST`,
 *          `APK_RELEASE_GOOGLE_PLAYSTORE`, `APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST`.
 *
 * - 0.22.0 (2021-05-13)
 *      - Added `TERMUX_DONATE_URL`.
 *
 * - 0.23.0 (2021-06-12)
 *      - Rename `INTERNAL_PRIVATE_APP_DATA_DIR_PATH` to `TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH`.
 *
 * - 0.24.0 (2021-06-27)
 *      - Add `COMMA_NORMAL`, `COMMA_ALTERNATIVE`.
 *      - Added following to `TERMUX_APP.TERMUX_SERVICE`:
 *          `EXTRA_RESULT_DIRECTORY`, `EXTRA_RESULT_SINGLE_FILE`, `EXTRA_RESULT_FILE_BASENAME`,
 *          `EXTRA_RESULT_FILE_OUTPUT_FORMAT`, `EXTRA_RESULT_FILE_ERROR_FORMAT`, `EXTRA_RESULT_FILES_SUFFIX`.
 *      - Added following to `TERMUX_APP.RUN_COMMAND_SERVICE`:
 *          `EXTRA_RESULT_DIRECTORY`, `EXTRA_RESULT_SINGLE_FILE`, `EXTRA_RESULT_FILE_BASENAME`,
 *          `EXTRA_RESULT_FILE_OUTPUT_FORMAT`, `EXTRA_RESULT_FILE_ERROR_FORMAT`, `EXTRA_RESULT_FILES_SUFFIX`,
 *          `EXTRA_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS`, `EXTRA_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS`.
 *      - Added following to `RESULT_SENDER`:
 *           `FORMAT_SUCCESS_STDOUT`, `FORMAT_SUCCESS_STDOUT__EXIT_CODE`, `FORMAT_SUCCESS_STDOUT__STDERR__EXIT_CODE`
 *           `FORMAT_FAILED_ERR__ERRMSG__STDOUT__STDERR__EXIT_CODE`,
 *           `RESULT_FILE_ERR_PREFIX`, `RESULT_FILE_ERRMSG_PREFIX` `RESULT_FILE_STDOUT_PREFIX`,
 *           `RESULT_FILE_STDERR_PREFIX`, `RESULT_FILE_EXIT_CODE_PREFIX`.
 *
 * - 0.25.0 (2021-08-19)
 *      - Added following to `TERMUX_APP.TERMUX_SERVICE`:
 *          `EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL`.
 *      - Added following to `TERMUX_APP.RUN_COMMAND_SERVICE`:
 *          `EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL`.
 *
 * - 0.26.0 (2021-08-25)
 *      - Changed `TERMUX_ACTIVITY.ACTION_FAILSAFE_SESSION` to `TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION`.
 *
 * - 0.27.0 (2021-09-02)
 *      - Added `TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_ID`, `TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_NAME`,
 *          `TERMUX_FLOAT_APP.TERMUX_FLOAT_SERVICE_NAME`.
 *      - Added following to `TERMUX_FLOAT_APP.TERMUX_FLOAT_SERVICE`:
 *          `ACTION_STOP_SERVICE`, `ACTION_SHOW`, `ACTION_HIDE`.
 *
 * - 0.28.0 (2021-09-02)
 *      - Added `TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE*` and `TERMUX_FLOAT_PROPERTIES_SECONDARY_FILE*`.
 *
 * - 0.29.0 (2021-09-04)
 *      - Added `TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_BASENAME`, `TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_BASENAME`,
 *          `TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_PATH`, `TERMUX_SHORTCUT_SCRIPT_ICONS_DIR`.
 *      - Added following to `TERMUX_WIDGET.TERMUX_WIDGET_PROVIDER`:
 *          `ACTION_WIDGET_ITEM_CLICKED`, `ACTION_REFRESH_WIDGET`, `EXTRA_FILE_CLICKED`.
 *      - Changed naming convention of `TERMUX_FLOAT_APP.TERMUX_FLOAT_SERVICE.ACTION_*`.
 *      - Fixed wrong path set for `TERMUX_SHORTCUT_SCRIPTS_DIR_PATH`.
 *
 * - 0.30.0 (2021-09-08)
 *      - Changed `APK_RELEASE_GITHUB_DEBUG_BUILD`to `APK_RELEASE_GITHUB` and
 *          `APK_RELEASE_GITHUB_DEBUG_BUILD_SIGNING_CERTIFICATE_SHA256_DIGEST` to
 *          `APK_RELEASE_GITHUB_SIGNING_CERTIFICATE_SHA256_DIGEST`.
 *
 * - 0.31.0 (2021-09-09)
 *      - Added following to `TERMUX_APP.TERMUX_SERVICE`:
 *          `MIN_VALUE_EXTRA_SESSION_ACTION` and `MAX_VALUE_EXTRA_SESSION_ACTION`.
 *
 * - 0.32.0 (2021-09-23)
 *      - Added `TERMUX_API.TERMUX_API_ACTIVITY_NAME`, `TERMUX_TASKER.TERMUX_TASKER_ACTIVITY_NAME`
 *          and `TERMUX_WIDGET.TERMUX_WIDGET_ACTIVITY_NAME`.
 *
 * - 0.33.0 (2021-10-08)
 *      - Added `TERMUX_PROPERTIES_FILE_PATHS_LIST` and `TERMUX_FLOAT_PROPERTIES_FILE_PATHS_LIST`.
 *
 * - 0.34.0 (2021-10-26)
 *      - Move `RESULT_SENDER` to `com.termux.shared.shell.command.ShellCommandConstants`.
 *
 * - 0.35.0 (2022-01-28)
 *      - Add `TERMUX_APP.TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY`.
 *
 * - 0.36.0 (2022-03-10)
 *      - Added `TERMUX_APP.TERMUX_SERVICE.EXTRA_RUNNER` and `TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_RUNNER`
 *
 * - 0.37.0 (2022-03-15)
 *  - Added `TERMUX_API_APT_*`.
 *
 * - 0.38.0 (2022-03-16)
 *      - Added `TERMUX_APP.TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH`.
 *
 * - 0.39.0 (2022-03-18)
 *      - Added `TERMUX_APP.TERMUX_SERVICE.EXTRA_SESSION_NAME`, `TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_SESSION_NAME`,
 *          `TERMUX_APP.TERMUX_SERVICE.EXTRA_SESSION_CREATE_MODE` and `TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_SESSION_CREATE_MODE`.
 *
 * - 0.40.0 (2022-04-17)
 *      - Added `TERMUX_APPS_DIR_PATH` and `TERMUX_APP.APPS_DIR_PATH`.
 *
 * - 0.41.0 (2022-04-17)
 *      - Added `TERMUX_APP.TERMUX_AM_SOCKET_FILE_PATH`.
 *
 * - 0.42.0 (2022-04-29)
 *      - Added `APK_RELEASE_TERMUX_DEVS` and `APK_RELEASE_TERMUX_DEVS_SIGNING_CERTIFICATE_SHA256_DIGEST`.
 *
 * - 0.43.0 (2022-05-29)
 *      - Changed `TERMUX_SUPPORT_EMAIL_URL` to support@termux.dev.
 *
 * - 0.44.0 (2022-05-29)
 *      - Changed `TERMUX_APP.APPS_DIR_PATH` basename from `termux-app` to `com.termux`.
 *
 * - 0.45.0 (2022-06-01)
 *      - Added `TERMUX_APP.BUILD_CONFIG_CLASS_NAME`.
 *
 * - 0.46.0 (2022-06-03)
 *      - Rename `TERMUX_APP.TERMUX_SERVICE.EXTRA_SESSION_NAME` to `*.EXTRA_SHELL_NAME`,
 *          `TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_SESSION_NAME` to `*.EXTRA_SHELL_NAME`,
 *          `TERMUX_APP.TERMUX_SERVICE.EXTRA_SESSION_CREATE_MODE` to `*.EXTRA_SHELL_CREATE_MODE` and
 *          `TERMUX_APP.RUN_COMMAND_SERVICE.EXTRA_SESSION_CREATE_MODE` to `*.EXTRA_SHELL_CREATE_MODE`.
 *
 * - 0.47.0 (2022-06-04)
 *      - Added `TERMUX_SITE` and `TERMUX_SITE_URL`.
 *      - Changed `TERMUX_DONATE_URL`.
 *
 * - 0.48.0 (2022-06-04)
 *      - Removed `TERMUX_GAME_PACKAGES_GITHUB_*`, `TERMUX_SCIENCE_PACKAGES_GITHUB_*`,
 *          `TERMUX_ROOT_PACKAGES_GITHUB_*`, `TERMUX_UNSTABLE_PACKAGES_GITHUB_*`
 *
 * - 0.49.0 (2022-06-11)
 *      - Added `TERMUX_ENV_PREFIX_ROOT`.
 *
 * - 0.50.0 (2022-06-11)
 *      - Added `TERMUX_CONFIG_PREFIX_DIR_PATH`, `TERMUX_ENV_FILE_PATH` and `TERMUX_ENV_TEMP_FILE_PATH`.
 *
 * - 0.51.0 (2022-06-13)
 *      - Added `TERMUX_APP.FILE_SHARE_RECEIVER_ACTIVITY_CLASS_NAME` and `TERMUX_APP.FILE_VIEW_RECEIVER_ACTIVITY_CLASS_NAME`.
 *
 * - 0.52.0 (2022-06-18)
 *      - Added `TERMUX_PREFIX_DIR_IGNORED_SUB_FILES_PATHS_TO_CONSIDER_AS_EMPTY`.
 *
 * - 0.53.0 (2025-01-12)
 *      - Renamed `TERMUX_API`, `TERMUX_STYLING`, `TERMUX_TASKER`, `TERMUX_WIDGET` classes with `_APP` suffix added.
 *      - Added `TERMUX_*_MAIN_ACTIVITY_NAME` and `TERMUX_*_LAUNCHER_ACTIVITY_NAME` constants to each app class.
 */

/**
 * A class that defines shared constants of the Termux app and its plugins.
 * This class will be hosted by termux-shared lib and should be imported by other termux plugin
 * apps as is instead of copying constants to random classes. The 3rd party apps can also import
 * it for interacting with termux apps. If changes are made to this file, increment the version number
 * and add an entry in the Changelog section above.
 *
 * Termux app default package name is "com.termux" and is used in [TERMUX_PREFIX_DIR_PATH].
 * The binaries compiled for termux have [TERMUX_PREFIX_DIR_PATH] hardcoded in them but it
 * can be changed during compilation.
 *
 * The [TERMUX_PACKAGE_NAME] must be the same as the applicationId of termux-app build.gradle
 * since its also used by [TERMUX_FILES_DIR_PATH].
 * If [TERMUX_PACKAGE_NAME] is changed, then binaries, specially used in bootstrap need to be
 * compiled appropriately. Check https://github.com/termux/termux-packages/wiki/Building-packages
 * for more info.
 *
 * Ideally the only places where changes should be required if changing package name are the following:
 * - The [TERMUX_PACKAGE_NAME] in [TermuxConstants].
 * - The "applicationId" in "build.gradle" of termux-app. This is package name that android and app
 *      stores will use and is also the final package name stored in "AndroidManifest.xml".
 * - The "manifestPlaceholders" values for [TERMUX_PACKAGE_NAME] and *_APP_NAME in
 *      "build.gradle" of termux-app.
 * - The "ENTITY" values for [TERMUX_PACKAGE_NAME] and *_APP_NAME in "strings.xml" of
 *      termux-app and of termux-shared.
 * - The "shortcut.xml" and "*_preferences.xml" files of termux-app since dynamic variables don't
 *      work in it.
 * - Optionally the "package" in "AndroidManifest.xml" if modifying project structure of termux-app.
 *      This is package name for java classes project structure and is prefixed if activity and service
 *      names use dot (.) notation. This is currently not advisable since this will break lot of
 *      stuff, including termux-* packages.
 * - Optionally the *_PATH variables in [TermuxConstants] containing the string "termux".
 *
 * Check https://developer.android.com/studio/build/application-id for info on "package" in
 * "AndroidManifest.xml" and "applicationId" in "build.gradle".
 *
 * The [TERMUX_PACKAGE_NAME] must be used in source code of Termux app and its plugins instead
 * of hardcoded "com.termux" paths.
 */
object TermuxConstants {

    /*
     * Termux organization variables.
     */

    /** Termux GitHub organization name */
    const val TERMUX_GITHUB_ORGANIZATION_NAME = "termux" // Default: "termux"
    /** Termux GitHub organization url */
    const val TERMUX_GITHUB_ORGANIZATION_URL = "https://github.com" + "/" + TERMUX_GITHUB_ORGANIZATION_NAME // Default: "https://github.com/termux"

    /** F-Droid packages base url */
    const val FDROID_PACKAGES_BASE_URL = "https://f-droid.org/en/packages" // Default: "https://f-droid.org/en/packages"

    /*
     * Termux and its plugin app and package names and urls.
     */

    /** Termux app name */
    const val TERMUX_APP_NAME = "Termux" // Default: "Termux"
    /** Termux package name */
    const val TERMUX_PACKAGE_NAME = "com.termux" // Default: "com.termux"
    /** Termux GitHub repo name */
    const val TERMUX_GITHUB_REPO_NAME = "termux-app" // Default: "termux-app"
    /** Termux GitHub repo url */
    const val TERMUX_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_GITHUB_REPO_NAME // Default: "https://github.com/termux/termux-app"
    /** Termux GitHub issues repo url */
    const val TERMUX_GITHUB_ISSUES_REPO_URL = TERMUX_GITHUB_REPO_URL + "/issues" // Default: "https://github.com/termux/termux-app/issues"
    /** Termux F-Droid package url */
    const val TERMUX_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_PACKAGE_NAME // Default: "https://f-droid.org/en/packages/com.termux"

    /** Termux:API app name */
    const val TERMUX_API_APP_NAME = "Termux:API" // Default: "Termux:API"
    /** Termux:API app package name */
    const val TERMUX_API_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".api" // Default: "com.termux.api"
    /** Termux:API GitHub repo name */
    const val TERMUX_API_GITHUB_REPO_NAME = "termux-api" // Default: "termux-api"
    /** Termux:API GitHub repo url */
    const val TERMUX_API_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_API_GITHUB_REPO_NAME // Default: "https://github.com/termux/termux-api"
    /** Termux:API GitHub issues repo url */
    const val TERMUX_API_GITHUB_ISSUES_REPO_URL = TERMUX_API_GITHUB_REPO_URL + "/issues" // Default: "https://github.com/termux/termux-api/issues"
    /** Termux:API F-Droid package url */
    const val TERMUX_API_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_API_PACKAGE_NAME // Default: "https://f-droid.org/en/packages/com.termux.api"

    /** Termux:Boot app name */
    const val TERMUX_BOOT_APP_NAME = "Termux:Boot" // Default: "Termux:Boot"
    /** Termux:Boot app package name */
    const val TERMUX_BOOT_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".boot" // Default: "com.termux.boot"
    /** Termux:Boot GitHub repo name */
    const val TERMUX_BOOT_GITHUB_REPO_NAME = "termux-boot" // Default: "termux-boot"
    /** Termux:Boot GitHub repo url */
    const val TERMUX_BOOT_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_BOOT_GITHUB_REPO_NAME // Default: "https://github.com/termux/termux-boot"
    /** Termux:Boot GitHub issues repo url */
    const val TERMUX_BOOT_GITHUB_ISSUES_REPO_URL = TERMUX_BOOT_GITHUB_REPO_URL + "/issues" // Default: "https://github.com/termux/termux-boot/issues"
    /** Termux:Boot F-Droid package url */
    const val TERMUX_BOOT_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_BOOT_PACKAGE_NAME // Default: "https://f-droid.org/en/packages/com.termux.boot"

    /** Termux:Float app name */
    const val TERMUX_FLOAT_APP_NAME = "Termux:Float" // Default: "Termux:Float"
    /** Termux:Float app package name */
    const val TERMUX_FLOAT_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".window" // Default: "com.termux.window"
    /** Termux:Float GitHub repo name */
    const val TERMUX_FLOAT_GITHUB_REPO_NAME = "termux-float" // Default: "termux-float"
    /** Termux:Float GitHub repo url */
    const val TERMUX_FLOAT_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_FLOAT_GITHUB_REPO_NAME // Default: "https://github.com/termux/termux-float"
    /** Termux:Float GitHub issues repo url */
    const val TERMUX_FLOAT_GITHUB_ISSUES_REPO_URL = TERMUX_FLOAT_GITHUB_REPO_URL + "/issues" // Default: "https://github.com/termux/termux-float/issues"
    /** Termux:Float F-Droid package url */
    const val TERMUX_FLOAT_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_FLOAT_PACKAGE_NAME // Default: "https://f-droid.org/en/packages/com.termux.window"

    /** Termux:Styling app name */
    const val TERMUX_STYLING_APP_NAME = "Termux:Styling" // Default: "Termux:Styling"
    /** Termux:Styling app package name */
    const val TERMUX_STYLING_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".styling" // Default: "com.termux.styling"
    /** Termux:Styling GitHub repo name */
    const val TERMUX_STYLING_GITHUB_REPO_NAME = "termux-styling" // Default: "termux-styling"
    /** Termux:Styling GitHub repo url */
    const val TERMUX_STYLING_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_STYLING_GITHUB_REPO_NAME // Default: "https://github.com/termux/termux-styling"
    /** Termux:Styling GitHub issues repo url */
    const val TERMUX_STYLING_GITHUB_ISSUES_REPO_URL = TERMUX_STYLING_GITHUB_REPO_URL + "/issues" // Default: "https://github.com/termux/termux-styling/issues"
    /** Termux:Styling F-Droid package url */
    const val TERMUX_STYLING_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_STYLING_PACKAGE_NAME // Default: "https://f-droid.org/en/packages/com.termux.styling"

    /** Termux:Tasker app name */
    const val TERMUX_TASKER_APP_NAME = "Termux:Tasker" // Default: "Termux:Tasker"
    /** Termux:Tasker app package name */
    const val TERMUX_TASKER_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".tasker" // Default: "com.termux.tasker"
    /** Termux:Tasker GitHub repo name */
    const val TERMUX_TASKER_GITHUB_REPO_NAME = "termux-tasker" // Default: "termux-tasker"
    /** Termux:Tasker GitHub repo url */
    const val TERMUX_TASKER_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_TASKER_GITHUB_REPO_NAME // Default: "https://github.com/termux/termux-tasker"
    /** Termux:Tasker GitHub issues repo url */
    const val TERMUX_TASKER_GITHUB_ISSUES_REPO_URL = TERMUX_TASKER_GITHUB_REPO_URL + "/issues" // Default: "https://github.com/termux/termux-tasker/issues"
    /** Termux:Tasker F-Droid package url */
    const val TERMUX_TASKER_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_TASKER_PACKAGE_NAME // Default: "https://f-droid.org/en/packages/com.termux.tasker"

    /** Termux:Widget app name */
    const val TERMUX_WIDGET_APP_NAME = "Termux:Widget" // Default: "Termux:Widget"
    /** Termux:Widget app package name */
    const val TERMUX_WIDGET_PACKAGE_NAME = TERMUX_PACKAGE_NAME + ".widget" // Default: "com.termux.widget"
    /** Termux:Widget GitHub repo name */
    const val TERMUX_WIDGET_GITHUB_REPO_NAME = "termux-widget" // Default: "termux-widget"
    /** Termux:Widget GitHub repo url */
    const val TERMUX_WIDGET_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_WIDGET_GITHUB_REPO_NAME // Default: "https://github.com/termux/termux-widget"
    /** Termux:Widget GitHub issues repo url */
    const val TERMUX_WIDGET_GITHUB_ISSUES_REPO_URL = TERMUX_WIDGET_GITHUB_REPO_URL + "/issues" // Default: "https://github.com/termux/termux-widget/issues"
    /** Termux:Widget F-Droid package url */
    const val TERMUX_WIDGET_FDROID_PACKAGE_URL = FDROID_PACKAGES_BASE_URL + "/" + TERMUX_WIDGET_PACKAGE_NAME // Default: "https://f-droid.org/en/packages/com.termux.widget"

    /*
     * Termux plugin apps lists.
     */

    @JvmField
    val TERMUX_PLUGIN_APP_NAMES_LIST = asList(
        TERMUX_API_APP_NAME,
        TERMUX_BOOT_APP_NAME,
        TERMUX_FLOAT_APP_NAME,
        TERMUX_STYLING_APP_NAME,
        TERMUX_TASKER_APP_NAME,
        TERMUX_WIDGET_APP_NAME
    )

    @JvmField
    val TERMUX_PLUGIN_APP_PACKAGE_NAMES_LIST = asList(
        TERMUX_API_PACKAGE_NAME,
        TERMUX_BOOT_PACKAGE_NAME,
        TERMUX_FLOAT_PACKAGE_NAME,
        TERMUX_STYLING_PACKAGE_NAME,
        TERMUX_TASKER_PACKAGE_NAME,
        TERMUX_WIDGET_PACKAGE_NAME
    )

    /*
     * Termux APK releases.
     */

    /** F-Droid APK release */
    const val APK_RELEASE_FDROID = "F-Droid" // Default: "F-Droid"

    /** F-Droid APK release signing certificate SHA-256 digest */
    const val APK_RELEASE_FDROID_SIGNING_CERTIFICATE_SHA256_DIGEST = "228FB2CFE90831C1499EC3CCAF61E96E8E1CE70766B9474672CE427334D41C42" // Default: "228FB2CFE90831C1499EC3CCAF61E96E8E1CE70766B9474672CE427334D41C42"

    /** GitHub APK release */
    const val APK_RELEASE_GITHUB = "Github" // Default: "Github"

    /** GitHub APK release signing certificate SHA-256 digest */
    const val APK_RELEASE_GITHUB_SIGNING_CERTIFICATE_SHA256_DIGEST = "B6DA01480EEFD5FBF2CD3771B8D1021EC791304BDD6C4BF41D3FAABAD48EE5E1" // Default: "B6DA01480EEFD5FBF2CD3771B8D1021EC791304BDD6C4BF41D3FAABAD48EE5E1"

    /** Google Play Store APK release */
    const val APK_RELEASE_GOOGLE_PLAYSTORE = "Google Play Store" // Default: "Google Play Store"

    /** Google Play Store APK release signing certificate SHA-256 digest */
    const val APK_RELEASE_GOOGLE_PLAYSTORE_SIGNING_CERTIFICATE_SHA256_DIGEST = "738F0A30A04D3C8A1BE304AF18D0779BCF3EA88FB60808F657A3521861C2EBF9" // Default: "738F0A30A04D3C8A1BE304AF18D0779BCF3EA88FB60808F657A3521861C2EBF9"

    /** Termux Devs APK release */
    const val APK_RELEASE_TERMUX_DEVS = "Termux Devs" // Default: "Termux Devs"

    /** Termux Devs APK release signing certificate SHA-256 digest */
    const val APK_RELEASE_TERMUX_DEVS_SIGNING_CERTIFICATE_SHA256_DIGEST = "F7A038EB551F1BE8FDF388686B784ABAB4552A5D82DF423E3D8F1B5CBE1C69AE" // Default: "F7A038EB551F1BE8FDF388686B784ABAB4552A5D82DF423E3D8F1B5CBE1C69AE"

    /*
     * Termux packages urls.
     */

    /** Termux Packages GitHub repo name */
    const val TERMUX_PACKAGES_GITHUB_REPO_NAME = "termux-packages" // Default: "termux-packages"
    /** Termux Packages GitHub repo url */
    const val TERMUX_PACKAGES_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_PACKAGES_GITHUB_REPO_NAME // Default: "https://github.com/termux/termux-packages"
    /** Termux Packages GitHub issues repo url */
    const val TERMUX_PACKAGES_GITHUB_ISSUES_REPO_URL = TERMUX_PACKAGES_GITHUB_REPO_URL + "/issues" // Default: "https://github.com/termux/termux-packages/issues"

    /** Termux API apt package name */
    const val TERMUX_API_APT_PACKAGE_NAME = "termux-api" // Default: "termux-api"
    /** Termux API apt GitHub repo name */
    const val TERMUX_API_APT_GITHUB_REPO_NAME = "termux-api-package" // Default: "termux-api-package"
    /** Termux API apt GitHub repo url */
    const val TERMUX_API_APT_GITHUB_REPO_URL = TERMUX_GITHUB_ORGANIZATION_URL + "/" + TERMUX_API_APT_GITHUB_REPO_NAME // Default: "https://github.com/termux/termux-api-package"
    /** Termux API apt GitHub issues repo url */
    const val TERMUX_API_APT_GITHUB_ISSUES_REPO_URL = TERMUX_API_APT_GITHUB_REPO_URL + "/issues" // Default: "https://github.com/termux/termux-api-package/issues"

    /*
     * Termux miscellaneous urls.
     */

    /** Termux Site */
    const val TERMUX_SITE = TERMUX_APP_NAME + " Site" // Default: "Termux Site"

    /** Termux Site url */
    const val TERMUX_SITE_URL = "https://termux.dev" // Default: "https://termux.dev"

    /** Termux Wiki */
    const val TERMUX_WIKI = TERMUX_APP_NAME + " Wiki" // Default: "Termux Wiki"

    /** Termux Wiki url */
    const val TERMUX_WIKI_URL = "https://wiki.termux.com" // Default: "https://wiki.termux.com"

    /** Termux GitHub wiki repo url */
    const val TERMUX_GITHUB_WIKI_REPO_URL = TERMUX_GITHUB_REPO_URL + "/wiki" // Default: "https://github.com/termux/termux-app/wiki"

    /** Termux Packages wiki repo url */
    const val TERMUX_PACKAGES_GITHUB_WIKI_REPO_URL = TERMUX_PACKAGES_GITHUB_REPO_URL + "/wiki" // Default: "https://github.com/termux/termux-packages/wiki"

    /** Termux support email url */
    const val TERMUX_SUPPORT_EMAIL_URL = "support@termux.dev" // Default: "support@termux.dev"

    /** Termux support email mailto url */
    const val TERMUX_SUPPORT_EMAIL_MAILTO_URL = "mailto:" + TERMUX_SUPPORT_EMAIL_URL // Default: "mailto:support@termux.dev"

    /** Termux Reddit subreddit */
    const val TERMUX_REDDIT_SUBREDDIT = "r/termux" // Default: "r/termux"

    /** Termux Reddit subreddit url */
    const val TERMUX_REDDIT_SUBREDDIT_URL = "https://www.reddit.com/r/termux" // Default: "https://www.reddit.com/r/termux"

    /** Termux donate url */
    const val TERMUX_DONATE_URL = TERMUX_SITE_URL + "/donate" // Default: "https://termux.dev/donate"

    /*
     * Termux app core directory paths.
     */

    /** Termux app internal private app data directory path */
    @SuppressLint("SdCardPath")
    const val TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH = "/data/data/" + TERMUX_PACKAGE_NAME // Default: "/data/data/com.termux"
    /** Termux app internal private app data directory */
    @JvmField
    val TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR = File(TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH)

    /** Termux app Files directory path */
    const val TERMUX_FILES_DIR_PATH = TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH + "/files" // Default: "/data/data/com.termux/files"
    /** Termux app Files directory */
    @JvmField
    val TERMUX_FILES_DIR = File(TERMUX_FILES_DIR_PATH)

    /** Termux app $PREFIX directory path */
    const val TERMUX_PREFIX_DIR_PATH = TERMUX_FILES_DIR_PATH + "/usr" // Default: "/data/data/com.termux/files/usr"
    /** Termux app $PREFIX directory */
    @JvmField
    val TERMUX_PREFIX_DIR = File(TERMUX_PREFIX_DIR_PATH)

    /** Termux app $PREFIX/bin directory path */
    const val TERMUX_BIN_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/bin" // Default: "/data/data/com.termux/files/usr/bin"
    /** Termux app $PREFIX/bin directory */
    @JvmField
    val TERMUX_BIN_PREFIX_DIR = File(TERMUX_BIN_PREFIX_DIR_PATH)

    /** Termux app $PREFIX/etc directory path */
    const val TERMUX_ETC_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/etc" // Default: "/data/data/com.termux/files/usr/etc"
    /** Termux app $PREFIX/etc directory */
    @JvmField
    val TERMUX_ETC_PREFIX_DIR = File(TERMUX_ETC_PREFIX_DIR_PATH)

    /** Termux app $PREFIX/include directory path */
    const val TERMUX_INCLUDE_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/include" // Default: "/data/data/com.termux/files/usr/include"
    /** Termux app $PREFIX/include directory */
    @JvmField
    val TERMUX_INCLUDE_PREFIX_DIR = File(TERMUX_INCLUDE_PREFIX_DIR_PATH)

    /** Termux app $PREFIX/lib directory path */
    const val TERMUX_LIB_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/lib" // Default: "/data/data/com.termux/files/usr/lib"
    /** Termux app $PREFIX/lib directory */
    @JvmField
    val TERMUX_LIB_PREFIX_DIR = File(TERMUX_LIB_PREFIX_DIR_PATH)

    /** Termux app $PREFIX/libexec directory path */
    const val TERMUX_LIBEXEC_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/libexec" // Default: "/data/data/com.termux/files/usr/libexec"
    /** Termux app $PREFIX/libexec directory */
    @JvmField
    val TERMUX_LIBEXEC_PREFIX_DIR = File(TERMUX_LIBEXEC_PREFIX_DIR_PATH)

    /** Termux app $PREFIX/share directory path */
    const val TERMUX_SHARE_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/share" // Default: "/data/data/com.termux/files/usr/share"
    /** Termux app $PREFIX/share directory */
    @JvmField
    val TERMUX_SHARE_PREFIX_DIR = File(TERMUX_SHARE_PREFIX_DIR_PATH)

    /** Termux app $PREFIX/tmp and $TMPDIR directory path */
    const val TERMUX_TMP_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/tmp" // Default: "/data/data/com.termux/files/usr/tmp"
    /** Termux app $PREFIX/tmp and $TMPDIR directory */
    @JvmField
    val TERMUX_TMP_PREFIX_DIR = File(TERMUX_TMP_PREFIX_DIR_PATH)

    /** Termux app $PREFIX/var directory path */
    const val TERMUX_VAR_PREFIX_DIR_PATH = TERMUX_PREFIX_DIR_PATH + "/var" // Default: "/data/data/com.termux/files/usr/var"
    /** Termux app $PREFIX/var directory */
    @JvmField
    val TERMUX_VAR_PREFIX_DIR = File(TERMUX_VAR_PREFIX_DIR_PATH)

    /** Termux app usr-staging directory path */
    const val TERMUX_STAGING_PREFIX_DIR_PATH = TERMUX_FILES_DIR_PATH + "/usr-staging" // Default: "/data/data/com.termux/files/usr-staging"
    /** Termux app usr-staging directory */
    @JvmField
    val TERMUX_STAGING_PREFIX_DIR = File(TERMUX_STAGING_PREFIX_DIR_PATH)

    /** Termux app $HOME directory path */
    const val TERMUX_HOME_DIR_PATH = TERMUX_FILES_DIR_PATH + "/home" // Default: "/data/data/com.termux/files/home"
    /** Termux app $HOME directory */
    @JvmField
    val TERMUX_HOME_DIR = File(TERMUX_HOME_DIR_PATH)

    /** Termux app config home directory path */
    const val TERMUX_CONFIG_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/.config/termux" // Default: "/data/data/com.termux/files/home/.config/termux"
    /** Termux app config home directory */
    @JvmField
    val TERMUX_CONFIG_HOME_DIR = File(TERMUX_CONFIG_HOME_DIR_PATH)

    /** Termux app config $PREFIX directory path */
    const val TERMUX_CONFIG_PREFIX_DIR_PATH = TERMUX_ETC_PREFIX_DIR_PATH + "/termux" // Default: "/data/data/com.termux/files/usr/etc/termux"
    /** Termux app config $PREFIX directory */
    @JvmField
    val TERMUX_CONFIG_PREFIX_DIR = File(TERMUX_CONFIG_PREFIX_DIR_PATH)

    /** Termux app data home directory path */
    const val TERMUX_DATA_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/.termux" // Default: "/data/data/com.termux/files/home/.termux"
    /** Termux app data home directory */
    @JvmField
    val TERMUX_DATA_HOME_DIR = File(TERMUX_DATA_HOME_DIR_PATH)

    /** Termux app storage home directory path */
    const val TERMUX_STORAGE_HOME_DIR_PATH = TERMUX_HOME_DIR_PATH + "/storage" // Default: "/data/data/com.termux/files/home/storage"
    /** Termux app storage home directory */
    @JvmField
    val TERMUX_STORAGE_HOME_DIR = File(TERMUX_STORAGE_HOME_DIR_PATH)

    /** Termux app background directory path */
    const val TERMUX_BACKGROUND_DIR_PATH = TERMUX_DATA_HOME_DIR_PATH + "/background" // Default: "/data/data/com.termux/files/.termux/background"
    /** Termux app background directory */
    @JvmField
    val TERMUX_BACKGROUND_DIR = File(TERMUX_BACKGROUND_DIR_PATH)

    /** Termux app backgorund original image file path */
    const val TERMUX_BACKGROUND_IMAGE_PATH = TERMUX_BACKGROUND_DIR_PATH + "/background.jpeg" // Default: "/data/data/com.termux/files/home/.termux/background.jpeg"

    /** Termux app backgorund original image file */
    @JvmField
    val TERMUX_BACKGROUND_IMAGE_FILE = File(TERMUX_BACKGROUND_IMAGE_PATH)

    /** Termux app portrait backgorund image file path */
    const val TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH = TERMUX_BACKGROUND_DIR_PATH + "/background_portrait.jpeg" // Default: "/data/data/com.termux/files/home/.termux/background/background_portrait.jpeg"

    /** Termux app portrait backgorund image file */
    @JvmField
    val TERMUX_BACKGROUND_IMAGE_PORTRAIT_FILE = File(TERMUX_BACKGROUND_IMAGE_PORTRAIT_PATH)

    /** Termux app landscape backgorund image file path */
    const val TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH = TERMUX_BACKGROUND_DIR_PATH + "/background_landscape.jpeg" // Default: "/data/data/com.termux/files/home/.termux/background/background_landscape.jpeg"

    /** Termux app landscape backgorund image file */
    @JvmField
    val TERMUX_BACKGROUND_IMAGE_LANDSCAPE_FILE = File(TERMUX_BACKGROUND_IMAGE_LANDSCAPE_PATH)

    /** Termux and plugin apps directory path */
    const val TERMUX_APPS_DIR_PATH = TERMUX_FILES_DIR_PATH + "/apps" // Default: "/data/data/com.termux/files/apps"
    /** Termux and plugin apps directory */
    @JvmField
    val TERMUX_APPS_DIR = File(TERMUX_APPS_DIR_PATH)

    /** Termux app $PREFIX directory path ignored sub file paths to consider it empty */
    @JvmField
    val TERMUX_PREFIX_DIR_IGNORED_SUB_FILES_PATHS_TO_CONSIDER_AS_EMPTY = asList(
        TERMUX_TMP_PREFIX_DIR_PATH
    )

    /*
     * Termux app and plugin preferences and properties file paths.
     */

    /** Termux app default SharedPreferences file basename without extension */
    const val TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_PACKAGE_NAME + "_preferences" // Default: "com.termux_preferences"

    /** Termux:API app default SharedPreferences file basename without extension */
    const val TERMUX_API_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_API_PACKAGE_NAME + "_preferences" // Default: "com.termux.api_preferences"

    /** Termux:Boot app default SharedPreferences file basename without extension */
    const val TERMUX_BOOT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_BOOT_PACKAGE_NAME + "_preferences" // Default: "com.termux.boot_preferences"

    /** Termux:Float app default SharedPreferences file basename without extension */
    const val TERMUX_FLOAT_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_FLOAT_PACKAGE_NAME + "_preferences" // Default: "com.termux.window_preferences"

    /** Termux:Styling app default SharedPreferences file basename without extension */
    const val TERMUX_STYLING_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_STYLING_PACKAGE_NAME + "_preferences" // Default: "com.termux.styling_preferences"

    /** Termux:Tasker app default SharedPreferences file basename without extension */
    const val TERMUX_TASKER_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_TASKER_PACKAGE_NAME + "_preferences" // Default: "com.termux.tasker_preferences"

    /** Termux:Widget app default SharedPreferences file basename without extension */
    const val TERMUX_WIDGET_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION = TERMUX_WIDGET_PACKAGE_NAME + "_preferences" // Default: "com.termux.widget_preferences"

    /** Termux app properties primary file path */
    const val TERMUX_PROPERTIES_PRIMARY_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/termux.properties" // Default: "/data/data/com.termux/files/home/.termux/termux.properties"
    /** Termux app properties primary file */
    @JvmField
    val TERMUX_PROPERTIES_PRIMARY_FILE = File(TERMUX_PROPERTIES_PRIMARY_FILE_PATH)

    /** Termux app properties secondary file path */
    const val TERMUX_PROPERTIES_SECONDARY_FILE_PATH = TERMUX_CONFIG_HOME_DIR_PATH + "/termux.properties" // Default: "/data/data/com.termux/files/home/.config/termux/termux.properties"
    /** Termux app properties secondary file */
    @JvmField
    val TERMUX_PROPERTIES_SECONDARY_FILE = File(TERMUX_PROPERTIES_SECONDARY_FILE_PATH)

    /** Termux app properties file paths list. **DO NOT** allow these files to be modified by
     * `android.content.ContentProvider` exposed to external apps, since they may silently
     * modify the values for security properties like `PROP_ALLOW_EXTERNAL_APPS` set by users
     * without their explicit consent. */
    @JvmField
    val TERMUX_PROPERTIES_FILE_PATHS_LIST = asList(
        TERMUX_PROPERTIES_PRIMARY_FILE_PATH,
        TERMUX_PROPERTIES_SECONDARY_FILE_PATH
    )

    /** Termux:Float app properties primary file path */
    const val TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/termux.float.properties" // Default: "/data/data/com.termux/files/home/.termux/termux.float.properties"
    /** Termux:Float app properties primary file */
    @JvmField
    val TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE = File(TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE_PATH)

    /** Termux:Float app properties secondary file path */
    const val TERMUX_FLOAT_PROPERTIES_SECONDARY_FILE_PATH = TERMUX_CONFIG_HOME_DIR_PATH + "/termux.float.properties" // Default: "/data/data/com.termux/files/home/.config/termux/termux.float.properties"
    /** Termux:Float app properties secondary file */
    @JvmField
    val TERMUX_FLOAT_PROPERTIES_SECONDARY_FILE = File(TERMUX_FLOAT_PROPERTIES_SECONDARY_FILE_PATH)

    /** Termux:Float app properties file paths list. **DO NOT** allow these files to be modified by
     * `android.content.ContentProvider` exposed to external apps, since they may silently
     * modify the values for security properties like `PROP_ALLOW_EXTERNAL_APPS` set by users
     * without their explicit consent. */
    @JvmField
    val TERMUX_FLOAT_PROPERTIES_FILE_PATHS_LIST = asList(
        TERMUX_FLOAT_PROPERTIES_PRIMARY_FILE_PATH,
        TERMUX_FLOAT_PROPERTIES_SECONDARY_FILE_PATH
    )

    /** Termux app and Termux:Styling colors.properties file path */
    const val TERMUX_COLOR_PROPERTIES_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/colors.properties" // Default: "/data/data/com.termux/files/home/.termux/colors.properties"
    /** Termux app and Termux:Styling colors.properties file */
    @JvmField
    val TERMUX_COLOR_PROPERTIES_FILE = File(TERMUX_COLOR_PROPERTIES_FILE_PATH)

    /** Termux app and Termux:Styling font.ttf file path */
    const val TERMUX_FONT_FILE_PATH = TERMUX_DATA_HOME_DIR_PATH + "/font.ttf" // Default: "/data/data/com.termux/files/home/.termux/font.ttf"
    /** Termux app and Termux:Styling font.ttf file */
    @JvmField
    val TERMUX_FONT_FILE = File(TERMUX_FONT_FILE_PATH)

    /** Termux app and plugins crash log file path */
    const val TERMUX_CRASH_LOG_FILE_PATH = TERMUX_HOME_DIR_PATH + "/crash_log.md" // Default: "/data/data/com.termux/files/home/crash_log.md"

    /** Termux app and plugins crash log backup file path */
    const val TERMUX_CRASH_LOG_BACKUP_FILE_PATH = TERMUX_HOME_DIR_PATH + "/crash_log_backup.md" // Default: "/data/data/com.termux/files/home/crash_log_backup.md"

    /** Termux app environment file path */
    const val TERMUX_ENV_FILE_PATH = TERMUX_CONFIG_PREFIX_DIR_PATH + "/termux.env" // Default: "/data/data/com.termux/files/usr/etc/termux/termux.env"

    /** Termux app environment temp file path */
    const val TERMUX_ENV_TEMP_FILE_PATH = TERMUX_CONFIG_PREFIX_DIR_PATH + "/termux.env.tmp" // Default: "/data/data/com.termux/files/usr/etc/termux/termux.env.tmp"

    /*
     * Termux app plugin specific paths.
     */

    /** Termux app directory path to store scripts to be run at boot by Termux:Boot */
    const val TERMUX_BOOT_SCRIPTS_DIR_PATH = TERMUX_DATA_HOME_DIR_PATH + "/boot" // Default: "/data/data/com.termux/files/home/.termux/boot"
    /** Termux app directory to store scripts to be run at boot by Termux:Boot */
    @JvmField
    val TERMUX_BOOT_SCRIPTS_DIR = File(TERMUX_BOOT_SCRIPTS_DIR_PATH)

    /** Termux app directory path to store foreground scripts that can be run by the termux launcher
     * widget provided by Termux:Widget */
    const val TERMUX_SHORTCUT_SCRIPTS_DIR_PATH = TERMUX_HOME_DIR_PATH + "/.shortcuts" // Default: "/data/data/com.termux/files/home/.shortcuts"
    /** Termux app directory to store foreground scripts that can be run by the termux launcher widget provided by Termux:Widget */
    @JvmField
    val TERMUX_SHORTCUT_SCRIPTS_DIR = File(TERMUX_SHORTCUT_SCRIPTS_DIR_PATH)

    /** Termux app directory basename that stores background scripts that can be run by the termux
     * launcher widget provided by Termux:Widget */
    const val TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_BASENAME = "tasks" // Default: "tasks"
    /** Termux app directory path to store background scripts that can be run by the termux launcher
     * widget provided by Termux:Widget */
    const val TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_PATH = TERMUX_SHORTCUT_SCRIPTS_DIR_PATH + "/" + TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_BASENAME // Default: "/data/data/com.termux/files/home/.shortcuts/tasks"
    /** Termux app directory to store background scripts that can be run by the termux launcher widget provided by Termux:Widget */
    @JvmField
    val TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR = File(TERMUX_SHORTCUT_TASKS_SCRIPTS_DIR_PATH)

    /** Termux app directory basename that stores icons for the foreground and background scripts
     * that can be run by the termux launcher widget provided by Termux:Widget */
    const val TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_BASENAME = "icons" // Default: "icons"
    /** Termux app directory path to store icons for the foreground and background scripts that can
     * be run by the termux launcher widget provided by Termux:Widget */
    const val TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_PATH = TERMUX_SHORTCUT_SCRIPTS_DIR_PATH + "/" + TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_BASENAME // Default: "/data/data/com.termux/files/home/.shortcuts/icons"
    /** Termux app directory to store icons for the foreground and background scripts that can be
     * run by the termux launcher widget provided by Termux:Widget */
    @JvmField
    val TERMUX_SHORTCUT_SCRIPT_ICONS_DIR = File(TERMUX_SHORTCUT_SCRIPT_ICONS_DIR_PATH)

    /** Termux app directory path to store scripts to be run by 3rd party twofortyfouram locale plugin
     * host apps like Tasker app via the Termux:Tasker plugin client */
    const val TERMUX_TASKER_SCRIPTS_DIR_PATH = TERMUX_DATA_HOME_DIR_PATH + "/tasker" // Default: "/data/data/com.termux/files/home/.termux/tasker"
    /** Termux app directory to store scripts to be run by 3rd party twofortyfouram locale plugin host apps like Tasker app via the Termux:Tasker plugin client */
    @JvmField
    val TERMUX_TASKER_SCRIPTS_DIR = File(TERMUX_TASKER_SCRIPTS_DIR_PATH)

    /*
     * Termux app and plugins notification variables.
     */

    /** Termux app notification channel id used by [TERMUX_APP.TERMUX_SERVICE] */
    const val TERMUX_APP_NOTIFICATION_CHANNEL_ID = "termux_notification_channel"
    /** Termux app notification channel name used by [TERMUX_APP.TERMUX_SERVICE] */
    const val TERMUX_APP_NOTIFICATION_CHANNEL_NAME = TERMUX_APP_NAME + " App"
    /** Termux app unique notification id used by [TERMUX_APP.TERMUX_SERVICE] */
    const val TERMUX_APP_NOTIFICATION_ID = 1337

    /** Termux app notification channel id used by [TERMUX_APP.RUN_COMMAND_SERVICE] */
    const val TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_ID = "termux_run_command_notification_channel"
    /** Termux app notification channel name used by [TERMUX_APP.RUN_COMMAND_SERVICE] */
    const val TERMUX_RUN_COMMAND_NOTIFICATION_CHANNEL_NAME = TERMUX_APP_NAME + " Run Command"
    /** Termux app unique notification id used by [TERMUX_APP.RUN_COMMAND_SERVICE] */
    const val TERMUX_RUN_COMMAND_NOTIFICATION_ID = 1338

    /** Termux app notification channel id used by plugin apps to show errors for command executions */
    const val TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_ID = "termux_plugin_command_errors_notification_channel"
    /** Termux app notification channel name used by plugin apps to show errors for command executions */
    const val TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_CHANNEL_NAME = TERMUX_APP_NAME + " Plugin Command Errors"
    /** Termux app unique notification id used by plugin apps to show errors for command executions */
    const val TERMUX_PLUGIN_COMMAND_ERRORS_NOTIFICATION_ID = 1339

    /** Termux app notification channel id used by termux app and plugins to show crash reports */
    const val TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_ID = "termux_crash_reports_notification_channel"
    /** Termux app notification channel name used by termux app and plugins to show crash reports */
    const val TERMUX_CRASH_REPORTS_NOTIFICATION_CHANNEL_NAME = TERMUX_APP_NAME + " Crash Reports"
    /** Termux app unique notification id used by termux app and plugins to show crash reports */
    const val TERMUX_CRASH_REPORTS_NOTIFICATION_ID = 1340

    /** Termux:Float app notification channel id used by [TERMUX_FLOAT_APP.TERMUX_FLOAT_SERVICE] */
    const val TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_ID = "termux_float_app_notification_channel"
    /** Termux:Float app notification channel name used by [TERMUX_FLOAT_APP.TERMUX_FLOAT_SERVICE] */
    const val TERMUX_FLOAT_APP_NOTIFICATION_CHANNEL_NAME = TERMUX_FLOAT_APP_NAME + " App"
    /** Termux:Float app unique notification id used by [TERMUX_FLOAT_APP.TERMUX_FLOAT_SERVICE] */
    const val TERMUX_FLOAT_APP_NOTIFICATION_ID = 1341

    /*
     * Termux app file share URI authority.
     */

    /** Termux app file share URI authority */
    const val TERMUX_FILE_SHARE_URI_AUTHORITY = "com.termux.files" // Default: "com.termux.files"

    /*
     * Termux app broadcast actions.
     */

    /** Broadcast action for notifying that Termux app has been opened */
    const val BROADCAST_TERMUX_OPENED = TERMUX_PACKAGE_NAME + ".app.OPEN" // Default: "com.termux.app.OPEN"

    /*
     * Termux app property variables.
     */

    /** Termux app property for allowing external apps to access termux */
    const val PROP_ALLOW_EXTERNAL_APPS = "allow-external-apps" // Default: "allow-external-apps"

    /** Termux app property for terminal cursor style */
    const val PROP_TERMINAL_CURSOR_STYLE = "terminal-cursor-style" // Default: "terminal-cursor-style"

    /** Termux app property for use full screen mode */
    const val PROP_USE_FULLSCREEN_MODE = "use-fullscreen-mode" // Default: "use-fullscreen-mode"

    /** Termux app property for open new session with current working directory */
    const val PROP_OPEN_NEW_SESSION_WITH_CURRENT_WORKING_DIRECTORY = "open-new-session-with-current-working-directory" // Default: "open-new-session-with-current-working-directory"

    /** Termux app property for open new session with same working directory as current session */
    const val PROP_OPEN_NEW_SESSION_WITH_SAME_WORKING_DIRECTORY_AS_CURRENT_SESSION = "open-new-session-with-same-working-directory-as-current-session" // Default: "open-new-session-with-same-working-directory-as-current-session"

    /** Termux app property for open new session with default working directory */
    const val PROP_OPEN_NEW_SESSION_WITH_DEFAULT_WORKING_DIRECTORY = "open-new-session-with-default-working-directory" // Default: "open-new-session-with-default-working-directory"

    /** Termux app property for use single session mode */
    const val PROP_USE_SINGLE_SESSION_MODE = "use-single-session-mode" // Default: "use-single-session-mode"

    /** Termux app property for terminal session create mode */
    const val PROP_TERMINAL_SESSION_CREATE_MODE = "terminal-session-create-mode" // Default: "terminal-session-create-mode"

    /** Termux app property for terminal shell create mode */
    const val PROP_TERMINAL_SHELL_CREATE_MODE = "terminal-shell-create-mode" // Default: "terminal-shell-create-mode"

    /** Termux app property for terminal shell default */
    const val PROP_TERMINAL_SHELL_DEFAULT = "terminal-shell-default" // Default: "terminal-shell-default"

    /** Termux app property for terminal shell fallback */
    const val PROP_TERMINAL_SHELL_FALLBACK = "terminal-shell-fallback" // Default: "terminal-shell-fallback"

    /** Termux app property for terminal session environment */
    const val PROP_TERMINAL_SESSION_ENVIRONMENT = "terminal-session-environment" // Default: "terminal-session-environment"

    /** Termux app property for terminal session environment file */
    const val PROP_TERMINAL_SESSION_ENVIRONMENT_FILE = "terminal-session-environment-file" // Default: "terminal-session-environment-file"

    /** Termux app property for terminal session shell */
    const val PROP_TERMINAL_SESSION_SHELL = "terminal-session-shell" // Default: "terminal-session-shell"

    /** Termux app property for terminal session shell args */
    const val PROP_TERMINAL_SESSION_SHELL_ARGS = "terminal-session-shell-args" // Default: "terminal-session-shell-args"

    /** Termux app property for terminal session name */
    const val PROP_TERMINAL_SESSION_NAME = "terminal-session-name" // Default: "terminal-session-name"

    /** Termux app property for terminal session action */
    const val PROP_TERMINAL_SESSION_ACTION = "terminal-session-action" // Default: "terminal-session-action"

    /** Termux app property for terminal session auto exit */
    const val PROP_TERMINAL_SESSION_AUTO_EXIT = "terminal-session-auto-exit" // Default: "terminal-session-auto-exit"

    /** Termux app property for terminal session keep environment */
    const val PROP_TERMINAL_SESSION_KEEP_ENVIRONMENT = "terminal-session-keep-environment" // Default: "terminal-session-keep-environment"

    /** Termux app property for terminal session working directory */
    const val PROP_TERMINAL_SESSION_WORKING_DIRECTORY = "terminal-session-working-directory" // Default: "terminal-session-working-directory"

    /** Termux app property for terminal session runner */
    const val PROP_TERMINAL_SESSION_RUNNER = "terminal-session-runner" // Default: "terminal-session-runner"

    /** Termux app property for terminal session result config */
    const val PROP_TERMINAL_SESSION_RESULT_CONFIG = "terminal-session-result-config" // Default: "terminal-session-result-config"

    /** Termux app property for terminal session result directory */
    const val PROP_TERMINAL_SESSION_RESULT_DIRECTORY = "terminal-session-result-directory" // Default: "terminal-session-result-directory"

    /** Termux app property for terminal session result single file */
    const val PROP_TERMINAL_SESSION_RESULT_SINGLE_FILE = "terminal-session-result-single-file" // Default: "terminal-session-result-single-file"

    /** Termux app property for terminal session result file basename */
    const val PROP_TERMINAL_SESSION_RESULT_FILE_BASENAME = "terminal-session-result-file-basename" // Default: "terminal-session-result-file-basename"

    /** Termux app property for terminal session result file output format */
    const val PROP_TERMINAL_SESSION_RESULT_FILE_OUTPUT_FORMAT = "terminal-session-result-file-output-format" // Default: "terminal-session-result-file-output-format"

    /** Termux app property for terminal session result file error format */
    const val PROP_TERMINAL_SESSION_RESULT_FILE_ERROR_FORMAT = "terminal-session-result-file-error-format" // Default: "terminal-session-result-file-error-format"

    /** Termux app property for terminal session result files suffix */
    const val PROP_TERMINAL_SESSION_RESULT_FILES_SUFFIX = "terminal-session-result-files-suffix" // Default: "terminal-session-result-files-suffix"

    /** Termux app property for terminal session replace comma alternative chars in arguments */
    const val PROP_TERMINAL_SESSION_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS = "terminal-session-replace-comma-alternative-chars-in-arguments" // Default: "terminal-session-replace-comma-alternative-chars-in-arguments"

    /** Termux app property for terminal session comma alternative chars in arguments */
    const val PROP_TERMINAL_SESSION_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS = "terminal-session-comma-alternative-chars-in-arguments" // Default: "terminal-session-comma-alternative-chars-in-arguments"

    /** Termux app property for terminal session stdin */
    const val PROP_TERMINAL_SESSION_STDIN = "terminal-session-stdin" // Default: "terminal-session-stdin"

    /** Termux app property for terminal session background custom log level */
    const val PROP_TERMINAL_SESSION_BACKGROUND_CUSTOM_LOG_LEVEL = "terminal-session-background-custom-log-level" // Default: "terminal-session-background-custom-log-level"

    /** Termux app property for terminal session command label */
    const val PROP_TERMINAL_SESSION_COMMAND_LABEL = "terminal-session-command-label" // Default: "terminal-session-command-label"

    /** Termux app property for terminal session command description */
    const val PROP_TERMINAL_SESSION_COMMAND_DESCRIPTION = "terminal-session-command-description" // Default: "terminal-session-command-description"

    /** Termux app property for terminal session command help */
    const val PROP_TERMINAL_SESSION_COMMAND_HELP = "terminal-session-command-help" // Default: "terminal-session-command-help"

    /** Termux app property for terminal session plugin API help */
    const val PROP_TERMINAL_SESSION_PLUGIN_API_HELP = "terminal-session-plugin-api-help" // Default: "terminal-session-plugin-api-help"

    /*
     * Termux app inner classes.
     */

    /** Termux app inner class for TERMUX_APP */
    object TERMUX_APP {

        /** Termux app activity name */
        const val TERMUX_ACTIVITY_NAME = TERMUX_APP_NAME + " Activity" // Default: "Termux Activity"

        /** Termux app main activity name */
        const val TERMUX_MAIN_ACTIVITY_NAME = TERMUX_APP_NAME + " Main Activity" // Default: "Termux Main Activity"

        /** Termux app launcher activity name */
        const val TERMUX_LAUNCHER_ACTIVITY_NAME = TERMUX_APP_NAME + " Launcher Activity" // Default: "Termux Launcher Activity"

        /** Termux app settings activity name */
        const val TERMUX_SETTINGS_ACTIVITY_NAME = TERMUX_APP_NAME + " Settings Activity" // Default: "Termux Settings Activity"

        /** Termux app file share receiver activity class name */
        const val FILE_SHARE_RECEIVER_ACTIVITY_CLASS_NAME = TERMUX_PACKAGE_NAME + ".filepicker.TermuxFileReceiverActivity" // Default: "com.termux.filepicker.TermuxFileReceiverActivity"

        /** Termux app file view receiver activity class name */
        const val FILE_VIEW_RECEIVER_ACTIVITY_CLASS_NAME = TERMUX_PACKAGE_NAME + ".filepicker.TermuxFileViewerActivity" // Default: "com.termux.filepicker.TermuxFileViewerActivity"

        /** Termux app BuildConfig class name */
        const val BUILD_CONFIG_CLASS_NAME = TERMUX_PACKAGE_NAME + ".BuildConfig" // Default: "com.termux.BuildConfig"

        /** Termux app am socket file path */
        const val TERMUX_AM_SOCKET_FILE_PATH = TERMUX_FILES_DIR_PATH + "/am.sock" // Default: "/data/data/com.termux/files/usr/var/run/am.sock"

        /** Termux app directory path to store termux-app */
        const val APPS_DIR_PATH = TERMUX_APPS_DIR_PATH + "/" + TERMUX_PACKAGE_NAME // Default: "/data/data/com.termux/files/apps/com.termux"

        /** Termux app directory to store termux-app */
        @JvmField
        val APPS_DIR = File(APPS_DIR_PATH)

        /** Termux app inner class for TERMUX_ACTIVITY */
        object TERMUX_ACTIVITY {

            /** Action to reload style */
            @Deprecated("Use ACTION_RECREATE_ACTIVITY instead")
            const val ACTION_RELOAD_STYLE = TERMUX_PACKAGE_NAME + ".app.TermuxActivity.RELOAD_STYLE" // Default: "com.termux.app.TermuxActivity.RELOAD_STYLE"

            /** Action to request permissions */
            const val ACTION_REQUEST_PERMISSIONS = TERMUX_PACKAGE_NAME + ".app.TermuxActivity.REQUEST_PERMISSIONS" // Default: "com.termux.app.TermuxActivity.REQUEST_PERMISSIONS"

            /** Action to notify app crash */
            const val ACTION_NOTIFY_APP_CRASH = TERMUX_PACKAGE_NAME + ".app.TermuxActivity.NOTIFY_APP_CRASH" // Default: "com.termux.app.TermuxActivity.NOTIFY_APP_CRASH"

            /** Extra for recreate activity */
            const val EXTRA_RECREATE_ACTIVITY = TERMUX_PACKAGE_NAME + ".app.TermuxActivity.recreate_activity" // Default: "com.termux.app.TermuxActivity.recreate_activity"

            /** Extra for failsafe session */
            const val EXTRA_FAILSAFE_SESSION = TERMUX_PACKAGE_NAME + ".app.TermuxActivity.failsafe_session" // Default: "com.termux.app.TermuxActivity.failsafe_session"
        }

        /** Termux app inner class for TERMUX_SERVICE */
        object TERMUX_SERVICE {

            /** Termux service name */
            const val TERMUX_SERVICE_NAME = TERMUX_APP_NAME + " Service" // Default: "Termux Service"

            /** Action to start service */
            const val ACTION_SERVICE = TERMUX_PACKAGE_NAME + ".app.TermuxService.ACTION_SERVICE" // Default: "com.termux.app.TermuxService.ACTION_SERVICE"

            /** Action to stop service */
            const val ACTION_STOP_SERVICE = TERMUX_PACKAGE_NAME + ".app.TermuxService.ACTION_STOP_SERVICE" // Default: "com.termux.app.TermuxService.ACTION_STOP_SERVICE"

            /** Extra for executable */
            const val EXTRA_EXECUTABLE = TERMUX_PACKAGE_NAME + ".app.TermuxService.executable" // Default: "com.termux.app.TermuxService.executable"

            /** Extra for arguments */
            const val EXTRA_ARGUMENTS = TERMUX_PACKAGE_NAME + ".app.TermuxService.arguments" // Default: "com.termux.app.TermuxService.arguments"

            /** Extra for runner */
            const val EXTRA_RUNNER = TERMUX_PACKAGE_NAME + ".app.TermuxService.runner" // Default: "com.termux.app.TermuxService.runner"

            /** Extra for shell name */
            const val EXTRA_SHELL_NAME = TERMUX_PACKAGE_NAME + ".app.TermuxService.shell_name" // Default: "com.termux.app.TermuxService.shell_name"

            /** Extra for shell create mode */
            const val EXTRA_SHELL_CREATE_MODE = TERMUX_PACKAGE_NAME + ".app.TermuxService.shell_create_mode" // Default: "com.termux.app.TermuxService.shell_create_mode"

            /** Extra for stdin */
            const val EXTRA_STDIN = TERMUX_PACKAGE_NAME + ".app.TermuxService.stdin" // Default: "com.termux.app.TermuxService.stdin"

            /** Extra for pending intent */
            const val EXTRA_PENDING_INTENT = TERMUX_PACKAGE_NAME + ".app.TermuxService.pending_intent" // Default: "com.termux.app.TermuxService.pending_intent"

            /** Extra for result bundle */
            const val EXTRA_RESULT_BUNDLE = TERMUX_PACKAGE_NAME + ".app.TermuxService.result_bundle" // Default: "com.termux.app.TermuxService.result_bundle"

            /** Extra for plugin result bundle */
            const val EXTRA_PLUGIN_RESULT_BUNDLE = TERMUX_PACKAGE_NAME + ".app.TermuxService.plugin_result_bundle" // Default: "com.termux.app.TermuxService.plugin_result_bundle"

            /** Extra for plugin result bundle stdout */
            const val EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT = TERMUX_PACKAGE_NAME + ".app.TermuxService.plugin_result_bundle_stdout" // Default: "com.termux.app.TermuxService.plugin_result_bundle_stdout"

            /** Extra for plugin result bundle stdout original length */
            const val EXTRA_PLUGIN_RESULT_BUNDLE_STDOUT_ORIGINAL_LENGTH = TERMUX_PACKAGE_NAME + ".app.TermuxService.plugin_result_bundle_stdout_original_length" // Default: "com.termux.app.TermuxService.plugin_result_bundle_stdout_original_length"

            /** Extra for plugin result bundle stderr */
            const val EXTRA_PLUGIN_RESULT_BUNDLE_STDERR = TERMUX_PACKAGE_NAME + ".app.TermuxService.plugin_result_bundle_stderr" // Default: "com.termux.app.TermuxService.plugin_result_bundle_stderr"

            /** Extra for plugin result bundle stderr original length */
            const val EXTRA_PLUGIN_RESULT_BUNDLE_STDERR_ORIGINAL_LENGTH = TERMUX_PACKAGE_NAME + ".app.TermuxService.plugin_result_bundle_stderr_original_length" // Default: "com.termux.app.TermuxService.plugin_result_bundle_stderr_original_length"

            /** Extra for plugin result bundle exit code */
            const val EXTRA_PLUGIN_RESULT_BUNDLE_EXIT_CODE = TERMUX_PACKAGE_NAME + ".app.TermuxService.plugin_result_bundle_exit_code" // Default: "com.termux.app.TermuxService.plugin_result_bundle_exit_code"

            /** Extra for plugin result bundle err */
            const val EXTRA_PLUGIN_RESULT_BUNDLE_ERR = TERMUX_PACKAGE_NAME + ".app.TermuxService.plugin_result_bundle_err" // Default: "com.termux.app.TermuxService.plugin_result_bundle_err"

            /** Extra for plugin result bundle errmsg */
            const val EXTRA_PLUGIN_RESULT_BUNDLE_ERRMSG = TERMUX_PACKAGE_NAME + ".app.TermuxService.plugin_result_bundle_errmsg" // Default: "com.termux.app.TermuxService.plugin_result_bundle_errmsg"

            /** Extra for stdout */
            const val EXTRA_STDOUT = TERMUX_PACKAGE_NAME + ".app.TermuxService.stdout" // Default: "com.termux.app.TermuxService.stdout"

            /** Extra for stderr */
            const val EXTRA_STDERR = TERMUX_PACKAGE_NAME + ".app.TermuxService.stderr" // Default: "com.termux.app.TermuxService.stderr"

            /** Extra for exit code */
            const val EXTRA_EXIT_CODE = TERMUX_PACKAGE_NAME + ".app.TermuxService.exit_code" // Default: "com.termux.app.TermuxService.exit_code"

            /** Extra for err */
            const val EXTRA_ERR = TERMUX_PACKAGE_NAME + ".app.TermuxService.err" // Default: "com.termux.app.TermuxService.err"

            /** Extra for errmsg */
            const val EXTRA_ERRMSG = TERMUX_PACKAGE_NAME + ".app.TermuxService.errmsg" // Default: "com.termux.app.TermuxService.errmsg"

            /** Extra for result directory */
            const val EXTRA_RESULT_DIRECTORY = TERMUX_PACKAGE_NAME + ".app.TermuxService.result_directory" // Default: "com.termux.app.TermuxService.result_directory"

            /** Extra for result single file */
            const val EXTRA_RESULT_SINGLE_FILE = TERMUX_PACKAGE_NAME + ".app.TermuxService.result_single_file" // Default: "com.termux.app.TermuxService.result_single_file"

            /** Extra for result file basename */
            const val EXTRA_RESULT_FILE_BASENAME = TERMUX_PACKAGE_NAME + ".app.TermuxService.result_file_basename" // Default: "com.termux.app.TermuxService.result_file_basename"

            /** Extra for result file output format */
            const val EXTRA_RESULT_FILE_OUTPUT_FORMAT = TERMUX_PACKAGE_NAME + ".app.TermuxService.result_file_output_format" // Default: "com.termux.app.TermuxService.result_file_output_format"

            /** Extra for result file error format */
            const val EXTRA_RESULT_FILE_ERROR_FORMAT = TERMUX_PACKAGE_NAME + ".app.TermuxService.result_file_error_format" // Default: "com.termux.app.TermuxService.result_file_error_format"

            /** Extra for result files suffix */
            const val EXTRA_RESULT_FILES_SUFFIX = TERMUX_PACKAGE_NAME + ".app.TermuxService.result_files_suffix" // Default: "com.termux.app.TermuxService.result_files_suffix"

            /** Extra for session action */
            const val EXTRA_SESSION_ACTION = TERMUX_PACKAGE_NAME + ".app.TermuxService.session_action" // Default: "com.termux.app.TermuxService.session_action"

            /** Minimum value for session action */
            const val MIN_VALUE_EXTRA_SESSION_ACTION = 0 // Default: 0

            /** Maximum value for session action */
            const val MAX_VALUE_EXTRA_SESSION_ACTION = 3 // Default: 3

            /** Value for session action: switch to new session and open activity */
            const val VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_OPEN_ACTIVITY = 0 // Default: 0

            /** Value for session action: keep current session and open activity */
            const val VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_OPEN_ACTIVITY = 1 // Default: 1

            /** Value for session action: switch to new session and don't open activity */
            const val VALUE_EXTRA_SESSION_ACTION_SWITCH_TO_NEW_SESSION_AND_DONT_OPEN_ACTIVITY = 2 // Default: 2

            /** Value for session action: keep current session and don't open activity */
            const val VALUE_EXTRA_SESSION_ACTION_KEEP_CURRENT_SESSION_AND_DONT_OPEN_ACTIVITY = 3 // Default: 3

            /** Extra for command label */
            const val EXTRA_COMMAND_LABEL = TERMUX_PACKAGE_NAME + ".app.TermuxService.command_label" // Default: "com.termux.app.TermuxService.command_label"

            /** Extra for command description */
            const val EXTRA_COMMAND_DESCRIPTION = TERMUX_PACKAGE_NAME + ".app.TermuxService.command_description" // Default: "com.termux.app.TermuxService.command_description"

            /** Extra for command help */
            const val EXTRA_COMMAND_HELP = TERMUX_PACKAGE_NAME + ".app.TermuxService.command_help" // Default: "com.termux.app.TermuxService.command_help"

            /** Extra for plugin API help */
            const val EXTRA_PLUGIN_API_HELP = TERMUX_PACKAGE_NAME + ".app.TermuxService.plugin_api_help" // Default: "com.termux.app.TermuxService.plugin_api_help"

            /** Extra for background custom log level */
            const val EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL = TERMUX_PACKAGE_NAME + ".app.TermuxService.background_custom_log_level" // Default: "com.termux.app.TermuxService.background_custom_log_level"
        }

        /** Termux app inner class for RUN_COMMAND_SERVICE */
        object RUN_COMMAND_SERVICE {

            /** Run command service name */
            const val RUN_COMMAND_SERVICE_NAME = TERMUX_APP_NAME + " Run Command Service" // Default: "Termux Run Command Service"

            /** Action to run command */
            const val ACTION_RUN_COMMAND = TERMUX_PACKAGE_NAME + ".app.RunCommandService.ACTION_RUN_COMMAND" // Default: "com.termux.app.RunCommandService.ACTION_RUN_COMMAND"

            /** Run command API help url */
            const val RUN_COMMAND_API_HELP_URL = TERMUX_WIKI_URL + "/usage/#run-command" // Default: "https://wiki.termux.com/wiki/usage/#run-command"

            /** Extra for executable */
            const val EXTRA_EXECUTABLE = TERMUX_PACKAGE_NAME + ".app.RunCommandService.executable" // Default: "com.termux.app.RunCommandService.executable"

            /** Extra for arguments */
            const val EXTRA_ARGUMENTS = TERMUX_PACKAGE_NAME + ".app.RunCommandService.arguments" // Default: "com.termux.app.RunCommandService.arguments"

            /** Extra for runner */
            const val EXTRA_RUNNER = TERMUX_PACKAGE_NAME + ".app.RunCommandService.runner" // Default: "com.termux.app.RunCommandService.runner"

            /** Extra for shell name */
            const val EXTRA_SHELL_NAME = TERMUX_PACKAGE_NAME + ".app.RunCommandService.shell_name" // Default: "com.termux.app.RunCommandService.shell_name"

            /** Extra for shell create mode */
            const val EXTRA_SHELL_CREATE_MODE = TERMUX_PACKAGE_NAME + ".app.RunCommandService.shell_create_mode" // Default: "com.termux.app.RunCommandService.shell_create_mode"

            /** Extra for stdin */
            const val EXTRA_STDIN = TERMUX_PACKAGE_NAME + ".app.RunCommandService.stdin" // Default: "com.termux.app.RunCommandService.stdin"

            /** Extra for pending intent */
            const val EXTRA_PENDING_INTENT = TERMUX_PACKAGE_NAME + ".app.RunCommandService.pending_intent" // Default: "com.termux.app.RunCommandService.pending_intent"

            /** Extra for session action */
            const val EXTRA_SESSION_ACTION = TERMUX_PACKAGE_NAME + ".app.RunCommandService.session_action" // Default: "com.termux.app.RunCommandService.session_action"

            /** Extra for command label */
            const val EXTRA_COMMAND_LABEL = TERMUX_PACKAGE_NAME + ".app.RunCommandService.command_label" // Default: "com.termux.app.RunCommandService.command_label"

            /** Extra for command description */
            const val EXTRA_COMMAND_DESCRIPTION = TERMUX_PACKAGE_NAME + ".app.RunCommandService.command_description" // Default: "com.termux.app.RunCommandService.command_description"

            /** Extra for command help */
            const val EXTRA_COMMAND_HELP = TERMUX_PACKAGE_NAME + ".app.RunCommandService.command_help" // Default: "com.termux.app.RunCommandService.command_help"

            /** Extra for result directory */
            const val EXTRA_RESULT_DIRECTORY = TERMUX_PACKAGE_NAME + ".app.RunCommandService.result_directory" // Default: "com.termux.app.RunCommandService.result_directory"

            /** Extra for result single file */
            const val EXTRA_RESULT_SINGLE_FILE = TERMUX_PACKAGE_NAME + ".app.RunCommandService.result_single_file" // Default: "com.termux.app.RunCommandService.result_single_file"

            /** Extra for result file basename */
            const val EXTRA_RESULT_FILE_BASENAME = TERMUX_PACKAGE_NAME + ".app.RunCommandService.result_file_basename" // Default: "com.termux.app.RunCommandService.result_file_basename"

            /** Extra for result file output format */
            const val EXTRA_RESULT_FILE_OUTPUT_FORMAT = TERMUX_PACKAGE_NAME + ".app.RunCommandService.result_file_output_format" // Default: "com.termux.app.RunCommandService.result_file_output_format"

            /** Extra for result file error format */
            const val EXTRA_RESULT_FILE_ERROR_FORMAT = TERMUX_PACKAGE_NAME + ".app.RunCommandService.result_file_error_format" // Default: "com.termux.app.RunCommandService.result_file_error_format"

            /** Extra for result files suffix */
            const val EXTRA_RESULT_FILES_SUFFIX = TERMUX_PACKAGE_NAME + ".app.RunCommandService.result_files_suffix" // Default: "com.termux.app.RunCommandService.result_files_suffix"

            /** Extra for replace comma alternative chars in arguments */
            const val EXTRA_REPLACE_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS = TERMUX_PACKAGE_NAME + ".app.RunCommandService.replace_comma_alternative_chars_in_arguments" // Default: "com.termux.app.RunCommandService.replace_comma_alternative_chars_in_arguments"

            /** Extra for comma alternative chars in arguments */
            const val EXTRA_COMMA_ALTERNATIVE_CHARS_IN_ARGUMENTS = TERMUX_PACKAGE_NAME + ".app.RunCommandService.comma_alternative_chars_in_arguments" // Default: "com.termux.app.RunCommandService.comma_alternative_chars_in_arguments"

            /** Extra for background custom log level */
            const val EXTRA_BACKGROUND_CUSTOM_LOG_LEVEL = TERMUX_PACKAGE_NAME + ".app.RunCommandService.background_custom_log_level" // Default: "com.termux.app.RunCommandService.background_custom_log_level"
        }
    }

    /** Termux:API app inner class for TERMUX_API_APP */
    object TERMUX_API_APP {

        /** Termux:API app activity name */
        const val TERMUX_API_ACTIVITY_NAME = TERMUX_API_APP_NAME + " Activity" // Default: "Termux:API Activity"

        /** Termux:API app main activity name */
        const val TERMUX_MAIN_ACTIVITY_NAME = TERMUX_API_APP_NAME + " Main Activity" // Default: "Termux:API Main Activity"

        /** Termux:API app launcher activity name */
        const val TERMUX_LAUNCHER_ACTIVITY_NAME = TERMUX_API_APP_NAME + " Launcher Activity" // Default: "Termux:API Launcher Activity"
    }

    /** Termux:Boot app inner class for TERMUX_BOOT_APP */
    object TERMUX_BOOT_APP {

        /** Termux:Boot app activity name */
        const val TERMUX_BOOT_ACTIVITY_NAME = TERMUX_BOOT_APP_NAME + " Activity" // Default: "Termux:Boot Activity"

        /** Termux:Boot app main activity name */
        const val TERMUX_MAIN_ACTIVITY_NAME = TERMUX_BOOT_APP_NAME + " Main Activity" // Default: "Termux:Boot Main Activity"

        /** Termux:Boot app launcher activity name */
        const val TERMUX_LAUNCHER_ACTIVITY_NAME = TERMUX_BOOT_APP_NAME + " Launcher Activity" // Default: "Termux:Boot Launcher Activity"
    }

    /** Termux:Float app inner class for TERMUX_FLOAT_APP */
    object TERMUX_FLOAT_APP {

        /** Termux:Float app activity name */
        const val TERMUX_FLOAT_ACTIVITY_NAME = TERMUX_FLOAT_APP_NAME + " Activity" // Default: "Termux:Float Activity"

        /** Termux:Float app main activity name */
        const val TERMUX_MAIN_ACTIVITY_NAME = TERMUX_FLOAT_APP_NAME + " Main Activity" // Default: "Termux:Float Main Activity"

        /** Termux:Float app launcher activity name */
        const val TERMUX_LAUNCHER_ACTIVITY_NAME = TERMUX_FLOAT_APP_NAME + " Launcher Activity" // Default: "Termux:Float Launcher Activity"

        /** Termux:Float app service name */
        const val TERMUX_FLOAT_SERVICE_NAME = TERMUX_FLOAT_APP_NAME + " Service" // Default: "Termux:Float Service"

        /** Termux:Float app inner class for TERMUX_FLOAT_SERVICE */
        object TERMUX_FLOAT_SERVICE {

            /** Action to stop service */
            const val ACTION_STOP_SERVICE = TERMUX_FLOAT_PACKAGE_NAME + ".TermuxFloatService.ACTION_STOP_SERVICE" // Default: "com.termux.window.TermuxFloatService.ACTION_STOP_SERVICE"

            /** Action to show */
            const val ACTION_SHOW = TERMUX_FLOAT_PACKAGE_NAME + ".TermuxFloatService.ACTION_SHOW" // Default: "com.termux.window.TermuxFloatService.ACTION_SHOW"

            /** Action to hide */
            const val ACTION_HIDE = TERMUX_FLOAT_PACKAGE_NAME + ".TermuxFloatService.ACTION_HIDE" // Default: "com.termux.window.TermuxFloatService.ACTION_HIDE"
        }
    }

    /** Termux:Styling app inner class for TERMUX_STYLING_APP */
    object TERMUX_STYLING_APP {

        /** Termux:Styling app activity name */
        const val TERMUX_STYLING_ACTIVITY_NAME = TERMUX_STYLING_APP_NAME + " Activity" // Default: "Termux:Styling Activity"

        /** Termux:Styling app main activity name */
        const val TERMUX_MAIN_ACTIVITY_NAME = TERMUX_STYLING_APP_NAME + " Main Activity" // Default: "Termux:Styling Main Activity"

        /** Termux:Styling app launcher activity name */
        const val TERMUX_LAUNCHER_ACTIVITY_NAME = TERMUX_STYLING_APP_NAME + " Launcher Activity" // Default: "Termux:Styling Launcher Activity"
    }

    /** Termux:Tasker app inner class for TERMUX_TASKER_APP */
    object TERMUX_TASKER_APP {

        /** Termux:Tasker app activity name */
        const val TERMUX_TASKER_ACTIVITY_NAME = TERMUX_TASKER_APP_NAME + " Activity" // Default: "Termux:Tasker Activity"

        /** Termux:Tasker app main activity name */
        const val TERMUX_MAIN_ACTIVITY_NAME = TERMUX_TASKER_APP_NAME + " Main Activity" // Default: "Termux:Tasker Main Activity"

        /** Termux:Tasker app launcher activity name */
        const val TERMUX_LAUNCHER_ACTIVITY_NAME = TERMUX_TASKER_APP_NAME + " Launcher Activity" // Default: "Termux:Tasker Launcher Activity"
    }

    /** Termux:Widget app inner class for TERMUX_WIDGET_APP */
    object TERMUX_WIDGET_APP {

        /** Termux:Widget app activity name */
        const val TERMUX_WIDGET_ACTIVITY_NAME = TERMUX_WIDGET_APP_NAME + " Activity" // Default: "Termux:Widget Activity"

        /** Termux:Widget app main activity name */
        const val TERMUX_MAIN_ACTIVITY_NAME = TERMUX_WIDGET_APP_NAME + " Main Activity" // Default: "Termux:Widget Main Activity"

        /** Termux:Widget app launcher activity name */
        const val TERMUX_LAUNCHER_ACTIVITY_NAME = TERMUX_WIDGET_APP_NAME + " Launcher Activity" // Default: "Termux:Widget Launcher Activity"

        /** Termux:Widget app inner class for TERMUX_WIDGET_PROVIDER */
        object TERMUX_WIDGET_PROVIDER {

            /** Action for widget item clicked */
            const val ACTION_WIDGET_ITEM_CLICKED = TERMUX_WIDGET_PACKAGE_NAME + ".TermuxWidgetProvider.ACTION_WIDGET_ITEM_CLICKED" // Default: "com.termux.widget.TermuxWidgetProvider.ACTION_WIDGET_ITEM_CLICKED"

            /** Action to refresh widget */
            const val ACTION_REFRESH_WIDGET = TERMUX_WIDGET_PACKAGE_NAME + ".TermuxWidgetProvider.ACTION_REFRESH_WIDGET" // Default: "com.termux.widget.TermuxWidgetProvider.ACTION_REFRESH_WIDGET"

            /** Extra for file clicked */
            const val EXTRA_FILE_CLICKED = TERMUX_WIDGET_PACKAGE_NAME + ".TermuxWidgetProvider.EXTRA_FILE_CLICKED" // Default: "com.termux.widget.TermuxWidgetProvider.EXTRA_FILE_CLICKED"
        }
    }

    /** Termux app comma characters */
    object COMMA {
        /** Normal comma */
        const val NORMAL = "," // Default: ","

        /** Alternative comma */
        const val ALTERNATIVE = "؛" // Default: "؛"
    }

    /** Termux app env prefix root */
    const val TERMUX_ENV_PREFIX_ROOT = "export PREFIX=\"" + TERMUX_PREFIX_DIR_PATH + "\"" // Default: "export PREFIX=\"/data/data/com.termux/files/usr\""

    /** Termux app env prefix */
    const val TERMUX_ENV_PREFIX = "export PREFIX=\${PREFIX:-" + TERMUX_PREFIX_DIR_PATH + "}" // Default: "export PREFIX=\${PREFIX:-/data/data/com.termux/files/usr}"

    /** Termux app env home */
    const val TERMUX_ENV_HOME = "export HOME=\"" + TERMUX_HOME_DIR_PATH + "\"" // Default: "export HOME=\"/data/data/com.termux/files/home\""

    /** Termux app env internal private app data dir */
    const val TERMUX_ENV_INTERNAL_PRIVATE_APP_DATA_DIR = "export TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR=\"" + TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR_PATH + "\"" // Default: "export TERMUX_INTERNAL_PRIVATE_APP_DATA_DIR=\"/data/data/com.termux\""
}
