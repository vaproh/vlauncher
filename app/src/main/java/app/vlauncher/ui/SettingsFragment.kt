package app.vlauncher.ui

import android.app.admin.DevicePolicyManager
import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import app.vlauncher.BuildConfig
import app.vlauncher.MainViewModel
import app.vlauncher.R
import app.vlauncher.data.Constants
import app.vlauncher.data.Prefs
import app.vlauncher.databinding.DialogTextSizeBinding
import app.vlauncher.databinding.DialogWeatherCityBinding
import app.vlauncher.databinding.FragmentSettingsBinding
import app.vlauncher.helper.appUsagePermissionGranted
import app.vlauncher.helper.createDialog
import app.vlauncher.helper.getColorFromAttr
import app.vlauncher.helper.hideStatusBar
import app.vlauncher.helper.isAccessServiceEnabled
import app.vlauncher.helper.isDarkThemeOn
import app.vlauncher.helper.isEinkDisplay
import app.vlauncher.helper.isOlauncherDefault
import app.vlauncher.helper.isTablet
import app.vlauncher.helper.openAppInfo
import app.vlauncher.helper.openUrl
import app.vlauncher.helper.setPlainWallpaper
import app.vlauncher.helper.shareApp
import app.vlauncher.helper.OlDialog
import app.vlauncher.helper.Fonts
import app.vlauncher.helper.Icons
import app.vlauncher.helper.showPopupMenu
import app.vlauncher.helper.showStatusBar
import app.vlauncher.helper.showToast
import app.vlauncher.helper.Weather
import app.vlauncher.helper.WeatherBreezy
import app.vlauncher.listener.DeviceAdmin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsFragment : BaseFragment(), View.OnClickListener, View.OnLongClickListener {

    private lateinit var prefs: Prefs
    private lateinit var viewModel: MainViewModel
    private lateinit var deviceManager: DevicePolicyManager
    private lateinit var componentName: ComponentName

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private var dialog: OlDialog? = null

    companion object {
        private const val REQUEST_CODE_WEATHER_LOCATION = 1001
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefs = Prefs(requireContext())
        viewModel = activity?.run {
            ViewModelProvider(this)[MainViewModel::class.java]
        } ?: throw Exception("Invalid Activity")
        viewModel.isOlauncherDefault()

        deviceManager = requireContext().getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        componentName = ComponentName(requireContext(), DeviceAdmin::class.java)
        checkAdminPermission()

        binding.homeAppsNum.text = prefs.homeAppsNum.toString()
        populateKeyboardText()
        populateScreenTimeOnOff()
        populateLockSettings()
        // Home button for recents feature disabled
        // populateHomeButtonRecents()
        populateWallpaperText()
        populateAppThemeText()
        populateTextSize()
        populateBoldFont()
        populateFont()
        populateAppIcons()
        populateIconPack()
        populateAlignment()
        populateStatusBar()
        populateDateTime()
        populateWeather()
        populateSwipeApps()
        populateActionHints()
        initClickListeners()
        initObservers()
    }

    override fun onClick(view: View) {
        when (view.id) {
            R.id.olauncherHiddenApps -> showHiddenApps()
            R.id.screenTimeOnOff -> viewModel.showDialog.postValue(Constants.Dialog.DIGITAL_WELLBEING)
            R.id.appInfo -> openAppInfo(requireContext(), Process.myUserHandle(), BuildConfig.APPLICATION_ID)
            R.id.setLauncher -> viewModel.resetLauncherLiveData.call()
            R.id.toggleLock -> toggleLockMode()
            // Home button for recents feature disabled
            // R.id.homeButtonRecents -> toggleHomeButtonRecents()
            R.id.autoShowKeyboard -> toggleKeyboardText()
            R.id.homeAppsNum -> showHomeAppsNumMenu(view)
            R.id.dailyWallpaperUrl -> requireContext().openUrl(prefs.dailyWallpaperUrl)
            R.id.dailyWallpaper -> toggleDailyWallpaperUpdate()
            R.id.wallpaperFrequency -> showWallpaperFrequencyMenu(view)
            R.id.alignment -> showAlignmentMenu(view)
            R.id.statusBar -> toggleStatusBar()
            R.id.dateTime -> showDateTimeMenu(view)
            R.id.weatherOnOff -> toggleWeather()
            R.id.weatherLocation -> showWeatherLocationMenu(view)
            R.id.weatherUnit -> showWeatherUnitMenu(view)
            R.id.weatherRefresh -> showWeatherRefreshMenu(view)
            R.id.appThemeText -> showAppThemeMenu(view, showSystem = false)
            R.id.textSizeValue -> showTextSizeDialog()
            R.id.boldFont -> toggleBoldFont()
            R.id.fontFamily -> showFontMenu(view)
            R.id.iconPack -> showIconPackMenu(view)
            R.id.appIconsHome -> toggleHomeAppIcons()
            R.id.appIconsDrawer -> toggleDrawerAppIcons()

            R.id.swipeLeftApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_LEFT_APP)
            R.id.swipeRightApp -> showAppListIfEnabled(Constants.FLAG_SET_SWIPE_RIGHT_APP)

            R.id.aboutOlauncher -> {
                prefs.aboutClicked = true
                requireContext().openUrl(Constants.URL_ABOUT_OLAUNCHER)
            }

            R.id.share -> requireActivity().shareApp()
            R.id.github -> requireContext().openUrl(Constants.URL_OLAUNCHER_GITHUB)
            R.id.privacy -> requireContext().openUrl(Constants.URL_OLAUNCHER_PRIVACY)
        }
    }

    override fun onLongClick(view: View): Boolean {
        when (view.id) {
            R.id.alignment -> {
                prefs.appLabelAlignment = prefs.homeAlignment
                findNavController().navigate(R.id.action_settingsFragment_to_appListFragment)
                requireContext().showToast(getString(R.string.alignment_changed))
            }

            R.id.dailyWallpaper -> removeWallpaper()
            R.id.appThemeText -> showAppThemeMenu(view, showSystem = true)
            R.id.swipeLeftApp -> toggleSwipeLeft()
            R.id.swipeRightApp -> toggleSwipeRight()
            R.id.toggleLock -> startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        return true
    }

    private fun initClickListeners() {
        binding.olauncherHiddenApps.setOnClickListener(this)
        binding.appInfo.setOnClickListener(this)
        binding.setLauncher.setOnClickListener(this)
        binding.aboutOlauncher.setOnClickListener(this)
        binding.autoShowKeyboard.setOnClickListener(this)
        binding.toggleLock.setOnClickListener(this)
        // Home button for recents feature disabled
        // binding.homeButtonRecents.setOnClickListener(this)
        binding.homeAppsNum.setOnClickListener(this)
        binding.screenTimeOnOff.setOnClickListener(this)
        binding.dailyWallpaperUrl.setOnClickListener(this)
        binding.dailyWallpaper.setOnClickListener(this)
        binding.wallpaperFrequency.setOnClickListener(this)
        binding.alignment.setOnClickListener(this)
        binding.statusBar.setOnClickListener(this)
        binding.dateTime.setOnClickListener(this)
        binding.weatherOnOff.setOnClickListener(this)
        binding.weatherLocation.setOnClickListener(this)
        binding.weatherUnit.setOnClickListener(this)
        binding.weatherRefresh.setOnClickListener(this)
        binding.swipeLeftApp.setOnClickListener(this)
        binding.swipeRightApp.setOnClickListener(this)
        binding.appThemeText.setOnClickListener(this)
        binding.textSizeValue.setOnClickListener(this)
        binding.boldFont.setOnClickListener(this)
        binding.fontFamily.setOnClickListener(this)
        binding.iconPack.setOnClickListener(this)
        binding.appIconsHome.setOnClickListener(this)
        binding.appIconsDrawer.setOnClickListener(this)

        binding.share.setOnClickListener(this)
        binding.github.setOnClickListener(this)
        binding.privacy.setOnClickListener(this)

        binding.dailyWallpaper.setOnLongClickListener(this)
        binding.alignment.setOnLongClickListener(this)
        binding.appThemeText.setOnLongClickListener(this)
        binding.swipeLeftApp.setOnLongClickListener(this)
        binding.swipeRightApp.setOnLongClickListener(this)
        binding.toggleLock.setOnLongClickListener(this)
    }

    private fun initObservers() {
        if (prefs.firstSettingsOpen) {
            viewModel.showDialog.postValue(Constants.Dialog.ABOUT)
            prefs.firstSettingsOpen = false
        }
        viewModel.isOlauncherDefault.observe(viewLifecycleOwner) {
            if (it) {
                binding.setLauncher.text = getString(R.string.change_default_launcher)
                prefs.toShowHintCounter += 1
            }
        }
        viewModel.homeAppAlignment.observe(viewLifecycleOwner) {
            populateAlignment()
        }
        viewModel.updateSwipeApps.observe(viewLifecycleOwner) {
            populateSwipeApps()
        }
    }

    // Popup menus

    private fun showHomeAppsNumMenu(anchor: View) {
        anchor.showPopupMenu(
            configure = { menu ->
                for (num in 0..8) menu.add(Menu.NONE, num, num, num.toString())
            }
        ) { item -> updateHomeAppsNum(item.itemId) }
    }

    private fun showDateTimeMenu(anchor: View) {
        anchor.showPopupMenu(R.menu.date_time) { item ->
            when (item.itemId) {
                R.id.dateTimeOn -> toggleDateTime(Constants.DateTime.ON)
                R.id.dateTimeOff -> toggleDateTime(Constants.DateTime.OFF)
                R.id.dateOnly -> toggleDateTime(Constants.DateTime.DATE_ONLY)
            }
        }
    }

    private fun showAlignmentMenu(anchor: View) {
        anchor.showPopupMenu(
            R.menu.alignment,
            configure = { menu ->
                menu.findItem(R.id.alignmentBottom).setTitle(
                    if (prefs.homeBottomAlignment) R.string.bottom_on else R.string.bottom_off
                )
            }
        ) { item ->
            when (item.itemId) {
                R.id.alignmentLeft -> updateAppAlignment(Gravity.START)
                R.id.alignmentCenter -> updateAppAlignment(Gravity.CENTER)
                R.id.alignmentRight -> updateAppAlignment(Gravity.END)
                R.id.alignmentBottom -> updateHomeBottomAlignment()
            }
        }
    }

    private fun updateAppAlignment(gravity: Int) {
        viewModel.updateHomeAlignment(gravity)
        prefs.appLabelAlignment = gravity
    }

    // "System" stays hidden unless the row is long pressed
    private fun showAppThemeMenu(anchor: View, showSystem: Boolean) {
        anchor.showPopupMenu(
            R.menu.app_theme,
            configure = { menu -> menu.findItem(R.id.themeSystem).isVisible = showSystem }
        ) { item ->
            when (item.itemId) {
                R.id.themeLight -> updateTheme(AppCompatDelegate.MODE_NIGHT_NO)
                R.id.themeDark -> updateTheme(AppCompatDelegate.MODE_NIGHT_YES)
                R.id.themeSystem -> updateTheme(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
            }
        }
    }

    // Dialogs

    private fun showDialog(newDialog: OlDialog) {
        dialog?.dismiss()
        dialog = newDialog
        newDialog.showRespectingStatusBar()
    }

    private fun showTextSizeDialog() {
        var stepper: DialogTextSizeBinding? = null
        val dialog = requireContext().createDialog(R.string.text_size, R.string.okay) { container ->
            DialogTextSizeBinding.inflate(layoutInflater, container, false).also { stepper = it }.root
        }
        stepper?.apply {
            textSizeCurrent.text = formatScale(pendingOrCurrentTextSizeScale())
            textSizeMinus.setOnClickListener { adjustTextSizePreview(-0.1f, this) }
            textSizePlus.setOnClickListener { adjustTextSizePreview(0.1f, this) }
        }
        dialog.setOnDismissListener { applyTextSizeScale() }
        showDialog(dialog)
    }

    // Prominent disclosure before sending the user to accessibility settings
    private fun showAccessibilityDialog() {
        val serviceEnabled = isAccessServiceEnabled(requireContext())
        showDialog(
            requireContext().createDialog(
                title = R.string.gestures,
                action = if (serviceEnabled) R.string.disable else R.string.enable,
                message = R.string.accessibility_disclosure,
                neutral = R.string.not_working,
                onNeutral = { requireContext().openUrl(Constants.URL_DOUBLE_TAP) },
                onAction = { openAccessibilityService() },
            )
        )
    }

    private fun toggleSwipeLeft() {
        prefs.swipeLeftEnabled = !prefs.swipeLeftEnabled
        if (prefs.swipeLeftEnabled) {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_left_app_enabled))
        } else {
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_left_app_disabled))
        }
    }

    private fun toggleSwipeRight() {
        prefs.swipeRightEnabled = !prefs.swipeRightEnabled
        if (prefs.swipeRightEnabled) {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColor))
            requireContext().showToast(getString(R.string.swipe_right_app_enabled))
        } else {
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
            requireContext().showToast(getString(R.string.swipe_right_app_disabled))
        }
    }

    private fun toggleStatusBar() {
        prefs.showStatusBar = !prefs.showStatusBar
        populateStatusBar()
    }

    private fun populateStatusBar() {
        if (prefs.showStatusBar) {
            requireActivity().window.showStatusBar()
            binding.statusBar.text = getString(R.string.on)
        } else {
            requireActivity().window.hideStatusBar()
            binding.statusBar.text = getString(R.string.off)
        }
    }

    private fun toggleDateTime(selected: Int) {
        prefs.dateTimeVisibility = selected
        populateDateTime()
        viewModel.toggleDateTime()
    }

    private fun populateDateTime() {
        binding.dateTime.text = getString(
            when (prefs.dateTimeVisibility) {
                Constants.DateTime.DATE_ONLY -> R.string.date
                Constants.DateTime.ON -> R.string.on
                else -> R.string.off
            }
        )
    }

    private fun populateWeather() {
        binding.weatherOnOff.text = getString(if (prefs.weatherEnabled) R.string.on else R.string.off)
        binding.weatherLocationRow.isVisible = prefs.weatherEnabled
        binding.weatherUnitRow.isVisible = prefs.weatherEnabled
        binding.weatherRefreshRow.isVisible = prefs.weatherEnabled
        binding.weatherLocation.text = when {
            WeatherBreezy.isActive(requireContext()) -> getString(R.string.weather_source_breezy)
            prefs.weatherLocationMode == Constants.Weather.MODE_CUSTOM ->
                prefs.weatherCityName.ifBlank { getString(R.string.weather_location_custom) }
            else -> getString(R.string.weather_location_device)
        }
        binding.weatherUnit.text =
            getString(if (prefs.weatherUnit == Constants.Weather.UNIT_FAHRENHEIT) R.string.weather_unit_f else R.string.weather_unit_c)
        binding.weatherRefresh.text = getString(
            when (prefs.weatherRefreshMinutes) {
                30 -> R.string.weather_refresh_30m
                180 -> R.string.weather_refresh_3h
                360 -> R.string.weather_refresh_6h
                720 -> R.string.weather_refresh_12h
                else -> R.string.weather_refresh_1h
            }
        )
    }

    private fun toggleWeather() {
        prefs.weatherEnabled = !prefs.weatherEnabled
        if (prefs.weatherEnabled) requestWeatherPermissions()
        populateWeather()
        viewModel.refreshHome(false)
    }

    private fun showWeatherLocationMenu(anchor: View) {
        anchor.showPopupMenu(configure = { menu ->
            menu.add(Menu.NONE, 0, 0, getString(R.string.weather_location_device))
            // Breezy owns the location while it is the active source.
            if (!WeatherBreezy.isActive(requireContext()))
                menu.add(Menu.NONE, 1, 1, getString(R.string.weather_location_custom))
        }) { item ->
            when (item.itemId) {
                0 -> {
                    prefs.weatherLocationMode = Constants.Weather.MODE_DEVICE
                    prefs.weatherUpdatedAt = 0L
                    populateWeather()
                    requestWeatherPermissions()
                    viewModel.refreshHome(false)
                }

                1 -> showWeatherCityDialog()
            }
        }
    }

    private fun showWeatherUnitMenu(anchor: View) {
        anchor.showPopupMenu(configure = { menu ->
            menu.add(Menu.NONE, 0, 0, getString(R.string.weather_unit_c))
            menu.add(Menu.NONE, 1, 1, getString(R.string.weather_unit_f))
        }) { item ->
            prefs.weatherUnit =
                if (item.itemId == 1) Constants.Weather.UNIT_FAHRENHEIT else Constants.Weather.UNIT_CELSIUS
            populateWeather()
            viewModel.refreshHome(false)
        }
    }

    private fun showWeatherRefreshMenu(anchor: View) {
        val minutes = listOf(30, 60, 180, 360, 720)
        val labels = listOf(
            R.string.weather_refresh_30m,
            R.string.weather_refresh_1h,
            R.string.weather_refresh_3h,
            R.string.weather_refresh_6h,
            R.string.weather_refresh_12h,
        )
        anchor.showPopupMenu(configure = { menu ->
            minutes.forEachIndexed { i, m -> menu.add(Menu.NONE, i, i, getString(labels[i])) }
        }) { item ->
            prefs.weatherRefreshMinutes = minutes[item.itemId]
            populateWeather()
            viewModel.refreshHome(false)
        }
    }

    private fun showWeatherCityDialog() {
        var input: DialogWeatherCityBinding? = null
        showDialog(
            requireContext().createDialog(
                title = R.string.weather_city_title,
                action = R.string.weather_search,
                content = { container ->
                    DialogWeatherCityBinding.inflate(layoutInflater, container, false)
                        .also { input = it }.root
                },
                onAction = {
                    val name = input?.cityInput?.text?.toString()?.trim().orEmpty()
                    if (name.isEmpty()) requireContext().showToast(R.string.weather_city_not_found)
                    else searchCity(name)
                },
            )
        )
    }

    private fun searchCity(name: String) {
        viewLifecycleOwner.lifecycleScope.launch {
            val city = withContext(Dispatchers.IO) { Weather.searchCity(name) }
            if (city == null) {
                requireContext().showToast(R.string.weather_city_not_found)
                return@launch
            }
            prefs.weatherLocationMode = Constants.Weather.MODE_CUSTOM
            prefs.weatherCityName = city.name
            prefs.weatherLatitude = city.latitude.toFloat()
            prefs.weatherLongitude = city.longitude.toFloat()
            prefs.weatherUpdatedAt = 0L
            populateWeather()
            viewModel.refreshHome(false)
            requireContext().showToast(getString(R.string.weather_location_set, city.name))
        }
    }

    private fun requestWeatherPermissions() {
        val wanted = mutableListOf<String>()
        if (prefs.weatherLocationMode == Constants.Weather.MODE_DEVICE)
            wanted.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (WeatherBreezy.installedPackage(requireContext()) != null)
            wanted.add(WeatherBreezy.READ_PERMISSION)
        val missing = wanted.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty())
            requestPermissions(missing.toTypedArray(), REQUEST_CODE_WEATHER_LOCATION)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQUEST_CODE_WEATHER_LOCATION) return
        val locationIndex = permissions.indexOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        if (locationIndex >= 0 && grantResults.getOrNull(locationIndex) != PackageManager.PERMISSION_GRANTED)
            requireContext().showToast(R.string.weather_location_permission_needed)
        viewModel.refreshHome(false)
    }

    private fun showHiddenApps() {
        if (prefs.hiddenApps.isEmpty()) {
            requireContext().showToast(getString(R.string.no_hidden_apps))
            return
        }
        viewModel.getHiddenApps()
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to Constants.FLAG_HIDDEN_APPS)
        )
    }

    private fun checkAdminPermission() {
        val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P)
            prefs.lockModeOn = isAdmin
    }

    private fun openAccessibilityService() {
        // prefs.lockModeOn = true
        populateLockSettings()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    private fun toggleLockMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (!prefs.lockModeOn && !isAccessServiceEnabled(requireContext())) {
                showAccessibilityDialog()
                return
            }
            prefs.lockModeOn = !prefs.lockModeOn
        } else {
            val isAdmin: Boolean = deviceManager.isAdminActive(componentName)
            if (isAdmin) {
                removeActiveAdmin("Admin permission removed.")
                prefs.lockModeOn = false
            } else {
                val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                intent.putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, componentName)
                intent.putExtra(
                    DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                    getString(R.string.admin_permission_message)
                )
                requireActivity().startActivityForResult(intent, Constants.REQUEST_CODE_ENABLE_ADMIN)
            }
        }
        populateLockSettings()
    }

    private fun removeActiveAdmin(toastMessage: String? = null) {
        try {
            deviceManager.removeActiveAdmin(componentName) // for backward compatibility
            requireContext().showToast(toastMessage)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun removeWallpaper() {
        if (requireContext().isEinkDisplay()) {
            prefs.appTheme = AppCompatDelegate.MODE_NIGHT_NO
            setPlainWallpaper(requireContext(), android.R.color.white)
        } else {
            prefs.appTheme = AppCompatDelegate.MODE_NIGHT_YES
            setPlainWallpaper(requireContext(), android.R.color.black)
        }
        if (!prefs.dailyWallpaper) return
        prefs.dailyWallpaper = false
        populateWallpaperText()
        viewModel.cancelWallpaperWorker()
    }

    private fun toggleDailyWallpaperUpdate() {
        if (prefs.dailyWallpaper.not() && prefs.appTheme == AppCompatDelegate.MODE_NIGHT_YES && viewModel.isOlauncherDefault.value == false) {
            requireContext().showToast(R.string.set_as_default_launcher_first)
            return
        }
        prefs.dailyWallpaper = !prefs.dailyWallpaper
        populateWallpaperText()
        if (prefs.dailyWallpaper) {
            viewModel.setWallpaperWorker()
            showWallpaperToasts()
        } else viewModel.cancelWallpaperWorker()
    }

    private fun showWallpaperToasts() {
        if (isOlauncherDefault(requireContext()))
            requireContext().showToast(getString(R.string.your_wallpaper_will_update_shortly))
        else
            requireContext().showToast(getString(R.string.olauncher_is_not_default_launcher), Toast.LENGTH_LONG)
    }

    private fun updateHomeAppsNum(num: Int) {
        binding.homeAppsNum.text = num.toString()
        prefs.homeAppsNum = num
        viewModel.refreshHome(true)
    }

    private var pendingTextSizeScale: Float = -1f

    private fun pendingOrCurrentTextSizeScale(): Float =
        if (pendingTextSizeScale > 0) pendingTextSizeScale else prefs.textSizeScale

    private fun formatScale(scale: Float): String = String.format("%.1f", scale)

    private fun adjustTextSizePreview(delta: Float, dialogBinding: DialogTextSizeBinding) {
        val maxScale = if (isTablet(requireContext())) 2.0f else 1.5f
        val current = pendingOrCurrentTextSizeScale()
        val newScale = Math.round((current + delta) * 10f) / 10f
        val clamped = newScale.coerceIn(0.5f, maxScale)
        if (clamped == current) return
        pendingTextSizeScale = clamped
        val formatted = formatScale(clamped)
        binding.textSizeValue.text = formatted
        dialogBinding.textSizeCurrent.text = formatted
    }

    private fun applyTextSizeScale() {
        if (pendingTextSizeScale < 0 || prefs.textSizeScale == pendingTextSizeScale) {
            pendingTextSizeScale = -1f
            return
        }
        prefs.textSizeScale = pendingTextSizeScale
        pendingTextSizeScale = -1f
        val activity = activity ?: return
        if (activity.isChangingConfigurations.not())
            activity.recreate()
    }

    private fun toggleKeyboardText() {
        if (prefs.autoShowKeyboard && prefs.keyboardMessageShown.not()) {
            viewModel.showDialog.postValue(Constants.Dialog.KEYBOARD)
            prefs.keyboardMessageShown = true
        } else {
            prefs.autoShowKeyboard = !prefs.autoShowKeyboard
            populateKeyboardText()
        }
    }

    private fun updateTheme(appTheme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == appTheme) return
        prefs.appTheme = appTheme
        populateAppThemeText(appTheme)
        setAppTheme(appTheme)
    }

    private fun setAppTheme(theme: Int) {
        if (AppCompatDelegate.getDefaultNightMode() == theme) return
        if (prefs.dailyWallpaper) {
            setPlainWallpaper(theme)
            viewModel.setWallpaperWorker()
        }
        requireActivity().recreate()
    }

    private fun setPlainWallpaper(appTheme: Int) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> setPlainWallpaper(requireContext(), android.R.color.black)
            AppCompatDelegate.MODE_NIGHT_NO -> setPlainWallpaper(requireContext(), android.R.color.white)
            else -> {
                if (requireContext().isDarkThemeOn())
                    setPlainWallpaper(requireContext(), android.R.color.black)
                else setPlainWallpaper(requireContext(), android.R.color.white)
            }
        }
    }

    private fun populateAppThemeText(appTheme: Int = prefs.appTheme) {
        when (appTheme) {
            AppCompatDelegate.MODE_NIGHT_YES -> binding.appThemeText.text = getString(R.string.dark)
            AppCompatDelegate.MODE_NIGHT_NO -> binding.appThemeText.text = getString(R.string.light)
            else -> binding.appThemeText.text = getString(R.string.system_default)
        }
    }

    private fun populateTextSize() {
        binding.textSizeValue.text = formatScale(prefs.textSizeScale)
    }

    private fun toggleBoldFont() {
        prefs.boldFont = !prefs.boldFont
        populateBoldFont()
        requireActivity().recreate()
    }

    private fun populateBoldFont() {
        binding.boldFont.text = getString(if (prefs.boldFont) R.string.on else R.string.off)
    }

    private fun showFontMenu(anchor: View) {
        anchor.showPopupMenu(configure = { menu ->
            Fonts.options.forEachIndexed { index, option ->
                menu.add(Menu.NONE, index, index, option.label)
            }
        }) { item ->
            val option = Fonts.options.getOrNull(item.itemId)
            if (option != null && prefs.fontFamily != option.id) {
                prefs.fontFamily = option.id
                requireActivity().recreate()
            }
        }
    }

    private fun populateFont() {
        binding.fontFamily.text = Fonts.labelFor(requireContext(), prefs.fontFamily)
    }

    private fun showIconPackMenu(anchor: View) {
        val packs = Icons.packs(requireContext())
        anchor.showPopupMenu(configure = { menu ->
            menu.add(Menu.NONE, 0, 0, getString(R.string.icon_pack_system))
            packs.forEachIndexed { index, pack ->
                menu.add(Menu.NONE, index + 1, index + 1, pack.label)
            }
        }) { item ->
            val selected = if (item.itemId == 0) "" else packs.getOrNull(item.itemId - 1)?.packageName ?: ""
            if (selected != prefs.iconPack) {
                prefs.iconPack = selected
                requireActivity().recreate()
            }
        }
    }

    private fun populateIconPack() {
        val pack = prefs.iconPack
        binding.iconPack.text = if (pack.isEmpty()) getString(R.string.icon_pack_system)
        else Icons.packs(requireContext()).firstOrNull { it.packageName == pack }?.label
            ?: getString(R.string.icon_pack_system)
    }

    private fun toggleHomeAppIcons() {
        prefs.showHomeAppIcons = !prefs.showHomeAppIcons
        populateAppIcons()
        requireActivity().recreate()
    }

    private fun toggleDrawerAppIcons() {
        prefs.showDrawerAppIcons = !prefs.showDrawerAppIcons
        populateAppIcons()
        requireActivity().recreate()
    }

    private fun populateAppIcons() {
        binding.appIconsHome.text = getString(if (prefs.showHomeAppIcons) R.string.on else R.string.off)
        binding.appIconsDrawer.text = getString(if (prefs.showDrawerAppIcons) R.string.on else R.string.off)
        binding.iconPackRow.visibility =
            if (prefs.showHomeAppIcons || prefs.showDrawerAppIcons) View.VISIBLE else View.GONE
    }

    private fun populateScreenTimeOnOff() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (requireContext().appUsagePermissionGranted()) binding.screenTimeOnOff.text = getString(R.string.on)
            else binding.screenTimeOnOff.text = getString(R.string.off)
        } else binding.screenTimeLayout.visibility = View.GONE
    }

    private fun populateKeyboardText() {
        if (prefs.autoShowKeyboard) binding.autoShowKeyboard.text = getString(R.string.on)
        else binding.autoShowKeyboard.text = getString(R.string.off)
    }

    private fun populateWallpaperText() {
        if (prefs.dailyWallpaper) binding.dailyWallpaper.text = getString(R.string.on)
        else binding.dailyWallpaper.text = getString(R.string.off)
        binding.wallpaperFrequencyRow.visibility = if (prefs.dailyWallpaper) View.VISIBLE else View.GONE
        binding.wallpaperFrequency.text = frequencyLabel(prefs.wallpaperFrequency)
    }

    private fun frequencyLabel(hours: Int): String = getString(
        when (hours) {
            1 -> R.string.wallpaper_every_hour
            3 -> R.string.wallpaper_every_3_hours
            6 -> R.string.wallpaper_every_6_hours
            12 -> R.string.wallpaper_every_12_hours
            168 -> R.string.wallpaper_weekly
            else -> R.string.wallpaper_daily
        }
    )

    private fun showWallpaperFrequencyMenu(anchor: View) {
        val options = listOf(1, 3, 6, 12, 24, 168)
        anchor.showPopupMenu(configure = { menu ->
            options.forEachIndexed { index, hours ->
                menu.add(Menu.NONE, index, index, frequencyLabel(hours))
            }
        }) { item ->
            val hours = options.getOrNull(item.itemId) ?: return@showPopupMenu
            if (hours != prefs.wallpaperFrequency) {
                prefs.wallpaperFrequency = hours
                populateWallpaperText()
                if (prefs.dailyWallpaper) viewModel.setWallpaperWorker()
            }
        }
    }

    private fun updateHomeBottomAlignment() {
        if (viewModel.isOlauncherDefault.value != true) {
            requireContext().showToast(getString(R.string.please_set_olauncher_as_default_first), Toast.LENGTH_LONG)
            return
        }
        prefs.homeBottomAlignment = !prefs.homeBottomAlignment
        populateAlignment()
        viewModel.updateHomeAlignment(prefs.homeAlignment)
    }

    private fun populateAlignment() {
        when (prefs.homeAlignment) {
            Gravity.START -> binding.alignment.text = getString(R.string.left)
            Gravity.CENTER -> binding.alignment.text = getString(R.string.center)
            Gravity.END -> binding.alignment.text = getString(R.string.right)
        }
    }

    // Home button for recents feature disabled
    // private fun toggleHomeButtonRecents() {
    //     if (!prefs.homeButtonShowRecents && !isAccessServiceEnabled(requireContext())) {
    //         showAccessibilityDialog()
    //         return
    //     }
    //     prefs.homeButtonShowRecents = !prefs.homeButtonShowRecents
    //     populateHomeButtonRecents()
    // }

    // private fun populateHomeButtonRecents() {
    //     binding.homeButtonRecents.text = getString(
    //         if (prefs.homeButtonShowRecents && isAccessServiceEnabled(requireContext())) R.string.on
    //         else R.string.off
    //     )
    // }

    private fun populateLockSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            binding.toggleLock.text = getString(
                if (prefs.lockModeOn && isAccessServiceEnabled(requireContext())) R.string.on
                else R.string.off
            )
        } else {
            binding.toggleLock.text = getString(
                if (prefs.lockModeOn) R.string.on
                else R.string.off
            )
        }
    }

    private fun populateSwipeApps() {
        binding.swipeLeftApp.text = prefs.appNameSwipeLeft
        binding.swipeRightApp.text = prefs.appNameSwipeRight
        if (!prefs.swipeLeftEnabled)
            binding.swipeLeftApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
        if (!prefs.swipeRightEnabled)
            binding.swipeRightApp.setTextColor(requireContext().getColorFromAttr(R.attr.primaryColorTrans50))
    }

