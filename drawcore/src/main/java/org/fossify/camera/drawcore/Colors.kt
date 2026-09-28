package org.fossify.camera.drawcore

import android.graphics.Color

/**
 * 统一配色（ColorOS 风，docs/07 §7.3）。
 *
 * 背景纯黑，主强调色橙，文字/图标白。
 * 所有组件从这里取色，不再各自硬编码。
 */
object Colors {

    /** 预览区背景：纯黑。 */
    val BG: Int = Color.rgb(0, 0, 0)

    /** 主强调色：橙。 */
    val ACCENT: Int = Color.rgb(0xFF, 0x95, 0x00)

    /** 主文字/图标：白。 */
    val TEXT_PRIMARY: Int = Color.WHITE

    /** 次文字/未选中：白 60%。 */
    val TEXT_SECONDARY: Int = Color.argb(0x99, 255, 255, 255)

    /** 快门外环灰。 */
    val SHUTTER_RING: Int = Color.rgb(0x55, 0x55, 0x55)

    /** 圆钮底：黑 40%。 */
    val BUTTON_BG: Int = Color.argb(0x66, 0, 0, 0)

    /** 录像红。 */
    val RECORD: Int = Color.rgb(0xFF, 0x3B, 0x30)
}
