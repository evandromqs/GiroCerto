package com.girocerto.overlay

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import kotlin.math.abs

/** Builds the floating bubble and owns its pointer/drag interaction. */
internal class BubbleViewFactory(
    private val context: Context,
    private val dp: (Int) -> Int,
    private val sizePx: () -> Int,
    private val opacity: () -> Float,
    private val callbacks: Callbacks
) {
    interface Callbacks {
        fun onClick()
        fun layoutParams(): WindowManager.LayoutParams?
        fun onDragStarted()
        fun onDrag(view: View, layout: WindowManager.LayoutParams)
        fun onDrop(view: View, layout: WindowManager.LayoutParams, initialX: Int, initialY: Int)
        fun onDragCancelled(view: View, layout: WindowManager.LayoutParams, initialX: Int, initialY: Int)
    }

    fun create(): View {
        val bubble = FrameLayout(context).apply {
            contentDescription = "Giro Certo. Toque para abrir; arraste até a lixeira para ocultar."
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#F2060B18"))
                setStroke(dp(2), Color.parseColor("#B9FC01"))
            }
            elevation = dp(12).toFloat()
            alpha = opacity()
            setOnClickListener { callbacks.onClick() }
        }
        bubble.addView(ImageView(context).apply {
            setImageResource(com.girocerto.app.R.drawable.ic_girocerto_brand)
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            alpha = .98f
        }, FrameLayout.LayoutParams((sizePx() * .80f).toInt(), (sizePx() * .80f).toInt(), Gravity.CENTER))
        bubble.setOnTouchListener(DragInteraction(context, callbacks, opacity))
        return bubble
    }

    private class DragInteraction(
        context: Context,
        private val callbacks: Callbacks,
        private val opacity: () -> Float,
    ) : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var touchX = 0f
        private var touchY = 0f
        private var dragging = false
        private val slop = ViewConfiguration.get(context).scaledTouchSlop

        override fun onTouch(view: View, event: MotionEvent): Boolean {
            val layout = callbacks.layoutParams() ?: return false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = layout.x; initialY = layout.y
                    touchX = event.rawX; touchY = event.rawY; dragging = false
                    view.animate().scaleX(.94f).scaleY(.94f).alpha((opacity() - .08f).coerceAtLeast(.4f)).setDuration(80).start()
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - touchX
                    val dy = event.rawY - touchY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                        dragging = true
                        callbacks.onDragStarted()
                    }
                    if (dragging) {
                        layout.x = initialX + dx.toInt()
                        layout.y = initialY + dy.toInt()
                        callbacks.onDrag(view, layout)
                    }
                    return true
                }
                MotionEvent.ACTION_UP -> {
                    view.animate().scaleX(1f).scaleY(1f).alpha(opacity()).setDuration(120).start()
                    if (dragging) callbacks.onDrop(view, layout, initialX, initialY) else view.performClick()
                    return true
                }
                MotionEvent.ACTION_CANCEL -> {
                    view.animate().scaleX(1f).scaleY(1f).alpha(opacity()).setDuration(120).start()
                    callbacks.onDragCancelled(view, layout, initialX, initialY)
                    return true
                }
            }
            return false
        }
    }
}
