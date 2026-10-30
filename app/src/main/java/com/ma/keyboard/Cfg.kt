package com.ma.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import org.json.JSONArray

data class Palette(
    val bg: Int, val key: Int, val keyText: Int, val border: Int, val press: Int,
    val special: Int, val specialText: Int, val toolbar: Int, val icon: Int, val accent: Int
)

object Themes {
    val ids = listOf("mawriter", "cream", "brown", "dark", "light", "custom")

    fun title(id: String): String = when (id) {
        "mawriter" -> "MA Writer (اصلی)"
        "cream" -> "کرم و کاهی"
        "brown" -> "قهوه‌ای کلاسیک"
        "dark" -> "ماشین‌تحریر تیره"
        "light" -> "روشن ساده"
        else -> "سفارشی"
    }

    fun preset(id: String): Palette = when (id) {
        "mawriter" -> Palette(c("#1C1815"), c("#2E2A24"), c("#F0E6D2"), c("#12100D"), c("#3A332C"),
            c("#3A332C"), c("#F0E6D2"), c("#2B2621"), c("#F0E6D2"), c("#C9A15A"))
        "brown" -> Palette(c("#4A3426"), c("#EADDC4"), c("#2B1D14"), c("#1F140D"), c("#C7AF86"),
            c("#B89B72"), c("#24170F"), c("#3B291D"), c("#EBDCC0"), c("#D9A441"))
        "dark" -> Palette(c("#1C1B19"), c("#2D2B28"), c("#ECE4D2"), c("#6B655B"), c("#4A463F"),
            c("#3A3732"), c("#ECE4D2"), c("#151412"), c("#D8CFBC"), c("#C9A14A"))
        "light" -> Palette(c("#ECEFF1"), c("#FFFFFF"), c("#212121"), c("#B0BEC5"), c("#CFD8DC"),
            c("#DDE3E7"), c("#263238"), c("#E3E8EB"), c("#37474F"), c("#3F6FD8"))
        else -> Palette(c("#EFE5CF"), c("#FBF6EA"), c("#3B2F25"), c("#7E6A52"), c("#D9C8A5"),
            c("#E1D2B3"), c("#3B2F25"), c("#E5D8BC"), c("#5A4634"), c("#B5473A"))
    }

    private fun c(s: String) = Color.parseColor(s)
}

class KbConfig(
    val scale: Float,
    val rowHeightDp: Float,
    val keyWidthPct: Float,
    /** Height of the keycap as a fraction of its row (independent from the width). */
    val keyHeightPct: Float,
    /** "round" = pill/stadium caps that fill the cell, "circle" = classic perfect circles, "square" = soft squares. */
    val keyShape: String,
    /** Whole keyboard width as a fraction of the screen (centred). */
    val kbWidthPct: Float,
    /** Whole keyboard height multiplier (scales every row). */
    val kbHeightPct: Float,
    val toolbarIconPct: Float,
    val toolbarStroke: Float,
    val toolbarGapDp: Float,
    val toolbarPlates: Boolean,
    val gapHDp: Float,
    val gapVDp: Float,
    val roundness: Float,
    val textSp: Float,
    val palette: Palette,
    val showNumbers: Boolean,
    val toolbarHeightDp: Float,
    val toolbarOrder: List<String>,
    val toolbarHidden: Set<String>,
    val oneHanded: String,
    val oneHandPct: Float,
    val showHints: Boolean,
    val longPressMs: Long,
    val nativeDigits: Boolean,
    val autoCap: Boolean,
    val key3d: Boolean
)

object Cfg {
    const val FILE = "makeyboard_settings"

    val ALL_TOOLS = listOf(
        "reminder", "clipboard", "emoji", "calc", "notes", "edit",
        "writing", "snippets", "codes", "private", "onehand", "settings"
    )

