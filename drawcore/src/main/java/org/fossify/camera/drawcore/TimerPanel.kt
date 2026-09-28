package org.fossify.camera.drawcore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * 延时拍照计时器（非 View 自绘助手，
 * 参照 FlashButton / ModeSwitch 范式）。
 *
 * 顶部图标按钮：半透明白圆底 + 自绘时钟
 * （Paint + Path，STROKE 表盘 + 两根指针），
 * 按下圆底由 0x40FFFFFF 提亮到 0x99FFFFFF。
 * 非 0 档时时钟图标由半透白切到纯白。
 *
 * 弹出面板：半透明黑圆角卡片（0xCC000000）
 * + 4 行选项（关 / 3 秒 / 5 秒 / 10 秒），
 * 行高 = 面板高 / 4。选中或按压预览行画
 * 半透明白圆角高亮条（0x33FFFFFF），文本纯白；
 * 未选中文本半透白（0x99FFFFFF）。
 */
class TimerPanel(private val parent: View) {

    companion object {
        const val MODE_OFF = 0
        const val MODE_3S = 1
        const val MODE_5S = 2
        const val MODE_10S = 3
    }

    private val buttonRect = RectF()
    private val panelRect = RectF()
    private val highlightRect = RectF()

    private var timerMode = MODE_OFF
    private var open = false

    // 触摸状态：面板关闭时按钮按下中
    private var btnPressed = false
    // 面板打开时 DOWN 落在面板内、正在预览的行号（-1 无）
    private var rowPreview = -1
    // 面板打开时点按钮关闭面板后，需吞掉后续 UP/MOVE
    private var gestureConsumed = false

