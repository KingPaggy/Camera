package org.fossify.camera.models

import androidx.annotation.DrawableRes
import androidx.annotation.IdRes

data class ResolutionOption(
    /** 自绘选项面板展示的分辨率名称（如 "1920x1080"）。 */
    val label: String,
    @IdRes val buttonViewId: Int,
    @DrawableRes val imageDrawableResId: Int,
)
