package org.fossify.camera.activities

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import org.fossify.camera.BuildConfig
import org.fossify.camera.R
import org.fossify.camera.databinding.ActivitySettingsBinding
import org.fossify.camera.extensions.checkLocationPermission
import org.fossify.camera.extensions.config
import org.fossify.camera.models.CaptureMode
import org.fossify.commons.dialogs.*
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*
import org.fossify.commons.models.FAQItem
import org.fossify.commons.models.RadioItem
import java.util.*
import kotlin.system.exitProcess

class SettingsActivity : SimpleActivity() {
    companion object {
        private const val TAG = "SettingsActivity"
    }

    private val binding by viewBinding(ActivitySettingsBinding::inflate)

    /**
     * 目录选择 launcher：用 Activity Result API，进程被系统杀掉重建后
     * 仍能恢复选择结果（startActivityForResult 在进程死亡后结果会丢失，
     * ColorOS 打开系统选择器时会杀掉后台的 SettingsActivity）。
     */
    private val pickFolderLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        Log.d(TAG, "pick result: code=${result.resultCode} uri=${result.data?.data}")
        if (result.resultCode == RESULT_OK) {
            result.data?.data?.let { uri -> applySaveFolder(uri) }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.apply {
            setContentView(root)
            setupOptionsMenu()
            refreshMenuItems()

            setupEdgeToEdge(padBottomSystem = listOf(settingsNestedScrollview))
            setupMaterialScrollListener(binding.settingsNestedScrollview, binding.settingsAppbar)
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.settingsAppbar, NavigationIcon.Arrow)

        setupCustomizeColors()
        setupUseEnglish()
        setupLanguage()
        setupSound()
        setupVolumeButtonsAsShutter()
        setupMaxBrightness()
        setupFlipPhotos()
        setupSavePhotoMetadata()
        setupSavePhotoVideoLocation()
        setupSavePhotosFolder()
        setupPhotoQuality()
        setupCaptureMode()
        updateTextColors(binding.settingsHolder)

        val properPrimaryColor = getProperPrimaryColor()
        binding.apply {
            arrayListOf(
                settingsColorCustomizationLabel,
                settingsGeneralSettingsLabel,
                settingsCameraLabel,
                settingsSavingLabel,
            ).forEach {
                it.setTextColor(properPrimaryColor)
            }
        }
    }

