package org.fossify.camera.drawcore

import android.content.Context
import android.graphics.Canvas
import android.view.MotionEvent
import android.view.View

/**
 * 自绘 Overlay 宿主 View：组合 ShutterButton / ModeSwitch / ExposureSlider
 * / FlipCameraButton / FlashButton / LastMediaThumbnail / RecordingTimer
 * / TimerPanel 八个自绘组件，对外暴露 [uiState] 与 [listener]。
 *
 * 绘制顺序（onDraw，避免遮挡）：翻转相机 → 闪光 → 计时器面板 → 录像计时文本
 * → 曝光滑块 → 模式切换 → 最近缩略图 → 快门（快门在最上层）。
 * 事件分发顺序（onTouchEvent）同绘制顺序；任一 checkTouchEvent 返回 true
 * 即消费并 VSync 对齐重绘；都不命中则不消费（返回 super），把事件留给相机层。
 *
 * MainActivity 直接改 [uiState] 字段后调 invalidate()；
 * 控件内交互通过 [Listener] 回调宿主。
 */
class DrawView(context: Context) : View(context) {

    /** 宿主回调：快门 / 模式切换 / 曝光 / 翻转 / 闪光 / 最近媒体 / 计时器 / 设置 / 分辨率。 */
    interface Listener {
        fun onShutterClick()
        fun onModeChanged(isPhoto: Boolean)
        fun onExposureChanged(value: Int)
        fun onFlipCamera()
        fun onFlashClick()
        fun onLastMediaClick()
        fun onTimerSelected(mode: Int)
        fun onSettingsClick()
        fun onResolutionClick()
        fun onResolutionSelected(index: Int)
    }

    var listener: Listener? = null

    /** 公开状态：MainActivity 直接读写字段，改完调 invalidate()。 */
    val uiState = UiState()

    private val shutter = ShutterButton(this)
    private val modeSwitch = ModeSwitch(this)
    private val exposureSlider = ExposureSlider(this)
    private val flipCamera = FlipCameraButton(this)
    private val flashButton = FlashButton(this)
    private val lastMediaThumbnail = LastMediaThumbnail(this)

    /** 设置入口图标（顶排最右）。 */
    private val settingsButton = SettingsButton(this)

    /** 分辨率切换（顶排按钮 + 展开面板，数据由宿主填充）。 */
    private val resolutionButton = ResolutionButton(this)

    /** 录像计时文本（快门上方，仅录像时可见，无触摸回调）。 */
    private val recordingTimer = RecordingTimer(this)

    /** 计时器面板（顶部最左按钮 + 展开面板，选中项回写 uiState 并上报宿主）。 */
    private val timerPanel = TimerPanel(this)

    /** 横向变焦档位条（占位，不接逻辑）。 */
    private val zoomStrip = ZoomStrip(this)

    init {
        shutter.setOnShutterClickListener { listener?.onShutterClick() }
        modeSwitch.setOnModeChangedListener { isPhoto ->
            uiState.isPhoto = isPhoto
            listener?.onModeChanged(isPhoto)
        }
        exposureSlider.setOnExposureChangedListener { value ->
            uiState.exposureValue = value
            listener?.onExposureChanged(value)
        }
        flipCamera.setOnFlipCameraClickListener { listener?.onFlipCamera() }
        flashButton.setOnFlashClickListener { listener?.onFlashClick() }
        lastMediaThumbnail.setOnLastMediaClickListener {
            listener?.onLastMediaClick()
        }
        timerPanel.setOnTimerSelectedListener { mode ->
            uiState.timerMode = mode
            listener?.onTimerSelected(mode)
        }
        settingsButton.setOnSettingsClickListener {
            listener?.onSettingsClick()
        }
        resolutionButton.setOnResolutionClickListener {
            listener?.onResolutionClick()
        }
        resolutionButton.setOnResolutionSelectedListener { index ->
            listener?.onResolutionSelected(index)
        }
    }

    /** 供宿主填充分辨率面板数据（取数完成后）。 */
    fun setResolutionLabels(labels: List<String>, selectedIndex: Int) {
        resolutionButton.setLabels(labels, selectedIndex)
    }

    /** 打开分辨率面板（须先 setResolutionLabels）。 */
    fun openResolutionPanel() {
        resolutionButton.setOpen(true)
    }

    /** 收起分辨率面板。 */
    fun closeResolutionPanel() {
        resolutionButton.close()
    }

    /** 分辨率面板是否展开。 */
    fun isResolutionPanelOpen(): Boolean = resolutionButton.isOpen()

    /** 供宿主拍照成功后触发快门脉冲动画（对应 CameraXPreviewListener.shutterAnimation）。 */
    fun triggerShutterPulse() {
        shutter.triggerPulse()
        postInvalidateOnAnimation()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)

        // 快门：底部中央，圆心 (w/2, h-117dp)，半径 38dp。
        val shutterCx = w / 2f
        val shutterCy = h - DpUtils.dp(117).toFloat()
        val shutterR = DpUtils.dp(38).toFloat()
        shutter.setBounds(shutterCx, shutterCy, shutterR)

        // 缩略图：快门左侧，距左 24dp，56dp 圆角方，圆心 y 同快门。
        val thumbD = DpUtils.dp(56).toFloat()
        val thumbL = DpUtils.dp(24).toFloat()
        lastMediaThumbnail.setBounds(
            thumbL, shutterCy - thumbD / 2f,
            thumbL + thumbD, shutterCy + thumbD / 2f)

