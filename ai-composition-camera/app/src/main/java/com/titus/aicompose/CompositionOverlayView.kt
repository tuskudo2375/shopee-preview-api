package com.titus.aicompose

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs

class CompositionOverlayView @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {
    private var result: CompositionResult? = null
    private var analyzing = false
    private var error: String? = null
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(139, 233, 253); strokeWidth = 5f; style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val faint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0x55FFFFFF; strokeWidth = 2f; style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 36f; textAlign = Paint.Align.CENTER }
    private val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xDDFFFFFF.toInt(); textSize = 27f; textAlign = Paint.Align.CENTER }
    private val pill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xBB111111.toInt(); style = Paint.Style.FILL }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; style = Paint.Style.FILL }

    fun setAnalyzing(value: Boolean) { analyzing = value; if (value) error = null; invalidate() }
    fun setError(message: String?) { error = message; analyzing = false; invalidate() }
    fun setResult(value: CompositionResult) { result = value; analyzing = false; error = null; invalidate() }
    fun clear() { result = null; analyzing = false; error = null; invalidate() }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        result?.let { drawResult(canvas, it) }
        val message = when { analyzing -> "AI đang phân tích cảnh…"; error != null -> error; else -> null }
        if (message != null) drawPill(canvas, message, height * .80f)
    }

    private fun drawResult(canvas: Canvas, r: CompositionResult) {
        val c = r.composition
        val rect = RectF(c.x * width, c.y * height, (c.x + c.width) * width, (c.y + c.height) * height)
        val seg = minOf(rect.width(), rect.height()) * .14f
        val p = Path().apply {
            moveTo(rect.left, rect.top + seg); lineTo(rect.left, rect.top); lineTo(rect.left + seg, rect.top)
            moveTo(rect.right - seg, rect.top); lineTo(rect.right, rect.top); lineTo(rect.right, rect.top + seg)
            moveTo(rect.right, rect.bottom - seg); lineTo(rect.right, rect.bottom); lineTo(rect.right - seg, rect.bottom)
            moveTo(rect.left + seg, rect.bottom); lineTo(rect.left, rect.bottom); lineTo(rect.left, rect.bottom - seg)
        }
        canvas.drawPath(p, line)
        canvas.drawLine(rect.left + rect.width()/3f, rect.top, rect.left + rect.width()/3f, rect.bottom, faint)
        canvas.drawLine(rect.left + rect.width()*2f/3f, rect.top, rect.left + rect.width()*2f/3f, rect.bottom, faint)
        canvas.drawLine(rect.left, rect.top + rect.height()/3f, rect.right, rect.top + rect.height()/3f, faint)
        canvas.drawLine(rect.left, rect.top + rect.height()*2f/3f, rect.right, rect.top + rect.height()*2f/3f, faint)
        canvas.drawCircle(r.subject.x * width, r.subject.y * height, 8f, dot)
        val mx = r.movement.horizontal
        val my = r.movement.vertical
        if (abs(mx) > .025f || abs(my) > .025f) {
            val cx = width / 2f; val cy = height * .18f; val dx = mx * width * .45f; val dy = my * height * .28f
            canvas.drawLine(cx, cy, cx + dx, cy + dy, line); canvas.drawCircle(cx + dx, cy + dy, 8f, dot)
        } else canvas.drawText("✓ Bố cục đẹp", width / 2f, height * .18f, text)
        drawPill(canvas, "${r.instruction}  ·  ${String.format("%.1f", r.recommendedZoom)}×", height * .73f)
    }

    private fun drawPill(canvas: Canvas, message: String?, cy: Float) {
        if (message.isNullOrBlank()) return
        val safe = if (message.length > 70) message.take(67) + "…" else message
        val w = minOf(width * .88f, maxOf(width * .48f, small.measureText(safe) + 64f))
        val rect = RectF((width-w)/2f, cy-45f, (width+w)/2f, cy+45f)
        canvas.drawRoundRect(rect, 28f, 28f, pill)
        canvas.drawText(safe, width/2f, cy+10f, small)
    }
}
