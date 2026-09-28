package org.fossify.camera.interfaces

interface MyPreview {

    fun isInPhotoMode(): Boolean

    fun setFlashlightState(state: Int)

    fun toggleFrontBackCamera()

    fun handleFlashlightClick()

    fun tryTakePicture()

    fun toggleRecording()

    fun initPhotoMode()

    fun initVideoMode()

    fun showChangeResolution()

    fun getExposureRange(): IntRange?

    fun setExposure(index: Int)

    /** 当前相机变焦范围（min..max）；null=不支持/未就绪。 */
    fun getZoomRange(): ClosedFloatingPointRange<Float>?

    /** 按倍率设置变焦（内部 clamp 到 min..max）。 */
    fun setZoomRatio(ratio: Float)
}