//    private fun populateDigitalWellbeing() {
//        binding.digitalWellbeing.isVisible = requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_PACKAGE_NAME).not()
//                && requireContext().isPackageInstalled(Constants.DIGITAL_WELLBEING_SAMSUNG_PACKAGE_NAME).not()
//                && prefs.hideDigitalWellbeing.not()
//    }

    private fun showAppListIfEnabled(flag: Int) {
        if ((flag == Constants.FLAG_SET_SWIPE_LEFT_APP) and !prefs.swipeLeftEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        if ((flag == Constants.FLAG_SET_SWIPE_RIGHT_APP) and !prefs.swipeRightEnabled) {
            requireContext().showToast(getString(R.string.long_press_to_enable))
            return
        }
        viewModel.getAppList(true)
        findNavController().navigate(
            R.id.action_settingsFragment_to_appListFragment,
            bundleOf(Constants.Key.FLAG to flag)
        )
    }

    private fun populateActionHints() {
        if (prefs.aboutClicked.not())
            binding.aboutOlauncher.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.ic_info, 0)
    }

    override fun onDestroyView() {
        // Dismissing the text size dialog applies any pending scale via its dismiss listener
        dialog?.dismiss()
        dialog = null
        applyTextSizeScale()
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        viewModel.checkForMessages.call()
        super.onDestroy()
    }
}
