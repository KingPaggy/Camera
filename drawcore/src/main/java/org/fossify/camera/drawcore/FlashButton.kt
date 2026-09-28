package org.fossify.camera.drawcore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * 闪光灯图标按钮（非 View 自绘助手，
 * 参照 CanvasButton/ShutterButton 范式）。
 *
 * 视觉：半透明白圆底 + 自绘 Z 形闪电
 * （Paint + Path 多边形），不引图片资源。
 * 按下时圆底由 0x40FFFFFF 提亮到 0x99FFFFFF
 * （CanvasButton pressed 风格）。
 *
 * 四种模式视觉区分（mode 与 app 侧
 * helpers/Constants.kt 闪光灯档位对应）：
 *  - 0 OFF       ：闪电半透明灰白（0x66FFFFFF），表示关闭；
 *  - 1 ON        ：闪电纯白高亮；
 *  - 2 AUTO      ：闪电纯白高亮 + 圆内右下角小字号 "A" 标记；
 *  - 3 ALWAYS_ON ：闪电纯白高亮 + 圆内一圈琥珀色描边
 *                 （持续开启指示）。
 */
class FlashButton(private val parent: View) {

    companion object {
        const val MODE_OFF = 0
        const val MODE_ON = 1
        const val MODE_AUTO = 2
        const val MODE_ALWAYS_ON = 3
    }

    private val rect = RectF()
    private var flashMode = MODE_OFF
    private var pressed = false
    private var listener: (() -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val boltPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setBounds(left: Float, top: Float,
                  right: Float, bottom: Float) {
        rect.set(left, top, right, bottom)
    }

    /** 0=OFF 1=ON 2=AUTO 3=ALWAYS_ON（与 Constants.kt 常量对应）。 */
    fun setFlashMode(mode: Int) {
        if (flashMode == mode) return
        flashMode = mode
        parent.invalidate()
    }

    fun setOnFlashClickListener(listener: () -> Unit) {
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

        // 2) ALWAYS_ON：圆内琥珀色高亮描边（持续开启指示）
        if (flashMode == MODE_ALWAYS_ON) {
            ringPaint.style = Paint.Style.STROKE
            ringPaint.color = Color.argb(255, 0xFF, 0xC1, 0x07)
            ringPaint.strokeWidth = DpUtils.dp(2).toFloat()
            canvas.drawCircle(cx, cy,
                radius - ringPaint.strokeWidth / 2f, ringPaint)
        }

        // 3) Z 形闪电：OFF 半透灰，其余纯白高亮
        val ir = radius * 0.55f
        boltPaint.style = Paint.Style.FILL
        boltPaint.color = when (flashMode) {
            MODE_OFF -> Color.argb(0x66, 255, 255, 255)
            else -> Color.WHITE
        }
        canvas.drawPath(buildBoltPath(cx, cy, ir), boltPaint)

        // 4) AUTO：右下角小 "A" 标记
        if (flashMode == MODE_AUTO) {
            textPaint.color = Color.WHITE
            textPaint.textSize = DpUtils.dp(9).toFloat()
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("A", cx + radius * 0.55f,
                cy + radius * 0.62f, textPaint)
        }
    }

    /** 以(cx,cy)为中心、ir 为单位半长的 Z 形闪电多边形。 */
    private fun buildBoltPath(cx: Float, cy: Float, ir: Float): Path {
        val p = Path()
        p.moveTo(cx + 0.30f * ir, cy - 1.00f * ir)
        p.lineTo(cx - 0.45f * ir, cy + 0.25f * ir)
        p.lineTo(cx - 0.05f * ir, cy + 0.25f * ir)
        p.lineTo(cx - 0.25f * ir, cy + 1.00f * ir)
        p.lineTo(cx + 0.45f * ir, cy - 0.15f * ir)
        p.lineTo(cx + 0.05f * ir, cy - 0.15f * ir)
        p.close()
        return p
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
