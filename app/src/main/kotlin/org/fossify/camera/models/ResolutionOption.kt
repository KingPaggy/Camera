package org.fossify.camera.models

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes

data class ResolutionOption(
    /** 自绘选项面板展示的名称（拍照为画幅比例如 "4:3"，视频为 "1920x1080"）。 */
    val label: String,
    @IdRes val buttonViewId: Int,
    @DrawableRes val imageDrawableResId: Int,
    /** 该选项在宿主全量分辨率列表中的下标；-1 表示行号即列表下标（无需映射）。 */
    val fullListIndex: Int = -1,
)
