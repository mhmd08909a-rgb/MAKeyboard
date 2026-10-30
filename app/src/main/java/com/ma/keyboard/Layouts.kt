package com.ma.keyboard

enum class KT { CHAR, SHIFT, DEL, ENTER, SPACE, SYM, ABC, LANG, PAGE2, PAGE1, CALC, ZWNJ }

enum class Mode { LETTERS, SYM1, SYM2 }

class Key(
    val type: KT,
    val label: String,
    val out: String = label,
    val alts: List<String> = emptyList(),
    val weight: Float = 1f
)

object Layouts {
    private const val ZWNJ = "\u200C"

    fun digits(lang: String, native: Boolean): String = when {
        native && lang == "fa" -> "۱۲۳۴۵۶۷۸۹۰"
        native && lang == "ar" -> "١٢٣٤٥٦٧٨٩٠"
        else -> "1234567890"
    }

    private val allDigits = listOf("1234567890", "۱۲۳۴۵۶۷۸۹۰", "١٢٣٤٥٦٧٨٩٠")

    private fun digitAlts(i: Int, own: String): List<String> {
        val r = allDigits.map { it[i].toString() }.filter { it != own }.toMutableList()
        when (i) {
            0 -> r.addAll(listOf("¹", "½", "⅓", "¼"))
            1 -> r.addAll(listOf("²", "⅔"))
            2 -> r.addAll(listOf("³", "¾"))
            9 -> r.addAll(listOf("°", "∅"))
        }
        return r
    }

    private fun digitRow(lang: String, native: Boolean): List<Key> {
        val d = digits(lang, native)
        return d.mapIndexed { i, ch -> Key(KT.CHAR, ch.toString(), alts = digitAlts(i, ch.toString())) }
    }

    private fun ch(s: String, alts: String = ""): Key =
        Key(KT.CHAR, s, alts = alts.map { it.toString() })

    private fun chl(s: String, alts: List<String>): Key = Key(KT.CHAR, s, alts = alts)

    private fun mark(m: String): Key = Key(KT.CHAR, "◌$m", m)

    private val symAlts: Map<String, List<String>> = mapOf(
        "-" to listOf("–", "—", "−", "_"),
        "+" to listOf("±"),
        "/" to listOf("\\", "÷", "|"),
        "!" to listOf("¡", "‼"),
        "?" to listOf("¿", "؟"),
        "؟" to listOf("?", "¿"),
        "$" to listOf("€", "£", "¥", "₹", "₽", "﷼", "¢"),
        "﷼" to listOf("$", "€", "£", "¥"),
        "*" to listOf("×", "★", "†", "‡"),
        "%" to listOf("‰", "٪"),
        "٪" to listOf("%", "‰"),
        "." to listOf("…", "·", "•"),
        "," to listOf("،", "٬"),
        "،" to listOf(",", "٬"),
        "\"" to listOf("“", "”", "„", "«", "»"),
        "'" to listOf("‘", "’", "‚", "‹", "›"),
        "(" to listOf("[", "{", "<"),
        ")" to listOf("]", "}", ">"),
        "«" to listOf("‹", "“"),
        "»" to listOf("›", "”"),
        "•" to listOf("·", "○", "●", "◆"),
        "°" to listOf("′", "″", "℃", "℉"),
        "✓" to listOf("✔", "✗", "✘"),
        "π" to listOf("Ω", "∞", "µ"),
        "√" to listOf("∛", "∑", "∫", "∏"),
        "×" to listOf("⋅", "✕"),
        "=" to listOf("≠", "≈", "<", ">", "≤", "≥", "±"),
        "&" to listOf("§", "¶"),
        "@" to listOf("©"),
        "#" to listOf("№"),
        ":" to listOf("؛", ";"),
        "؛" to listOf(";", ":"),
        "٫" to listOf("٬", "."),
        "ـ" to listOf("_", "-"),
        "~" to listOf("≈", "∼"),
        "^" to listOf("ˆ", "↑"),
        "∆" to listOf("∇", "△")
    )

    private fun sym(s: String): Key = Key(KT.CHAR, s, alts = symAlts[s] ?: emptyList())

