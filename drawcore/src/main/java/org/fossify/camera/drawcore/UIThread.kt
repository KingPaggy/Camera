package org.fossify.camera.drawcore

import android.os.Handler
import android.os.Looper

/**
 * 主线程任务封装。
 *
 * 语义对齐裁剪自 Telegram `AndroidUtilities.runOnUIThread` / `cancelRunOnUIThread`：
 *  - post(r)                : 立即在主线程执行
 *  - post(r, delayMillis)   : delayMillis <= 0 时等价于 post(r)，否则延迟执行
 *  - cancel(r)              : 取消尚未执行的回调（removeCallbacks）
 *
 * 内部固定持有主线程 Looper 的 Handler，不依赖 ApplicationLoader。
 */
object UIThread {

    private val handler = Handler(Looper.getMainLooper())

    @JvmStatic
    fun post(r: Runnable) {
        handler.post(r)
    }

    @JvmStatic
    fun post(r: Runnable, delayMillis: Long) {
        if (delayMillis <= 0L) {
            handler.post(r)
        } else {
            handler.postDelayed(r, delayMillis)
        }
    }

    @JvmStatic
    fun cancel(r: Runnable) {
        handler.removeCallbacks(r)
    }
}
