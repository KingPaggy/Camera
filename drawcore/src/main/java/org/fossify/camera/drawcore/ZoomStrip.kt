package org.fossify.camera.drawcore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import kotlin.math.roundToInt

/**
 * 横向变焦档位条（0.6 / 1 / 2 / 3.5 四档，docs/10）。
 *
 * 视觉：横排 4 档文字，选中档橙色，置灰档白 60% 透明。
 * 数据：setRange(min..max) 驱动可用性置灰；
 * setSelectedIndex 驱动高亮；点击回调上报档下标。
 * range == null（相机未开/不支持）时：不绘制、
 * checkTouchEvent 恒 false（同 ExposureSlider 模式）。
 * 范围类型用 ClosedFloatingPointRange（FloatRange 本版本不可解析）。
 */
class ZoomStrip(private val parent: View) {

    companion object {
        /** 档位倍率（与 LABELS 下标一一对应，MainActivity 复用）。 */
        val RATIOS = listOf(0.6f, 1f, 2f, 3.5f)

        /** 档位显示文案。 */
        val LABELS = listOf("0.6", "1", "2", "3.5")
    }

    private var cx = 0f
    private var cy = 0f

    private var range: ClosedFloatingPointRange<Float>? = null
    private var selectedIndex = 1
    private var listener: ((Int) -> Unit)? = null

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** 条中心定位（DrawView.onSizeChanged 调用）。 */
    fun setCenter(cx: Float, cy: Float) {
        this.cx = cx
        this.cy = cy
    }

    /** null=不支持/相机未开：隐藏并禁用（等值守卫防重复重绘）。 */
    fun setRange(range: ClosedFloatingPointRange<Float>?) {
        if (this.range == range) return
        this.range = range
        parent.invalidate()
    }

    /** 外部同步当前选中档（档位点击/捏合后回写，不触发回调）。 */
    fun setSelectedIndex(index: Int) {
        if (this.selectedIndex == index) return
        this.selectedIndex = index
        parent.invalidate()
    }

    /** 回调参数为点击档位下标（0..3）。 */
    fun setOnZoomSelectedListener(listener: (Int) -> Unit) {
        this.listener = listener
    }

    fun draw(canvas: Canvas) {
        if (range == null) return // 禁用态：不绘制
        if (cx == 0f && cy == 0f) return
        textPaint.textSize = DpUtils.dp(14).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
        val baseline = cy -
            (textPaint.descent() + textPaint.ascent()) / 2f

        // 4 档居中：x(i) = cx + (i - 1.5) * gap（docs/10 §10.4.3）。
        val gap = DpUtils.dp(36).toFloat()
        RATIOS.forEachIndexed { i, ratio ->
            val x = cx + (i - 1.5f) * gap
            textPaint.color = when {
                i == selectedIndex -> Colors.ACCENT
                isEnabled(ratio) -> Color.WHITE
                else -> Color.argb(0x99, 255, 255, 255) // 白 60%：置灰
            }
            canvas.drawText(LABELS[i], x, baseline, textPaint)
        }
    }

    fun checkTouchEvent(event: MotionEvent): Boolean {
        if (range == null) return false // 禁用态：触摸恒不消费
        if (event.pointerCount > 1) return false // 多指：放行捏合
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return false

        val index = hitIndex(event.x, event.y) ?: return false
        if (!isEnabled(RATIOS[index])) return false // 置灰档：放行
        listener?.invoke(index)
        return true
    }

    /** 命中档位：档间中线划分，y 在条高 ±16dp 内。 */
    private fun hitIndex(x: Float, y: Float): Int? {
        val gap = DpUtils.dp(36).toFloat()
        val hotY = DpUtils.dp(16).toFloat()
        if (y < cy - hotY || y > cy + hotY) return null
        val i = ((x - cx) / gap + 1.5f).roundToInt()
        return i.takeIf { it in RATIOS.indices }
    }

    /** 档位可用性：落在变焦范围内（浮点容差 1e-3）。 */
    private fun isEnabled(ratio: Float): Boolean {
        val r = range ?: return false
        return ratio >= r.start - 1e-3f && ratio <= r.endInclusive + 1e-3f
    }}
