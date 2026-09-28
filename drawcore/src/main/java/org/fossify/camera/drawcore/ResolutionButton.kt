package org.fossify.camera.drawcore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import kotlin.math.min

/**
 * 分辨率切换（非 View 自绘助手，
 * 参照 TimerPanel 的「按钮 + 展开面板」一体范式）。
 *
 * 顶部图标按钮：半透明白圆底 + 自绘分辨率图标
 * （STROKE 圆角外框 + 中心实心点），按下圆底提亮。
 *
 * 弹出面板：半透明黑圆角卡片（0xCC000000），
 * 从按钮正下方向下展开，行数 = 当前分辨率选项数。
 * 行高固定 44dp，选中/按压预览行画半透明白高亮条，
 * 选中项文本纯白、其余半透白。
 *
 * 数据由宿主填充：setLabels(labels, selectedIndex)。
 * 选中回调参数为行索引（对应 app 侧 storeSize 的 index）。
 */
class ResolutionButton(private val parent: View) {

    private val buttonRect = RectF()
    private val panelRect = RectF()
    private val highlightRect = RectF()

    private var labels = emptyArray<String>()
    private var selectedIndex = -1
    private var open = false

    // 触摸状态：面板关闭时按钮按下中
    private var btnPressed = false
    // 面板打开时 DOWN 落在面板内、正在预览的行号（-1 无）
    private var rowPreview = -1
    // 面板打开时点按钮关闭面板后，需吞掉后续 UP/MOVE
    private var gestureConsumed = false

    private var listener: ((Int) -> Unit)? = null
    private var clickListener: (() -> Unit)? = null

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val panelPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val rowHeight = DpUtils.dp(44).toFloat()
    private val panelWidth = DpUtils.dp(160).toFloat()
    private val panelGap = DpUtils.dp(12).toFloat()

    fun setBounds(left: Float, top: Float,
                  right: Float, bottom: Float) {
        buttonRect.set(left, top, right, bottom)
        // 面板跟随按钮正下方展开，左缘对齐按钮。
        rebuildPanelBounds()
    }

    /** 填充分辨率选项；labels 为空时面板不可用。 */
    fun setLabels(labels: List<String>, selectedIndex: Int) {
        this.labels = labels.toTypedArray()
        this.selectedIndex = selectedIndex
        rebuildPanelBounds()
        parent.invalidate()
    }

    fun isOpen(): Boolean = open

    /** 关闭面板（如切换到别处时由宿主收起）。 */
    fun close() {
        open = false
        rowPreview = -1
        gestureConsumed = false
        parent.invalidate()
    }

    /** 回调参数为行索引 0..labels.size-1。 */
    fun setOnResolutionSelectedListener(listener: (Int) -> Unit) {
        this.listener = listener
    }

    /** 按钮点击回调（面板未开时）；宿主取数并 setOpen(true) 后再展开。 */
    fun setOnResolutionClickListener(listener: () -> Unit) {
        this.clickListener = listener
    }

    /** 宿主控制面板开合（取数填充 labels 后调用）。 */
    fun setOpen(open: Boolean) {
        if (this.open == open) return
        this.open = open
        if (!open) {
            rowPreview = -1
            gestureConsumed = false
        }
        parent.invalidate()
    }

    private fun rebuildPanelBounds() {
        val h = labels.size * rowHeight
        // 面板右缘对齐按钮右缘，向左展开（按钮靠右，避免向右溢出）。
        panelRect.set(
            buttonRect.right - panelWidth,
            buttonRect.bottom + panelGap,
            buttonRect.right,
            buttonRect.bottom + panelGap + h,
        )
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

        // 分辨率图标：圆角外框 + 中心实心点
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeWidth = DpUtils.dp(1.5f).toFloat()
        iconPaint.color = Color.WHITE
        val box = radius * 0.55f
        val boxL = cx - box
        val boxT = cy - box
        canvas.drawRoundRect(
            RectF(boxL, boxT, cx + box, cy + box),
            DpUtils.dp(2).toFloat(),
            DpUtils.dp(2).toFloat(),
            iconPaint,
        )
        iconPaint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, radius * 0.14f, iconPaint)
    }

    private fun drawPanel(canvas: Canvas) {
        if (panelRect.width() <= 0f ||
            panelRect.height() <= 0f ||
            labels.isEmpty()) return

        // 半透明黑圆角卡片
        val radius = DpUtils.dp(12).toFloat()
        panelPaint.color = Color.argb(0xCC, 0, 0, 0)
        canvas.drawRoundRect(panelRect, radius, radius, panelPaint)

        textPaint.textSize = DpUtils.dp(14).toFloat()
        textPaint.textAlign = Paint.Align.CENTER

        for (i in labels.indices) {
            val rowTop = panelRect.top + i * rowHeight
            val rowBottom = rowTop + rowHeight
            val cy = (rowTop + rowBottom) / 2f
            val baseline = cy -
                (textPaint.descent() + textPaint.ascent()) / 2f

            // 高亮：按压预览行优先，否则当前选中项
            val active = if (rowPreview >= 0) {
                rowPreview == i
            } else {
                selectedIndex == i
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
        // 面板外：关闭面板，但不消费该 DOWN——交给下层继续处理。
        open = false
        rowPreview = -1
        gestureConsumed = false
        parent.invalidate()
        return false
    }

    private fun onUp(x: Float, y: Float): Boolean {
        if (!open) {
            if (btnPressed) {
                // 按钮按下后抬手在按钮上 -> 通知宿主取数展开面板，
                // 面板开合由宿主 setOpen 控制（不在此自动展开）。
                btnPressed = false
                if (hitButton(x, y)) clickListener?.invoke()
                parent.invalidate()
                return true
            }
            if (gestureConsumed) {
                gestureConsumed = false
                return true
            }
            return false
        }
        // 面板打开中：DOWN 已落在面板内 -> UP 仍在同行则选中
        if (rowPreview >= 0) {
            val row = rowAt(y)
            if (row == rowPreview) {
                selectedIndex = row
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

    /** 由 y 坐标映射到行号，越界夹取。 */
    private fun rowAt(y: Float): Int {
        var idx = ((y - panelRect.top) / rowHeight).toInt()
        if (idx < 0) idx = 0
        if (idx >= labels.size) idx = labels.size - 1
        return idx
    }
}
