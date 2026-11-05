package com.example.comexamplet9keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.min

class IconView @JvmOverloads constructor(
    context: Context,
    private var kind: Int = 0,
    private var iconColor: Int = Color.WHITE,
    private var strokeWidthPx: Float = 4f
) : View(context) {

    companion object {
        const val SHIFT_OFF = 0
        const val SHIFT_ON = 1
        const val SHIFT_CAPS = 2
        const val ENTER = 3
        const val MIC = 4
        const val UP = 5
        const val DOWN = 6
        const val LEFT = 7
        const val RIGHT = 8
        const val SPACE = 9
        const val CURSOR = 10
    }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val path = Path()
    private val tempRect = RectF()

    fun setLook(kind: Int, color: Int) {
        this.kind = kind
        this.iconColor = color
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        strokePaint.color = iconColor
        strokePaint.strokeWidth = strokeWidthPx
        fillPaint.color = iconColor

        val cx = width / 2f
        val cy = height / 2f
        val size =
            min(width - paddingLeft - paddingRight, height - paddingTop - paddingBottom) * 0.5f
        if (size <= 0f) return

        when (kind) {
            SHIFT_OFF -> {
                path.reset()
                path.moveTo(cx, cy - size * 0.5f)
                path.lineTo(cx + size * 0.35f, cy + size * 0.05f)
                path.lineTo(cx + size * 0.18f, cy + size * 0.05f)
                path.lineTo(cx + size * 0.18f, cy + size * 0.45f)
                path.lineTo(cx - size * 0.18f, cy + size * 0.45f)
                path.lineTo(cx - size * 0.18f, cy + size * 0.05f)
                path.lineTo(cx - size * 0.35f, cy + size * 0.05f)
                path.close()
                canvas.drawPath(path, strokePaint)
            }

            SHIFT_ON -> {
                path.reset()
                path.moveTo(cx, cy - size * 0.5f)
                path.lineTo(cx + size * 0.35f, cy + size * 0.05f)
                path.lineTo(cx + size * 0.18f, cy + size * 0.05f)
                path.lineTo(cx + size * 0.18f, cy + size * 0.45f)
                path.lineTo(cx - size * 0.18f, cy + size * 0.45f)
                path.lineTo(cx - size * 0.18f, cy + size * 0.05f)
                path.lineTo(cx - size * 0.35f, cy + size * 0.05f)
                path.close()
                canvas.drawPath(path, fillPaint)
            }

            SHIFT_CAPS -> {
                path.reset()
                path.moveTo(cx, cy - size * 0.5f)
                path.lineTo(cx + size * 0.35f, cy + size * 0.05f)
                path.lineTo(cx + size * 0.18f, cy + size * 0.05f)
                path.lineTo(cx + size * 0.18f, cy + size * 0.35f)
                path.lineTo(cx - size * 0.18f, cy + size * 0.35f)
                path.lineTo(cx - size * 0.18f, cy + size * 0.05f)
                path.lineTo(cx - size * 0.35f, cy + size * 0.05f)
                path.close()
                canvas.drawPath(path, fillPaint)
                canvas.drawLine(
                    cx - size * 0.35f,
                    cy + size * 0.5f,
                    cx + size * 0.35f,
                    cy + size * 0.5f,
                    strokePaint
                )
            }

            ENTER -> {
                path.reset()
                path.moveTo(cx + size * 0.35f, cy - size * 0.35f)
                path.lineTo(cx + size * 0.35f, cy + size * 0.1f)
                path.lineTo(cx - size * 0.3f, cy + size * 0.1f)
                canvas.drawPath(path, strokePaint)
                path.reset()
                path.moveTo(cx - size * 0.1f, cy - size * 0.1f)
                path.lineTo(cx - size * 0.3f, cy + size * 0.1f)
                path.lineTo(cx - size * 0.1f, cy + size * 0.3f)
                canvas.drawPath(path, strokePaint)
            }

            MIC -> {
                tempRect.set(cx - size * 0.18f, cy - size * 0.45f, cx + size * 0.18f, cy + size * 0.1f)
                canvas.drawRoundRect(tempRect, size * 0.18f, size * 0.18f, strokePaint)
                tempRect.set(
                    cx - size * 0.32f,
                    cy - size * 0.15f,
                    cx + size * 0.32f,
                    cy + size * 0.25f
                )
                canvas.drawArc(tempRect, 0f, 180f, false, strokePaint)
                canvas.drawLine(cx, cy + size * 0.25f, cx, cy + size * 0.45f, strokePaint)
                canvas.drawLine(
                    cx - size * 0.2f,
                    cy + size * 0.45f,
                    cx + size * 0.2f,
                    cy + size * 0.45f,
                    strokePaint
                )
            }

            UP -> {
                path.reset()
                path.moveTo(cx - size * 0.35f, cy + size * 0.2f)
                path.lineTo(cx, cy - size * 0.2f)
                path.lineTo(cx + size * 0.35f, cy + size * 0.2f)
                canvas.drawPath(path, strokePaint)
            }

            DOWN -> {
                path.reset()
                path.moveTo(cx - size * 0.35f, cy - size * 0.2f)
                path.lineTo(cx, cy + size * 0.2f)
                path.lineTo(cx + size * 0.35f, cy - size * 0.2f)
                canvas.drawPath(path, strokePaint)
            }

            LEFT -> {
                path.reset()
                path.moveTo(cx + size * 0.2f, cy - size * 0.35f)
                path.lineTo(cx - size * 0.2f, cy)
                path.lineTo(cx + size * 0.2f, cy + size * 0.35f)
                canvas.drawPath(path, strokePaint)
            }

            RIGHT -> {
                path.reset()
                path.moveTo(cx - size * 0.2f, cy - size * 0.35f)
                path.lineTo(cx + size * 0.2f, cy)
                path.lineTo(cx - size * 0.2f, cy + size * 0.35f)
                canvas.drawPath(path, strokePaint)
            }

            SPACE -> {
                path.reset()
                path.moveTo(cx - size * 0.4f, cy - size * 0.15f)
                path.lineTo(cx - size * 0.4f, cy + size * 0.15f)
                path.lineTo(cx + size * 0.4f, cy + size * 0.15f)
                path.lineTo(cx + size * 0.4f, cy - size * 0.15f)
                canvas.drawPath(path, strokePaint)
            }

            CURSOR -> {
                path.reset()
                path.moveTo(cx - size * 0.15f, cy - size * 0.15f)
                path.lineTo(cx, cy - size * 0.35f)
                path.lineTo(cx + size * 0.15f, cy - size * 0.15f)
                path.moveTo(cx - size * 0.15f, cy + size * 0.15f)
                path.lineTo(cx, cy + size * 0.35f)
                path.lineTo(cx + size * 0.15f, cy + size * 0.15f)
                path.moveTo(cx - size * 0.15f, cy - size * 0.15f)
                path.lineTo(cx - size * 0.35f, cy)
                path.lineTo(cx - size * 0.15f, cy + size * 0.15f)
                path.moveTo(cx + size * 0.15f, cy - size * 0.15f)
                path.lineTo(cx + size * 0.35f, cy)
                path.lineTo(cx + size * 0.15f, cy + size * 0.15f)
                canvas.drawPath(path, strokePaint)
            }
        }
    }
}