    private fun refreshMenuItems() {
        binding.settingsToolbar.menu.apply {
            findItem(R.id.more_apps_from_us).isVisible =
                !resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)
        }
    }

    private fun setupOptionsMenu() {
        binding.settingsToolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.more_apps_from_us -> launchMoreAppsFromUsIntent()
                R.id.about -> launchAbout()
                else -> return@setOnMenuItemClickListener false
            }
            return@setOnMenuItemClickListener true
        }
    }

    private fun setupCustomizeColors() {
        binding.settingsColorCustomizationHolder.setOnClickListener {
            startCustomizationActivity()
        }
    }

    private fun setupUseEnglish() = binding.apply {
        settingsUseEnglishHolder.beVisibleIf((config.wasUseEnglishToggled || Locale.getDefault().language != "en") && !isTiramisuPlus())
        settingsUseEnglish.isChecked = config.useEnglish
        settingsUseEnglishHolder.setOnClickListener {
            settingsUseEnglish.toggle()
            config.useEnglish = settingsUseEnglish.isChecked
            exitProcess(0)
        }
    }

    private fun setupLanguage() = binding.apply {
        settingsLanguage.text = Locale.getDefault().displayLanguage
        settingsLanguageHolder.beVisibleIf(isTiramisuPlus())

        listOf(settingsGeneralSettingsHolder, settingsGeneralSettingsLabel).forEach {
            it.beGoneIf(settingsUseEnglishHolder.isGone() && settingsPurchaseThankYouHolder.isGone() && settingsLanguageHolder.isGone())
        }

        settingsLanguageHolder.setOnClickListener {
            launchChangeAppLanguageIntent()
        }
    }

    private fun launchAbout() {
        val licenses = LICENSE_GLIDE

        val faqItems = arrayListOf(
            FAQItem(R.string.faq_1_title, R.string.faq_1_text)
        )

        if (!resources.getBoolean(org.fossify.commons.R.bool.hide_google_relations)) {
            faqItems.add(
                FAQItem(
                    org.fossify.commons.R.string.faq_2_title_commons,
                    org.fossify.commons.R.string.faq_2_text_commons
                )
            )
            faqItems.add(
                FAQItem(
                    org.fossify.commons.R.string.faq_6_title_commons,
                    org.fossify.commons.R.string.faq_6_text_commons
                )
            )
        }

        startAboutActivity(
            appNameId = R.string.app_name,
            licenseMask = licenses,
            versionName = BuildConfig.VERSION_NAME,
            faqItems = faqItems,
            showFAQBeforeMail = true
        )
    }

    private fun getLastPart(path: String): String {
        val humanized = humanizePath(path)
        return humanized.substringAfterLast("/", humanized)
    }

    private fun setupSound() = binding.apply {
        settingsSound.isChecked = config.isSoundEnabled
        settingsSoundHolder.setOnClickListener {
            settingsSound.toggle()
            config.isSoundEnabled = settingsSound.isChecked
        }
    }

    private fun setupVolumeButtonsAsShutter() = binding.apply {
        settingsVolumeButtonsAsShutter.isChecked = config.volumeButtonsAsShutter
        settingsVolumeButtonsAsShutterHolder.setOnClickListener {
            settingsVolumeButtonsAsShutter.toggle()
            config.volumeButtonsAsShutter = settingsVolumeButtonsAsShutter.isChecked
        }
    }

    private fun setupMaxBrightness() = binding.apply {
        settingsMaxBrightness.isChecked = config.maxBrightness
        settingsMaxBrightnessHolder.setOnClickListener {
            settingsMaxBrightness.toggle()
            config.maxBrightness = settingsMaxBrightness.isChecked
        }
    }

    private fun setupFlipPhotos() = binding.apply {
        settingsFlipPhotos.isChecked = config.flipPhotos
        settingsFlipPhotosHolder.setOnClickListener {
            settingsFlipPhotos.toggle()
            config.flipPhotos = settingsFlipPhotos.isChecked
        }
    }

    private fun setupSavePhotoMetadata() = binding.apply {
        settingsSavePhotoMetadata.isChecked = config.savePhotoMetadata
        settingsSavePhotoMetadataHolder.setOnClickListener {
            settingsSavePhotoMetadata.toggle()
            config.savePhotoMetadata = settingsSavePhotoMetadata.isChecked
        }
    }

    private fun setupSavePhotoVideoLocation() = binding.apply {
        settingsSavePhotoVideoLocation.isChecked = config.savePhotoVideoLocation
        settingsSavePhotoVideoLocationHolder.setOnClickListener {
            val willEnableSavePhotoVideoLocation = !config.savePhotoVideoLocation

            if (willEnableSavePhotoVideoLocation) {
                if (checkLocationPermission()) {
                    updateSavePhotoVideoLocationConfig(true)
                } else {
                    handlePermission(PERMISSION_ACCESS_FINE_LOCATION) { _ ->
                        if (checkLocationPermission()) {
                            updateSavePhotoVideoLocationConfig(true)
                        } else {
                            OpenDeviceSettingsDialog(
                                activity = this@SettingsActivity,
                                message = getString(org.fossify.commons.R.string.allow_location_permission)
                            )
                        }
                    }
                }
            } else {
                updateSavePhotoVideoLocationConfig(false)
            }
        }
    }

    private fun updateSavePhotoVideoLocationConfig(enabled: Boolean) {
        binding.settingsSavePhotoVideoLocation.isChecked = enabled
        config.savePhotoVideoLocation = enabled
    }

    private fun setupSavePhotosFolder() = binding.apply {
        settingsSavePhotosLabel.text = addLockedLabelIfNeeded(R.string.save_photos)
        settingsSavePhotos.text = getLastPart(config.savePhotosFolder)
        settingsSavePhotosHolder.setOnClickListener {
            if (isOrWasThankYouInstalled()) {
                pickSaveFolder()
            } else {
                FeatureLockedDialog(this@SettingsActivity) { }
            }
        }
    }

    /**
     * 单次系统文件夹选择器：直接用 ACTION_OPEN_DOCUMENT_TREE，
     * 一步「选目录 + 授权」，不再叠加应用内 FilePickerDialog。
     */
    private fun pickSaveFolder() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }
        pickFolderLauncher.launch(intent)
    }

    private fun applySaveFolder(uri: Uri) {
        val path = treeUriToPath(uri)
        Log.d(TAG, "applySaveFolder: uri=$uri path=$path")
        if (path == null) {
            Log.w(TAG, "无法解析所选目录 uri=$uri")
            return
        }
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: Exception) {
            // 选择器未授予持久权限时忽略
        }
        // 特殊目录（Android/data、obb、SD、OTG）持久化 SAF 授权；
        // 普通公共目录（DCIM/Pictures）此调用为 no-op，靠 File 直接可写。
        storeAndroidTreeUri(path, uri.toString())
        config.savePhotosFolder = path
        binding.settingsSavePhotos.text = getLastPart(path)
    }

    /**
     * content://.../tree/primary:DCIM%2FMyCamera
     * -> /storage/emulated/0/DCIM/MyCamera；无法解析返回 null。
     */
    private fun treeUriToPath(uri: Uri): String? {
        val docId = try {
            DocumentsContract.getDocumentId(uri)
        } catch (e: Exception) {
            // 部分 ROM（ColorOS）对 tree uri 调 getDocumentId 会抛异常，
            // 退回 lastPathSegment 解析（如 "primary:DCIM"）。
            Log.w(TAG, "getDocumentId 失败，走 lastPathSegment", e)
            uri.lastPathSegment
        } ?: return null
        Log.d(TAG, "treeUriToPath docId=$docId")
        if (!docId.startsWith("primary:")) {
            Log.w(TAG, "非 primary docId=$docId")
            return null
        }
        val rel = docId.removePrefix("primary:").trim('/')
        val root = Environment.getExternalStorageDirectory().absolutePath
        return root + "/" + rel
    }

    private fun setupPhotoQuality() {
        updatePhotoQuality(config.photoQuality)
        binding.settingsPhotoQualityHolder.setOnClickListener {
            val items = arrayListOf(
                RadioItem(100, "100%"),
                RadioItem(95, "95%"),
                RadioItem(90, "90%"),
                RadioItem(85, "85%"),
                RadioItem(80, "80%"),
                RadioItem(75, "75%"),
                RadioItem(70, "70%"),
                RadioItem(65, "65%"),
                RadioItem(60, "60%"),
                RadioItem(55, "55%"),
                RadioItem(50, "50%")
            )

            RadioGroupDialog(this@SettingsActivity, items, config.photoQuality) {
                config.photoQuality = it as Int
                updatePhotoQuality(it)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun updatePhotoQuality(quality: Int) {
        binding.settingsPhotoQuality.text = "$quality%"
    }

    private fun setupCaptureMode() {
        updateCaptureMode(config.captureMode)
        binding.settingsCaptureModeHolder.setOnClickListener {
            val items = CaptureMode.values().mapIndexed { index, captureMode ->
                RadioItem(index, getString(captureMode.stringResId), captureMode)
            }

            RadioGroupDialog(this@SettingsActivity, ArrayList(items), config.captureMode.ordinal) {
                config.captureMode = it as CaptureMode
                updateCaptureMode(it)
            }
        }
    }

    private fun updateCaptureMode(captureMode: CaptureMode) {
        binding.settingsCaptureMode.text = getString(captureMode.stringResId)
    }
}
