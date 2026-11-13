package com.pindou.studio.util

import android.app.Activity
import android.os.Build
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/** 从 Context 里找到宿主 Activity。 */
fun android.content.Context.findActivity(): Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/** 开屏期间隐藏系统栏（参考 splash_screen 的沉浸式做法）。 */
fun Activity.hideSystemBars() {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
    }
}

/** 恢复系统栏。 */
fun Activity.showSystemBars() {
    val controller = WindowCompat.getInsetsController(window, window.decorView)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        controller.show(WindowInsetsCompat.Type.systemBars())
    } else {
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
    }
}
