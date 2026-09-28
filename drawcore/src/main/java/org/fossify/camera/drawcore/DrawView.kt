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

        // 快门：底部中央，圆心 (w/2, h-150dp)，半径 40dp。
        val shutterCx = w / 2f
        val shutterCy = h - DpUtils.dp(150).toFloat()
        val shutterR = DpUtils.dp(40).toFloat()
        shutter.setBounds(shutterCx, shutterCy, shutterR)

        // 模式切换：快门上方 24dp，水平居中，200dp x 48dp。
        val sw = DpUtils.dp(200).toFloat()
        val sh = DpUtils.dp(48).toFloat()
        val gap = DpUtils.dp(24).toFloat()
        val sBottom = shutterCy - shutterR - gap
        val sLeft = (w - sw) / 2f
        modeSwitch.setBounds(sLeft, sBottom - sh, sLeft + sw, sBottom)

        // 录像计时文本：ModeSwitch 顶再往上 12dp，水平居中于 w/2，96dp x 36dp。
        // 仅录像且 recordingDuration>0 时由组件自身绘制文本；平时不可见。
        val rtW = DpUtils.dp(96).toFloat()
        val rtH = DpUtils.dp(36).toFloat()
        val rtBottom = (sBottom - sh) - DpUtils.dp(12).toFloat()
        val rtLeft = (w - rtW) / 2f
        recordingTimer.setBounds(rtLeft, rtBottom - rtH,
            rtLeft + rtW, rtBottom)

        // 曝光滑块：右侧竖条，右缘距右 24dp，宽 48dp，高 0.6h 垂直居中。
        val ew = DpUtils.dp(48).toFloat()
        val eh = h * 0.6f
        val eRight = w - DpUtils.dp(24).toFloat()
        val eTop = (h - eh) / 2f
        exposureSlider.setBounds(eRight - ew, eTop, eRight, eTop + eh)

        // 顶部一排（避开状态栏，顶 48dp）：5 个 44dp 按钮水平居中、间距 12dp，
        // 从左到右 = TimerPanel（计时器）→ FlipCamera（翻转）→ FlashButton（闪光）
        // → SettingsButton（设置）→ ResolutionButton（分辨率）。
        // 组宽 = 5*44 + 4*12 = 268dp，组左缘 = (w - 268dp)/2。
        val topD = DpUtils.dp(44).toFloat()
        val topT = DpUtils.dp(48).toFloat()
        val topGap = DpUtils.dp(12).toFloat()
        val groupW = 5 * topD + 4 * topGap
        val groupL = (w - groupW) / 2f
        fun xAt(i: Int) = groupL + i * (topD + topGap)
        timerPanel.setBounds(xAt(0), topT, xAt(0) + topD, topT + topD)
        flipCamera.setBounds(xAt(1), topT, xAt(1) + topD, topT + topD)
        flashButton.setBounds(xAt(2), topT, xAt(2) + topD, topT + topD)
        settingsButton.setBounds(xAt(3), topT, xAt(3) + topD, topT + topD)
        resolutionButton.setBounds(xAt(4), topT, xAt(4) + topD, topT + topD)

        // TimerPanel 展开面板：从计时器按钮正下方（左缘与按钮对齐）向下展开，
        // 宽 160dp、高 4 行 × 44dp ≈ 176dp，按钮下方留 12dp 间距。
        val panelW = DpUtils.dp(160).toFloat()
        val panelH = DpUtils.dp(176).toFloat()
        val panelL = xAt(0)
        val panelT = topT + topD + DpUtils.dp(12).toFloat()
        timerPanel.setPanelBounds(panelL, panelT,
            panelL + panelW, panelT + panelH)

        // 最近媒体缩略图：快门左侧圆形，直径 52dp，圆心 y 与快门一致。
        val thumbD = DpUtils.dp(52).toFloat()
        val thumbHalf = thumbD / 2f
        val thumbCx =
            shutterCx - shutterR - DpUtils.dp(24).toFloat() - thumbHalf
        lastMediaThumbnail.setBounds(
            thumbCx - thumbHalf, shutterCy - thumbHalf,
            thumbCx + thumbHalf, shutterCy + thumbHalf,
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 半透明底，便于在相机预览上观察 Overlay。
        canvas.drawColor(0x33000000)

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

        // 顺序：翻转 → 闪光 → 设置 → 分辨率(含面板) → 计时器面板 → 录像计时
        // 文本 → 曝光滑块 → 模式切换 → 缩略图 → 快门。
        // 顶排两个展开面板（分辨率/计时器）在其按钮之后绘制，保证展开时
        // 盖在顶部按钮之上；后绘的计时器面板层叠在其上。
        flipCamera.draw(canvas)
        flashButton.draw(canvas)
        settingsButton.draw(canvas)
        resolutionButton.draw(canvas)
        timerPanel.draw(canvas)
        recordingTimer.draw(canvas)
        exposureSlider.draw(canvas)
        modeSwitch.draw(canvas)
        lastMediaThumbnail.draw(canvas)
        shutter.draw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        // 分发顺序：分辨率(面板优先) → 设置 → 翻转 → 闪光 → 计时器 → 曝光
        // → 模式 → 缩略图 → 快门。先命中者消费，其余不再派发。
        if (resolutionButton.checkTouchEvent(event) ||
            settingsButton.checkTouchEvent(event) ||
            flipCamera.checkTouchEvent(event) ||
            flashButton.checkTouchEvent(event) ||
            timerPanel.checkTouchEvent(event) ||
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
