package app.vlauncher

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.findNavController
import app.vlauncher.BuildConfig
import app.vlauncher.data.Constants
import app.vlauncher.data.Prefs
import app.vlauncher.databinding.ActivityMainBinding
import app.vlauncher.helper.getColorFromAttr
import app.vlauncher.helper.hasBeenDays
import app.vlauncher.helper.hasBeenHours
import app.vlauncher.helper.hasBeenMinutes
import app.vlauncher.helper.isDarkThemeOn
import app.vlauncher.helper.isDaySince
import app.vlauncher.helper.isDefaultLauncher
import app.vlauncher.helper.Fonts
import app.vlauncher.helper.OlDialog
import app.vlauncher.helper.isEinkDisplay
import app.vlauncher.helper.isOlauncherDefault
import app.vlauncher.helper.isSystemAnimationsDisabled
import app.vlauncher.helper.isTablet
import app.vlauncher.helper.openUrl
import app.vlauncher.helper.rateApp
import app.vlauncher.helper.resetLauncherViaFakeActivity
import app.vlauncher.helper.setPlainWallpaper
import app.vlauncher.helper.Icons
import app.vlauncher.helper.AppUpdate
import app.vlauncher.helper.Updater
import app.vlauncher.helper.createDialog
import app.vlauncher.helper.shareApp
import app.vlauncher.helper.showLauncherSelector
import app.vlauncher.helper.showMessageDialog
import app.vlauncher.helper.showToast
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var navController: NavController
    private lateinit var viewModel: MainViewModel
    private lateinit var binding: ActivityMainBinding
    private var timerJob: Job? = null
    private var isResumed = false
    private var profileReceiver: BroadcastReceiver? = null
    private var launcherAppsCallback: LauncherApps.Callback? = null
    private var messageDialog: OlDialog? = null