    private fun bottomLetters(lang: String): List<Key> = when (lang) {
        "fa" -> listOf(
            Key(KT.SYM, "۱۲۳؟", weight = 1.3f), Key(KT.LANG, "🌐", weight = 1.05f),
            chl("،", listOf(",", "؛", ":", "٬")), Key(KT.SPACE, "فارسی", " ", weight = 4f),
            Key(KT.ZWNJ, "نیم‌فاصله", ZWNJ, weight = 1.3f),
            chl(".", listOf("…", "؟", "!", "٫", "·")), Key(KT.ENTER, "⏎", weight = 1.5f)
        )
        "ar" -> listOf(
            Key(KT.SYM, "١٢٣؟", weight = 1.3f), Key(KT.LANG, "🌐", weight = 1.05f),
            chl("،", listOf(",", "؛", ":")), Key(KT.SPACE, "العربية", " ", weight = 5f),
            chl(".", listOf("…", "؟", "!", "٫")), Key(KT.ENTER, "⏎", weight = 1.5f)
        )
        else -> listOf(
            Key(KT.SYM, "?123", weight = 1.3f), Key(KT.LANG, "🌐", weight = 1.05f),
            chl(",", listOf(";", ":", "'", "\"")), Key(KT.SPACE, "English", " ", weight = 5f),
            chl(".", listOf("…", "?", "!", "-", "@")), Key(KT.ENTER, "⏎", weight = 1.5f)
        )
    }

    private val enAlts = mapOf(
        "a" to "àáâäãåæ", "e" to "èéêë€", "i" to "ìíîï", "o" to "òóôöõøœ", "u" to "ùúûü",
        "c" to "ç©", "n" to "ñ", "s" to "ß§$", "y" to "ýÿ¥", "z" to "ž", "l" to "£ł",
        "r" to "®", "t" to "™þ", "g" to "ğ", "d" to "ð"
    )

    private fun english(shift: Boolean, numRow: Boolean, lang: String, native: Boolean): List<List<Key>> {
        val d = "1234567890"
        fun letter(s: String, i: Int = -1): Key {
            val base = (if (i >= 0 && !numRow) d[i].toString() else "") + (enAlts[s] ?: "")
            val l = if (shift) s.uppercase() else s
            val alts = base.map { it.toString() }.map { if (shift) it.uppercase() else it }
            return Key(KT.CHAR, l, alts = alts)
        }
        val r1 = "qwertyuiop".mapIndexed { i, c -> letter(c.toString(), i) }
        val r2 = "asdfghjkl".map { letter(it.toString()) }
        val r3 = mutableListOf(Key(KT.SHIFT, "⇧", weight = 1.5f))
        r3.addAll("zxcvbnm".map { letter(it.toString()) })
        r3.add(Key(KT.DEL, "⌫", weight = 1.5f))
        val rows = mutableListOf<List<Key>>()
        if (numRow) rows.add(digitRow(lang, native))
        rows.add(r1); rows.add(r2); rows.add(r3); rows.add(bottomLetters("en"))
        return rows
    }

