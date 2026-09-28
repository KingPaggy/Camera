package org.fossify.camera.drawcore

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.view.MotionEvent
import android.view.View

/**
 * 最近照片缩略图（非 View 自绘助手，参照 ShutterButton / CanvasButton 范式）。
 *
 * 视觉：bounds 内切圆内绘制圆形裁剪的 Bitmap 缩略图，外圈 2dp 白描边；
 * 按下时叠加一层半透明白色覆盖层作为按压反馈。setBitmap(null) 时组件
 * 完全隐藏——draw 不绘制任何内容，checkTouchEvent 恒返回 false。
 *
 * 裁剪方案：BitmapShader(CLAMP) + drawCircle。用 Matrix 把 bitmap 按
 * center-crop(cover) 缩放到内切圆直径，圆心对齐 bounds 中心；drawCircle
 * 天然只画圆形区域，无需 save/clipPath，也不会溢出方界。
 *
 * 本组件只负责「绘制 + 点击回调」，MediaStore 查询与 Bitmap 加载均在
 * app 侧完成，外部通过 setBitmap 喂入现成 Bitmap。
 */
class LastMediaThumbnail(private val parent: View) {

    private var left = 0f
    private var top = 0f
    private var right = 0f
    private var bottom = 0f

    private var bitmap: Bitmap? = null
    private var pressed = false
    private var listener: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shaderMatrix = Matrix()

    /** 传入正方形区域（right-left == bottom-top），圆形缩略图内切。 */
    fun setBounds(left: Float, top: Float, right: Float,
                  bottom: Float) {
        this.left = left
        this.top = top
        this.right = right
        this.bottom = bottom
        parent.invalidate()
    }

    /** null 表示无数据：组件隐藏，不绘制、不消费触摸事件。
     *  等值守卫：onDraw 每帧同步同一 bitmap 时直接返回，避免把按压态
     *  pressed 重置导致 UP 永远触发不了点击，也避免无谓的重复 invalidate。 */
    fun setBitmap(bitmap: Bitmap?) {
        if (this.bitmap === bitmap) return
        this.bitmap = bitmap
        pressed = false
        parent.invalidate()
    }

    fun setOnLastMediaClickListener(listener: () -> Unit) {
        this.listener = listener
    }

    fun draw(canvas: Canvas) {
        val b = bitmap ?: return
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val radius = minOf(right - left, bottom - top) / 2f
        if (radius <= 0f) return

        // 1) 圆形裁剪的缩略图：BitmapShader + center-crop 缩放对齐
        val stroke = DpUtils.dp(2).toFloat()
        val innerR = radius - stroke / 2f
        val shader = BitmapShader(b, Shader.TileMode.CLAMP,
            Shader.TileMode.CLAMP)
        val scale = (innerR * 2f) / minOf(b.width, b.height)
        shaderMatrix.setScale(scale, scale)
        shaderMatrix.postTranslate(
            cx - b.width * scale / 2f,
            cy - b.height * scale / 2f)
        shader.setLocalMatrix(shaderMatrix)
        paint.shader = shader
        paint.style = Paint.Style.FILL
        canvas.drawCircle(cx, cy, innerR, paint)
        paint.shader = null

        // 2) 外圈 2dp 白描边
        paint.style = Paint.Style.STROKE
        paint.color = Color.WHITE
        paint.strokeWidth = stroke
        canvas.drawCircle(cx, cy, radius - stroke / 2f, paint)

        // 3) 按压态：半透明白覆盖层，区分按压
        if (pressed) {
            paint.style = Paint.Style.FILL
            paint.color = PRESSED_OVERLAY
            canvas.drawCircle(cx, cy, innerR, paint)
        }
    }

    fun checkTouchEvent(event: MotionEvent): Boolean {
        if (bitmap == null) return false
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
                parent.invalidate()
                if (hit(event.x, event.y)) {
                    listener?.invoke()
                }
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

    /** 命中检测：以 bounds 内切圆为热区。 */
    private fun hit(x: Float, y: Float): Boolean {
        val cx = (left + right) / 2f
        val cy = (top + bottom) / 2f
        val r = minOf(right - left, bottom - top) / 2f
        val dx = x - cx
        val dy = y - cy
        return dx * dx + dy * dy <= r * r
    }

    /** 当前 bounds（供宿主排除系统手势区）。 */
    fun getBounds(): FloatArray = floatArrayOf(left, top, right, bottom)

    private companion object {
        /** 按压覆盖层：约 33% 不透明白。 */
        const val PRESSED_OVERLAY = 0x55FFFFFF
    }
}
