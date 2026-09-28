package org.fossify.camera.models

enum class TimerMode(val millisInFuture: Long) {
    OFF(0),
    TIMER_3(3000),
    TIMER_5(5000),
    TIMER_10(10000)
}
