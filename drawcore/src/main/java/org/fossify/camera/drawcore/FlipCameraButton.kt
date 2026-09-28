package org.fossify.camera.drawcore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 切换前后摄像头图标按钮（非 View 自绘助手，
 * 参照 CanvasButton/ShutterButton 范式）。
 *
 * 视觉：半透明白圆底 + 自绘环绕双箭头图标
 * （两段互补圆弧 + 两端三角箭头，Paint + Path 绘制）。
 * 按下时圆底由 0x40FFFFFF 提亮到 0x99FFFFFF
 * （CanvasButton pressed 风格），UP 仍在命中区触发回调。
 *
 * setVisible(false) 时：draw 直接 return 不绘制；
 * checkTouchEvent 恒返回 false，完全不响应触摸。
 */
class FlipCameraButton(private val parent: View) {

    private val rect = RectF()
    private var visible = true
    private var pressed = false
    private var listener: (() -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setBounds(left: Float, top: Float,
                  right: Float, bottom: Float) {
        rect.set(left, top, right, bottom)
    }

    fun setVisible(visible: Boolean) {
        if (this.visible == visible) return
        this.visible = visible
        if (!visible) pressed = false
        parent.invalidate()
    }

    fun setOnFlipCameraClickListener(listener: () -> Unit) {
        this.listener = listener
    }

    fun draw(canvas: Canvas) {
        if (!visible || rect.width() <= 0f) return
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

        // 2) 环绕双箭头：两段互补圆弧 + 两个三角箭头
        val ir = radius * 0.55f
        val iconRect = RectF(cx - ir, cy - ir, cx + ir, cy + ir)

        arcPaint.style = Paint.Style.STROKE
        arcPaint.color = Color.WHITE
        arcPaint.strokeWidth = DpUtils.dp(2).toFloat()
        arcPaint.strokeCap = Paint.Cap.ROUND

        // 屏幕坐标 y 向下：0°=东，90°=南，顺时针角度增大。
        // 上半弧 160°→300°，下半弧 340°→120°
        // （arcTo forceMoveTo=true 防 stray line）。
        val arc = Path()
        arc.arcTo(iconRect, 160f, 140f, true)
        canvas.drawPath(arc, arcPaint)
        arc.rewind()
        arc.arcTo(iconRect, 340f, 140f, true)
        canvas.drawPath(arc, arcPaint)

        // 两端三角箭头，分别落在两段弧的末端（切线方向）
        arrowPaint.style = Paint.Style.FILL
        arrowPaint.color = Color.WHITE
        drawArrowHead(canvas, cx, cy, ir, 300f)
        drawArrowHead(canvas, cx, cy, ir, 120f)
    }

    /** 在半径 r 的圆周上、角度 thetaDeg(度) 处，
     *  画沿顺时针切线指向的小三角箭头。 */
    private fun drawArrowHead(canvas: Canvas, cx: Float, cy: Float,
                              r: Float, thetaDeg: Float) {
        val rad = Math.toRadians(thetaDeg.toDouble())
        val c = cos(rad).toFloat()
        val s = sin(rad).toFloat()
        // 弧上端点（apex）
        val px = cx + r * c
        val py = cy + r * s
        // 顺时针切线方向 dP/dθ = (-sinθ, cosθ)
        val tx = -s
        val ty = c
        // 法向量（切线旋转 90°）
        val nx = -ty
        val ny = tx
        val len = DpUtils.dp(6).toFloat()
        val half = DpUtils.dp(3).toFloat()
        val baseX = px - tx * len
        val baseY = py - ty * len
        val p = Path()
        p.moveTo(px, py)
        p.lineTo(baseX + nx * half, baseY + ny * half)
        p.lineTo(baseX - nx * half, baseY - ny * half)
        p.close()
        canvas.drawPath(p, arrowPaint)
    }

    fun checkTouchEvent(event: MotionEvent): Boolean {
        if (!visible) return false
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
