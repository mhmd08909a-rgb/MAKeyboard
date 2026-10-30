package com.ma.keyboard

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Crisp line icons drawn in code (no image assets, scale to any toolbar height). */
class IconView(context: Context, var icon: String) : View(context) {
    var tint: Int = 0xFF5A4634.toInt()
        set(v) { field = v; invalidate() }
    var accent: Int = 0xFFB5473A.toInt()
        set(v) { field = v; invalidate() }
    var active: Boolean = false
        set(v) { field = v; invalidate() }
    var dot: Boolean = false
        set(v) { field = v; invalidate() }
    /** Icon size as a fraction of the view's shorter side (version 1: fixed 0.62). */
    var sizeFrac: Float = 0.62f
        set(v) { field = v; invalidate() }
    /** Line width in the 24-unit icon grid (version 1: fixed 1.7, too thin on most screens). */
    var strokeW: Float = 1.7f
        set(v) { field = v; invalidate() }
    /** Optional round plate behind the icon (0 = none) so it stands out from the toolbar. */
    var plate: Int = 0
        set(v) { field = v; invalidate() }

    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val path = Path()
    private val r = RectF()

    init {
        isClickable = true
        contentDescription = Cfg.toolTitle(icon)
    }

    override fun onDraw(canvas: Canvas) {
        val size = min(width, height) * sizeFrac
        val s = size / 24f
        val ox = (width - size) / 2f
        val oy = (height - size) / 2f
        if (plate != 0 && !active) {
            fill.color = plate
            canvas.drawCircle(width / 2f, height / 2f, min(width, height) * 0.46f, fill)
        }
        if (active) {
            fill.color = (accent and 0x00FFFFFF) or 0x55000000
            canvas.drawCircle(width / 2f, height / 2f, min(width, height) * 0.44f, fill)
        }
        canvas.save()
        canvas.translate(ox, oy)
        canvas.scale(s, s)
        stroke.strokeWidth = strokeW
        stroke.color = if (active) accent else tint
        fill.color = stroke.color
        draw(canvas, icon)
        canvas.restore()
        if (dot) {
            val cx = ox + 20f * s; val cy = oy + 4f * s
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawCircle(cx, cy, 3.6f * s, fill)
            fill.color = 0xFFE53935.toInt()
            canvas.drawCircle(cx, cy, 2.7f * s, fill)
        }
    }

    private fun line(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) = c.drawLine(x1, y1, x2, y2, stroke)
    private fun rr(c: Canvas, l: Float, t: Float, rt: Float, b: Float, rad: Float) {
        r.set(l, t, rt, b); c.drawRoundRect(r, rad, rad, stroke)
    }

