package org.fossify.camera.drawcore

import android.graphics.Bitmap

/**
 * 自绘 Overlay 的不可变宿主状态容器。
 *
 * MainActivity 直接读写字段，改完调 DrawView.invalidate() 触发重绘；
 * DrawView.onDraw 每帧从这里把状态同步进三个自绘组件。
 *
 * 字段名固定，MainActivity 按名访问，不要改名。
 */
class UiState {
    /** true=照片模式，false=录像 */
    var isPhoto = true

    /** 录像中（快门显示红点） */
    var isRecording = false

    /** 当前曝光档位 */
    var exposureValue = 0

    /** 相机曝光范围；null=不支持（滑块禁用） */
    var exposureRange: IntRange? = null

    /** 设备是否有前后摄（驱动 FlipCameraButton 显隐） */
    var hasFrontAndBack = false

    /** 闪光模式：FLASH_OFF=0/ON=1/AUTO=2/ALWAYS_ON=3（驱动 FlashButton 图标） */
    var flashMode = 0

    /** 最近媒体缩略图；null=无（驱动缩略图显隐） */
    var lastMediaBitmap: Bitmap? = null

    /** 录像已录时长（纳秒）；≤0 时隐藏计时文本（驱动 RecordingTimer） */
    var recordingDuration = 0L

    /** TimerMode.ordinal：0=关 1=3s 2=5s 3=10s（驱动 TimerPanel 选中态） */
    var timerMode = 0
}
