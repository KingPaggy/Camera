package org.fossify.camera.drawcore

import android.content.res.Resources
import kotlin.math.ceil

/**
 * dp 换算工具类。
 *
 * 语义对齐裁剪自 Telegram `AndroidUtilities.dp(float)`：
 *   dp(value) = (int) Math.ceil(density * value)，且 value == 0 时直接返回 0。
 * density 取自系统资源 DisplayMetrics，与 TG 静态 density 字段等价。
 *
 * 注意：TG 另有 dpr（四舍五入）/ dp2（向下取整）两个变体，本阶段只抽 dp。
 */
object DpUtils {

    private val density: Float
        get() = Resources.getSystem().displayMetrics.density

    @JvmStatic
    fun dp(value: Float): Int {
        if (value == 0f) {
            return 0
        }
        return ceil(density * value).toInt()
    }

    @JvmStatic
    fun dp(value: Int): Int = dp(value.toFloat())
}