    val COLOR_KEYS = listOf(
        "c_bg" to "پس‌زمینه", "c_key" to "رنگ کلید", "c_text" to "رنگ حروف",
        "c_border" to "حاشیه کلید", "c_press" to "جلوه فشردن", "c_special" to "کلیدهای ویژه",
        "c_special_text" to "حروف کلید ویژه", "c_toolbar" to "نوار ابزار",
        "c_icon" to "آیکون‌ها", "c_accent" to "رنگ تأکید"
    )

    /** Size keys: reset separately with "بازگرداندن اندازه‌های پیش‌فرض". */
    val SIZE_KEYS = listOf(
        "scale", "row_h", "key_w", "key_hp", "gap_h", "gap_v", "round", "text_sp", "key_shape", "kb_w", "kb_hp"
    )

    /** Toolbar look keys (order/hidden tools are kept). */
    val TOOLBAR_KEYS = listOf("tb_h", "tb_icon", "tb_stroke", "tb_gap", "tb_plate")

    /** Colour/theme keys. Layout keys ("lay_*") are deliberately NOT here: a theme change never touches the layout. */
    val THEME_KEYS = listOf("theme", "custom_base", "hints", "key_3d") + COLOR_KEYS.map { it.first }

    val APPEARANCE_KEYS = SIZE_KEYS + TOOLBAR_KEYS + THEME_KEYS +
        listOf("num_row", "tb_order", "tb_hidden", "one_hand", "one_hand_pct")

    /** Prefix of the saved custom layouts, one key per page (e.g. lay_fa, lay_en, lay_sym1_fa). */
    const val LAYOUT_PREFIX = "lay_"

    fun toolTitle(id: String): String = when (id) {
        "reminder" -> "یادآور"
        "clipboard" -> "کلیپ‌بورد"
        "emoji" -> "ایموجی"
        "calc" -> "ماشین‌حساب"
        "notes" -> "یادداشت‌ها"
        "edit" -> "کنترل متن"
        "writing" -> "حالت نویسندگی"
        "snippets" -> "میان‌برها"
        "codes" -> "کدگذاری کلمه"
        "private" -> "حالت خصوصی"
        "onehand" -> "حالت یک‌دستی"
        "settings" -> "تنظیمات"
        else -> id
    }