    private fun persian(shift: Boolean, numRow: Boolean, native: Boolean): List<List<Key>> {
        val dg = digits("fa", native)
        val rows = mutableListOf<List<Key>>()
        if (numRow) rows.add(digitRow("fa", native))
        if (!shift) {
            val top = listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "چ")
            val topAlts = mapOf("ه" to listOf("ۀ", "ة", "هٔ"), "ج" to listOf("چ"))
            rows.add(top.mapIndexed { i, s ->
                val a = mutableListOf<String>()
                if (i < 10 && !numRow) a.add(dg[i].toString())
                a.addAll(topAlts[s] ?: emptyList())
                chl(s, a)
            })
            rows.add(listOf(
                ch("ش"), ch("س"), chl("ی", listOf("ئ", "ي", "ى")), ch("ب"), chl("ل", listOf("لا")),
                chl("ا", listOf("آ", "أ", "إ", "ء")), chl("ت", listOf("ة", "ث")), ch("ن"), ch("م"),
                chl("ک", listOf("ك")), ch("گ")
            ))
            // No Shift on Persian: the symbols page (?123 / ۱۲۳) already holds the marks and punctuation.
            rows.add(listOf(ch("ظ"), ch("ط"), chl("ز", listOf("ژ")), ch("ر"),
                chl("ذ", listOf("ﺫ")), ch("د"), ch("پ"), chl("و", listOf("ؤ")), Key(KT.DEL, "⌫", weight = 1.3f)))
        } else {
            rows.add(listOf(mark("\u0651"), mark("\u064E"), mark("\u0650"), mark("\u064F"), mark("\u064B"),
                mark("\u064D"), mark("\u064C"), mark("\u0652"), ch("ء"), ch("آ"), ch("أ"), ch("إ")))
            rows.add(listOf(ch("ؤ"), ch("ئ"), ch("ي"), ch("ة"), ch("ك"), ch("ۀ"), sym("«"), sym("»"),
                mark("\u0654"), sym("ـ"), sym("؛")))
            rows.add(listOf(Key(KT.SHIFT, "⇧", weight = 1.3f), sym("!"), sym("؟"), sym("،"), sym("٫"),
                ch("٬"), sym(":"), sym("\""), sym("'"), Key(KT.DEL, "⌫", weight = 1.3f)))
        }
        rows.add(bottomLetters("fa"))
        return rows
    }

    private fun arabic(shift: Boolean, numRow: Boolean, native: Boolean): List<List<Key>> {
        val dg = digits("ar", native)
        val rows = mutableListOf<List<Key>>()
        if (numRow) rows.add(digitRow("ar", native))
        if (!shift) {
            val top = listOf("ض", "ص", "ث", "ق", "ف", "غ", "ع", "ه", "خ", "ح", "ج", "د")
            rows.add(top.mapIndexed { i, s -> chl(s, if (i < 10 && !numRow) listOf(dg[i].toString()) else emptyList()) })
            rows.add(listOf(ch("ش"), ch("س"), chl("ي", listOf("ئ", "ى")), ch("ب"), chl("ل", listOf("لا")),
                chl("ا", listOf("أ", "إ", "آ", "ء")), ch("ت"), ch("ن"), ch("م"), ch("ك"), ch("ط")))
            rows.add(listOf(Key(KT.SHIFT, "⇧", weight = 1.3f), ch("ذ"), chl("ء", listOf("ئ", "ؤ")), ch("ؤ"),
                ch("ر"), chl("ى", listOf("ئ")), ch("ة"), ch("و"), ch("ز"), ch("ظ"), Key(KT.DEL, "⌫", weight = 1.3f)))
        } else {
            rows.add(listOf(mark("\u064E"), mark("\u064B"), mark("\u064F"), mark("\u064C"), mark("\u0650"),
                mark("\u064D"), mark("\u0652"), mark("\u0651"), sym("ـ"), ch("آ"), ch("أ"), ch("إ")))
            rows.add(listOf(ch("ئ"), Key(KT.CHAR, "لا"), sym("«"), sym("»"), sym("؛"), sym("،"), sym("٪"),
                sym("!"), sym("؟"), sym(":"), sym("\"")))
            rows.add(listOf(Key(KT.SHIFT, "⇧", weight = 1.3f), sym("'"), sym("("), sym(")"), sym("-"),
                sym("٫"), ch("٬"), sym("."), Key(KT.DEL, "⌫", weight = 1.3f)))
        }
        rows.add(bottomLetters("ar"))
        return rows
    }

    private fun symbolBottom(lang: String): List<Key> = listOf(
        Key(KT.ABC, if (lang == "en") "ABC" else if (lang == "ar") "أبج" else "الفبا", weight = 1.4f),
        Key(KT.CALC, "🧮", weight = 1.2f),
        sym(if (lang == "en") "," else "،"),
        Key(KT.SPACE, Cfg.langName(lang), " ", weight = 4f),
        sym("."),
        Key(KT.ENTER, "⏎", weight = 1.5f)
    )

    /**
     * Symbols are split over two pages. Every symbol requested in the spec is present:
     * @ # ﷼ ـ & - + ) ( ٫ / » « : ؛ ! ؟ ~ ` | • √ π ÷ × § ∆ { } = ° ^ ¢ $ ¥ £ ٪ © ® ™ ✓ \ ] [
     */
    private fun symbols1(lang: String, native: Boolean): List<List<Key>> = listOf(
        digitRow(lang, native),
        listOf("@", "#", "﷼", "ـ", "&", "-", "+", "(", ")", "/").map { sym(it) },
        listOf("«", "»", ":", "؛", "!", "؟", "٫", "٪", "\"", "'").map { sym(it) },
        listOf(Key(KT.PAGE2, "=\\<", weight = 1.5f)) + listOf("*", "_", ";", "%", "?", ",", ".").map { sym(it) } +
            listOf(Key(KT.DEL, "⌫", weight = 1.5f)),
        symbolBottom(lang)
    )

    private fun symbols2(lang: String, native: Boolean): List<List<Key>> = listOf(
        digitRow(lang, native),
        listOf("~", "`", "|", "•", "√", "π", "÷", "×", "§", "∆").map { sym(it) },
        listOf("{", "}", "=", "°", "^", "¢", "$", "¥", "£", "€").map { sym(it) },
        listOf(Key(KT.PAGE1, if (lang == "en") "?123" else "۱۲۳", weight = 1.5f)) +
            listOf("©", "®", "™", "✓", "\\", "[", "]").map { sym(it) } +
            listOf(Key(KT.DEL, "⌫", weight = 1.5f)),
        symbolBottom(lang)
    )

    // ------------------------------------------------------------------
    // Custom layouts ("ویرایش چیدمان کیبورد")
    //
    // Version 1 built every page from hard-coded lists, so there was nothing a user could move.
    // Now every key gets a stable id; a page's saved order is a JSON list of rows of ids that is
    // merged with the default page by LayoutModel.reconcile (no key can be lost or duplicated,
    // system keys stay in their row, the bottom row only reorders inside itself).
    // ------------------------------------------------------------------

    /** Max keys in one letter row after editing (keeps keys from becoming tiny). */
    const val MAX_KEYS_PER_ROW = 13

    /** Saved-layout page name. English shares one page for lower/upper case. */
    fun pageKey(lang: String, mode: Mode, shift: Boolean): String = when (mode) {
        Mode.SYM1 -> "sym1_$lang"
        Mode.SYM2 -> "sym2_$lang"
        Mode.LETTERS -> if (shift && lang == "ar") "ar_s" else lang
    }

    fun pageTitle(page: String): String {
        val parts = page.split("_")
        val l = Cfg.langName(parts.last().let { if (it == "s") parts.first() else it })
        return when {
            page.startsWith("sym1") -> "نمادها ۱ ($l)"
            page.startsWith("sym2") -> "نمادها ۲ ($l)"
            page.endsWith("_s") -> "$l (Shift)"
            else -> "$l (حروف)"
        }
    }

    val EDITABLE_PAGES = listOf("fa", "en", "ar", "ar_s", "sym1_fa", "sym2_fa", "sym1_en", "sym2_en", "sym1_ar", "sym2_ar")

    fun pageArgs(page: String): Triple<String, Mode, Boolean> {
        val p = page.split("_")
        return when {
            page.startsWith("sym1") -> Triple(p[1], Mode.SYM1, false)
            page.startsWith("sym2") -> Triple(p[1], Mode.SYM2, false)
            page.endsWith("_s") -> Triple(p[0], Mode.LETTERS, true)
            else -> Triple(p[0], Mode.LETTERS, false)
        }
    }

    private fun rawId(k: Key, lower: Boolean): String =
        if (k.type == KT.CHAR) "c:" + (if (lower) k.out.lowercase() else k.out) else "t:" + k.type.name

    /** Stable ids for rows of keys; repeated keys get #0, #1... in default order. */
    fun ids(rows: List<List<Key>>, lower: Boolean): List<List<String>> {
        val seen = HashMap<String, Int>()
        return rows.map { r -> r.map { k -> val b = rawId(k, lower); val n = seen[b] ?: 0; seen[b] = n + 1; "$b#$n" } }
    }

    fun toJson(rows: List<List<String>>): String {
        val a = org.json.JSONArray()
        for (r in rows) { val ra = org.json.JSONArray(); r.forEach { ra.put(it) }; a.put(ra) }
        return org.json.JSONObject().put("v", 1).put("rows", a).toString()
    }

    fun fromJson(s: String?): List<List<String>>? {
        if (s.isNullOrBlank()) return null
        return try {
            val a = org.json.JSONObject(s).getJSONArray("rows")
            List(a.length()) { i -> val r = a.getJSONArray(i); List(r.length()) { j -> r.getString(j) } }
        } catch (e: Exception) { null }
    }

    /** Applies an id order to the default keys (after reconcile). */
    fun arrange(base: List<List<Key>>, order: List<List<String>>, lower: Boolean): List<List<Key>> {
        val byId = HashMap<String, Key>()
        val baseIds = ids(base, lower)
        for (r in base.indices) for (i in base[r].indices) byId[baseIds[r][i]] = base[r][i]
        return order.map { r -> r.mapNotNull { byId[it] } }
    }

    /** The editable part of a page: letters pages leave the optional digit row out. */
    fun editableBase(lang: String, mode: Mode, shift: Boolean, native: Boolean): List<List<Key>> =
        buildDefault(lang, mode, shift, false, native)

    fun build(lang: String, mode: Mode, shift: Boolean, numRow: Boolean, native: Boolean, customJson: String? = null): List<List<Key>> {
        val def = buildDefault(lang, mode, shift, numRow, native)
        val custom = fromJson(customJson) ?: return def
        val lower = lang == "en" && mode == Mode.LETTERS
        val hasDigitRow = mode == Mode.LETTERS && numRow
        val head = if (hasDigitRow) def.take(1) else emptyList()
        val body = if (hasDigitRow) def.drop(1) else def
        val order = LayoutModel.reconcile(ids(body, lower), custom, MAX_KEYS_PER_ROW)
        return head + arrange(body, order, lower)
    }

    fun buildDefault(lang: String, mode: Mode, shift: Boolean, numRow: Boolean, native: Boolean): List<List<Key>> =
        when (mode) {
            Mode.SYM1 -> symbols1(lang, native)
            Mode.SYM2 -> symbols2(lang, native)
            Mode.LETTERS -> when (lang) {
                "fa" -> persian(false, numRow, native)
                "ar" -> arabic(shift, numRow, native)
                else -> english(shift, numRow, lang, native)
            }
        }
}
