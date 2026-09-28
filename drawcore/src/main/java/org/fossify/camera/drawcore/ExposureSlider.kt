package org.fossify.camera.drawcore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View

/**
 * 右侧竖条曝光滑块（非 View 自绘助手，参照 SeekBarView 范式）。
 *
 * 视觉：半透明白竖条轨道 + 从滑块头到底部的高亮段 + 白色小圆滑块头。
 * range == null（相机不支持曝光）时：不绘制、checkTouchEvent 恒 false。
 *
 * 映射方向（下小上大）：
 *   y = top(轨道顶)    -> value = range.last（最大曝光）
 *   y = bottom(轨道底) -> value = range.first（最小曝光）
 * 拖动中按 y 连续映射后取整吸附档位；滑块头位置用 thumbAnim
 * 在档位间平滑 glide（docs/06 §4.3 animatedThumbX 范式）。
 */
class ExposureSlider(private val parent: View) {

    private var left = 0f
    private var top = 0f
    private var right = 0f
    private var bottom = 0f

    private var range: IntRange? = null
    private var value = 0
    private var listener: ((Int) -> Unit)? = null
    private var dragging = false

    /** 滑块头 y 平滑吸附（拖动跟随手指，跳档时 glide）。 */
    private val thumbAnim = AnimatedFloat(
        parent, 80L, CubicBezierInterpolator.EASE_OUT)

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 竖条区域（DrawView 给出右侧竖直热区矩形）。 */
    fun setBounds(left: Float, top: Float, right: Float, bottom: Float) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
    }

    /** null = 相机不支持曝光：隐藏并禁用；非空时档位 = range。
     *  等值守卫：onDraw 每帧同步同一 range 时直接返回，不重复 invalidate。 */
    fun setRange(range: IntRange?) {
        if (this.range == range) return
        this.range = range
        if (range != null) {
            value = value.coerceIn(range.first, range.last)
        }
        parent.invalidate()
    }

    fun setValue(value: Int) {
        val r = range ?: return
        val clamped = value.coerceIn(r.first, r.last)
        if (this.value == clamped) return
        this.value = clamped
        parent.invalidate()
    }

    fun getValue(): Int = value

    /** 回调参数为吸附后的当前档位值（拖动中实时回调）。 */
    fun setOnExposureChangedListener(listener: (Int) -> Unit) {
        this.listener = listener
    }

    fun draw(canvas: Canvas) {
        val r = range ?: return // 禁用态：不绘制
        val h = bottom - top
        if (h <= 0f) return
        val cx = (left + right) / 2f

        // set() 在 draw/onDraw 内调用（docs/06 §2.3）
        val thumbY = thumbAnim.set(valueToY(value))

        // 1) 整条轨道（半透明白细线）
        trackPaint.color = Color.argb(0x40, 255, 255, 255)
        trackPaint.strokeWidth = DpUtils.dp(2).toFloat()
        canvas.drawLine(cx, top, cx, bottom, trackPaint)

        // 2) 已拖区段：从滑块头到底部（下小方向）高亮
        fillPaint.color = Color.argb(0xCC, 255, 255, 255)
        fillPaint.strokeWidth = DpUtils.dp(2).toFloat()
        canvas.drawLine(cx, thumbY, cx, bottom, fillPaint)

        // 3) 滑块头小圆
        thumbPaint.color = Color.WHITE
        canvas.drawCircle(cx, thumbY, DpUtils.dp(8).toFloat(),
            thumbPaint)
    }

    fun checkTouchEvent(event: MotionEvent): Boolean {
        val r = range ?: return false // 禁用态：触摸恒不消费
        val x = event.x
        val y = event.y
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (!hit(x, y)) return false
                dragging = true
                applyY(y)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                applyY(y)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                applyY(y) // 吸附到最近档位并回调最终值
                parent.invalidate()
                return true
            }
            else -> return false
        }
    }

    private fun applyY(y: Float) {
        val v = yToValue(y)
        if (v != value) {
            value = v
            listener?.invoke(v)
        }
        parent.invalidate()
    }

    /** 命中：竖条矩形 + 四周 16dp 热区扩展（docs/06 §2.2）。 */
    private fun hit(x: Float, y: Float): Boolean {
        val hot = DpUtils.dp(16).toFloat()
        return x >= left - hot && x <= right + hot &&
            y >= top - hot && y <= bottom + hot
    }

    /**
     * 下小上大：
     *   frac = (y - top) / height   （top=0, bottom=1）
     *   value = round(range.first + (1 - frac) * span)
     * 取整后即吸附档位；clamp 到 range 内。
     */
    private fun yToValue(y: Float): Int {
        val r = range ?: return 0
        val h = (bottom - top).coerceAtLeast(1f)
        val frac = ((y - top) / h).coerceIn(0f, 1f)
        val span = (r.last - r.first).toFloat()
        val v = Math.round(r.first + (1f - frac) * span)
        return v.toInt().coerceIn(r.first, r.last)
    }

    /** value -> 滑块头 y（与 yToValue 互逆）。 */
    private fun valueToY(v: Int): Float {
        val r = range ?: return (top + bottom) / 2f
        val span = (r.last - r.first).toFloat()
        if (span <= 0f) return (top + bottom) / 2f
        val frac = 1f - (v - r.first).toFloat() / span
        return top + frac * (bottom - top)
    }
}
