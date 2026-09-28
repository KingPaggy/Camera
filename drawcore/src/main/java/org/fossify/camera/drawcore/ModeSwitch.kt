package org.fossify.camera.drawcore

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/**
 * 「照片 | 视频」吸附式切换（非 View 自绘助手，参照 Switch 轨道/拇指范式）。
 *
 * 背景：半透明黑圆角胶囊；选中档：半透明白圆角滑块。滑块位置由
 * modeAnim 在左/右两段之间平滑吸附（docs/06 §4.2：thumbX =
 * segmentIndex * segmentWidth，AnimatedFloat 取整吸附）。
 * 两段文字 alpha 按 modeAnim 进度在「纯白/半透白」间插值。
 *
 * 交互：DOWN 命中左/右段即切档 + 回调（点击即可，无需拖拽）。
 */
class ModeSwitch(private val parent: View) {

    private val rect = RectF()
    private var photoMode = true
    private var listener: ((Boolean) -> Unit)? = null

    /** 0=照片(左) 1=视频(右)。 */
    private val modeAnim = AnimatedFloat(
        parent, 220L, CubicBezierInterpolator.EASE_OUT_QUINT)

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

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
        // set() 必须在 draw/onDraw 内调用（docs/06 §2.3）
        val p = modeAnim.set(if (photoMode) 0f else 1f)

        // 1) 背景胶囊：半透明黑
        bgPaint.color = Color.argb(0x55, 0, 0, 0)
        canvas.drawRoundRect(rect, rect.height() / 2f,
            rect.height() / 2f, bgPaint)

        // 2) 选中滑块：半透明白圆角矩形，在两段间滑动
        val pad = DpUtils.dp(2).toFloat()
        val thumbW = (rect.width() - 2 * pad) / 2f
        val thumbLeft = rect.left + pad + p * thumbW
        val thumb = RectF(thumbLeft, rect.top + pad,
            thumbLeft + thumbW, rect.bottom - pad)
        thumbPaint.color = Color.argb(0x99, 255, 255, 255)
        canvas.drawRoundRect(thumb, thumb.height() / 2f,
            thumb.height() / 2f, thumbPaint)

        // 3) 两段文字：各居一段中心，alpha 随选中态插值
        textPaint.textSize = DpUtils.dp(13).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
        val baseline = rect.centerY() -
            (textPaint.descent() + textPaint.ascent()) / 2f
        val photoCx = rect.left + rect.width() / 4f
        val videoCx = rect.right - rect.width() / 4f

        textPaint.color = Color.argb(
            (255 - 115 * p).toInt().coerceIn(0, 255), 255, 255, 255)
        canvas.drawText("照片", photoCx, baseline, textPaint)

        textPaint.color = Color.argb(
            (140 + 115 * p).toInt().coerceIn(0, 255), 255, 255, 255)
        canvas.drawText("视频", videoCx, baseline, textPaint)
    }

    fun checkTouchEvent(event: MotionEvent): Boolean {
        // 点击式切换：仅在 DOWN 命中时消费并切档
        if (event.actionMasked != MotionEvent.ACTION_DOWN) return false
        val x = event.x
        val y = event.y
        if (!rect.contains(x, y)) return false
        val wantPhoto = x < rect.centerX()
        if (wantPhoto != photoMode) {
            photoMode = wantPhoto
            listener?.invoke(photoMode)
        }
        parent.invalidate()
        return true
    }
}
