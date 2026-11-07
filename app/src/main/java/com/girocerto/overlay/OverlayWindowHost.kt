package com.girocerto.overlay

import android.content.Context
import android.graphics.PixelFormat
import android.os.Build
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager

/** Owns the Android window operations used by the overlay service. */
internal class OverlayWindowHost(context: Context) {
    private val appContext = context.applicationContext
    private val windows = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    fun attach(view: View, params: WindowManager.LayoutParams) = windows.addView(view, params)

    fun update(view: View, params: WindowManager.LayoutParams) = windows.updateViewLayout(view, params)

    fun remove(view: View) = windows.removeView(view)

    fun navigationBarInset(fallback: Int): Int =
        if (Build.VERSION.SDK_INT >= 30) {
            windows.currentWindowMetrics.windowInsets
                .getInsetsIgnoringVisibility(WindowInsets.Type.navigationBars()).bottom
        } else fallback

    fun topSystemInset(fallback: Int): Int =
        if (Build.VERSION.SDK_INT >= 30) {
            windows.currentWindowMetrics.windowInsets.getInsetsIgnoringVisibility(
                WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()
            ).top
        } else fallback

    fun layoutParams(
        width: Int,
        height: Int,
        x: Int,
        y: Int,
        minimumWidth: Int,
        bottomSafetyMargin: Int
    ): WindowManager.LayoutParams {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val metrics = appContext.resources.displayMetrics
        return WindowManager.LayoutParams(
            width,
            height,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x.coerceIn(0, (metrics.widthPixels - width.coerceAtLeast(minimumWidth)).coerceAtLeast(0))
            this.y = y.coerceIn(0, (metrics.heightPixels - bottomSafetyMargin).coerceAtLeast(0))
        }
    }
}