        // 翻转：快门右侧，距右 24dp，44dp 圆，圆心 y 同快门。
        val flipD = DpUtils.dp(44).toFloat()
        val flipR = w - DpUtils.dp(24).toFloat()
        flipCamera.setBounds(
            flipR - flipD, shutterCy - flipD / 2f,
            flipR, shutterCy + flipD / 2f)

        // 模式栏（视频/照片 文字 tab）：居中 140dp x 28dp，
        // 距屏幕底 31dp（上移一个字高）。
        val sw = DpUtils.dp(140).toFloat()
        val sh = DpUtils.dp(28).toFloat()
        val sBottom = h - DpUtils.dp(31).toFloat()
        modeSwitch.setBounds((w - sw) / 2f, sBottom - sh,
            (w + sw) / 2f, sBottom)

        // zoom 占位横条：距快门上边缘 16dp（约 1.2 字高）。
        zoomStrip.setCenter(w / 2f,
            shutterCy - shutterR - DpUtils.dp(16).toFloat())

        // 顶部左上组：3 个 40dp 圆钮横排
        // = FlashButton → TimerPanel → ResolutionButton。
        val topD = DpUtils.dp(40).toFloat()
        val topT = DpUtils.dp(48).toFloat()
        val topGap = DpUtils.dp(12).toFloat()
        val topL = DpUtils.dp(16).toFloat()
        flashButton.setBounds(topL, topT, topL + topD, topT + topD)
        val timerL = topL + (topD + topGap)
        timerPanel.setBounds(timerL, topT, timerL + topD, topT + topD)
        val resL = topL + 2 * (topD + topGap)
        resolutionButton.setBounds(resL, topT, resL + topD, topT + topD)

        // TimerPanel 展开面板：从计时器按钮正下方展开。
        val panelW = DpUtils.dp(160).toFloat()
        val panelH = DpUtils.dp(176).toFloat()
        timerPanel.setPanelBounds(timerL,
            topT + topD + DpUtils.dp(12).toFloat(),
            timerL + panelW,
            topT + topD + DpUtils.dp(12).toFloat() + panelH)

        // 右上设置：44dp 圆钮，距右 16dp、距顶 48dp。
        val setD = DpUtils.dp(44).toFloat()
        settingsButton.setBounds(
            w - DpUtils.dp(16).toFloat() - setD, topT,
            w - DpUtils.dp(16).toFloat(), topT + setD)

        // 录像计时文本：顶部居中，仅录像时可见。
        val rtW = DpUtils.dp(96).toFloat()
        val rtH = DpUtils.dp(36).toFloat()
        recordingTimer.setBounds((w - rtW) / 2f, topT,
            (w + rtW) / 2f, topT + rtH)

        // 曝光滑块：右侧竖条保留不动（docs/07 §7.2）。
        val ew = DpUtils.dp(48).toFloat()
        val eh = h * 0.6f
        val eRight = w - DpUtils.dp(24).toFloat()
        exposureSlider.setBounds(eRight - ew, (h - eh) / 2f,
            eRight, (h + eh) / 2f)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 不画不透明背景：DrawView 浮在 CameraX PreviewView 之上，
        // 必须透明才能让预览画面透出来（docs/07 §7.3 黑底由
        // 窗口/PreviewView 自身承担，不在 overlay 上画）。

        // 每帧从 uiState 拉状态进组件（onDraw 只读字段，不改 uiState）。
        exposureSlider.setRange(uiState.exposureRange)
        exposureSlider.setValue(uiState.exposureValue)
        modeSwitch.setPhotoMode(uiState.isPhoto)
        shutter.updateState(uiState.isPhoto, uiState.isRecording)
        flipCamera.setVisible(uiState.hasFrontAndBack)
        flashButton.setFlashMode(uiState.flashMode)
        lastMediaThumbnail.setBitmap(uiState.lastMediaBitmap)
        // 每帧同步：录像时长进计时文本；当前 TimerMode.ordinal 进面板选中态。
        recordingTimer.setDurationNanos(uiState.recordingDuration)
        timerPanel.setTimerMode(uiState.timerMode)

        // 顺序：曝光滑块 → zoom 条 → 计时器面板 → 分辨率面板 → 录像计时
        // → 闪光 → 设置 → 翻转 → 缩略图 → 模式 tab → 快门（最上层）。
        exposureSlider.draw(canvas)
        zoomStrip.draw(canvas)
        timerPanel.draw(canvas)
        resolutionButton.draw(canvas)
        recordingTimer.draw(canvas)
        flashButton.draw(canvas)
        settingsButton.draw(canvas)
        flipCamera.draw(canvas)
        lastMediaThumbnail.draw(canvas)
        modeSwitch.draw(canvas)
        shutter.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 分发顺序：分辨率(面板优先) → 计时器 → 设置 → 闪光 → 翻转
        // → 曝光 → 模式 → 缩略图 → 快门。先命中者消费，其余不再派发。
        // zoomStrip 占位不消费事件。
        if (resolutionButton.checkTouchEvent(event) ||
            timerPanel.checkTouchEvent(event) ||
            settingsButton.checkTouchEvent(event) ||
            flashButton.checkTouchEvent(event) ||
            flipCamera.checkTouchEvent(event) ||
            exposureSlider.checkTouchEvent(event) ||
            modeSwitch.checkTouchEvent(event) ||
            lastMediaThumbnail.checkTouchEvent(event) ||
            shutter.checkTouchEvent(event)
        ) {
            postInvalidateOnAnimation()
            return true
        }
        // 未命中：不吞事件，留给相机层处理。
        return super.onTouchEvent(event)
    }
}
