package org.fossify.camera.activities

import android.content.ContentUris
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.KeyEvent
import android.view.Window
import android.view.WindowManager
import android.widget.Toast
import androidx.camera.view.PreviewView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import org.fossify.camera.BuildConfig
import org.fossify.camera.R
import org.fossify.camera.drawcore.DrawView
import org.fossify.camera.extensions.config
import org.fossify.camera.helpers.MediaSoundHelper
import org.fossify.camera.helpers.PhotoProcessor
import org.fossify.camera.implementations.CameraXInitializer
import org.fossify.camera.implementations.CameraXPreviewListener
import org.fossify.camera.interfaces.MyPreview
import org.fossify.camera.models.ResolutionOption
import org.fossify.camera.models.TimerMode
import org.fossify.commons.extensions.*
import org.fossify.commons.helpers.*

/**
 * 阶段1：UI 剥离后的精简宿主。
 * 仅保留：权限请求、CameraX 预览创建、拍照/录像入口（物理快门/音量键）。
 * 所有 View/XML UI 已移除，CameraXPreviewListener 回调留空，
 * 待阶段3 由自绘 DrawingView Overlay 消费。
 */
class MainActivity : SimpleActivity(), PhotoProcessor.MediaSavedListener,
    CameraXPreviewListener, DrawView.Listener {

    private lateinit var mediaSoundHelper: MediaSoundHelper
    private var mPreview: MyPreview? = null
    private var mDrawView: DrawView? = null
    private var mIsHardwareShutterHandled = false

    /** 主线程 Handler：照片模式定时拍摄延时到期后在此触发快门。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    private val TAG = "CameraMainActivity"

    /** 最近一条媒体的 content uri，供 onLastMediaClick 打开。 */
    private var latestMediaUri: Uri? = null

    /** 分辨率面板 pending：CameraX showImageSizes 传入的选中回调与当前 index。 */
    private var pendingResolutionOnSelect: ((index: Int, changed: Boolean) -> Unit)? = null
    private var pendingSelectedIndex = -1
    /** 面板当前行；行号 → 全量列表下标映射见 ResolutionOption.fullListIndex。 */
    private var pendingResolutions: List<ResolutionOption> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        useDynamicTheme = false
        super.onCreate(savedInstanceState)
        appLaunched(BuildConfig.APPLICATION_ID)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        initVariables()
        tryInitCamera()
        supportActionBar?.hide()

        val windowInsetsController = ViewCompat.getWindowInsetsController(window.decorView)
        windowInsetsController?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        windowInsetsController?.hide(WindowInsetsCompat.Type.statusBars())

        if (isOreoMr1Plus()) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD or
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON or
                        WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
        }
    }

    override fun onResume() {
        super.onResume()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onPause() {
        super.onPause()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    override fun onDestroy() {
        super.onDestroy()
        mPreview = null
        mediaSoundHelper.release()
    }

    private fun initVariables() {
        mIsHardwareShutterHandled = false
        mediaSoundHelper = MediaSoundHelper(this)
        mediaSoundHelper.loadSounds()
    }

    // 物理快门/音量键触发拍照或录像（阶段1 临时入口）
    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val isShutterKey = keyCode == KeyEvent.KEYCODE_CAMERA
        val isVolumeShutter = keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP
        return if ((isShutterKey || isVolumeShutter) && !mIsHardwareShutterHandled) {
            mIsHardwareShutterHandled = true
            shutterPressed()
            true
        } else {
            super.onKeyDown(keyCode, event)
        }
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_CAMERA || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) {
            mIsHardwareShutterHandled = false
        }
        return super.onKeyUp(keyCode, event)
    }

    private fun shutterPressed() {
        if (isInPhotoMode()) {
            mPreview?.tryTakePicture()
        } else {
            mPreview?.toggleRecording()
        }
    }

    private fun tryInitCamera() {
        handlePermission(PERMISSION_CAMERA) { granted ->
            if (granted) {
                handleStoragePermission {
                    initializeCamera(isInPhotoMode())
                }
            } else {
                toast(org.fossify.commons.R.string.no_camera_permissions)
                finish()
            }
        }
    }

    private fun handleStoragePermission(callback: (granted: Boolean) -> Unit) {
        if (isTiramisuPlus()) {
            val mediaPermissionIds =
                mutableListOf(PERMISSION_READ_MEDIA_IMAGES, PERMISSION_READ_MEDIA_VIDEO)
            if (isUpsideDownCakePlus()) {
                mediaPermissionIds.add(PERMISSION_READ_MEDIA_VISUAL_USER_SELECTED)
            }
            handlePartialMediaPermissions(permissionIds = mediaPermissionIds, callback = callback)
        } else {
            handlePermission(PERMISSION_WRITE_STORAGE, callback)
        }
    }

    private fun isInPhotoMode(): Boolean {
        return mPreview?.isInPhotoMode() ?: if (isVideoCaptureIntent()) {
            false
        } else if (isImageCaptureIntent()) {
            true
        } else {
            config.initPhotoMode
        }
    }

    private fun isImageCaptureIntent(): Boolean =
        intent?.action == MediaStore.ACTION_IMAGE_CAPTURE || intent?.action == MediaStore.ACTION_IMAGE_CAPTURE_SECURE

    private fun isVideoCaptureIntent(): Boolean = intent?.action == MediaStore.ACTION_VIDEO_CAPTURE

    private fun initializeCamera(isInPhotoMode: Boolean) {
        setContentView(R.layout.activity_main)
        val drawView = DrawView(this).apply { listener = this@MainActivity }
        mDrawView = drawView
        findViewById<android.widget.FrameLayout>(R.id.overlay_container).apply {
            addView(drawView)
        }
        val previewView = findViewById<PreviewView>(R.id.preview_view)
        // 4:3 画幅：FIT_CENTER 让画面完整居中（上下黑边），
        // 再整体上移一个字高（13dp）。
        previewView.scaleType = PreviewView.ScaleType.FIT_CENTER
        val oneCharPx = (13 * resources.displayMetrics.density)
        previewView.translationY = -oneCharPx

        mPreview = CameraXInitializer(this).createCameraXPreview(
            previewView,
            listener = this,
            mediaSoundHelper = mediaSoundHelper,
            outputUri = intent.extras?.get(MediaStore.EXTRA_OUTPUT) as? Uri,
            isThirdPartyIntent = isVideoCaptureIntent() || isImageCaptureIntent(),
            initInPhotoMode = isInPhotoMode,
        )
        drawView.uiState.exposureRange = mPreview?.getExposureRange()
        // 面板初始选中态：从 config 同步当前 TimerMode.ordinal。
        drawView.uiState.timerMode = config.timerMode.ordinal
        drawView.invalidate()
        updateLatestMediaThumbnail()
    }

    // ===== CameraXPreviewListener：UI 相关回调留空，待阶段3 自绘层消费 =====
    override fun onInitPhotoMode() {
        mDrawView?.uiState?.isPhoto = true
        mDrawView?.invalidate()
    }

    override fun onInitVideoMode() {
        mDrawView?.uiState?.isPhoto = false
        mDrawView?.invalidate()
    }
    override fun setHasFrontAndBackCamera(hasFrontAndBack: Boolean) {
        mDrawView?.uiState?.hasFrontAndBack = hasFrontAndBack
        mDrawView?.invalidate()
    }
    override fun setFlashAvailable(available: Boolean) {}
    override fun shutterAnimation() {
        mDrawView?.triggerShutterPulse()
    }
    override fun onChangeFlashMode(flashMode: Int) {
        mDrawView?.uiState?.flashMode = flashMode
        mDrawView?.invalidate()
    }
    override fun onPhotoCaptureStart() {}
    override fun onPhotoCaptureEnd() {}
    override fun onVideoRecordingStarted() {
        mDrawView?.uiState?.isRecording = true
        mDrawView?.invalidate()
    }

    override fun onVideoRecordingStopped() {
        mDrawView?.uiState?.isRecording = false
        // 停止录像同时清零计时，隐藏计时文本。
        mDrawView?.uiState?.recordingDuration = 0
        mDrawView?.invalidate()
    }

    override fun onVideoDurationChanged(durationNanos: Long) {
        mDrawView?.uiState?.recordingDuration = durationNanos
        mDrawView?.invalidate()
    }
    override fun onFocusCamera(xPos: Float, yPos: Float) {}
    override fun onTouchPreview() {}
    override fun displaySelectedResolution(resolutionOption: ResolutionOption) {}
    override fun showImageSizes(
        selectedResolution: ResolutionOption,
        resolutions: List<ResolutionOption>,
        isPhotoCapture: Boolean,
        isFrontCamera: Boolean,
        onSelect: (index: Int, changed: Boolean) -> Unit,
    ) {
        // 复用 CameraX showChangeResolution 的取数逻辑；这里只把数据填入
        // 自绘分辨率面板并展开（原 XML 弹窗改为自绘层）。
        val labels = resolutions.map { it.label }
        val selectedIndex = resolutions
            .indexOfFirst { it.label == selectedResolution.label }
            .coerceAtLeast(0)
        pendingResolutionOnSelect = onSelect
        pendingSelectedIndex = selectedIndex
        pendingResolutions = resolutions
        mDrawView?.let { d ->
            d.setResolutionLabels(labels, selectedIndex)
            d.openResolutionPanel()
        }
    }

    override fun showFlashOptions(photoCapture: Boolean) {}
    override fun adjustPreviewView(requiresCentering: Boolean) {}

    // ===== DrawView.Listener：自绘层 Overlay 回调桥接 =====
    override fun onShutterClick() {
        // 延时仅作用于照片模式：定时 > 0 时延迟到期后才真正拍照；
        // 录像模式直接启停，不做延时。tryTakePicture 本身无延时逻辑，延时
        // 在此桥接层用 mainHandler.postDelayed 实现。
        if (isInPhotoMode() && config.timerMode != TimerMode.OFF) {
            mainHandler.postDelayed({
                mPreview?.tryTakePicture()
            }, config.timerMode.millisInFuture)
        } else {
            shutterPressed()
        }
    }

    override fun onModeChanged(isPhoto: Boolean) {
        // 模式切换收起分辨率面板，避免旧列表残留。
        mDrawView?.closeResolutionPanel()
        pendingResolutionOnSelect = null
        pendingResolutions = emptyList()
        if (isPhoto) {
            mPreview?.initPhotoMode()
        } else {
            mPreview?.initVideoMode()
        }
        config.initPhotoMode = isPhoto
    }

    override fun onExposureChanged(value: Int) {
        mPreview?.setExposure(value)
    }

    override fun onFlipCamera() {
        mPreview?.toggleFrontBackCamera()
    }

    override fun onFlashClick() {
        // OFF(0) → ON(1) → AUTO(2) → ALWAYS_ON(3) → OFF；
        // setFlashlightState 内部会回调 onChangeFlashMode 驱动图标刷新。
        val next = (config.flashlightState + 1) % 4
        mPreview?.setFlashlightState(next)
    }

    override fun onLastMediaClick() {
        openLatestMedia()
    }

    override fun onTimerSelected(mode: Int) {
        // DrawView 已把 uiState.timerMode 回写；这里持久化到 config，
        // mode 即 TimerMode.ordinal（0=关 1=3s 2=5s 3=10s）。
        config.timerMode = TimerMode.entries[mode]
    }

    override fun onSettingsClick() {
        // 设置入口：保留 XML 的 SettingsActivity，直接启动。
        startActivity(Intent(this, SettingsActivity::class.java))
    }

    override fun onResolutionClick() {
        // 复用 CameraX showChangeResolution 的取数 + 应用逻辑：
        // 内部取当前/支持分辨率列表后回调 showImageSizes 弹自绘面板。
        mPreview?.showChangeResolution()
    }

    override fun onResolutionSelected(index: Int) {
        // 面板行号 → 全量列表下标（fullListIndex）再回传，MediaSizeStore
        // 语义不变；changed 仍按行号比较（不同行即不同比例/质量档）。
        val onSelect = pendingResolutionOnSelect ?: return
        pendingResolutionOnSelect = null
        val changed = index != pendingSelectedIndex
        val fullIndex = pendingResolutions.getOrNull(index)?.fullListIndex
            ?.takeIf { it >= 0 } ?: index
        onSelect(fullIndex, changed)
    }

    // ===== 最近媒体缩略图数据流 =====

    /**
     * 后台查询 MediaStore 最近一条图片/视频，加载 156x156 缩略图，
     * 回主线程写入 uiState.lastMediaBitmap 并缓存其 content uri。
     * 无结果或失败时置 null（缩略图组件自行隐藏）。
     */
    private fun updateLatestMediaThumbnail() {
        Thread {
            var uri: Uri? = null
            var bitmap: Bitmap? = null
            try {
                // 双表直查：原 Files 聚合表在本 ROM 返回空，改为
                // 图片、视频各取最新一条，比较日期取较新者。
                val images = queryLatest(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                val videos = queryLatest(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
                val best = listOfNotNull(images, videos).maxByOrNull { it.first }
                if (best != null) {
                    uri = best.second
                    bitmap = contentResolver.loadThumbnail(
                        uri!!, Size(156, 156), null,
                    )
                }
                Log.d(TAG, "最近媒体: uri=${uri ?: "null"} loaded=${bitmap != null}")
            } catch (t: Throwable) {
                Log.w(TAG, "缩略图加载失败: ${t.message}", t)
                bitmap = null
            }
            runOnUiThread {
                latestMediaUri = uri
                mDrawView?.uiState?.lastMediaBitmap = bitmap
                mDrawView?.invalidate()
            }
        }.start()
    }

    /** 查询某媒体表最新一条：返回 (dateAdded, contentUri)；无数据返回 null。 */
    private fun queryLatest(baseUri: Uri): Pair<Long, Uri>? {
        var best: Pair<Long, Uri>? = null
        contentResolver.query(
            baseUri,
            arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATE_ADDED),
            null, null,
            // 注意：本 ROM 的 sortOrder 不支持 "LIMIT 1"（抛异常），
            // 倒序查询后只取第一条即可。
            "${MediaStore.MediaColumns.DATE_ADDED} DESC",
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID),
                )
                val date = cursor.getLong(
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED),
                )
                best = date to ContentUris.withAppendedId(baseUri, id)
            }
        }
        return best
    }

    /** 打开系统相册/图库查看最近媒体（与原版 Fossify 一致：content uri
     *  先转文件路径，再 openPathIntent——OPPO 相册只认文件路径）。
     *  空态（latestMediaUri == null）时轻量 Toast 提示，不打开图库。 */
    private fun openLatestMedia() {
        val uri = latestMediaUri
        if (uri == null) {
            Toast.makeText(this, R.string.no_media_yet,
                Toast.LENGTH_SHORT).show()
            return
        }
        val path = applicationContext.getRealPathFromURI(uri) ?: uri.toString()
        try {
            openPathIntent(path, false, BuildConfig.APPLICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "openPathIntent 失败: ${e.message}")
        }
    }

    // 第三方调用（如系统相机 Intent）：保存后返回结果
    override fun onMediaSaved(uri: Uri) {
        updateLatestMediaThumbnail()
        if (isImageCaptureIntent() || isVideoCaptureIntent()) {
            Intent().apply {
                data = uri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                setResult(RESULT_OK, this)
            }
            finish()
        }
    }

    override fun onImageCaptured(bitmap: Bitmap) {
        if (isImageCaptureIntent()) {
            Intent().apply {
                putExtra("data", bitmap)
                setResult(RESULT_OK, this)
            }
            finish()
        }
    }

    override fun mediaSaved(path: String) {
        updateLatestMediaThumbnail()
        if (isImageCaptureIntent()) {
            setResult(RESULT_OK)
            finish()
        }
    }
}
