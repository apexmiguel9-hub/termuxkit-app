package com.termux.app
import java.util.*

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.res.Configuration
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import android.view.ContextMenu
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ListView
import android.widget.RelativeLayout
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.annotation.Nullable
import androidx.appcompat.app.AppCompatActivity
import androidx.drawerlayout.widget.DrawerLayout
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.termux.R
import com.termux.app.api.file.FileReceiverActivity
import com.termux.app.activities.HelpActivity
import com.termux.app.activities.SettingsActivity
import com.termux.app.style.TermuxBackgroundManager
import com.termux.app.terminal.TermuxActivityRootView
import com.termux.app.terminal.TermuxSessionsListViewController
import com.termux.app.terminal.TermuxTerminalSessionActivityClient
import com.termux.app.terminal.TermuxTerminalViewClient
import com.termux.app.terminal.io.TerminalToolbarViewPager
import com.termux.app.terminal.io.TermuxTerminalExtraKeys
import com.termux.shared.activities.ReportActivity
import com.termux.shared.activity.ActivityUtils
import com.termux.shared.activity.media.AppCompatActivityUtils
import com.termux.shared.android.PermissionUtils
import com.termux.shared.data.DataUtils
import com.termux.shared.data.IntentUtils
import com.termux.shared.logger.Logger
import com.termux.shared.termux.TermuxConstants
import com.termux.shared.termux.TermuxConstants.TERMUX_APP.TERMUX_ACTIVITY
import com.termux.shared.termux.TermuxUtils
import com.termux.shared.termux.crash.TermuxCrashUtils
import com.termux.shared.termux.extrakeys.ExtraKeysView
import com.termux.shared.termux.interact.TextInputDialogUtils
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences
import com.termux.shared.termux.settings.properties.TermuxAppSharedProperties
import com.termux.shared.termux.theme.TermuxThemeUtils
import com.termux.shared.theme.NightMode
import com.termux.shared.view.ViewUtils
import com.termux.terminal.TerminalSession
import com.termux.view.TerminalView

/**
 * A terminal emulator activity.
 *
 * See
 * - http://www.mongrel-phones.com.au/default/how_to_make_a_local_service_and_bind_to_it_in_android
 * - https://code.google.com/p/android/issues/detail?id=6426
 * about memory leaks.
 */
class TermuxActivity : AppCompatActivity(), ServiceConnection {

    /**
     * The connection to the [TermuxService]. Requested in [onCreate] with a call to
     * [bindService], and obtained and stored in [onServiceConnected].
     */
    var termuxService: TermuxService? = null
        private set

    /**
     * The [TerminalView] shown in [TermuxActivity] that displays the terminal.
     */
    lateinit var terminalView: TerminalView
        private set

    /**
     * The [TerminalViewClient] interface implementation to allow for communication between
     * [TerminalView] and [TermuxActivity].
     */
    lateinit var termuxTerminalViewClient: TermuxTerminalViewClient
        private set

    /**
     * The [TerminalSessionClient] interface implementation to allow for communication between
     * [TerminalSession] and [TermuxActivity].
     */
    lateinit var termuxTerminalSessionActivityClient: TermuxTerminalSessionActivityClient
        private set

    /**
     * Termux app shared preferences manager.
     */
    private var preferences: TermuxAppSharedPreferences? = null

    /**
     * Termux app SharedProperties loaded from termux.properties
     */
    private lateinit var properties: TermuxAppSharedProperties

    /**
     * The root view of the [TermuxActivity].
     */
    lateinit var termuxActivityRootView: TermuxActivityRootView
        private set

    /**
     * The space at the bottom of [termuxActivityRootView] of the [TermuxActivity].
     */
    private lateinit var termuxActivityBottomSpaceView: View

    /**
     * The terminal extra keys view.
     */
    private var extraKeysView: ExtraKeysView? = null

    /**
     * The client for the [extraKeysView].
     */
    private lateinit var termuxTerminalExtraKeys: TermuxTerminalExtraKeys

    /**
     * The termux sessions list controller.
     */
    private lateinit var termuxSessionListViewController: TermuxSessionsListViewController

    /**
     * The termux background manager for updating background.
     */
    private lateinit var termuxBackgroundManager: TermuxBackgroundManager

    /**
     * The BottomNavigationView for tab navigation.
     */
    private var bottomNavigation: BottomNavigationView? = null

    /**
     * The terminal drawer layout.
     */
    private var terminalDrawerLayout: DrawerLayout? = null

    /**
     * The GUI tab content.
     */
    private var guiTabContent: FrameLayout? = null

    /**
     * The [TermuxActivity] broadcast receiver for various things like terminal style configuration changes.
     */
    private val mTermuxActivityBroadcastReceiver = TermuxActivityBroadcastReceiver()

    /**
     * The last toast shown, used cancel current toast before showing new in [showToast].
     */
    private var lastToast: Toast? = null

    /**
     * If between onResume() and onStop(). Note that only one session is in the foreground of the terminal view at the
     * time, so if the session causing a change is not in the foreground it should probably be treated as background.
     */
    private var isVisible = false

    /**
     * If onResume() was called after onCreate().
     */
    private var isOnResumeAfterOnCreate = false

    /**
     * If activity was restarted like due to call to [recreate] after receiving
     * [TERMUX_ACTIVITY.ACTION_RELOAD_STYLE], system dark night mode was changed or activity
     * was killed by android.
     */
    private var isActivityRecreated = false

    /**
     * The [TermuxActivity] is in an invalid state and must not be run.
     */
    private var isInvalidState = false

    private var navBarHeight = 0

    private var terminalToolbarDefaultHeight = 0f

