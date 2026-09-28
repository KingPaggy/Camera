package org.fossify.camera.drawcore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * 圆形快门按钮（非 View 自绘助手，参照 CanvasButton 范式）。
 *
 * 视觉：外环描边圆环 + 内部填充圆。照片模式全白；录像中切红
 * （内圆点略小）。按下时内圆按 AnimatedFloat 缩到 ~0.9，同时外环
 * 描边略加粗作为「变色」反馈；UP 仍在命中区触发点击回调。
 *
 * 白/红之间按 recordAnim 逐通道插值（docs/06 §2.1 Switch 混色范式）；
 * pressAnim / recordAnim 的 set() 均在 draw()(onDraw) 内调用，
 * 动画未结束时由 AnimatedFloat 自续 parent.invalidate()（§2.3）。
 */
class ShutterButton(private val parent: View) {

    private var cx = 0f
    private var cy = 0f
    private var radius = 0f

    private var isPhoto = true
    private var isRecording = false
    private var pressed = false
    private var listener: (() -> Unit)? = null

    /** 按下进度：0=常态 1=按压缩起。 */
    private val pressAnim = AnimatedFloat(
        parent, 120L, CubicBezierInterpolator.EASE_OUT)
    /** 录像态进度：0=照片白 1=录像红。 */
    private val recordAnim = AnimatedFloat(
        parent, 200L, CubicBezierInterpolator.DEFAULT)

    /**
     * 快门脉冲缩放：1=常态，>1=triggerPulse 触发的短暂放大。
     * 初始 1.0，过渡 120ms，与 pressAnim 同范式（set 在 draw 内调用）。
     */
    private val pulseScale = AnimatedFloat(
        1.0f, parent, 0L, 120L, CubicBezierInterpolator.EASE_OUT)
    /** pulseScale 目标值；triggerPulse 在主线程改写，draw 内 set 驱动。 */
    private var pulseTarget = 1.0f

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 设置圆心与外环半径（DrawView 每帧/尺寸变化时调用）。 */
    fun setBounds(cx: Float, cy: Float, radius: Float) {
        this.cx = cx
        this.cy = cy
        this.radius = radius
    }

    /** 外部同步当前档位（拍照/录像），触发白红过渡动画。
     *  等值守卫：onDraw 每帧同步相同状态时直接返回，不重复 invalidate。 */
    fun updateState(isPhoto: Boolean, isRecording: Boolean) {
        if (this.isPhoto == isPhoto && this.isRecording == isRecording) return
        this.isPhoto = isPhoto
        this.isRecording = isRecording
        parent.invalidate()
    }

    fun setOnShutterClickListener(listener: () -> Unit) {
        this.listener = listener
    }

    /**
     * 快门脉冲动画：整体短暂放大到 1.25 倍，120ms 后回弹到 1.0
     * （原 Fossify shutterAnimation 反馈）。主线程改写 pulseTarget，
     * 实际过渡由 draw() 内 pulseScale.set(pulseTarget) 驱动；
     * 不影响 checkTouchEvent / 点击逻辑。
     */
    fun triggerPulse() {
        UIThread.post {
            pulseTarget = 1.25f
            parent.invalidate()
        }
        UIThread.post({
            pulseTarget = 1.0f
            parent.invalidate()
        }, 120L)
    }

    fun draw(canvas: Canvas) {
        if (radius <= 0f) return
        // set() 必须在 onDraw/draw 内调用（docs/06 §2.3）
        val press = pressAnim.set(if (pressed) 1f else 0f)
        val rec = recordAnim.set(if (isRecording) 1f else 0f)
        val pulse = pulseScale.set(pulseTarget)

        val color = mixColor(Colors.ACCENT, Colors.RECORD, rec)

        // 绘制顺序 1：外环灰描边圆（ColorOS 风），整体随 pulse 缩放
        paint.style = Paint.Style.STROKE
        paint.color = Colors.SHUTTER_RING
        paint.strokeWidth = DpUtils.dp(2).toFloat()
        canvas.drawCircle(cx, cy, radius * pulse, paint)

        // 绘制顺序 2：内圆橙/红填充；按压缩到 ~0.9
        val innerR = (radius - DpUtils.dp(10)) *
            (1f - 0.1f * press) * pulse
        paint.style = Paint.Style.FILL
        paint.color = color
        canvas.drawCircle(cx, cy, innerR, paint)
    }

    fun checkTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!hit(event.x, event.y)) return false
                pressed = true
                parent.invalidate()
                return true
            }
            MotionEvent.ACTION_UP -> {
                if (!pressed) return false
                pressed = false
                if (hit(event.x, event.y)) {
                    listener?.invoke()
                }
                parent.invalidate()
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!pressed) return false
                pressed = false
                parent.invalidate()
                return true
            }
            else -> return pressed
        }
    }

    /** 命中检测：外圆半径 90% 为热区，留边距防误触。 */
    private fun hit(x: Float, y: Float): Boolean {
        val dx = x - cx
        val dy = y - cy
        val r = radius * 0.9f
        return dx * dx + dy * dy <= r * r
    }

    /** ARGB 逐通道线性插值（docs/06 §2.1）。 */
    private fun mixColor(from: Int, to: Int, t: Float): Int {
        val r = (Color.red(from) +
            (Color.red(to) - Color.red(from)) * t).toInt()
        val g = (Color.green(from) +
            (Color.green(to) - Color.green(from)) * t).toInt()
        val b = (Color.blue(from) +
            (Color.blue(to) - Color.blue(from)) * t).toInt()
        return Color.argb(255, r, g, b)
    }
}
