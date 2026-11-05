package com.example.comexamplet9keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import androidx.core.graphics.toColorInt
import kotlin.math.min

class JoystickView(context: Context) : View(context) {
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#2E3236".toColorInt()
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * context.resources.displayMetrics.density
        color = "#5FD3C5".toColorInt()
    }
    private val knobPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = "#5FD3C5".toColorInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(cx, cy) - strokePaint.strokeWidth
        if (radius <= 0f) return

        canvas.drawCircle(cx, cy, radius, bgPaint)
        canvas.drawCircle(cx, cy, radius, strokePaint)
        canvas.drawCircle(cx, cy, radius * 0.35f, knobPaint)
    }
}