    companion object {
        private const val CONTEXT_MENU_SELECT_URL_ID = 0
        private const val CONTEXT_MENU_SHARE_TRANSCRIPT_ID = 1
        private const val CONTEXT_MENU_SHARE_SELECTED_TEXT = 10
        private const val CONTEXT_MENU_AUTOFILL_USERNAME = 11
        private const val CONTEXT_MENU_AUTOFILL_PASSWORD = 2
        private const val CONTEXT_MENU_RESET_TERMINAL_ID = 3
        private const val CONTEXT_MENU_KILL_PROCESS_ID = 4
        private const val CONTEXT_MENU_STYLING_ID = 5
        private const val CONTEXT_SUBMENU_FONT_AND_COLOR_ID = 14
        private const val CONTEXT_SUBMENU_SET_BACKROUND_IMAGE_ID = 15
        private const val CONTEXT_SUBMENU_REMOVE_BACKGROUND_IMAGE_ID = 16
        private const val CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON = 6
        private const val CONTEXT_MENU_HELP_ID = 7
        private const val CONTEXT_MENU_SETTINGS_ID = 8
        private const val CONTEXT_MENU_REPORT_ID = 9

        private const val ARG_TERMINAL_TOOLBAR_TEXT_INPUT = "terminal_toolbar_text_input"
        private const val ARG_ACTIVITY_RECREATED = "activity_recreated"

        private const val LOG_TAG = "TermuxActivity"

        private const val NAV_TERMINAL = R.id.nav_terminal
        private const val NAV_GUI = R.id.nav_gui
        private const val NAV_SETTINGS = R.id.nav_settings
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        Logger.logDebug(LOG_TAG, "onCreate")
        isOnResumeAfterOnCreate = true

        if (savedInstanceState != null)
            isActivityRecreated = savedInstanceState.getBoolean(ARG_ACTIVITY_RECREATED, false)

        // Delete ReportInfo serialized object files from cache older than 14 days
        ReportActivity.deleteReportInfoFilesOlderThanXDays(this, 14, false)

        // Load Termux app SharedProperties from disk
        properties = TermuxAppSharedProperties.getProperties()
        reloadProperties()

        setActivityTheme()

        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_termux)

        // Load termux shared preferences
        // This will also fail if TermuxConstants.TERMUX_PACKAGE_NAME does not equal applicationId
        preferences = TermuxAppSharedPreferences.build(this, true)
        if (preferences == null) {
            // An AlertDialog should have shown to kill the app, so we don't continue running activity code
            isInvalidState = true
            return
        }

        setMargins()

        termuxActivityRootView = findViewById(R.id.activity_termux_root_view)
        termuxActivityRootView.setActivity(this)
        termuxActivityBottomSpaceView = findViewById(R.id.activity_termux_bottom_space_view)
        termuxActivityRootView.setOnApplyWindowInsetsListener(TermuxActivityRootView.WindowInsetsListener())

        val content = findViewById<View>(android.R.id.content)
        content.setOnApplyWindowInsetsListener { _, insets ->
            navBarHeight = insets.systemWindowInsetBottom
            insets
        }

