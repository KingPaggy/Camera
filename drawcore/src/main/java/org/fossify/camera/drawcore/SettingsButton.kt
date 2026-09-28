package org.fossify.camera.drawcore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.min
import kotlin.math.cos
import kotlin.math.sin

/**
 * 设置入口图标按钮（非 View 自绘助手，
 * 参照 FlashButton 范式）。
 *
 * 视觉：半透明白圆底 + 自绘齿轮图标
 * （Paint 实心圆 + 圆周 8 根粗齿条），
 * 按下圆底由 0x40FFFFFF 提亮到 0x99FFFFFF。
 */
class SettingsButton(private val parent: View) {

    private val rect = android.graphics.RectF()
    private var pressed = false
    private var listener: (() -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gearPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setBounds(left: Float, top: Float,
                  right: Float, bottom: Float) {
        rect.set(left, top, right, bottom)
    }

    fun setOnSettingsClickListener(listener: () -> Unit) {
        this.listener = listener
    }

    fun draw(canvas: Canvas) {
        if (rect.width() <= 0f) return
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = min(rect.width(), rect.height()) / 2f

        // 1) 圆形底：半透明白，按下提亮
        bgPaint.color = if (pressed) {
            Color.argb(0x99, 255, 255, 255)
        } else {
            Color.argb(0x40, 255, 255, 255)
        }
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // 2) 齿轮：实心圆 + 圆周 8 根粗齿条
        gearPaint.style = Paint.Style.FILL
        gearPaint.color = Color.WHITE
        val innerR = radius * 0.42f
        canvas.drawCircle(cx, cy, innerR, gearPaint)

        gearPaint.style = Paint.Style.STROKE
        gearPaint.strokeCap = Paint.Cap.ROUND
        val toothW = radius * 0.28f
        gearPaint.strokeWidth = radius * 0.16f
        val teeth = 8
        for (i in 0 until teeth) {
            val a = (Math.PI * 2 * i / teeth).toFloat()
            val x1 = cx + cos(a) * innerR * 0.8f
            val y1 = cy + sin(a) * innerR * 0.8f
            val x2 = cx + cos(a) * (innerR + toothW)
            val y2 = cy + sin(a) * (innerR + toothW)
            canvas.drawLine(x1, y1, x2, y2, gearPaint)
        }
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
                if (hit(event.x, event.y)) listener?.invoke()
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

    /** 命中检测：圆半径 95% 为热区。 */
    private fun hit(x: Float, y: Float): Boolean {
        val dx = x - rect.centerX()
        val dy = y - rect.centerY()
        val r = min(rect.width(), rect.height()) / 2f * 0.95f
        return dx * dx + dy * dy <= r * r
    }
}
