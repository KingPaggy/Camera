package org.fossify.camera.drawcore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * 录像计时器（非 View 自绘助手，参照 ModeSwitch 文本绘制范式）。
 *
 * 视觉：半透明白底圆角胶囊 + 中央白色 mm:ss 文本。纯展示组件，
 * 不消费任何触摸事件（checkTouchEvent 恒 false）。
 *
 * setDurationNanos(≤0) 表示未录像：完全隐藏（不绘制、不命中）。
 */
class RecordingTimer(private val parent: View) {

    private val rect = RectF()

    /** 当前已录像时长（纳秒）；≤0 = 未录像，组件隐藏。 */
    private var durationNanos = 0L

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 设置胶囊包围盒（DrawView 布局时调用）。 */
    fun setBounds(left: Float, top: Float, right: Float, bottom: Float) {
        rect.set(left, top, right, bottom)
    }

    /** 设置当前录像时长（纳秒）；≤0 = 未录像，组件隐藏。 */
    fun setDurationNanos(nanos: Long) {
        if (durationNanos == nanos) return
        durationNanos = nanos
        parent.invalidate()
    }

    fun draw(canvas: Canvas) {
        if (durationNanos <= 0L) return
        if (rect.width() <= 0f || rect.height() <= 0f) return

        // 1) 胶囊背景：半透明白底
        bgPaint.color = 0x66FFFFFF
        canvas.drawRoundRect(rect, rect.height() / 2f,
            rect.height() / 2f, bgPaint)

        // 2) 中央白色 mm:ss 文本（垂直居中，对齐 ModeSwitch 范式）
        textPaint.color = Color.WHITE
        textPaint.textSize = DpUtils.dp(14).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
        val baseline = rect.centerY() -
            (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(format(durationNanos), rect.centerX(),
            baseline, textPaint)
    }

    fun checkTouchEvent(event: MotionEvent): Boolean = false

    /**
     * 纳秒 → mm:ss。
     * ms = nanos / 1_000_000；mm = ms / 60_000；ss = (ms/1000) % 60。
     * 例：65 秒 → 01:05；90 分钟 → 90:00（分钟不封顶，允许 >59，
     * 与系统录像计时器一致；如需 HH:mm:ss 在此把 mm 拆成 hh:mm）。
     */
    private fun format(nanos: Long): String {
        val ms = nanos / 1_000_000L
        val mm = ms / 60_000L
        val ss = (ms / 1_000L) % 60L
        return "%02d:%02d".format(mm, ss)
    }
}
