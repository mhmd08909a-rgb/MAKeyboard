package com.ma.keyboard

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import java.io.File

/**
 * Canvas-drawn keyboard with round typewriter keycaps, press animation,
 * long-press alternates, key-repeat delete and space-bar cursor control.
 */
class KeyboardView(context: Context) : View(context) {

    interface Listener {
        fun onPress(k: Key)
        fun onKey(k: Key, out: String)
        fun onLongKey(k: Key): Boolean
        fun onDeleteRepeat(step: Int)
        fun onCursorMove(steps: Int)
    }

    /** Used by the layout editor: keys are picked up with a long press and dropped elsewhere. */
    interface EditListener {
        /** May the key at (fromRow, fromIdx) be dropped in [toRow]? Used to dim forbidden rows. */
        fun canDrop(fromRow: Int, fromIdx: Int, toRow: Int): Boolean
        /** toIdx = insertion index in the target row as drawn before the move. */
        fun onDrop(fromRow: Int, fromIdx: Int, toRow: Int, toIdx: Int)
        fun onPicked(key: Key) {}
    }

    var listener: Listener? = null
    /** "en" = club crests from assets/keyart/en, "fa" = player photos from assets/keyart/fa, null = off. */
    var keyArtDir: String? = null
        set(v) { field = v; invalidate() }
    private val appCtx = context.applicationContext
    private val artCache = HashMap<String, Bitmap?>()
    private fun art(name: String): Bitmap? {
        val dir = keyArtDir ?: return null
        return artCache.getOrPut("$dir/$name") {
            try {
                val custom = File(appCtx.filesDir, "keyart/$dir/$name.png")
                if (custom.exists()) BitmapFactory.decodeFile(custom.path)
                else appCtx.assets.open("keyart/$dir/$name.png").use { BitmapFactory.decodeStream(it) }
            } catch (e: Exception) { null }
        }
    }
    /** Call after the user changes pictures so the new ones show up. */
    fun clearArtCache() { artCache.clear(); invalidate() }
    private fun artFor(k: Key): Bitmap? = when (k.type) {
        KT.CHAR -> if (k.out.length == 1) art(if (keyArtDir == "en") k.out.lowercase() else k.out) else null
        KT.SPACE -> art("space")
        else -> null
    }
    private val artPath = Path()
    private val artSrc = RectF()
    private val artShadow = 0xDD000000.toInt()
    var editListener: EditListener? = null
    /** Layout-editor mode: no text is typed, keys can be moved. */
    var editMode = false
        set(v) { field = v; cancelTouch(); invalidate() }
    /**
     * Upper limit for the whole view height in px (0 = none). The service sets it from the real
     * screen height, so big sizes shrink rows instead of pushing keys off the screen.
     */
    var maxHeightPx = 0
        set(v) { if (field != v) { field = v; requestLayout(); layoutKeys(); invalidate() } }
    private var cfg: KbConfig = Cfg.load(context)
    private var rows: List<List<Key>> = emptyList()
    var shiftState = 0
        set(v) { field = v; invalidate() }
    var enterAction = EditorInfo.IME_ACTION_NONE
        set(v) { field = v; invalidate() }

    private class G(val key: Key, val cell: RectF, val cap: RectF, val row: Int, val col: Int)
    private val geo = ArrayList<G>()
    private var level = FloatArray(0)
    private var pressed = BooleanArray(0)
    private var lastFrame = 0L

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val tmp = RectF()
    private val path = Path()

    private val uiHandler = Handler(Looper.getMainLooper())
    private var active = -1
    private var pointerId = -1
    private var downX = 0f
    private var lastX = 0f
    private var longFired = false
    private var spaceDrag = false
    private var spaceAccum = 0f
    private var repeatStep = 0
    private var repeating = false

    // layout-editor drag state
    private var dragFrom = -1
    private var dragX = 0f
    private var dragY = 0f
    private var dropRow = -1
    private var dropIdx = -1
    private var dropOk = false
    /** Tap-to-select fallback in the editor: first tap selects, second tap places. */
    private var selIdx = -1

    private var popupAlts: List<String> = emptyList()
    private var popupKey = -1
    private var popupSel = -1
    private val popupRect = RectF()
    private var popupCell = 0f