    private fun draw(c: Canvas, id: String) {
        when (id) {
            "reminder" -> {
                path.reset()
                path.moveTo(6f, 16.5f); path.lineTo(6f, 11f)
                path.cubicTo(6f, 7.5f, 8.5f, 5f, 12f, 5f); path.cubicTo(15.5f, 5f, 18f, 7.5f, 18f, 11f)
                path.lineTo(18f, 16.5f); path.lineTo(19.5f, 18f); path.lineTo(4.5f, 18f); path.close()
                c.drawPath(path, stroke)
                line(c, 12f, 3f, 12f, 5f)
                c.drawCircle(12f, 20.3f, 1.5f, fill)
            }
            "clipboard" -> {
                rr(c, 5.5f, 5f, 18.5f, 21f, 2f); rr(c, 9f, 3f, 15f, 7f, 1.2f)
                line(c, 9f, 11.5f, 15f, 11.5f); line(c, 9f, 15.5f, 13.5f, 15.5f)
            }
            "emoji" -> {
                c.drawCircle(12f, 12f, 8.5f, stroke)
                c.drawCircle(9.2f, 10f, 1.1f, fill); c.drawCircle(14.8f, 10f, 1.1f, fill)
                r.set(7.8f, 10.5f, 16.2f, 16.6f); c.drawArc(r, 20f, 140f, false, stroke)
            }
            "calc" -> {
                rr(c, 5.5f, 3f, 18.5f, 21f, 2f); rr(c, 8f, 5.5f, 16f, 9.5f, 0.8f)
                for (y in listOf(12.8f, 15.6f, 18.4f)) for (x in listOf(9f, 12f, 15f)) c.drawCircle(x, y, 0.95f, fill)
            }
            "notes" -> {
                rr(c, 5f, 3.5f, 19f, 20.5f, 2f)
                line(c, 8f, 8.5f, 16f, 8.5f); line(c, 8f, 12f, 16f, 12f); line(c, 8f, 15.5f, 13f, 15.5f)
            }
            "edit" -> {
                line(c, 12f, 5f, 12f, 19f); line(c, 9.8f, 5f, 14.2f, 5f); line(c, 9.8f, 19f, 14.2f, 19f)
                path.reset(); path.moveTo(6.5f, 9f); path.lineTo(3.5f, 12f); path.lineTo(6.5f, 15f); c.drawPath(path, stroke)
                path.reset(); path.moveTo(17.5f, 9f); path.lineTo(20.5f, 12f); path.lineTo(17.5f, 15f); c.drawPath(path, stroke)
            }
            "writing" -> {
                path.reset(); path.moveTo(12f, 2.5f); path.lineTo(17.5f, 10f); path.lineTo(12f, 21.5f)
                path.lineTo(6.5f, 10f); path.close(); c.drawPath(path, stroke)
                line(c, 12f, 11.5f, 12f, 17f); c.drawCircle(12f, 10f, 1.3f, fill)
            }
            "snippets" -> {
                path.reset(); path.moveTo(3.5f, 12f); path.lineTo(10.5f, 5f); path.lineTo(20f, 5f)
                path.lineTo(20f, 14.5f); path.lineTo(13f, 21.5f); path.close(); c.drawPath(path, stroke)
                c.drawCircle(16f, 9f, 1.4f, fill)
            }
            "codes" -> {
                c.drawCircle(8f, 12f, 4f, stroke); line(c, 12f, 12f, 21f, 12f)
                line(c, 17.5f, 12f, 17.5f, 15.5f); line(c, 20.5f, 12f, 20.5f, 15f)
            }
            "private" -> {
                r.set(7.5f, 4f, 16.5f, 14f); c.drawArc(r, 180f, 180f, false, stroke)
                line(c, 7.5f, 9f, 7.5f, 11f); line(c, 16.5f, 9f, 16.5f, 11f)
                r.set(5.5f, 11f, 18.5f, 20.5f)
                if (active) c.drawRoundRect(r, 2f, 2f, fill) else c.drawRoundRect(r, 2f, 2f, stroke)
            }
            "onehand" -> {
                rr(c, 3f, 7f, 15f, 18f, 2f)
                c.drawCircle(6f, 10.5f, 0.8f, fill); c.drawCircle(9f, 10.5f, 0.8f, fill); c.drawCircle(12f, 10.5f, 0.8f, fill)
                line(c, 6f, 14.5f, 12f, 14.5f)
                line(c, 17.5f, 12.5f, 21.5f, 12.5f)
                path.reset(); path.moveTo(19.5f, 10.5f); path.lineTo(21.5f, 12.5f); path.lineTo(19.5f, 14.5f); c.drawPath(path, stroke)
            }
            "settings" -> {
                c.drawCircle(12f, 12f, 3f, stroke); c.drawCircle(12f, 12f, 6.3f, stroke)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    line(c, 12f + 6.3f * cos(a).toFloat(), 12f + 6.3f * sin(a).toFloat(),
                        12f + 8.8f * cos(a).toFloat(), 12f + 8.8f * sin(a).toFloat())
                }
            }
            "keyboard" -> {
                rr(c, 2.5f, 6f, 21.5f, 18f, 2f)
                for (x in listOf(6f, 9.5f, 13f, 16.5f)) c.drawCircle(x, 10f, 0.85f, fill)
                line(c, 7.5f, 14.5f, 16.5f, 14.5f)
            }
            "search" -> { c.drawCircle(10.5f, 10.5f, 5.8f, stroke); line(c, 14.8f, 14.8f, 20f, 20f) }
            "pin" -> {
                path.reset(); path.moveTo(9f, 3.5f); path.lineTo(15f, 3.5f); path.lineTo(14f, 9f)
                path.lineTo(17f, 13f); path.lineTo(7f, 13f); path.lineTo(10f, 9f); path.close()
                if (active) c.drawPath(path, fill) else c.drawPath(path, stroke)
                line(c, 12f, 13f, 12f, 20.5f)
            }
            "trash" -> {
                line(c, 4.5f, 7f, 19.5f, 7f); line(c, 9.5f, 4f, 14.5f, 4f)
                path.reset(); path.moveTo(6.5f, 7f); path.lineTo(7.5f, 20.5f); path.lineTo(16.5f, 20.5f)
                path.lineTo(17.5f, 7f); c.drawPath(path, stroke)
                line(c, 10.5f, 10.5f, 10.5f, 17f); line(c, 13.5f, 10.5f, 13.5f, 17f)
            }
            "close" -> { line(c, 6f, 6f, 18f, 18f); line(c, 18f, 6f, 6f, 18f) }
            "plus" -> { line(c, 12f, 5f, 12f, 19f); line(c, 5f, 12f, 19f, 12f) }
            "check" -> { path.reset(); path.moveTo(5f, 12.5f); path.lineTo(10f, 17.5f); path.lineTo(19f, 7f); c.drawPath(path, stroke) }
            "snooze" -> {
                c.drawCircle(12f, 13f, 7.5f, stroke)
                path.reset(); path.moveTo(12f, 9f); path.lineTo(12f, 13f); path.lineTo(15f, 15f); c.drawPath(path, stroke)
                line(c, 4f, 5.5f, 7f, 3f); line(c, 20f, 5.5f, 17f, 3f)
            }
            "open" -> {
                rr(c, 4f, 6f, 18f, 20f, 2f)
                line(c, 12f, 12f, 20f, 4f); line(c, 15f, 4f, 20f, 4f); line(c, 20f, 4f, 20f, 9f)
            }
            "folder" -> {
                path.reset(); path.moveTo(3.5f, 7f); path.lineTo(9.5f, 7f); path.lineTo(11.5f, 9f); path.lineTo(20.5f, 9f)
                path.lineTo(20.5f, 19f); path.lineTo(3.5f, 19f); path.close(); c.drawPath(path, stroke)
            }
            "swap" -> {
                line(c, 4f, 9f, 19f, 9f); path.reset(); path.moveTo(16f, 6f); path.lineTo(19f, 9f); path.lineTo(16f, 12f); c.drawPath(path, stroke)
                line(c, 20f, 15f, 5f, 15f); path.reset(); path.moveTo(8f, 12f); path.lineTo(5f, 15f); path.lineTo(8f, 18f); c.drawPath(path, stroke)
            }
            "expand" -> {
                path.reset(); path.moveTo(4f, 9f); path.lineTo(4f, 4f); path.lineTo(9f, 4f); c.drawPath(path, stroke)
                path.reset(); path.moveTo(20f, 15f); path.lineTo(20f, 20f); path.lineTo(15f, 20f); c.drawPath(path, stroke)
                line(c, 4f, 4f, 10f, 10f); line(c, 20f, 20f, 14f, 14f)
            }
            "backspace" -> {
                path.reset(); path.moveTo(8f, 5.5f); path.lineTo(20.5f, 5.5f); path.lineTo(20.5f, 18.5f); path.lineTo(8f, 18.5f)
                path.lineTo(2.5f, 12f); path.close(); c.drawPath(path, stroke)
                line(c, 11f, 9f, 17f, 15f); line(c, 17f, 9f, 11f, 15f)
            }
            "star" -> {
                path.reset()
                for (i in 0 until 10) {
                    val rad = if (i % 2 == 0) 9f else 4f
                    val a = Math.toRadians(-90.0 + i * 36.0)
                    val x = 12f + rad * cos(a).toFloat(); val y = 12.5f + rad * sin(a).toFloat()
                    if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                path.close()
                if (active) c.drawPath(path, fill) else c.drawPath(path, stroke)
            }
            else -> c.drawCircle(12f, 12f, 3f, fill)
        }
    }
}