    private var listener: ((Int) -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun setBounds(left: Float, top: Float,
                  right: Float, bottom: Float) {
        buttonRect.set(left, top, right, bottom)
    }

    fun setPanelBounds(left: Float, top: Float,
                       right: Float, bottom: Float) {
        panelRect.set(left, top, right, bottom)
    }

    /** 0=关 1=3s 2=5s 3=10s（与 app 侧 TimerMode ordinal 对应）。 */
    fun setTimerMode(mode: Int) {
        if (timerMode == mode) return
        timerMode = mode
        parent.invalidate()
    }

    /** 回调参数为 ordinal 0-3。 */
    fun setOnTimerSelectedListener(listener: (Int) -> Unit) {
        this.listener = listener
    }

    fun draw(canvas: Canvas) {
        drawButton(canvas)
        if (open) drawPanel(canvas)
    }

    private fun drawButton(canvas: Canvas) {
        if (buttonRect.width() <= 0f) return
        val cx = buttonRect.centerX()
        val cy = buttonRect.centerY()
        val radius = min(buttonRect.width(),
            buttonRect.height()) / 2f

        // 圆形底：半透明白，按下提亮
        bgPaint.color = if (btnPressed) {
            Color.argb(0x99, 255, 255, 255)
        } else {
            Color.argb(0x40, 255, 255, 255)
        }
        canvas.drawCircle(cx, cy, radius, bgPaint)

        // 时钟图标：OFF 半透白，非 0 档纯白
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = DpUtils.dp(1.5f).toFloat()
        iconPaint.strokeCap = Paint.Cap.ROUND
        iconPaint.color = if (timerMode == MODE_OFF) {
            Color.argb(0xCC, 255, 255, 255)
        } else {
            Color.WHITE
        }
        val ir = radius * 0.55f
        canvas.drawCircle(cx, cy, ir, iconPaint)
        // 时针 / 分针
        canvas.drawLine(cx, cy, cx, cy - ir * 0.6f, iconPaint)
        canvas.drawLine(cx, cy, cx + ir * 0.45f,
            cy + ir * 0.15f, iconPaint)
    }

    private fun drawPanel(canvas: Canvas) {
        if (panelRect.width() <= 0f ||
            panelRect.height() <= 0f) return

        // 半透明黑圆角卡片
        val radius = DpUtils.dp(12).toFloat()
        panelPaint.color = Color.argb(0xCC, 0, 0, 0)
        canvas.drawRoundRect(panelRect, radius, radius, panelPaint)

        val rowH = panelRect.height() / 4f
        val labels = arrayOf("关", "3 秒", "5 秒", "10 秒")
        textPaint.textSize = DpUtils.dp(14).toFloat()
        textPaint.textAlign = Paint.Align.CENTER

        for (i in 0..3) {
            val rowTop = panelRect.top + i * rowH
            val rowBottom = rowTop + rowH
            val cy = (rowTop + rowBottom) / 2f
            val baseline = cy -
                (textPaint.descent() + textPaint.ascent()) / 2f

            // 高亮：按压预览行优先，否则当前选中档
            val active = if (rowPreview >= 0) {
                rowPreview == i
            } else {
                timerMode == i
            }
            if (active) {
                val padX = DpUtils.dp(8).toFloat()
                val padY = DpUtils.dp(3).toFloat()
                highlightRect.set(panelRect.left + padX,
                    rowTop + padY,
                    panelRect.right - padX,
                    rowBottom - padY)
                highlightPaint.color =
                    Color.argb(0x33, 255, 255, 255)
                canvas.drawRoundRect(highlightRect,
                    highlightRect.height() / 2f,
                    highlightRect.height() / 2f,
                    highlightPaint)
                textPaint.color = Color.WHITE
            } else {
                textPaint.color =
                    Color.argb(0x99, 255, 255, 255)
            }
            canvas.drawText(labels[i], panelRect.centerX(),
                baseline, textPaint)
        }
    }

    fun checkTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN ->
                return onDown(event.x, event.y)
            MotionEvent.ACTION_UP ->
                return onUp(event.x, event.y)
            MotionEvent.ACTION_MOVE ->
                return onMove(event.x, event.y)
            MotionEvent.ACTION_CANCEL -> {
                val was = btnPressed || rowPreview >= 0 ||
                    gestureConsumed
                btnPressed = false
                rowPreview = -1
                gestureConsumed = false
                parent.invalidate()
                return was
            }
            else ->
                return btnPressed || rowPreview >= 0 ||
                    gestureConsumed
        }
    }

    private fun onDown(x: Float, y: Float): Boolean {
        if (!open) {
            // 面板关闭：只响应按钮圆
            if (hitButton(x, y)) {
                btnPressed = true
                parent.invalidate()
                return true
            }
            return false
        }
        // 面板打开：点按钮 -> 关闭（切换关闭，不重开）
        if (hitButton(x, y)) {
            open = false
            rowPreview = -1
            gestureConsumed = true
            parent.invalidate()
            return true
        }
        // 面板内：按 y 映射行号进入选中预览
        if (panelRect.contains(x, y)) {
            rowPreview = rowAt(y)
            gestureConsumed = true
            parent.invalidate()
            return true
        }
        // 面板外：关闭面板，但不消费该 DOWN——
        // 交给下层继续处理（例如点快门或别处）。
        open = false
        rowPreview = -1
        gestureConsumed = false
        parent.invalidate()
        return false
    }

    private fun onUp(x: Float, y: Float): Boolean {
        if (!open) {
            if (btnPressed) {
                // 按钮按下后抬手在按钮上 -> 打开面板
                btnPressed = false
                if (hitButton(x, y)) open = true
                parent.invalidate()
                return true
            }
            if (gestureConsumed) {
                // 面板打开时由按钮 DOWN 关闭的手势，吞掉 UP
                gestureConsumed = false
                return true
            }
            return false
        }
        // 面板打开中：DOWN 已落在面板内 -> UP 仍在同行则选中
        if (rowPreview >= 0) {
            val row = rowAt(y)
            if (row == rowPreview) {
                timerMode = row
                listener?.invoke(row)
            }
            rowPreview = -1
            open = false
            gestureConsumed = false
            parent.invalidate()
            return true
        }
        return false
    }

    private fun onMove(x: Float, y: Float): Boolean {
        if (open && rowPreview >= 0) {
            val row = rowAt(y)
            if (row != rowPreview) {
                rowPreview = row
                parent.invalidate()
            }
            return true
        }
        return btnPressed || gestureConsumed
    }

    /** 命中检测：圆半径 95% 为热区。 */
    private fun hitButton(x: Float, y: Float): Boolean {
        val dx = x - buttonRect.centerX()
        val dy = y - buttonRect.centerY()
        val r = min(buttonRect.width(),
            buttonRect.height()) / 2f * 0.95f
        return dx * dx + dy * dy <= r * r
    }

    /** 由 y 坐标映射到行号 0-3，越界夹取。 */
    private fun rowAt(y: Float): Int {
        val rowH = panelRect.height() / 4f
        var idx = ((y - panelRect.top) / rowH).toInt()
        if (idx < 0) idx = 0
        if (idx > 3) idx = 3
        return idx
    }
}
