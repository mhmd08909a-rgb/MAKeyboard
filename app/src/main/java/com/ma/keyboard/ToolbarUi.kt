package com.ma.keyboard

import android.content.Context
import android.widget.LinearLayout

/**
 * Shared toolbar building, used by the keyboard service and the live preview in settings.
 *
 * Root causes of the faint toolbar in version 1:
 *  - the toolbar row had NO background, so theme icon colours (e.g. cream icons of MA Writer) were
 *    drawn on whatever the system put behind the IME window;
 *  - lines were 1.7/24 of a 26dp icon (about 1.8dp) with no backing;
 *  - every icon was a fixed 48dp wide, so with 12 tools the row just ran off the screen edge.
 * Now: themed opaque background, contrast-checked colours (WCAG 4.5:1), thicker configurable
 * strokes, a plate behind each icon, adjustable height/size/spacing, and icons share the width
 * evenly (min 44dp touch target) or scroll when they really do not fit.
 */
object ToolbarUi {
    fun dp(c: Context, v: Float): Int = (v * c.resources.displayMetrics.density + 0.5f).toInt()

    fun heightPx(c: Context, cfg: KbConfig): Int = dp(c, (cfg.toolbarHeightDp * cfg.scale).coerceIn(32f, 90f))

    fun style(v: IconView, cfg: KbConfig, bg: Int, plates: Boolean = cfg.toolbarPlates) {
        v.tint = Cfg.readable(cfg.palette.icon, bg)
        v.accent = Cfg.readable(cfg.palette.accent, bg, 3.0)
        v.sizeFrac = cfg.toolbarIconPct
        v.strokeW = cfg.toolbarStroke
        v.plate = if (plates) Cfg.plate(bg, v.tint) else 0
    }

    /** Fills [bar] with icons; [availableWidth] is the visible toolbar width in px. */
    fun fill(c: Context, bar: LinearLayout, cfg: KbConfig, availableWidth: Int, ids: List<String>, make: (String) -> IconView) {
        bar.removeAllViews()
        if (ids.isEmpty()) return
        val h = heightPx(c, cfg)
        val gap = dp(c, cfg.toolbarGapDp)
        val minTouch = dp(c, 44f)
        val n = ids.size
        val fits = n * (minTouch + gap) <= availableWidth
        val itemW = if (fits) (availableWidth / n) - gap else maxOf(minTouch, dp(c, 48f))
        bar.minimumWidth = if (fits) availableWidth else 0
        for (id in ids) {
            val v = make(id)
            style(v, cfg, cfg.palette.toolbar)
            bar.addView(v, LinearLayout.LayoutParams(itemW, h).apply { setMargins(gap / 2, 0, gap - gap / 2, 0) })
        }
    }
}