//    override fun onBackPressed() {
//        if (navController.currentDestination?.id != R.id.mainFragment)
//            super.onBackPressed()
//    }

    override fun attachBaseContext(context: Context) {
        val newConfig = Configuration(context.resources.configuration)
        newConfig.fontScale = Prefs(context).textSizeScale
        applyOverrideConfiguration(newConfig)
        super.attachBaseContext(context)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        prefs = Prefs(this)
        if (isEinkDisplay()) prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        super.onCreate(savedInstanceState)
        theme.applyStyle(Fonts.overlayFor(prefs.fontFamily), true)
        // Bold only applies to the default family; bundled families ship their own weights.
        if (prefs.fontFamily == Fonts.DEFAULT && prefs.boldFont) theme.applyStyle(R.style.BoldFontOverlay, true)
        if (isEinkDisplay() || isSystemAnimationsDisabled()) theme.applyStyle(R.style.NoAnimationOverlay, true)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        navController = this.findNavController(R.id.nav_host_fragment)
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        val onBackPressedCallback = object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Back never leaves the home screen; elsewhere it pops the nav stack
                if (navController.currentDestination?.id != R.id.mainFragment)
                    navController.popBackStack()
            }
        }
        onBackPressedDispatcher.addCallback(this, onBackPressedCallback)

        if (prefs.firstOpen) {
            viewModel.firstOpen(true)
            prefs.firstOpen = false
            prefs.firstOpenTime = System.currentTimeMillis()
            viewModel.setDefaultClockApp()
            viewModel.resetLauncherLiveData.call()
        }

        initObservers(viewModel)
        viewModel.getAppList()
        Icons.warmUp(this)
        checkForAppUpdate()
        registerShortcutCallback()
        setupOrientation()

        window.addFlags(FLAG_LAYOUT_NO_LIMITS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            profileReceiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    viewModel.isPrivateSpaceToggling = false
                    viewModel.getPrivateSpaceAppList()
                }
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PROFILE_AVAILABLE)
                addAction(Intent.ACTION_PROFILE_UNAVAILABLE)
            }
            registerReceiver(profileReceiver, filter)
        }
    }

    override fun onStart() {
        super.onStart()
        restartLauncherOrCheckTheme()
    }

    override fun onResume() {
        super.onResume()
        isResumed = true
        viewModel.isPrivateSpaceToggling = false
        viewModel.getAppList()
    }

    private fun registerShortcutCallback() {
        val launcherApps = getSystemService(LauncherApps::class.java)
        launcherAppsCallback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String, user: android.os.UserHandle) = Unit
            override fun onPackageAdded(packageName: String, user: android.os.UserHandle) = Unit
            override fun onPackageChanged(packageName: String, user: android.os.UserHandle) = Unit
            override fun onPackagesAvailable(
                packageNames: Array<out String>,
                user: android.os.UserHandle,
                replacing: Boolean,
            ) = Unit

            override fun onPackagesUnavailable(
                packageNames: Array<out String>,
                user: android.os.UserHandle,
                replacing: Boolean,
            ) = Unit

            override fun onShortcutsChanged(
                packageName: String,
                shortcuts: MutableList<ShortcutInfo>,
                user: android.os.UserHandle,
            ) {
                viewModel.getAppList()
            }
        }
        launcherApps.registerCallback(launcherAppsCallback!!)
    }

    override fun onStop() {
        isResumed = false
        backToHomeScreen()
        super.onStop()
    }

    override fun onUserLeaveHint() {
        backToHomeScreen()
        super.onUserLeaveHint()
    }

    override fun onNewIntent(intent: Intent?) {
        // Home button for recents feature disabled
        // val alreadyHome = navController.currentDestination?.id == R.id.mainFragment
        backToHomeScreen()
        // if (alreadyHome && isResumed && prefs.homeButtonShowRecents)
        //     viewModel.showRecentApps.call()
        super.onNewIntent(intent)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppCompatDelegate.setDefaultNightMode(prefs.appTheme)
        if (prefs.dailyWallpaper && AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM) {
            setPlainWallpaper()
            viewModel.setWallpaperWorker()
            recreate()
        }
    }

    private fun initObservers(viewModel: MainViewModel) {
        viewModel.launcherResetFailed.observe(this) {
            openLauncherChooser(it)
        }
        viewModel.resetLauncherLiveData.observe(this) {
            if (isDefaultLauncher() || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
                resetLauncherViaFakeActivity()
            else
                showLauncherSelector(Constants.REQUEST_CODE_LAUNCHER_SELECTOR)
        }
        viewModel.checkForMessages.observe(this) {
            checkForMessages()
        }
        viewModel.showDialog.observe(this) {
            when (it) {
                Constants.Dialog.ABOUT -> {
                    showMessage(R.string.app_name, R.string.welcome_to_olauncher_settings, R.string.okay) {}
                }

                Constants.Dialog.WALLPAPER -> {
                    prefs.wallpaperMsgShown = true
                    prefs.userState = Constants.UserState.REVIEW
                    showMessage(R.string.did_you_know, R.string.wallpaper_message, R.string.enable) {
                        prefs.dailyWallpaper = true
                        viewModel.setWallpaperWorker()
                        showToast(getString(R.string.your_wallpaper_will_update_shortly))
                    }
                }

                Constants.Dialog.REVIEW -> {
                    prefs.userState = Constants.UserState.RATE
                    showMessage(R.string.hey, R.string.review_message, R.string.leave_a_review) {
                        prefs.rateClicked = true
                        showToast("😇❤️")
                        rateApp()
                    }
                }

                Constants.Dialog.RATE -> {
                    prefs.userState = Constants.UserState.SHARE
                    showMessage(R.string.app_name, R.string.rate_us_message, R.string.rate_now) {
                        prefs.rateClicked = true
                        showToast("🤩❤️")
                        rateApp()
                    }
                }

                Constants.Dialog.SHARE -> {
                    prefs.shareShownTime = System.currentTimeMillis()
                    showMessage(R.string.hey, R.string.share_message, R.string.share_now) {
                        showToast("😊❤️")
                        shareApp()
                    }
                }

                Constants.Dialog.HIDDEN -> {
                    showMessage(R.string.hidden_apps, R.string.hidden_apps_message, R.string.okay) {
                    }
                }

                Constants.Dialog.KEYBOARD -> {
                    showMessage(R.string.app_name, R.string.keyboard_message, R.string.okay) {
                    }
                }

                Constants.Dialog.DIGITAL_WELLBEING -> {
                    showMessage(R.string.screen_time, R.string.app_usage_message, R.string.permission) {
                        startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    }
                }

                Constants.Dialog.PRO_MESSAGE -> {
                    showMessage(R.string.hey, R.string.pro_message, R.string.olauncher_pro) {
                        openUrl(Constants.URL_OLAUNCHER_PRO)
                    }
                }
            }
        }
    }

    private fun showMessage(title: Int, message: Int, action: Int, clickListener: () -> Unit) {
        messageDialog?.dismiss()
        messageDialog = showMessageDialog(title, message, action, clickListener)
    }

    private fun checkForMessages() {
        if (prefs.firstOpenTime == 0L)
            prefs.firstOpenTime = System.currentTimeMillis()

        val calendar = Calendar.getInstance()
        val dayOfYear = calendar.get(Calendar.DAY_OF_YEAR)
        if (dayOfYear == 1 && dayOfYear != prefs.shownOnDayOfYear) {
            prefs.shownOnDayOfYear = dayOfYear
            showMessage(R.string.hey, R.string.new_year_wish, R.string.cheers) {}
            return
        } else if (dayOfYear == 32 && dayOfYear != prefs.shownOnDayOfYear) {
            prefs.shownOnDayOfYear = dayOfYear
            showMessage(R.string.hey, R.string.new_year_wish_1, R.string.cheers) {}
            return
        }

        when (prefs.userState) {
            Constants.UserState.START -> {
                if (prefs.firstOpenTime.hasBeenMinutes(10))
                    prefs.userState = Constants.UserState.WALLPAPER
            }

            Constants.UserState.WALLPAPER -> {
                if (prefs.wallpaperMsgShown || prefs.dailyWallpaper)
                    prefs.userState = Constants.UserState.REVIEW
                else if (isOlauncherDefault(this))
                    viewModel.showDialog.postValue(Constants.Dialog.WALLPAPER)
            }

            Constants.UserState.REVIEW -> {
                if (prefs.rateClicked)
                    prefs.userState = Constants.UserState.SHARE
                else if (isOlauncherDefault(this) && prefs.firstOpenTime.hasBeenHours(1))
                    viewModel.showDialog.postValue(Constants.Dialog.REVIEW)
            }

            Constants.UserState.RATE -> {
                if (prefs.rateClicked)
                    prefs.userState = Constants.UserState.SHARE
                else if (isOlauncherDefault(this)
                    && prefs.firstOpenTime.isDaySince() >= 7
                    && calendar.get(Calendar.HOUR_OF_DAY) >= 16
                ) viewModel.showDialog.postValue(Constants.Dialog.RATE)
            }

            Constants.UserState.SHARE -> {
                if (isOlauncherDefault(this) && prefs.firstOpenTime.hasBeenDays(14)
                    && prefs.shareShownTime.isDaySince() >= 70
                    && calendar.get(Calendar.HOUR_OF_DAY) >= 16
                ) viewModel.showDialog.postValue(Constants.Dialog.SHARE)
            }
        }
    }

    @SuppressLint("SourceLockedOrientationActivity")
    private fun setupOrientation() {
        if (isTablet(this) || Build.VERSION.SDK_INT == Build.VERSION_CODES.O)
            return
        // In Android 8.0, windowIsTranslucent cannot be used with screenOrientation=portrait
        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }

    fun checkForAppUpdate(forced: Boolean = false) {
        lifecycleScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            if (!forced && now - prefs.updateLastCheck < Updater.CHECK_INTERVAL_MS) return@launch
            prefs.updateLastCheck = now
            val update = Updater.checkLatest() ?: return@launch
            if (!Updater.isNewer(BuildConfig.VERSION_NAME, update.tag)) {
                if (forced) withContext(Dispatchers.Main) { showToast(R.string.up_to_date) }
                return@launch
            }
            withContext(Dispatchers.Main) { showUpdateDialog(update) }
        }
    }

    private fun showUpdateDialog(update: AppUpdate) {
        createDialog(
            title = R.string.update_available,
            action = R.string.download,
            message = R.string.update_available_message,
            onAction = {
                lifecycleScope.launch(Dispatchers.IO) {
                    val apk = Updater.downloadApk(this@MainActivity, update)
                    if (apk != null) withContext(Dispatchers.Main) { Updater.installApk(this@MainActivity, apk) }
                    else withContext(Dispatchers.Main) { showToast(R.string.update_download_failed) }
                }
            },
        ).showRespectingStatusBar()
    }

    private fun backToHomeScreen() {
        if (viewModel.isPrivateSpaceToggling) return
        messageDialog?.dismiss()
        if (navController.currentDestination?.id != R.id.mainFragment)
            navController.popBackStack(R.id.mainFragment, false)
    }

    private fun setPlainWallpaper() {
        if (this.isDarkThemeOn())
            setPlainWallpaper(this, android.R.color.black)
        else setPlainWallpaper(this, android.R.color.white)
    }

    private fun openLauncherChooser(resetFailed: Boolean) {
        if (resetFailed) {
            val intent = Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)
            startActivity(intent)
        }
    }

    private fun restartLauncherOrCheckTheme(forceRestart: Boolean = false) {
        if (forceRestart || prefs.launcherRestartTimestamp.hasBeenHours(4)) {
            prefs.launcherRestartTimestamp = System.currentTimeMillis()
            cacheDir.deleteRecursively()
            recreate()
        } else
            checkTheme()
    }

    private fun checkTheme() {
        timerJob?.cancel()
        timerJob = lifecycleScope.launch {
            delay(200)
            if ((prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.white))
                || (prefs.appTheme == AppCompatDelegate.MODE_NIGHT_NO && getColorFromAttr(R.attr.primaryColor) != getColor(R.color.black))
            )
                restartLauncherOrCheckTheme(true)
        }
    }

    override fun onDestroy() {
        messageDialog?.dismiss()
        messageDialog = null
        launcherAppsCallback?.let {
            getSystemService(LauncherApps::class.java).unregisterCallback(it)
        }
        profileReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (_: Exception) {
            }
        }
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            Constants.REQUEST_CODE_ENABLE_ADMIN -> {
                if (resultCode == Activity.RESULT_OK)
                    prefs.lockModeOn = true
            }

            Constants.REQUEST_CODE_LAUNCHER_SELECTOR -> {
                if (resultCode == Activity.RESULT_OK)
                    resetLauncherViaFakeActivity()
            }
        }
    }
}
