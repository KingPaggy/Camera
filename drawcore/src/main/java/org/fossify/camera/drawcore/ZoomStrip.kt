package org.fossify.camera.drawcore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * 横向变焦档位条（占位，docs/07 §7.2）。
 *
 * 视觉：横排 5 档文字 0.6 / 1.4x / 2 / 3.5 / 7，
 * 当前选中档（1.4x）橙色，其余白色。
 *
 * 本阶段纯绘制占位，**不接 CameraX 变焦逻辑**，
 * checkTouchEvent 恒返回 false，点击不响应。
 * 后期接变焦时再补触摸与回调。
 */
class ZoomStrip(private val parent: View) {

    private var cx = 0f
    private var cy = 0f

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val labels = listOf("0.6", "1.4x", "2", "3.5", "7")
    private val selectedIndex = 1

    /** 设置条中心位置（DrawView.onSizeChanged 调用）。 */
    fun setCenter(cx: Float, cy: Float) {
        this.cx = cx
        this.cy = cy
    }

    fun draw(canvas: Canvas) {
        if (cx == 0f && cy == 0f) return
        textPaint.textSize = DpUtils.dp(14).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
        val baseline = cy -
            (textPaint.descent() + textPaint.ascent()) / 2f

        val gap = DpUtils.dp(36).toFloat()
        labels.forEachIndexed { i, label ->
            val x = cx + (i - (labels.size - 1) / 2f) * gap
            textPaint.color = if (i == selectedIndex) {
                Colors.ACCENT
            } else {
                Color.WHITE
            }
            canvas.drawText(label, x, baseline, textPaint)
        }
    }

    /** 占位：不消费任何触摸事件。 */
    fun checkTouchEvent(event: MotionEvent): Boolean = false
}