        if (properties.isUsingFullScreen) {
            window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)
        }

        // Must be done every time activity is created in order to registerForActivityResult,
        // Even if the logic of launching is based on user input.
        setBackgroundManager()

        setTermuxTerminalViewAndClients()

        setTerminalToolbarView(savedInstanceState)

        setSettingsButtonView()

        setNewSessionButtonView()

        setToggleKeyboardView()

        registerForContextMenu(terminalView)

        setupBottomNavigationView()

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this)

        try {
            // Start the [TermuxService] and make it run regardless of who is bound to it
            val serviceIntent = Intent(this, TermuxService::class.java)
            startService(serviceIntent)

            // Attempt to bind to the service, this will call the [onServiceConnected]
            // callback if it succeeds.
            if (!bindService(serviceIntent, this, 0))
                throw RuntimeException("bindService() failed")
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "TermuxActivity failed to start TermuxService", e)
            Logger.showToast(
                this,
                getString(
                    if (e.message != null && e.message.contains("app is in background"))
                        R.string.error_termux_service_start_failed_bg
                    else
                        R.string.error_termux_service_start_failed_general
                ),
                true
            )
            isInvalidState = true
            return
        }

        // Send the [TermuxConstants.BROADCAST_TERMUX_OPENED] broadcast to notify apps that Termux
        // app has been opened.
        TermuxUtils.sendTermuxOpenedBroadcast(this)
    }

    override fun onStart() {
        super.onStart()

        Logger.logDebug(LOG_TAG, "onStart")

        if (isInvalidState) return

        isVisible = true

        termuxTerminalSessionActivityClient.onStart()
        termuxTerminalViewClient.onStart()

        if ((preferences ?: return).isTerminalMarginAdjustmentEnabled)
            addTermuxActivityRootViewGlobalLayoutListener()

        registerTermuxActivityBroadcastReceiver()
    }

    override fun onResume() {
        super.onResume()

        Logger.logVerbose(LOG_TAG, "onResume")

        if (isInvalidState) return

        termuxTerminalSessionActivityClient.onResume()
        termuxTerminalViewClient.onResume()

        // Check if a crash happened on last run of the app or if a plugin crashed and show a
        // notification with the crash details if it did
        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(this, LOG_TAG)

        isOnResumeAfterOnCreate = false
    }

    override fun onStop() {
        super.onStop()

        Logger.logDebug(LOG_TAG, "onStop")

        if (isInvalidState) return

        isVisible = false

        termuxTerminalSessionActivityClient.onStop()
        termuxTerminalViewClient.onStop()

        removeTermuxActivityRootViewGlobalLayoutListener()

        unregisterTermuxActivityBroadcastReceiver()
        drawer.closeDrawers()
    }

    override fun onDestroy() {
        super.onDestroy()

        Logger.logDebug(LOG_TAG, "onDestroy")

        if (isInvalidState) return

        termuxService?.let {
            // Do not leave service and session clients with references to activity.
            it.unsetTermuxTerminalSessionClient()
            termuxService = null
        }

        try {
            unbindService(this)
        } catch (e: Exception) {
            Logger.logStackTraceWithMessage(LOG_TAG, "unbindService() failed", e)
        }
    }

    override fun onSaveInstanceState(@NonNull savedInstanceState: Bundle) {
        Logger.logVerbose(LOG_TAG, "onSaveInstanceState")

        super.onSaveInstanceState(savedInstanceState)
        saveTerminalToolbarTextInput(savedInstanceState)
        savedInstanceState.putBoolean(ARG_ACTIVITY_RECREATED, true)
    }

    override fun onConfigurationChanged(@NonNull newConfig: Configuration) {
        Logger.logVerbose(LOG_TAG, "onConfigurationChanged")

        super.onConfigurationChanged(newConfig)
        termuxTerminalSessionActivityClient.onConfigurationChanged(newConfig)
    }

    /**
     * Part of the [ServiceConnection] interface. The service is bound with
     * [bindService] in [onCreate] which will cause a call to this
     * callback method.
     */
    override fun onServiceConnected(componentName: ComponentName, service: IBinder) {
        Logger.logDebug(LOG_TAG, "onServiceConnected")

        termuxService = (service as TermuxService.LocalBinder).service

        setTermuxSessionsListView()

        val intent = intent
        setIntent(null)

        if (termuxService?.isTermuxSessionsEmpty ?: true) {
            if (isVisible) {
                TermuxInstaller.setupBootstrapIfNeeded(this) {
                    if (termuxService == null) return@setupBootstrapIfNeeded // Activity might have been destroyed.
                    try {
                        val launchFailsafe = intent?.extras?.getBoolean(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false) ?: false
                        termuxTerminalSessionActivityClient.addNewSession(launchFailsafe, null)

                        // Auto-launch proot distro if installed
                        launchProotDistroIfAvailable()

                    } catch (e: WindowManager.BadTokenException) {
                        // Activity finished - ignore.
                    }
                }
            } else {
                // The service connected while not in foreground - just bail out.
                finishActivityIfNotFinishing()
            }
        } else {
            // If termux was started from launcher "New session" shortcut and activity is recreated,
            // then the original intent will be re-delivered, resulting in a new session being re-added
            // each time.
            if (!isActivityRecreated && intent != null && Intent.ACTION_RUN == intent.action) {
                // Android 7.1 app shortcut from res/xml/shortcuts.xml.
                val isFailSafe = intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_FAILSAFE_SESSION, false)
                termuxTerminalSessionActivityClient.addNewSession(isFailSafe, null)
            } else {
                termuxTerminalSessionActivityClient.setCurrentSession(termuxTerminalSessionActivityClient.getCurrentStoredSessionOrLast())
            }
        }

        // Update the [TerminalSession] and [TerminalEmulator] clients.
        termuxService?.setTermuxTerminalSessionClient(termuxTerminalSessionActivityClient)
    }

    override fun onServiceDisconnected(name: ComponentName) {
        Logger.logDebug(LOG_TAG, "onServiceDisconnected")

        // Respect being stopped from the [TermuxService] notification action.
        finishActivityIfNotFinishing()
    }

    private fun reloadProperties() {
        properties.loadTermuxPropertiesFromDisk()

        if (::termuxTerminalViewClient.isInitialized)
            termuxTerminalViewClient.onReloadProperties()
    }

    private fun setActivityTheme() {
        // Update NightMode.APP_NIGHT_MODE
        TermuxThemeUtils.setAppNightMode(properties.nightMode)

        // Set activity night mode. If NightMode.SYSTEM is set, then android will automatically
        // trigger recreation of activity when uiMode/dark mode configuration is changed so that
        // day or night theme takes affect.
        AppCompatActivityUtils.setNightMode(this, NightMode.appNightMode.name, true)
    }

    private fun setMargins() {
        val relativeLayout = findViewById<RelativeLayout>(R.id.activity_termux_root_relative_layout)
        val marginHorizontal = properties.terminalMarginHorizontal
        val marginVertical = properties.terminalMarginVertical
        ViewUtils.setLayoutMarginsInDp(relativeLayout, marginHorizontal, marginVertical, marginHorizontal, marginVertical)
    }

    fun addTermuxActivityRootViewGlobalLayoutListener() {
        termuxActivityRootView.viewTreeObserver.addOnGlobalLayoutListener(termuxActivityRootView)
    }

    fun removeTermuxActivityRootViewGlobalLayoutListener() {
        if (::termuxActivityRootView.isInitialized)
            termuxActivityRootView.viewTreeObserver.removeOnGlobalLayoutListener(termuxActivityRootView)
    }

    private fun setTermuxTerminalViewAndClients() {
        // Set termux terminal view and session clients
        termuxTerminalSessionActivityClient = TermuxTerminalSessionActivityClient(this)
        termuxTerminalViewClient = TermuxTerminalViewClient(this, termuxTerminalSessionActivityClient)

        // Set termux terminal view
        terminalView = findViewById(R.id.terminal_view)
        terminalView.setTerminalViewClient(termuxTerminalViewClient)

        if (::termuxTerminalViewClient.isInitialized)
            termuxTerminalViewClient.onCreate()

        if (::termuxTerminalSessionActivityClient.isInitialized)
            termuxTerminalSessionActivityClient.onCreate()
    }

    private fun setTermuxSessionsListView() {
        val termuxSessionsListView = findViewById<ListView>(R.id.terminal_sessions_list)
        termuxSessionListViewController = TermuxSessionsListViewController(this, termuxService?.termuxSessions ?: emptyList())
        termuxSessionsListView.adapter = termuxSessionListViewController
        termuxSessionsListView.setOnItemClickListener(termuxSessionListViewController)
        termuxSessionsListView.setOnItemLongClickListener(termuxSessionListViewController)
    }

    private fun setTerminalToolbarView(savedInstanceState: Bundle?) {
        termuxTerminalExtraKeys = TermuxTerminalExtraKeys(this, terminalView,
            termuxTerminalViewClient, termuxTerminalSessionActivityClient)

        val terminalToolbarViewPager = terminalToolbarViewPager
        if ((preferences ?: return).shouldShowTerminalToolbar) terminalToolbarViewPager.visibility = View.VISIBLE

        val layoutParams = terminalToolbarViewPager.layoutParams
        terminalToolbarDefaultHeight = layoutParams.height.toFloat()

        setTerminalToolbarHeight()

        val savedTextInput = savedInstanceState?.getString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT)

        terminalToolbarViewPager.adapter = TerminalToolbarViewPager.PageAdapter(this, savedTextInput)
        terminalToolbarViewPager.addOnPageChangeListener(TerminalToolbarViewPager.OnPageChangeListener(this, terminalToolbarViewPager))
    }

    private fun setTerminalToolbarHeight() {
        val terminalToolbarViewPager = terminalToolbarViewPager
        if (terminalToolbarViewPager == null) return

        val layoutParams = terminalToolbarViewPager.layoutParams
        layoutParams.height = Math.round(terminalToolbarDefaultHeight *
            (if (termuxTerminalExtraKeys.extraKeysInfo == null) 0 else termuxTerminalExtraKeys.extraKeysInfo?.matrix ?: emptyList().size) *
            properties.terminalToolbarHeightScaleFactor)
        terminalToolbarViewPager.layoutParams = layoutParams
    }

    fun toggleTerminalToolbar() {
        val terminalToolbarViewPager = terminalToolbarViewPager
        if (terminalToolbarViewPager == null) return

        val showNow = (preferences ?: return).toogleShowTerminalToolbar()
        Logger.showToast(this, if (showNow) getString(R.string.msg_enabling_terminal_toolbar) else getString(R.string.msg_disabling_terminal_toolbar), true)
        terminalToolbarViewPager.visibility = if (showNow) View.VISIBLE else View.GONE
        if (showNow && isTerminalToolbarTextInputViewSelected) {
            // Focus the text input view if just revealed.
            findViewById<View>(R.id.terminal_toolbar_text_input).requestFocus()
        }
    }

    private fun saveTerminalToolbarTextInput(savedInstanceState: Bundle) {
        val textInputView = findViewById<EditText>(R.id.terminal_toolbar_text_input)
        if (textInputView != null) {
            val textInput = textInputView.text.toString()
            if (textInput.isNotEmpty()) savedInstanceState.putString(ARG_TERMINAL_TOOLBAR_TEXT_INPUT, textInput)
        }
    }

    private fun setSettingsButtonView() {
        val settingsButton = findViewById<ImageButton>(R.id.settings_button)
        settingsButton.setOnClickListener {
            ActivityUtils.startActivity(this, Intent(this, SettingsActivity::class.java))
        }
    }

    private fun setNewSessionButtonView() {
        val newSessionButton = findViewById<View>(R.id.new_session_button)
        newSessionButton.setOnClickListener { termuxTerminalSessionActivityClient.addNewSession(false, null) }
        newSessionButton.setOnLongClickListener {
            TextInputDialogUtils.textInput(
                this,
                R.string.title_create_named_session,
                null,
                R.string.action_create_named_session_confirm,
                { text -> termuxTerminalSessionActivityClient.addNewSession(false, text) },
                R.string.action_new_session_failsafe,
                { text -> termuxTerminalSessionActivityClient.addNewSession(true, text) },
                -1,
                null,
                null
            )
            true
        }
    }

    private fun setToggleKeyboardView() {
        findViewById<View>(R.id.toggle_keyboard_button).setOnClickListener {
            termuxTerminalViewClient.onToggleSoftKeyboardRequest()
            drawer.closeDrawers()
        }

        findViewById<View>(R.id.toggle_keyboard_button).setOnLongClickListener {
            toggleTerminalToolbar()
            true
        }
    }

    private fun setBackgroundManager() {
        termuxBackgroundManager = TermuxBackgroundManager(this)
    }

    private fun setupBottomNavigationView() {
        // Find views
        bottomNavigation = findViewById(R.id.bottom_navigation)
        terminalDrawerLayout = findViewById(R.id.terminal_drawer_layout)
        guiTabContent = findViewById(R.id.gui_tab_content)

        // Check if views were found
        if (bottomNavigation == null) {
            Logger.logError(LOG_TAG, "BottomNavigationView not found!")
            return
        }
        if (terminalDrawerLayout == null) {
            Logger.logError(LOG_TAG, "TerminalDrawerLayout not found!")
            return
        }
        if (guiTabContent == null) {
            Logger.logError(LOG_TAG, "GuiTabContent not found!")
            return
        }

        Logger.logDebug(LOG_TAG, "Views found - BottomNav: $bottomNavigation, TerminalDrawer: $terminalDrawerLayout, GuiContent: $guiTabContent")
        Logger.logDebug(LOG_TAG, "Initial visibility - TerminalDrawer: ${terminalDrawerLayout.visibility}, GuiContent: ${guiTabContent.visibility}")

        // Set default selection to Terminal
        bottomNavigation.selectedItemId = NAV_TERMINAL

        // Set up the listener
        bottomNavigation.setOnItemSelectedListener { item ->
            Logger.logDebug(LOG_TAG, "=== Item clicked: ${item.itemId} ===")

            // Use post() to ensure visibility changes happen after the current event is processed
            // This prevents blocking the main thread and avoids ANR
            when (item.itemId) {
                NAV_TERMINAL -> {
                    Logger.logDebug(LOG_TAG, "Switching to Terminal tab (posted)")
                    terminalDrawerLayout?.post {
                        Logger.logDebug(LOG_TAG, "Executing Terminal tab switch")
                        terminalDrawerLayout?.visibility = View.VISIBLE
                        guiTabContent?.visibility = View.GONE
                        terminalDrawerLayout?.invalidate()
                        guiTabContent?.invalidate()
                        terminalDrawerLayout?.requestLayout()
                        guiTabContent?.requestLayout()
                        Logger.logDebug(LOG_TAG, "Terminal tab switch complete - TerminalDrawer: ${terminalDrawerLayout?.visibility}, GuiContent: ${guiTabContent?.visibility}")
                    }
                    true
                }
                NAV_GUI -> {
                    Logger.logDebug(LOG_TAG, "Switching to GUI tab (posted)")
                    guiTabContent?.post {
                        Logger.logDebug(LOG_TAG, "Executing GUI tab switch")
                        terminalDrawerLayout?.visibility = View.GONE
                        guiTabContent?.visibility = View.VISIBLE
                        guiTabContent?.bringToFront()
                        terminalDrawerLayout?.invalidate()
                        guiTabContent?.invalidate()
                        terminalDrawerLayout?.requestLayout()
                        guiTabContent?.requestLayout()
                        Logger.logDebug(LOG_TAG, "GUI tab switch complete - TerminalDrawer: ${terminalDrawerLayout?.visibility}, GuiContent: ${guiTabContent?.visibility}")
                    }
                    true
                }
                NAV_SETTINGS -> {
                    Logger.logDebug(LOG_TAG, "Opening Settings")
                    // Open settings activity
                    ActivityUtils.startActivity(this@TermuxActivity, Intent(this@TermuxActivity, SettingsActivity::class.java))
                    true
                }
                else -> {
                    Logger.logDebug(LOG_TAG, "Unknown item: ${item.itemId}")
                    false
                }
            }
        }
    }

    @SuppressLint("RtlHardcoded")
    override fun onBackPressed() {
        if (drawer.isDrawerOpen(Gravity.LEFT)) {
            drawer.closeDrawers()
        } else {
            finishActivityIfNotFinishing()
        }
    }

    fun finishActivityIfNotFinishing() {
        // prevent duplicate calls to finish() if called from multiple places
        if (!isFinishing) {
            finish()
        }
    }

    /** Show a toast and dismiss the last one if still visible. */
    fun showToast(text: String?, longDuration: Boolean) {
        if (text == null || text.isEmpty()) return
        lastToast?.cancel()
        lastToast = Toast.makeText(this, text, if (longDuration) Toast.LENGTH_LONG else Toast.LENGTH_SHORT).apply {
            setGravity(Gravity.TOP, 0, 0)
            show()
        }
    }

    override fun onCreateContextMenu(menu: ContextMenu, v: View, menuInfo: ContextMenuInfo?) {
        val currentSession = currentSession ?: return

        val autoFillEnabled = terminalView.isAutoFillEnabled

        menu.add(Menu.NONE, CONTEXT_MENU_SELECT_URL_ID, Menu.NONE, R.string.action_select_url)
        menu.add(Menu.NONE, CONTEXT_MENU_SHARE_TRANSCRIPT_ID, Menu.NONE, R.string.action_share_transcript)
        if (!DataUtils.isNullOrEmpty(terminalView.storedSelectedText))
            menu.add(Menu.NONE, CONTEXT_MENU_SHARE_SELECTED_TEXT, Menu.NONE, R.string.action_share_selected_text)
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_USERNAME, Menu.NONE, R.string.action_autofill_username)
        if (autoFillEnabled)
            menu.add(Menu.NONE, CONTEXT_MENU_AUTOFILL_PASSWORD, Menu.NONE, R.string.action_autofill_password)
        menu.add(Menu.NONE, CONTEXT_MENU_RESET_TERMINAL_ID, Menu.NONE, R.string.action_reset_terminal)
        menu.add(Menu.NONE, CONTEXT_MENU_KILL_PROCESS_ID, Menu.NONE, resources.getString(R.string.action_kill_process, currentSession.pid))
            .setEnabled(currentSession.isRunning)
        val subMenu = menu.addSubMenu(Menu.NONE, CONTEXT_MENU_STYLING_ID, Menu.NONE, R.string.action_style_terminal)
        subMenu.clearHeader()
        subMenu.add(SubMenu.NONE, CONTEXT_SUBMENU_FONT_AND_COLOR_ID, SubMenu.NONE, R.string.action_font_and_color)
        subMenu.add(SubMenu.NONE, CONTEXT_SUBMENU_SET_BACKROUND_IMAGE_ID, SubMenu.NONE, R.string.action_set_background_image)
        subMenu.add(SubMenu.NONE, CONTEXT_SUBMENU_REMOVE_BACKGROUND_IMAGE_ID, SubMenu.NONE, R.string.action_remove_background_image)
        menu.add(Menu.NONE, CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON, Menu.NONE, R.string.action_toggle_keep_screen_on)
            .setCheckable(true)
            .setChecked((preferences ?: return).shouldKeepScreenOn())
        menu.add(Menu.NONE, CONTEXT_MENU_HELP_ID, Menu.NONE, R.string.action_open_help)
        menu.add(Menu.NONE, CONTEXT_MENU_SETTINGS_ID, Menu.NONE, R.string.action_open_settings)
        menu.add(Menu.NONE, CONTEXT_MENU_REPORT_ID, Menu.NONE, R.string.action_report_issue)
    }

    /** Hook system menu to show context menu instead. */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        terminalView.showContextMenu()
        return false
    }

    override fun onContextItemSelected(item: MenuItem): Boolean {
        val session = currentSession

        when (item.itemId) {
            CONTEXT_MENU_SELECT_URL_ID -> {
                termuxTerminalViewClient.showUrlSelection()
                return true
            }
            CONTEXT_MENU_SHARE_TRANSCRIPT_ID -> {
                termuxTerminalViewClient.shareSessionTranscript()
                return true
            }
            CONTEXT_MENU_SHARE_SELECTED_TEXT -> {
                termuxTerminalViewClient.shareSelectedText()
                return true
            }
            CONTEXT_MENU_AUTOFILL_USERNAME -> {
                terminalView.requestAutoFillUsername()
                return true
            }
            CONTEXT_MENU_AUTOFILL_PASSWORD -> {
                terminalView.requestAutoFillPassword()
                return true
            }
            CONTEXT_MENU_RESET_TERMINAL_ID -> {
                onResetTerminalSession(session)
                return true
            }
            CONTEXT_MENU_KILL_PROCESS_ID -> {
                showKillSessionDialog(session)
                return true
            }
            CONTEXT_SUBMENU_FONT_AND_COLOR_ID -> {
                showFontAndColorDialog()
                return true
            }
            CONTEXT_SUBMENU_SET_BACKROUND_IMAGE_ID -> {
                termuxBackgroundManager.setBackgroundImage()
                return true
            }
            CONTEXT_SUBMENU_REMOVE_BACKGROUND_IMAGE_ID -> {
                termuxBackgroundManager.removeBackgroundImage(true)
                return true
            }
            CONTEXT_MENU_TOGGLE_KEEP_SCREEN_ON -> {
                toggleKeepScreenOn()
                return true
            }
            CONTEXT_MENU_HELP_ID -> {
                ActivityUtils.startActivity(this, Intent(this, HelpActivity::class.java))
                return true
            }
            CONTEXT_MENU_SETTINGS_ID -> {
                ActivityUtils.startActivity(this, Intent(this, SettingsActivity::class.java))
                return true
            }
            CONTEXT_MENU_REPORT_ID -> {
                termuxTerminalViewClient.reportIssueFromTranscript()
                return true
            }
            else -> return super.onContextItemSelected(item)
        }
    }

    override fun onContextMenuClosed(menu: Menu) {
        super.onContextMenuClosed(menu)
        // onContextMenuClosed() is triggered twice if back button is pressed to dismiss instead of tap for some reason
        terminalView.onContextMenuClosed(menu)
    }

    private fun showKillSessionDialog(session: TerminalSession?) {
        if (session == null) return

        AlertDialog.Builder(this)
            .setIcon(android.R.drawable.ic_dialog_alert)
            .setMessage(R.string.title_confirm_kill_process)
            .setPositiveButton(android.R.string.yes) { dialog, _ ->
                dialog.dismiss()
                session.finishIfRunning()
            }
            .setNegativeButton(android.R.string.no, null)
            .show()
    }

    private fun onResetTerminalSession(session: TerminalSession?) {
        if (session != null) {
            session.reset()
            showToast(resources.getString(R.string.msg_terminal_reset), true)

            if (::termuxTerminalSessionActivityClient.isInitialized)
                termuxTerminalSessionActivityClient.onResetTerminalSession()
        }
    }

    private fun showFontAndColorDialog() {
        val stylingIntent = Intent()
        stylingIntent.setClassName(
            TermuxConstants.TERMUX_STYLING_PACKAGE_NAME,
            TermuxConstants.TERMUX_STYLING_APP.TERMUX_STYLING_ACTIVITY_NAME
        )
        try {
            startActivity(stylingIntent)
        } catch (e: ActivityNotFoundException) {
            // The startActivity() call is not documented to throw IllegalArgumentException.
            // However, crash reporting shows that it sometimes does, so catch it here.
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install) { _, _ ->
                    ActivityUtils.startActivity(this, Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL)))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        } catch (e: IllegalArgumentException) {
            AlertDialog.Builder(this)
                .setMessage(getString(R.string.error_styling_not_installed))
                .setPositiveButton(R.string.action_styling_install) { _, _ ->
                    ActivityUtils.startActivity(this, Intent(Intent.ACTION_VIEW, Uri.parse(TermuxConstants.TERMUX_STYLING_FDROID_PACKAGE_URL)))
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun toggleKeepScreenOn() {
        if (terminalView.keepScreenOn) {
            terminalView.keepScreenOn = false
            (preferences ?: return).setKeepScreenOn(false)
        } else {
            terminalView.keepScreenOn = true
            (preferences ?: return).setKeepScreenOn(true)
        }
    }

    /**
     * For processes to access primary external storage (/sdcard, /storage/emulated/0, ~/storage/shared),
     * termux needs to be granted legacy WRITE_EXTERNAL_STORAGE or MANAGE_EXTERNAL_STORAGE permissions
     * if targeting targetSdkVersion 30 (android 11) and running on sdk 30 (android 11) and higher.
     */
    fun requestStoragePermission(isPermissionCallback: Boolean) {
        Thread {
            // Do not ask for permission again
            val requestCode = if (isPermissionCallback) -1 else PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION

            // If permission is granted, then also setup storage symlinks.
            if (PermissionUtils.checkAndRequestLegacyOrManageExternalStoragePermission(
                    this, requestCode, !isPermissionCallback
                )
            ) {
                if (isPermissionCallback)
                    Logger.logInfoAndShowToast(this, LOG_TAG,
                        getString(com.termux.shared.R.string.msg_storage_permission_granted_on_request))

                TermuxInstaller.setupStorageSymlinks(this)
            } else {
                if (isPermissionCallback)
                    Logger.logInfoAndShowToast(this, LOG_TAG,
                        getString(com.termux.shared.R.string.msg_storage_permission_not_granted_on_request))
            }
        }.start()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, @Nullable data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        Logger.logVerbose(LOG_TAG, "onActivityResult: requestCode: $requestCode, resultCode: $resultCode, data: ${IntentUtils.getIntentString(data)}")
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, @NonNull permissions: Array<String>, @NonNull grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Logger.logVerbose(LOG_TAG, "onRequestPermissionsResult: requestCode: $requestCode, permissions: ${permissions.contentToString()}, grantResults: ${grantResults.contentToString()}")
        if (requestCode == PermissionUtils.REQUEST_GRANT_STORAGE_PERMISSION) {
            requestStoragePermission(true)
        }
    }

    val navBarHeight: Int
        get() = navBarHeight

    val termuxActivityRootView: TermuxActivityRootView
        get() = termuxActivityRootView

    val termuxActivityBottomSpaceView: View
        get() = termuxActivityBottomSpaceView

    fun getExtraKeysView(): ExtraKeysView? = extraKeysView

    fun getTermuxTerminalExtraKeys(): TermuxTerminalExtraKeys = termuxTerminalExtraKeys

    fun setExtraKeysView(extraKeysView: ExtraKeysView) {
        extraKeysView = extraKeysView
    }

    val drawer: DrawerLayout
        get() = findViewById(R.id.terminal_drawer_layout)

    val terminalToolbarViewPager: ViewPager
        get() = findViewById(R.id.terminal_toolbar_view_pager)

    val terminalToolbarDefaultHeight: Float
        get() = terminalToolbarDefaultHeight

    val isTerminalViewSelected: Boolean
        get() = terminalToolbarViewPager.currentItem == 0

    val isTerminalToolbarTextInputViewSelected: Boolean
        get() = terminalToolbarViewPager.currentItem == 1

    fun termuxSessionListNotifyUpdated() {
        termuxSessionListViewController.notifyDataSetChanged()
    }

    fun isVisible(): Boolean = isVisible

    fun isOnResumeAfterOnCreate(): Boolean = isOnResumeAfterOnCreate

    fun isActivityRecreated(): Boolean = isActivityRecreated

    fun getTermuxService(): TermuxService? = termuxService

    fun getTerminalView(): TerminalView = terminalView

    fun getTermuxTerminalViewClient(): TermuxTerminalViewClient = termuxTerminalViewClient

    fun getTermuxTerminalSessionClient(): TermuxTerminalSessionActivityClient = termuxTerminalSessionActivityClient

    val currentSession: TerminalSession?
        get() = terminalView.currentSession

    fun getPreferences(): TermuxAppSharedPreferences = preferences ?: return

    fun getProperties(): TermuxAppSharedProperties = properties

    fun getmTermuxBackgroundManager(): TermuxBackgroundManager = termuxBackgroundManager

    companion object {
        fun updateTermuxActivityStyling(context: Context, recreateActivity: Boolean) {
            // Make sure that terminal styling is always applied.
            val stylingIntent = Intent(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE)
            stylingIntent.putExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, recreateActivity)
            context.sendBroadcast(stylingIntent)
        }

        fun startTermuxActivity(context: Context) {
            ActivityUtils.startActivity(context, newInstance(context))
        }

        fun newInstance(context: Context): Intent {
            val intent = Intent(context, TermuxActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            return intent
        }
    }

    private fun registerTermuxActivityBroadcastReceiver() {
        val intentFilter = IntentFilter().apply {
            addAction(TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH)
            addAction(TERMUX_ACTIVITY.ACTION_RELOAD_STYLE)
            addAction(TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS)
        }

        registerReceiver(mTermuxActivityBroadcastReceiver, intentFilter)
    }

    private fun unregisterTermuxActivityBroadcastReceiver() {
        unregisterReceiver(mTermuxActivityBroadcastReceiver)
    }

    private fun fixTermuxActivityBroadcastReceiverIntent(intent: Intent?) {
        if (intent == null) return

        val extraReloadStyle = intent.getStringExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE)
        if ("storage" == extraReloadStyle) {
            intent.removeExtra(TERMUX_ACTIVITY.EXTRA_RELOAD_STYLE)
            intent.action = TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS
        }
    }

    inner class TermuxActivityBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent?) {
            if (intent == null) return

            if (isVisible) {
                fixTermuxActivityBroadcastReceiverIntent(intent)

                when (intent.action) {
                    TERMUX_ACTIVITY.ACTION_NOTIFY_APP_CRASH -> {
                        Logger.logDebug(LOG_TAG, "Received intent to notify app crash")
                        TermuxCrashUtils.notifyAppCrashFromCrashLogFile(context, LOG_TAG)
                    }
                    TERMUX_ACTIVITY.ACTION_RELOAD_STYLE -> {
                        Logger.logDebug(LOG_TAG, "Received intent to reload styling")
                        reloadActivityStyling(intent.getBooleanExtra(TERMUX_ACTIVITY.EXTRA_RECREATE_ACTIVITY, true))
                    }
                    TERMUX_ACTIVITY.ACTION_REQUEST_PERMISSIONS -> {
                        Logger.logDebug(LOG_TAG, "Received intent to request storage permissions")
                        requestStoragePermission(false)
                    }
                    else -> {}
                }
            }
        }
    }

    private fun reloadActivityStyling(recreateActivity: Boolean) {
        if (::properties.isInitialized) {
            reloadProperties()

            extraKeysView?.let {
                it.setButtonTextAllCaps(properties.shouldExtraKeysTextBeAllCaps())
                it.reload(termuxTerminalExtraKeys.extraKeysInfo, terminalToolbarDefaultHeight)
            }

            // Update NightMode.APP_NIGHT_MODE
            TermuxThemeUtils.setAppNightMode(properties.nightMode)
        }

        setMargins()
        setTerminalToolbarHeight()

        FileReceiverActivity.updateFileReceiverActivityComponentsState(this)

        if (::termuxTerminalSessionActivityClient.isInitialized)
            termuxTerminalSessionActivityClient.onReloadActivityStyling()

        if (::termuxTerminalViewClient.isInitialized)
            termuxTerminalViewClient.onReloadActivityStyling()

        // To change the activity and drawer theme, activity needs to be recreated.
        // It will destroy the activity, including all stored variables and views, and onCreate()
        // will be called again. Extra keys input text, terminal sessions and transcripts will be preserved.
        if (recreateActivity) {
            Logger.logDebug(LOG_TAG, "Recreating activity")
            recreate()
        }
    }

    private fun launchProotDistroIfAvailable() {
        Thread {
            try {
                // Get installed distros
                val p = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ls /data/data/com.termux/files/usr/var/lib/proot-distro/installed-rootfs/ 2>/dev/null"))
                val reader = java.io.BufferedReader(java.io.InputStreamReader(p.inputStream))
                val distros = mutableListOf<String>()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (!line?.trim() ?: "".isEmpty()) distros.add(line.trim())
                }
                p.waitFor()

                if (distros.isEmpty()) return@Thread

                runOnUiThread {
                    if (distros.size == 1) {
                        // Only one distro, launch directly
                        injectCommand("proot-distro login ${distros[0]}")
                    } else {
                        // Multiple distros, show bottom sheet picker
                        val sheet = BottomSheetDialog(this, com.google.android.material.R.style.Theme_MaterialComponents_BottomSheetDialog)

                        val scrollView = android.widget.ScrollView(this)
                        val layout = android.widget.LinearLayout(this).apply {
                            orientation = android.widget.LinearLayout.VERTICAL
                            setPadding(40, 16, 40, 48)
                            setBackgroundColor(android.graphics.Color.parseColor("#1A1A2E"))
                        }

                        // Handle bar
                        val handleContainer = android.widget.LinearLayout(this).apply {
                            gravity = Gravity.CENTER
                            setPadding(0, 16, 0, 24)
                        }
                        val handle = android.view.View(this)
                        val handleParams = android.widget.LinearLayout.LayoutParams(120, 8)
                        handle.layoutParams = handleParams
                        val handleBg = android.graphics.drawable.GradientDrawable().apply {
                            setColor(android.graphics.Color.parseColor("#555577"))
                            cornerRadius = 16f
                        }
                        handle.background = handleBg
                        handleContainer.addView(handle)
                        layout.addView(handleContainer)

                        // Title
                        val title = android.widget.TextView(this).apply {
                            text = "Select Distribution"
                            textSize = 22f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            setTextColor(android.graphics.Color.parseColor("#E0E0FF"))
                            setPadding(8, 0, 0, 32)
                        }
                        layout.addView(title)

                        for (distro in distros) {
                            // Card container
                            val card = android.widget.LinearLayout(this).apply {
                                orientation = android.widget.LinearLayout.HORIZONTAL
                                gravity = Gravity.CENTER_VERTICAL
                                setPadding(32, 28, 32, 28)

                                val cardBg = android.graphics.drawable.GradientDrawable().apply {
                                    setColor(android.graphics.Color.parseColor("#2D2D4E"))
                                    cornerRadius = 24f
                                }
                                background = cardBg

                                layoutParams = android.widget.LinearLayout.LayoutParams(
                                    android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.LinearLayout.LayoutParams.WRAP_CONTENT
                                ).apply {
                                    setMargins(0, 0, 0, 20)
                                }
                            }

                            // Logo
                            val logo = android.widget.ImageView(this)
                            val resId = resources.getIdentifier("distro_$distro", "drawable", packageName)
                            logo.setImageResource(if (resId != 0) resId else android.R.drawable.ic_menu_info_details)
                            val logoParams = android.widget.LinearLayout.LayoutParams(80, 80).apply {
                                setMargins(0, 0, 32, 0)
                            }
                            logo.layoutParams = logoParams
                            card.addView(logo)

                            // Text column
                            val textCol = android.widget.LinearLayout(this).apply {
                                orientation = android.widget.LinearLayout.VERTICAL
                            }

                            val name = android.widget.TextView(this).apply {
                                text = distro.replaceFirstChar { it.uppercase() }
                                textSize = 18f
                                setTypeface(null, android.graphics.Typeface.BOLD)
                                setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
                            }
                            textCol.addView(name)

                            val sub = android.widget.TextView(this).apply {
                                text = "proot-distro"
                                textSize = 12f
                                setTextColor(android.graphics.Color.parseColor("#8888AA"))
                            }
                            textCol.addView(sub)

                            card.addView(textCol)

                            card.setOnClickListener {
                                injectCommand("proot-distro login $distro")
                                sheet.dismiss()
                            }
                            layout.addView(card)
                        }

                        scrollView.addView(layout)
                        sheet.setContentView(scrollView)
                        sheet.show()
                    }
                }
            } catch (e: Exception) {
                Logger.logError(LOG_TAG, "Failed to detect proot distros: ${e.message}")
            }
        }.start()
    }

    private fun injectCommand(command: String) {
        currentSession?.let { session ->
            session.write("$command\n")
        }
    }
}