    companion object {
        var elite: Typeface? = null
        var vazir: Typeface? = null
        fun fonts(c: Context) {
            if (elite == null) elite = try { Typeface.createFromAsset(c.assets, "fonts/SpecialElite.ttf") } catch (e: Exception) { Typeface.MONOSPACE }
            if (vazir == null) vazir = try { Typeface.createFromAsset(c.assets, "fonts/Vazirmatn.ttf") } catch (e: Exception) { Typeface.DEFAULT }
        }
        fun isArabicScript(s: String) = s.any { it in '\u0600'..'\u06FF' || it in '\uFB50'..'\uFDFF' || it in '\uFE70'..'\uFEFF' }
    }

    init {
        fonts(context)
        isHapticFeedbackEnabled = false
        isSoundEffectsEnabled = false
    }

    private fun dp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics)
    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    fun setConfig(c: KbConfig) { cfg = c; requestLayout(); layoutKeys(); invalidate() }

    fun setRows(r: List<List<Key>>) {
        val sizeChanged = r.size != rows.size
        if (active >= 0 || dragFrom >= 0) cancelTouch()
        selIdx = -1
        rows = r
        if (sizeChanged || maxHeightPx > 0) requestLayout()
        layoutKeys(); invalidate()
    }

    private val padTop get() = dp(4f)
    private val padBottom get() = dp(6f)

    /** Row height the user asked for (scale x row height x keyboard height). */
    fun wantedRowHeight(): Float = dp(cfg.rowHeightDp * cfg.scale * cfg.kbHeightPct)

    /** Row height really used: shrinks only if all rows would not fit in [maxHeightPx]. */
    fun rowHeight(rowCount: Int = rows.size): Float =
        FitMath.fitRowHeight(max(rowCount, 1), wantedRowHeight(), maxHeightPx.toFloat(), padTop + padBottom, dp(30f))

    fun preferredHeight(rowCount: Int = rows.size): Int =
        (max(rowCount, 1) * rowHeight(rowCount) + padTop + padBottom).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = View.MeasureSpec.getSize(widthMeasureSpec)
        setMeasuredDimension(w, preferredHeight())
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) { layoutKeys() }

    /**
     * Geometry. Fixes of version 1:
     *  - caps were forced to a perfect circle of min(width, height): with 12 keys per Persian row the
     *    width won, so "row height" and "scale" did not make letters bigger. Now the default shape is a
     *    rounded pill that uses the cell's full width x height; "circle" stays available.
     *  - key width and key height are independent percentages of the cell.
     *  - gaps are clamped so caps never overlap or collapse; text is clamped to the cap.
     */
    private fun layoutKeys() {
        geo.clear()
        val w = width.toFloat()
        if (w <= 0f || rows.isEmpty()) { level = FloatArray(0); pressed = BooleanArray(0); return }
        // Dense rows (Persian top row has 12 keys) get almost no side margin and tighter gaps,
        // so every key is as wide as the screen allows.
        val denseRow = rows.any { r -> r.size >= 11 && r.none { it.type == KT.SPACE } }
        val padX = if (denseRow) dp(1f) else dp(3f)
        val usable = w - 2 * padX
        val maxW = rows.filter { r -> r.none { it.type == KT.SPACE } }.maxOfOrNull { r -> r.sumOf { it.weight.toDouble() }.toFloat() }
            ?: rows.maxOf { r -> r.sumOf { it.weight.toDouble() }.toFloat() }
        val unit = usable / max(maxW, 1f)
        val rh = rowHeight()
        var y = padTop
        for ((ri, row) in rows.withIndex()) {
            val total = row.sumOf { it.weight.toDouble() }.toFloat()
            val hasSpace = row.any { it.type == KT.SPACE }
            val spaceExtra = if (hasSpace && total < maxW) (maxW - total) else 0f
            val rowW = min(usable, (total + spaceExtra) * unit)
            val rowUnit = if ((total + spaceExtra) * unit > usable) usable / (total + spaceExtra) else unit
            var x = padX + (usable - rowW) / 2f
            for ((ci, k) in row.withIndex()) {
                val kw = (k.weight + if (k.type == KT.SPACE) spaceExtra else 0f) * rowUnit
                val cell = RectF(x, y, x + kw, y + rh)
                val gapH = if (denseRow) min(cfg.gapHDp, 1.5f) else cfg.gapHDp
                val gh = FitMath.clampGap(dp(gapH * cfg.scale), rowUnit, 0.3f)
                val gv = FitMath.clampGap(dp(cfg.gapVDp * cfg.scale), rh, 0.3f)
                val inner = RectF(cell.left + gh / 2, cell.top + gv / 2, cell.right - gh / 2, cell.bottom - gv / 2)
                val single = k.weight <= 1.05f && k.type != KT.SPACE
                var capW = inner.width() * (if (single) cfg.keyWidthPct else 1f)
                var capH = inner.height() * cfg.keyHeightPct
                if (cfg.keyShape == "circle" && single) { val d = min(capW, capH); capW = d; capH = d }
                val cx = inner.centerX(); val cy = inner.centerY()
                geo.add(G(k, cell, RectF(cx - capW / 2, cy - capH / 2, cx + capW / 2, cy + capH / 2), ri, ci))
                x += kw
            }
            y += rh
        }
        level = FloatArray(geo.size)
        pressed = BooleanArray(geo.size)
    }

    private fun capRadius(cap: RectF): Float {
        val m = min(cap.width(), cap.height())
        return when (cfg.keyShape) {
            "square" -> cfg.roundness * m * 0.28f
            else -> cfg.roundness * m / 2f
        }
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        val f = t.coerceIn(0f, 1f)
        return Color.argb(
            (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).toInt(),
            (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
            (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
            (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
        )
    }

    private fun lighten(c: Int, f: Float) = blend(c, Color.WHITE, f)
    private fun darken(c: Int, f: Float) = blend(c, Color.BLACK, f)

    override fun onDraw(canvas: Canvas) {
        val p = cfg.palette
        canvas.drawColor(p.bg)
        val now = SystemClock.uptimeMillis()
        val dt = if (lastFrame == 0L) 16f else (now - lastFrame).coerceIn(1, 64).toFloat()
        lastFrame = now
        var animating = false
        for (i in geo.indices) {
            val target = if (pressed[i]) 1f else 0f
            if (level[i] != target) {
                val step = dt / (if (pressed[i]) 45f else 130f)
                level[i] = if (target > level[i]) min(1f, level[i] + step) else max(0f, level[i] - step)
                animating = true
            }
            drawKey(canvas, geo[i], level[i], p)
        }
        if (popupKey >= 0) drawPopup(canvas, p)
        if (editMode) drawEditOverlay(canvas, p)
        if (animating) postInvalidateOnAnimation() else lastFrame = 0L
    }

    private fun isSpecial(k: Key) = k.type != KT.CHAR && k.type != KT.SPACE

    private fun drawKey(c: Canvas, g: G, press: Float, p: Palette) {
        val k = g.key
        val special = isSpecial(k)
        val shiftOn = k.type == KT.SHIFT && shiftState > 0
        var base = if (special) p.special else p.key
        if (shiftOn) base = blend(base, p.accent, 0.55f)
        if (k.type == KT.ENTER) base = blend(p.special, p.accent, 0.35f)
        val cap = g.cap
        val rad = capRadius(cap)
        val lift = dp(2.2f * cfg.scale)
        val sink = lift * press
        // shadow
        fill.color = darken(p.bg, 0.45f)
        fill.alpha = 150
        tmp.set(cap.left, cap.top + lift, cap.right, cap.bottom + lift * 0.9f)
        c.drawRoundRect(tmp, rad, rad, fill)
        fill.alpha = 255
        tmp.set(cap.left, cap.top + sink, cap.right, cap.bottom + sink)
        // metal rim (typewriter key ring)
        if (cfg.key3d) {
            fill.color = p.border
            c.drawRoundRect(tmp, rad, rad, fill)
            val ring = dp(2.2f * cfg.scale)
            tmp.inset(ring, ring)
            val r2 = max(0f, rad - ring)
            fill.color = blend(base, p.press, press)
            c.drawRoundRect(tmp, r2, r2, fill)
            // glossy highlight on the upper half of the keycap
            fill.color = Color.WHITE
            fill.alpha = (34 * (1f - press)).toInt()
            val hl = RectF(tmp.left + tmp.width() * 0.14f, tmp.top + tmp.height() * 0.08f, tmp.right - tmp.width() * 0.14f, tmp.centerY())
            c.drawRoundRect(hl, r2 * 0.8f, r2 * 0.8f, fill)
            fill.alpha = 255
        } else {
            fill.color = blend(base, p.press, press)
            c.drawRoundRect(tmp, rad, rad, fill)
            stroke.color = p.border; stroke.strokeWidth = dp(1f)
            c.drawRoundRect(tmp, rad, rad, stroke)
        }
        val cx = tmp.centerX(); val cy = tmp.centerY()
        val bmp = artFor(k)
        if (bmp != null) {
            // crest fills the cap; centre-crop so nothing is stretched
            val dst = RectF(tmp)
            val bw = bmp.width.toFloat(); val bh = bmp.height.toFloat()
            val dw = dst.width(); val dh = dst.height()
            val scale = max(dw / bw, dh / bh)
            val sw = dw / scale; val sh = dh / scale
            artSrc.set((bw - sw) / 2f, (bh - sh) / 2f, (bw + sw) / 2f, (bh + sh) / 2f)
            val save = c.save()
            artPath.reset(); artPath.addRoundRect(dst, rad, rad, Path.Direction.CW)
            c.clipPath(artPath)
            c.drawBitmap(bmp, artSrc.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }, dst, null)
            c.restoreToCount(save)
        }
        val color = if (bmp != null) Color.WHITE else if (special) p.specialText else p.keyText
        val iconSize = min(tmp.width(), tmp.height()) * 0.42f
        when (k.type) {
            KT.SHIFT -> drawShift(c, cx, cy, iconSize, color, p)
            KT.DEL -> drawDelete(c, cx, cy, iconSize, color)
            KT.ENTER -> drawEnter(c, cx, cy, iconSize, color)
            KT.LANG -> drawGlobe(c, cx, cy, iconSize, color)
            else -> {
                val label = k.label
                val small = k.type != KT.CHAR || label.length > 2
                text.typeface = if (isArabicScript(label)) vazir else elite
                val maxH = tmp.height() * (if (small) 0.38f else 0.62f)
                var ts = min(sp(cfg.textSp * cfg.scale) * (if (small) 0.62f else 1f), maxH)
                text.textSize = ts
                val avail = tmp.width() * 0.86f
                val tw = text.measureText(label)
                if (tw > avail && tw > 0) { ts *= avail / tw; text.textSize = ts }
                text.color = if (k.type == KT.SPACE && bmp == null) blend(color, p.key, 0.45f) else color
                // over a crest the letter gets a dark halo so it stays readable on any logo
                text.setShadowLayer(if (bmp != null) dp(3f) else 0f, 0f, dp(1f), artShadow)
                val fm = text.fontMetrics
                c.drawText(label, cx, cy - (fm.ascent + fm.descent) / 2f, text)
                text.clearShadowLayer()
                if (cfg.showHints && k.type == KT.CHAR && k.alts.isNotEmpty() && tmp.width() > dp(22f)) {
                    val h = k.alts[0]
                    text.typeface = if (isArabicScript(h)) vazir else elite
                    text.textSize = ts * 0.42f
                    text.color = blend(color, base, 0.45f)
                    c.drawText(h, tmp.right - tmp.width() * 0.24f, tmp.top + tmp.height() * 0.30f, text)
                }
            }
        }
    }

    private fun prep(color: Int, w: Float) {
        stroke.color = color; stroke.strokeWidth = w
        stroke.strokeCap = Paint.Cap.ROUND; stroke.strokeJoin = Paint.Join.ROUND
    }

    private fun drawShift(c: Canvas, cx: Float, cy: Float, s: Float, color: Int, p: Palette) {
        prep(color, s * 0.11f)
        path.reset()
        path.moveTo(cx, cy - s * 0.55f)
        path.lineTo(cx + s * 0.5f, cy)
        path.lineTo(cx + s * 0.22f, cy)
        path.lineTo(cx + s * 0.22f, cy + s * 0.42f)
        path.lineTo(cx - s * 0.22f, cy + s * 0.42f)
        path.lineTo(cx - s * 0.22f, cy)
        path.lineTo(cx - s * 0.5f, cy)
        path.close()
        if (shiftState > 0) { fill.color = color; c.drawPath(path, fill) } else c.drawPath(path, stroke)
        if (shiftState == 2) c.drawLine(cx - s * 0.32f, cy + s * 0.62f, cx + s * 0.32f, cy + s * 0.62f, stroke)
    }

    private fun drawDelete(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        prep(color, s * 0.1f)
        path.reset()
        path.moveTo(cx - s * 0.65f, cy)
        path.lineTo(cx - s * 0.3f, cy - s * 0.42f)
        path.lineTo(cx + s * 0.62f, cy - s * 0.42f)
        path.lineTo(cx + s * 0.62f, cy + s * 0.42f)
        path.lineTo(cx - s * 0.3f, cy + s * 0.42f)
        path.close()
        c.drawPath(path, stroke)
        c.drawLine(cx - s * 0.02f, cy - s * 0.18f, cx + s * 0.34f, cy + s * 0.18f, stroke)
        c.drawLine(cx + s * 0.34f, cy - s * 0.18f, cx - s * 0.02f, cy + s * 0.18f, stroke)
    }

    private fun drawGlobe(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        prep(color, s * 0.09f)
        val r = s * 0.5f
        c.drawCircle(cx, cy, r, stroke)
        tmp.set(cx - r * 0.45f, cy - r, cx + r * 0.45f, cy + r)
        c.drawOval(tmp, stroke)
        c.drawLine(cx - r, cy, cx + r, cy, stroke)
        c.drawLine(cx - r * 0.85f, cy - r * 0.5f, cx + r * 0.85f, cy - r * 0.5f, stroke)
        c.drawLine(cx - r * 0.85f, cy + r * 0.5f, cx + r * 0.85f, cy + r * 0.5f, stroke)
    }

    private fun drawEnter(c: Canvas, cx: Float, cy: Float, s: Float, color: Int) {
        prep(color, s * 0.11f)
        path.reset()
        when (enterAction) {
            EditorInfo.IME_ACTION_SEARCH -> {
                c.drawCircle(cx - s * 0.1f, cy - s * 0.1f, s * 0.32f, stroke)
                c.drawLine(cx + s * 0.13f, cy + s * 0.13f, cx + s * 0.45f, cy + s * 0.45f, stroke)
            }
            EditorInfo.IME_ACTION_SEND, EditorInfo.IME_ACTION_GO, EditorInfo.IME_ACTION_NEXT -> {
                c.drawLine(cx - s * 0.5f, cy, cx + s * 0.5f, cy, stroke)
                path.moveTo(cx + s * 0.15f, cy - s * 0.35f); path.lineTo(cx + s * 0.5f, cy); path.lineTo(cx + s * 0.15f, cy + s * 0.35f)
                c.drawPath(path, stroke)
            }
            EditorInfo.IME_ACTION_DONE -> {
                path.moveTo(cx - s * 0.45f, cy); path.lineTo(cx - s * 0.12f, cy + s * 0.32f); path.lineTo(cx + s * 0.48f, cy - s * 0.32f)
                c.drawPath(path, stroke)
            }
            else -> {
                path.moveTo(cx + s * 0.5f, cy - s * 0.45f); path.lineTo(cx + s * 0.5f, cy + s * 0.12f); path.lineTo(cx - s * 0.45f, cy + s * 0.12f)
                c.drawPath(path, stroke)
                path.reset()
                path.moveTo(cx - s * 0.15f, cy - s * 0.2f); path.lineTo(cx - s * 0.48f, cy + s * 0.12f); path.lineTo(cx - s * 0.15f, cy + s * 0.44f)
                c.drawPath(path, stroke)
            }
        }
    }

    private fun rowBounds(r: Int): RectF? {
        var out: RectF? = null
        for (g in geo) if (g.row == r) { if (out == null) out = RectF(g.cell) else out.union(g.cell) }
        return out
    }

    private fun drawEditOverlay(c: Canvas, p: Palette) {
        val src = if (dragFrom >= 0) dragFrom else selIdx
        if (src < 0 || src >= geo.size) return
        val from = geo[src]
        // dim rows where this key is not allowed
        fill.color = Color.BLACK; fill.alpha = 110
        for (r in 0 until rows.size) {
            if (editListener?.canDrop(from.row, from.col, r) == false) rowBounds(r)?.let { c.drawRect(it, fill) }
        }
        fill.alpha = 255
        // highlight the picked key
        stroke.color = p.accent; stroke.strokeWidth = dp(2.5f)
        val rad = capRadius(from.cap)
        c.drawRoundRect(from.cap, rad, rad, stroke)
        // insertion marker
        if (dropRow >= 0) {
            val rb = rowBounds(dropRow)
            if (rb != null) {
                val inRow = geo.filter { it.row == dropRow }
                val x = when {
                    inRow.isEmpty() -> rb.centerX()
                    dropIdx >= inRow.size -> inRow.last().cell.right
                    else -> inRow[dropIdx].cell.left
                }
                fill.color = if (dropOk) p.accent else Color.rgb(200, 40, 40)
                c.drawRoundRect(RectF(x - dp(2.5f), rb.top + dp(4f), x + dp(2.5f), rb.bottom - dp(4f)), dp(2f), dp(2f), fill)
            }
        }
        // ghost of the dragged key under the finger
        if (dragFrom >= 0) {
            val w = from.cap.width(); val h = from.cap.height()
            tmp.set(dragX - w / 2, dragY - h * 1.1f, dragX + w / 2, dragY - h * 0.1f)
            fill.color = p.accent; fill.alpha = 220
            c.drawRoundRect(tmp, rad, rad, fill)
            fill.alpha = 255
            if (from.key.type == KT.CHAR || from.key.type == KT.SPACE || from.key.type == KT.ZWNJ || from.key.type == KT.SYM ||
                from.key.type == KT.ABC || from.key.type == KT.PAGE1 || from.key.type == KT.PAGE2 || from.key.type == KT.CALC) {
                text.typeface = if (isArabicScript(from.key.label)) vazir else elite
                text.textSize = min(sp(cfg.textSp), h * 0.5f)
                text.color = p.bg
                val fm = text.fontMetrics
                c.drawText(from.key.label, tmp.centerX(), tmp.centerY() - (fm.ascent + fm.descent) / 2f, text)
            } else {
                val color = p.bg
                val s = min(tmp.width(), tmp.height()) * 0.42f
                when (from.key.type) {
                    KT.SHIFT -> drawShift(c, tmp.centerX(), tmp.centerY(), s, color, p)
                    KT.DEL -> drawDelete(c, tmp.centerX(), tmp.centerY(), s, color)
                    KT.ENTER -> drawEnter(c, tmp.centerX(), tmp.centerY(), s, color)
                    KT.LANG -> drawGlobe(c, tmp.centerX(), tmp.centerY(), s, color)
                    else -> {}
                }
            }
        }
    }

    /** Which row / insertion index a point falls on (rows are drawn left to right). */
    private fun dropTarget(x: Float, y: Float): Pair<Int, Int> {
        if (rows.isEmpty()) return Pair(-1, -1)
        val rh = rowHeight()
        val r = ((y - padTop) / rh).toInt().coerceIn(0, rows.size - 1)
        val inRow = geo.filter { it.row == r }
        var idx = inRow.size
        for ((i, g) in inRow.withIndex()) if (x < g.cell.centerX()) { idx = i; break }
        return Pair(r, idx)
    }

    private fun drawPopup(c: Canvas, p: Palette) {
        fill.color = darken(p.bg, 0.5f); fill.alpha = 120
        tmp.set(popupRect); tmp.offset(0f, dp(3f))
        c.drawRoundRect(tmp, dp(14f), dp(14f), fill)
        fill.alpha = 255
        fill.color = p.special
        c.drawRoundRect(popupRect, dp(14f), dp(14f), fill)
        stroke.color = p.accent; stroke.strokeWidth = dp(1.5f)
        c.drawRoundRect(popupRect, dp(14f), dp(14f), stroke)
        for (i in popupAlts.indices) {
            val l = popupRect.left + i * popupCell
            val cx = l + popupCell / 2f; val cy = popupRect.centerY()
            if (i == popupSel) {
                fill.color = p.accent
                c.drawCircle(cx, cy, min(popupCell, popupRect.height()) * 0.44f, fill)
            }
            val s = popupAlts[i]
            text.typeface = if (isArabicScript(s)) vazir else elite
            text.textSize = min(sp(cfg.textSp * cfg.scale), popupRect.height() * 0.5f)
            text.color = if (i == popupSel) p.bg else p.specialText
            val fm = text.fontMetrics
            c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, text)
        }
    }

    private fun keyAt(x: Float, y: Float): Int {
        var best = -1; var bestD = Float.MAX_VALUE
        for (i in geo.indices) {
            val r = geo[i].cell
            if (r.contains(x, y)) return i
            val dx = max(0f, max(r.left - x, x - r.right))
            val dy = max(0f, max(r.top - y, y - r.bottom))
            val d = dx * dx + dy * dy
            if (d < bestD) { bestD = d; best = i }
        }
        return if (bestD < dp(24f) * dp(24f)) best else -1
    }

    private val longRunnable = Runnable { onLong() }

    /**
     * Delete repeat. Version 1 bugs: a short tap never deleted (finish() skipped DEL and the first
     * repeat tick was step 0, which the service ignored), so single deletes were unreliable.
     * Now: one delete happens on touch-down, repeat starts only after a hold, and every exit path
     * (finger up, cancel, second finger, keyboard hidden, view detached) stops it immediately.
     */
    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (!repeating || active < 0 || active >= geo.size || geo[active].key.type != KT.DEL) { repeating = false; return }
            listener?.onDeleteRepeat(repeatStep++)
            uiHandler.postDelayed(this, when {
                repeatStep > 20 -> 40L
                repeatStep > 8 -> 55L
                else -> 75L
            })
        }
    }

    /** Stops any running long-press / delete repeat and forgets the touch. */
    fun cancelTouch() {
        uiHandler.removeCallbacks(longRunnable)
        uiHandler.removeCallbacks(repeatRunnable)
        repeating = false
        if (active in pressed.indices) pressed[active] = false
        active = -1; pointerId = -1
        popupKey = -1; popupAlts = emptyList()
        dragFrom = -1; dropRow = -1; dropIdx = -1
        invalidate()
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (editMode) return editTouch(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> start(e.getPointerId(0), e.x, e.y)
            MotionEvent.ACTION_POINTER_DOWN -> {
                val i = e.actionIndex
                if (active >= 0) finish(true)
                start(e.getPointerId(i), e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_MOVE -> {
                val i = e.findPointerIndex(pointerId)
                if (i >= 0) move(e.getX(i), e.getY(i))
            }
            MotionEvent.ACTION_POINTER_UP -> if (e.getPointerId(e.actionIndex) == pointerId) finish(true)
            // ACTION_UP is the last finger leaving the screen: always stop, whatever pointer id we track.
            MotionEvent.ACTION_UP -> finish(e.getPointerId(e.actionIndex) == pointerId)
            MotionEvent.ACTION_CANCEL -> finish(false)
        }
        return true
    }

    private fun editTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val idx = keyAt(e.x, e.y)
                active = idx; downX = e.x; dragX = e.x; dragY = e.y; longFired = false
                if (idx >= 0) uiHandler.postDelayed(longRunnable, cfg.longPressMs)
            }
            MotionEvent.ACTION_MOVE -> {
                dragX = e.x; dragY = e.y
                if (dragFrom >= 0) {
                    val (r, i) = dropTarget(e.x, e.y)
                    dropRow = r; dropIdx = i
                    val g = geo[dragFrom]
                    dropOk = editListener?.canDrop(g.row, g.col, r) ?: false
                    invalidate()
                } else if (abs(e.x - downX) > dp(12f)) uiHandler.removeCallbacks(longRunnable)
            }
            MotionEvent.ACTION_UP -> {
                uiHandler.removeCallbacks(longRunnable)
                if (dragFrom >= 0) {
                    val g = geo[dragFrom]
                    if (dropRow >= 0 && dropOk) editListener?.onDrop(g.row, g.col, dropRow, dropIdx)
                    dragFrom = -1; dropRow = -1; selIdx = -1
                } else if (active >= 0) {
                    // tap-to-move fallback: tap a key, then tap where it should go
                    if (selIdx < 0) { selIdx = active; editListener?.onPicked(geo[active].key) }
                    else if (selIdx == active) selIdx = -1
                    else {
                        val s = geo[selIdx]
                        val (r, i) = dropTarget(e.x, e.y)
                        if (editListener?.canDrop(s.row, s.col, r) == true) editListener?.onDrop(s.row, s.col, r, i)
                        selIdx = -1
                    }
                }
                active = -1
                invalidate()
            }
            MotionEvent.ACTION_CANCEL -> cancelTouch()
        }
        return true
    }

    private fun start(pid: Int, x: Float, y: Float) {
        val idx = keyAt(x, y)
        if (idx < 0) return
        active = idx; pointerId = pid; downX = x; lastX = x
        longFired = false; spaceDrag = false; spaceAccum = 0f; repeatStep = 0
        pressed[idx] = true; invalidate()
        val k = geo[idx].key
        listener?.onPress(k)
        when (k.type) {
            KT.DEL -> {
                // exactly one deletion for the tap itself...
                listener?.onKey(k, "")
                // ...and continuous deletion only if the finger stays down
                repeating = true
                uiHandler.postDelayed(repeatRunnable, 420L)
            }
            else -> uiHandler.postDelayed(longRunnable, if (k.type == KT.SPACE) 520L else cfg.longPressMs)
        }
    }

    private fun move(x: Float, y: Float) {
        if (active < 0) return
        val k = geo[active].key
        if (popupKey >= 0) {
            val sel = ((x - popupRect.left) / popupCell).toInt()
            val ns = sel.coerceIn(0, popupAlts.size - 1)
            if (ns != popupSel) { popupSel = ns; invalidate() }
            return
        }
        if (k.type == KT.SPACE) {
            if (!spaceDrag && abs(x - downX) > dp(14f)) {
                spaceDrag = true; uiHandler.removeCallbacks(longRunnable); lastX = x
            }
            if (spaceDrag) {
                spaceAccum += x - lastX; lastX = x
                val step = dp(11f)
                while (spaceAccum >= step) { listener?.onCursorMove(1); spaceAccum -= step }
                while (spaceAccum <= -step) { listener?.onCursorMove(-1); spaceAccum += step }
            }
            return
        }
        if (k.type == KT.CHAR && !longFired) {
            val n = keyAt(x, y)
            if (n >= 0 && n != active && geo[n].key.type == KT.CHAR) {
                pressed[active] = false
                active = n; pressed[n] = true
                uiHandler.removeCallbacks(longRunnable)
                uiHandler.postDelayed(longRunnable, cfg.longPressMs)
                invalidate()
            }
        }
    }

    private fun onLong() {
        if (active < 0 || active >= geo.size) return
        val g = geo[active]
        val k = g.key
        if (editMode) {
            dragFrom = active; selIdx = -1; longFired = true
            editListener?.onPicked(k)
            val (r, i) = dropTarget(dragX, dragY)
            dropRow = r; dropIdx = i
            dropOk = editListener?.canDrop(g.row, g.col, r) ?: false
            performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
            invalidate()
            return
        }
        if (k.type == KT.CHAR && k.alts.isNotEmpty()) {
            longFired = true
            popupAlts = listOf(k.out) + k.alts
            popupKey = active
            popupSel = 1.coerceAtMost(popupAlts.size - 1)
            popupCell = max(g.cell.width(), dp(34f))
            val w = popupCell * popupAlts.size
            var left = g.cell.centerX() - w / 2f
            left = left.coerceIn(dp(2f), max(dp(2f), width - w - dp(2f)))
            val h = rowHeight() * 0.95f
            val top = max(0f, g.cell.top - h * 0.85f)
            popupRect.set(left, top, left + w, top + h)
            listener?.onPress(k)
            invalidate()
            return
        }
        if (listener?.onLongKey(k) == true) longFired = true
    }

    private fun finish(commit: Boolean) {
        uiHandler.removeCallbacks(longRunnable)
        uiHandler.removeCallbacks(repeatRunnable)
        repeating = false
        val idx = active
        if (idx < 0 || idx >= geo.size) { active = -1; pointerId = -1; return }
        val k = geo[idx].key
        if (popupKey >= 0) {
            if (commit && popupSel in popupAlts.indices) listener?.onKey(k, popupAlts[popupSel])
            popupKey = -1; popupAlts = emptyList()
        } else if (commit && !longFired && !spaceDrag && k.type != KT.DEL) {
            // DEL already deleted on touch-down; never delete a second time on release
            listener?.onKey(k, k.out)
        }
        if (idx in pressed.indices) pressed[idx] = false
        active = -1; pointerId = -1
        invalidate()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        if (visibility != View.VISIBLE) cancelTouch()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        uiHandler.removeCallbacksAndMessages(null)
        repeating = false
        active = -1; popupKey = -1; dragFrom = -1
    }
}
