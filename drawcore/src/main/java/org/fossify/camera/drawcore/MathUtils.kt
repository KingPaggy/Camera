package org.fossify.camera.drawcore

/**
 * 数值裁剪工具类。
 *
 * clamp 语义与 `androidx.core.math.MathUtils.clamp` 一致：
 * 返回 [min, max] 区间内的 value（value < min 取 min，value > max 取 max）。
 *
 * 说明：lerp 不依赖系统隐藏 API（该类在 android-36 android.jar 中为 @hide，编译期不可见）。
 * 本模块内 lerp 一律用内联表达式 `a + f * (b - a)`
 * （与 Telegram AndroidUtilities.lerp 实现逐字节等价），不在此封装。
 */
object MathUtils {

    @JvmStatic
    fun clamp(value: Float, min: Float, max: Float): Float =
        Math.max(min, Math.min(value, max))
}
