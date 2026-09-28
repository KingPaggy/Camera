package org.fossify.camera.drawcore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * 「视频 | 照片」文字 tab 切换（ColorOS 风，docs/07 §7.2）。
 *
 * 不画背景胶囊，纯文字：选中档橙色 + 上方小三角，未选中白色。
 * 位置：左段=视频，右段=照片。点击左右即切档。
 * 颜色过渡由 modeAnim 在白/橙间插值。
 */
class ModeSwitch(private val parent: View) {

    private val rect = RectF()
    private var photoMode = true
    private var listener: ((Boolean) -> Unit)? = null

    /** 0=视频(左) 1=照片(右)。 */
    private val modeAnim = AnimatedFloat(
        parent, 220L, CubicBezierInterpolator.EASE_OUT_QUINT)

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val triPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setBounds(left: Float, top: Float, right: Float, bottom: Float) {
        rect.set(left, top, right, bottom)
    }

    /** 外部同步当前档位（初始化 / 录像结束时调用），不触发回调。 */
    fun setPhotoMode(isPhoto: Boolean) {
        if (photoMode == isPhoto) return
        photoMode = isPhoto
        parent.invalidate()
    }

    fun isPhotoMode(): Boolean = photoMode

    /** 回调参数 true=照片 false=视频。 */
    fun setOnModeChangedListener(listener: (Boolean) -> Unit) {
        this.listener = listener
    }

    fun draw(canvas: Canvas) {
        if (rect.width() <= 0f || rect.height() <= 0f) return
        // p: 0=视频选中 1=照片选中
        val p = modeAnim.set(if (photoMode) 1f else 0f)

        textPaint.textSize = DpUtils.dp(13).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
        val baseline = rect.centerY() -
            (textPaint.descent() + textPaint.ascent()) / 2f

        val videoCx = rect.left + rect.width() / 4f
        val photoCx = rect.right - rect.width() / 4f

        // 视频：p=0 时选中橙，p=1 时白
        textPaint.color = mixColor(Colors.ACCENT, Color.WHITE, p)
        canvas.drawText("视频", videoCx, baseline, textPaint)

        // 照片：p=1 时选中橙，p=0 时白
        textPaint.color = mixColor(Color.WHITE, Colors.ACCENT, p)
        canvas.drawText("照片", photoCx, baseline, textPaint)

        // 选中三角：在选中文字正上方
        val triCx = if (photoMode) photoCx else videoCx
        val triTop = rect.top
        val triW = DpUtils.dp(4).toFloat()
        val triH = DpUtils.dp(3).toFloat()
        val tri = Path()
        tri.moveTo(triCx, triTop)
        tri.lineTo(triCx - triW, triTop + triH)
        tri.lineTo(triCx + triW, triTop + triH)
        tri.close()
        triPaint.color = Colors.ACCENT
        canvas.drawPath(tri, triPaint)
    }

    fun checkTouchEvent(event: MotionEvent): Boolean {
        // 点击式切换：仅在 DOWN 命中时消费并切档
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return false
        val x = event.x
        val y = event.y
        if (!rect.contains(x, y)) return false
        val wantPhoto = x >= rect.centerX()
        if (wantPhoto != photoMode) {
            photoMode = wantPhoto
            listener?.invoke(photoMode)
        }
        parent.invalidate()
        return true
    }

    /** ARGB 逐通道线性插值。 */
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