    fun sp(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun palette(p: SharedPreferences): Palette {
        val theme = p.getString("theme", "mawriter") ?: "mawriter"
        if (theme != "custom") return Themes.preset(theme)
        val b = Themes.preset(p.getString("custom_base", "mawriter") ?: "mawriter")
        return Palette(
            p.getInt("c_bg", b.bg), p.getInt("c_key", b.key), p.getInt("c_text", b.keyText),
            p.getInt("c_border", b.border), p.getInt("c_press", b.press), p.getInt("c_special", b.special),
            p.getInt("c_special_text", b.specialText), p.getInt("c_toolbar", b.toolbar),
            p.getInt("c_icon", b.icon), p.getInt("c_accent", b.accent)
        )
    }

    fun colorOf(pal: Palette, key: String): Int = when (key) {
        "c_bg" -> pal.bg; "c_key" -> pal.key; "c_text" -> pal.keyText; "c_border" -> pal.border
        "c_press" -> pal.press; "c_special" -> pal.special; "c_special_text" -> pal.specialText
        "c_toolbar" -> pal.toolbar; "c_icon" -> pal.icon; else -> pal.accent
    }

    /** Switches to the custom theme (seeded from the current theme) and changes one colour. */
    fun setCustomColor(c: Context, key: String, color: Int) {
        setCustomColor(sp(c), key, color)
    }

    fun setCustomColor(p: SharedPreferences, key: String, color: Int) {
        val e = p.edit()
        val theme = p.getString("theme", "mawriter") ?: "mawriter"
        if (theme != "custom") {
            val cur = Themes.preset(theme)
            for ((k, _) in COLOR_KEYS) e.putInt(k, colorOf(cur, k))
            e.putString("custom_base", theme)
            e.putString("theme", "custom")
        }
        e.putInt(key, color)
        e.apply()
    }

    fun toolbarOrder(p: SharedPreferences): MutableList<String> {
        val raw = p.getString("tb_order", null)
        val list = raw?.split(",")?.filter { it in ALL_TOOLS }?.toMutableList() ?: mutableListOf()
        for (t in ALL_TOOLS) if (t !in list) list.add(t)
        return list
    }

    fun toolbarHidden(p: SharedPreferences): MutableSet<String> =
        (p.getString("tb_hidden", "") ?: "").split(",").filter { it.isNotBlank() }.toMutableSet()

    // Defaults. Version 1 forced every letter into a perfect circle limited by the key WIDTH
    // (about 25dp on a phone with a 12-key Persian row), so row height never made keys bigger.
    const val DEF_SCALE = 100
    const val DEF_ROW_H = 58
    const val DEF_KEY_W = 100
    const val DEF_KEY_HP = 92
    const val DEF_GAP_H = 3
    const val DEF_GAP_V = 5
    const val DEF_ROUND = 80
    const val DEF_TEXT = 22
    const val DEF_KB_W = 100
    const val DEF_KB_HP = 100
    const val DEF_TB_H = 46
    const val DEF_TB_ICON = 72
    const val DEF_TB_STROKE = 24
    const val DEF_TB_GAP = 4
    const val DEF_SHAPE = "round"

    fun load(c: Context): KbConfig = load(sp(c))

    fun load(p: SharedPreferences): KbConfig {
        return KbConfig(
            scale = p.getInt("scale", DEF_SCALE).coerceIn(60, 160) / 100f,
            rowHeightDp = p.getInt("row_h", DEF_ROW_H).coerceIn(32, 110).toFloat(),
            keyWidthPct = p.getInt("key_w", DEF_KEY_W).coerceIn(50, 100) / 100f,
            keyHeightPct = p.getInt("key_hp", DEF_KEY_HP).coerceIn(50, 100) / 100f,
            keyShape = p.getString("key_shape", DEF_SHAPE) ?: DEF_SHAPE,
            kbWidthPct = p.getInt("kb_w", DEF_KB_W).coerceIn(60, 100) / 100f,
            kbHeightPct = p.getInt("kb_hp", DEF_KB_HP).coerceIn(70, 150) / 100f,
            toolbarIconPct = p.getInt("tb_icon", DEF_TB_ICON).coerceIn(45, 95) / 100f,
            toolbarStroke = p.getInt("tb_stroke", DEF_TB_STROKE).coerceIn(14, 40) / 10f,
            toolbarGapDp = p.getInt("tb_gap", DEF_TB_GAP).coerceIn(0, 20).toFloat(),
            toolbarPlates = p.getBoolean("tb_plate", true),
            gapHDp = p.getInt("gap_h", DEF_GAP_H).coerceIn(0, 16).toFloat(),
            gapVDp = p.getInt("gap_v", DEF_GAP_V).coerceIn(0, 20).toFloat(),
            roundness = p.getInt("round", DEF_ROUND).coerceIn(0, 100) / 100f,
            textSp = p.getInt("text_sp", DEF_TEXT).coerceIn(10, 44).toFloat(),
            palette = palette(p),
            showNumbers = p.getBoolean("num_row", false),
            toolbarHeightDp = p.getInt("tb_h", DEF_TB_H).coerceIn(30, 80).toFloat(),
            toolbarOrder = toolbarOrder(p),
            toolbarHidden = toolbarHidden(p),
            oneHanded = p.getString("one_hand", "off") ?: "off",
            oneHandPct = p.getInt("one_hand_pct", 82) / 100f,
            showHints = p.getBoolean("hints", true),
            longPressMs = p.getInt("lp_ms", 380).toLong(),
            nativeDigits = p.getBoolean("native_digits", true),
            autoCap = p.getBoolean("autocap", true),
            key3d = p.getBoolean("key_3d", true)
        )
    }

    fun languages(p: SharedPreferences): List<String> {
        val l = (p.getString("langs", "fa,en") ?: "fa,en").split(",").filter { it in listOf("fa", "en", "ar") }
        return if (l.isEmpty()) listOf("fa") else l
    }

    fun langName(l: String) = when (l) { "fa" -> "فارسی"; "ar" -> "العربية"; else -> "English" }

    fun resetAppearance(c: Context) = resetKeys(sp(c), APPEARANCE_KEYS)

    fun resetKeys(p: SharedPreferences, keys: List<String>) {
        val e = p.edit()
        for (k in keys) e.remove(k)
        e.apply()
    }

    /** Removes every saved custom layout page (only the layout; colours and sizes stay). */
    fun resetLayouts(p: SharedPreferences) {
        val e = p.edit()
        for (k in p.all.keys) if (k.startsWith(LAYOUT_PREFIX)) e.remove(k)
        e.apply()
    }

    fun layoutJson(p: SharedPreferences, page: String): String? = p.getString(LAYOUT_PREFIX + page, null)

    // ---- contrast helpers (WCAG relative luminance) ----

    private fun lum(c: Int): Double {
        fun ch(v: Int): Double { val s = v / 255.0; return if (s <= 0.03928) s / 12.92 else Math.pow((s + 0.055) / 1.055, 2.4) }
        return 0.2126 * ch(Color.red(c)) + 0.7152 * ch(Color.green(c)) + 0.0722 * ch(Color.blue(c))
    }

    fun contrast(a: Int, b: Int): Double {
        val la = lum(a); val lb = lum(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun mix(a: Int, b: Int, t: Float): Int = Color.rgb(
        (Color.red(a) + (Color.red(b) - Color.red(a)) * t).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * t).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t).toInt()
    )

    /**
     * Keeps the theme's icon colour but pushes it towards black or white until it reaches
     * [minRatio] against [bg]. Custom themes can no longer make toolbar icons disappear.
     */
    fun readable(fg: Int, bg: Int, minRatio: Double = 4.5): Int {
        val opaque = fg or 0xFF000000.toInt()
        if (contrast(opaque, bg) >= minRatio) return opaque
        val target = if (lum(bg) > 0.4) Color.BLACK else Color.WHITE
        var t = 0.1f
        while (t <= 1f) {
            val c = mix(opaque, target, t)
            if (contrast(c, bg) >= minRatio) return c
            t += 0.1f
        }
        return target
    }

    /** A plate slightly lifted from the toolbar colour, drawn behind each toolbar icon. */
    fun plate(toolbar: Int, icon: Int): Int = mix(toolbar, icon, 0.14f)

    fun resetAll(c: Context) {
        val p = sp(c)
        val keep = setOf("pin_salt", "pin_hash", "codes_lock")
        val e = p.edit()
        for (k in p.all.keys) if (k !in keep) e.remove(k)
        e.apply()
    }

    fun categories(p: SharedPreferences, kind: String): MutableList<String> {
        val def = when (kind) {
            Kind.CLIP -> listOf("شخصی", "کار", "جملات", "آدرس‌ها", "نوشته‌ها")
            Kind.CODE -> listOf("شخصی", "کار", "بانکی", "سایر")
            Kind.SNIP -> listOf("عمومی", "نقل‌قول‌ها", "جملات آماده", "متن‌های بلند", "امضا", "ایمیل")
            else -> emptyList()
        }
        val raw = p.getString("cats_$kind", null) ?: return def.toMutableList()
        return try {
            val a = JSONArray(raw)
            MutableList(a.length()) { a.getString(it) }
        } catch (e: Exception) {
            def.toMutableList()
        }
    }

    fun saveCategories(p: SharedPreferences, kind: String, list: List<String>) {
        val a = JSONArray()
        list.forEach { a.put(it) }
        p.edit().putString("cats_$kind", a.toString()).apply()
    }
}
