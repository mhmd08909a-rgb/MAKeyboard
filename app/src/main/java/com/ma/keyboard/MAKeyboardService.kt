package com.ma.keyboard

import android.app.AlertDialog
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MAKeyboardService : InputMethodService(), KeyboardView.Listener {

    private lateinit var column: LinearLayout
    private lateinit var toolbar: LinearLayout
    private lateinit var toolbarScroll: HorizontalScrollView
    private lateinit var suggestBar: LinearLayout
    private lateinit var suggestScroll: HorizontalScrollView
    private lateinit var root: FrameLayout
    private lateinit var panelBox: FrameLayout
    private lateinit var kb: KeyboardView
    private lateinit var feedback: Feedback
    private lateinit var clipMgr: ClipboardManager

    private var mode = Mode.LETTERS
    private var shift = 0
    private var lang = "fa"
    private var openPanel: String? = null
    private var codesUnlocked = false
    private var lastSuggestKey = ""

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener { captureClip() }

    private val prefs get() = Cfg.sp(this)
    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()

    override fun onCreate() {
        super.onCreate()
        Repo.init(this)
        feedback = Feedback(this)
        clipMgr = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipMgr.addPrimaryClipChangedListener(clipListener)
        Repo.addListener { if (::toolbar.isInitialized) mainHandlerPost { buildToolbar() } }
    }

    private fun mainHandlerPost(f: () -> Unit) = android.os.Handler(mainLooper).post { f() }

    override fun onCreateInputView(): View {
        column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        toolbar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        toolbarScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(toolbar) }
        suggestBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        suggestScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(suggestBar) }
        kb = KeyboardView(this).apply { listener = this@MAKeyboardService }
        panelBox = FrameLayout(this)
        column.addView(toolbarScroll, LinearLayout.LayoutParams(-1, -2))
        column.addView(suggestScroll, LinearLayout.LayoutParams(-1, -2))
        column.addView(kb, LinearLayout.LayoutParams(-1, -2))
        column.addView(panelBox, LinearLayout.LayoutParams(-1, -2))
        root = FrameLayout(this)
        root.addView(column, FrameLayout.LayoutParams(-1, -2))
        applyColors(Cfg.load(this))
        return root
    }

    /** Opaque themed backgrounds: version 1 left the toolbar transparent, which made icons fade. */
    private fun applyColors(cfg: KbConfig) {
        if (!::root.isInitialized) return
        root.setBackgroundColor(cfg.palette.bg)
        column.setBackgroundColor(cfg.palette.bg)
        toolbarScroll.setBackgroundColor(cfg.palette.toolbar)
        suggestScroll.setBackgroundColor(cfg.palette.toolbar)
        panelBox.setBackgroundColor(cfg.palette.toolbar)
    }

    /**
     * Height budget for keyboard + panels. Uses the real screen height (any aspect ratio,
     * portrait or landscape) minus the toolbar and suggestion strip, so no row is pushed
     * below the visible area.
     */
    private fun maxBodyHeight(cfg: KbConfig): Int {
        val dm = resources.displayMetrics
        val landscape = dm.widthPixels > dm.heightPixels
        val total = (dm.heightPixels * (if (landscape) 0.58f else 0.62f)).toInt()
        return (total - ToolbarUi.heightPx(this, cfg) - dp(34f)).coerceAtLeast(dp(150f))
    }

    private fun keyboardWidthPx(cfg: KbConfig): Int {
        val screenW = resources.displayMetrics.widthPixels
        return when {
            cfg.oneHanded != "off" -> (screenW * cfg.oneHandPct).toInt()
            else -> (screenW * cfg.kbWidthPct).toInt()
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        if (!::kb.isInitialized) return
        feedback.reload(prefs)
        val langs = Cfg.languages(prefs)
        // keep the language the user picked last time instead of always jumping back to the first one
        lang = prefs.getString("last_lang", null)?.takeIf { it in langs } ?: langs.firstOrNull() ?: "fa"
        kb.cancelTouch()
        val cfg = Cfg.load(this)
        kb.setConfig(cfg)
        kb.clearArtCache()
        kb.maxHeightPx = maxBodyHeight(cfg)
        applyColors(cfg)
        kb.enterAction = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION) ?: EditorInfo.IME_ACTION_NONE
        applyOneHand()
        buildToolbar()
        showPanel(null)
        render()
        clearSuggest()
    }

    private fun applyOneHand() {
        val cfg = Cfg.load(this)
        val full = cfg.oneHanded == "off" && cfg.kbWidthPct >= 0.999f
        val w = if (full) -1 else keyboardWidthPx(cfg)
        val grav = when (cfg.oneHanded) {
            "right" -> Gravity.END or Gravity.BOTTOM
            "left" -> Gravity.START or Gravity.BOTTOM
            else -> Gravity.CENTER_HORIZONTAL or Gravity.BOTTOM
        }
        val params = FrameLayout.LayoutParams(w, -2, grav)
        column.layoutParams = params
    }

    private fun render() {
        kb.keyArtDir = when (lang) {
            "en" -> if (prefs.getBoolean("key_art_en", true)) "en" else null
            "fa" -> if (prefs.getBoolean("key_art_fa", true)) prefs.getString("fa_pack", "fa") else null
            else -> null
        }
        val page = Layouts.pageKey(lang, mode, shift > 0)
        val lay = Layouts.build(lang, mode, shift > 0, prefs.getBoolean("num_row", false), prefs.getBoolean("native_digits", false),
            Cfg.layoutJson(prefs, page))
        kb.setRows(lay)
        kb.shiftState = shift
    }

    // ---------------- toolbar ----------------

    private fun buildToolbar() {
        if (!::toolbar.isInitialized) return
        val cfg = Cfg.load(this)
        val private = prefs.getBoolean("private", false)
        val due = dueCount()
        val ids = cfg.toolbarOrder.filter { it !in cfg.toolbarHidden && !(private && (it == "clipboard" || it == "codes")) }
            .toMutableList()
        // settings must stay reachable even if the user hid it
        if ("settings" !in ids) ids.add("settings")
        ToolbarUi.fill(this, toolbar, cfg, keyboardWidthPx(cfg), ids) { id ->
            IconView(this, id).apply {
                active = when (id) {
                    "private" -> private
                    "onehand" -> cfg.oneHanded != "off"
                    else -> openPanel == id
                }
                dot = id == "reminder" && due > 0
                setOnClickListener { onTool(id) }
            }
        }
    }

    private fun onTool(id: String) {
        feedback.key(Feedback.KEY)
        when (id) {
            "private" -> {
                val now = !prefs.getBoolean("private", false)
                prefs.edit().putBoolean("private", now).apply()
                if (now) clearSuggest()
                buildToolbar()
                toast(if (now) "حالت خصوصی فعال شد" else "حالت خصوصی غیرفعال شد")
            }
            "onehand" -> {
                val cur = prefs.getString("one_hand", "off") ?: "off"
                val next = when (cur) { "off" -> "right"; "right" -> "left"; else -> "off" }
                prefs.edit().putString("one_hand", next).apply()
                applyOneHand(); buildToolbar()
            }
            "settings" -> {
                val i = Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(i)
            }
            else -> {
                if (openPanel == id) showPanel(null) else showPanel(id)
            }
        }
    }

    private fun dueCount(): Int {
        val now = System.currentTimeMillis()
        return Repo.all(Kind.NOTE).count { it.dueAt > 0 && it.dueAt <= now && !it.done && !it.seen }
    }

    // ---------------- panels ----------------

    private fun showPanel(id: String?) {
        openPanel = id
        panelBox.removeAllViews()
        if (id == null) {
            kb.visibility = View.VISIBLE
            buildToolbar()
            return
        }
        kb.visibility = View.GONE
        val view: View = when (id) {
            "clipboard" -> clipPanel()
            "emoji" -> emojiPanel()
            "calc" -> calcPanel()
            "notes", "reminder" -> notesPanel()
            "edit" -> editPanel()
            "writing" -> writingPanel()
            "snippets" -> entryPanel(Kind.SNIP)
            "codes" -> codesGate()
            else -> TextView(this)
        }
        val h = if (id == "calc") calcPanelHeight else panelHeight()
        panelBox.addView(view, FrameLayout.LayoutParams(-1, h))
        buildToolbar()
    }

    /**
     * Panels take the same height as the keyboard they replace (version 1 used a fixed 260dp,
     * which cut off the calculator's bottom row), clamped to the screen budget.
     */
    private fun panelHeight(): Int {
        val cfg = Cfg.load(this)
        val max = maxBodyHeight(cfg)
        return kb.preferredHeight().coerceIn(minOf(dp(230f), max), max)
    }

    private var calcPanelHeight = 0

    /** Panel text colour, contrast-checked against the panel background. */
    private fun panelText(cfg: KbConfig): Int = Cfg.readable(cfg.palette.keyText, cfg.palette.toolbar)

    private fun icon(id: String, cfg: KbConfig): IconView =
        IconView(this, id).apply { ToolbarUi.style(this, cfg, cfg.palette.toolbar, plates = false) }

    private fun header(title: String): LinearLayout {
        val cfg = Cfg.load(this)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(8f), 0, dp(8f), 0) }
        val tv = TextView(this).apply { text = title; textSize = 15f; setTextColor(panelText(cfg)) }
        row.addView(tv, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(icon("close", cfg).apply {
            setOnClickListener { showPanel(null) } }, LinearLayout.LayoutParams(dp(44f), dp(40f)))
        return row
    }

    private fun wrapPanel(title: String, body: View): LinearLayout {
        val cfg = Cfg.load(this)
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(cfg.palette.toolbar)
            addView(header(title), LinearLayout.LayoutParams(-1, -2))
            addView(body, LinearLayout.LayoutParams(-1, 0, 1f))
        }
    }

    private fun scrollList(): Pair<ScrollView, LinearLayout> {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6f), 0, dp(6f), dp(6f)) }
        return Pair(ScrollView(this).apply { addView(col) }, col)
    }

    private fun rowText(text: String, color: Int): TextView = TextView(this).apply {
        this.text = text; setTextColor(color); textSize = 15f
        setPadding(dp(10f), dp(8f), dp(10f), dp(8f))
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }

    private fun pill(text: String, onClick: () -> Unit): TextView {
        val cfg = Cfg.load(this)
        return TextView(this).apply {
            this.text = text; textSize = 13f; setTextColor(cfg.palette.specialText)
            setBackgroundColor(cfg.palette.special)
            setPadding(dp(12f), dp(6f), dp(12f), dp(6f))
            setOnClickListener { onClick() }
        }
    }

    private fun searchBox(hint: String, onChange: (String) -> Unit): EditText = EditText(this).apply {
        this.hint = hint; textSize = 14f; setSingleLine(true)
        addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) { onChange(s?.toString() ?: "") }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // -------- clipboard --------

    private fun captureClip() {
        if (prefs.getBoolean("private", false)) return
        try {
            val clip = clipMgr.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(this)?.toString() ?: return
            if (text.isBlank()) return
            Repo.addClip(text, prefs.getInt("clip_max", 100))
        } catch (e: Exception) { }
    }

    private fun clipPanel(): View {
        val cfg = Cfg.load(this)
        val cats = listOf("همه") + Cfg.categories(prefs, Kind.CLIP)
        var filterCat = "همه"
        var query = ""
        val (scroll, col) = scrollList()
        fun refresh() {
            col.removeAllViews()
            val items = Repo.all(Kind.CLIP)
                .filter { filterCat == "همه" || it.category == filterCat }
                .filter { query.isBlank() || it.content.contains(query, true) }
                .sortedWith(compareByDescending<Entry> { it.pinned }.thenByDescending { it.updated })
            if (items.isEmpty()) col.addView(rowText("چیزی نیست. هر چی کپی کنی اینجا میاد.", panelText(cfg)))
            for (e in items) {
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val t = rowText(TextUtil.oneLine(e.content, 120), panelText(cfg)).apply {
                    setOnClickListener { commit(e.content); }
                    setOnLongClickListener { pickCategory(e, Kind.CLIP) { refresh() }; true }
                }
                r.addView(t, LinearLayout.LayoutParams(0, -2, 1f))
                r.addView(icon("pin", cfg).apply { active = e.pinned
                    setOnClickListener { e.pinned = !e.pinned; e.updated = System.currentTimeMillis(); Repo.upsert(e); refresh() } },
                    LinearLayout.LayoutParams(dp(38f), dp(38f)))
                r.addView(icon("trash", cfg).apply {
                    setOnClickListener { Repo.delete(Kind.CLIP, e.id); refresh() } },
                    LinearLayout.LayoutParams(dp(38f), dp(38f)))
                col.addView(r, LinearLayout.LayoutParams(-1, -2))
            }
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        top.addView(searchBox("جست‌وجو در کلیپ‌بورد") { query = it; refresh() }, LinearLayout.LayoutParams(-1, -2))
        val chips = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chipScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) }
        for (c in cats) chips.addView(pill(c) { filterCat = c; refresh() }, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(3f), 0, dp(3f), 0) })
        top.addView(chipScroll, LinearLayout.LayoutParams(-1, -2))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(top)
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(pill("پاک‌کردن تاریخچه (سنجاق‌ها می‌مانند)") {
            val kept = Repo.all(Kind.CLIP).filter { it.pinned }
            Repo.replaceAll(Kind.CLIP, kept); refresh()
        }, LinearLayout.LayoutParams(-1, -2))
        refresh()
        return wrapPanel("کلیپ‌بورد", body)
    }

    private fun pickCategory(e: Entry, kind: String, done: () -> Unit) {
        val cats = Cfg.categories(prefs, kind).toTypedArray()
        AlertDialog.Builder(this).setTitle("دسته‌بندی")
            .setItems(cats) { _, i -> e.category = cats[i]; e.updated = System.currentTimeMillis(); Repo.upsert(e); done() }
            .show()
    }

    // -------- emoji --------

    private fun emojiPanel(): View {
        val cfg = Cfg.load(this)
        val all = EmojiData.load(this)
        val tabs = listOf("recent", "fav") + EmojiData.CATS
        val tabLabel = mapOf("recent" to "🕘", "fav" to "⭐", "smileys" to "😀", "people" to "🧑", "animals" to "🐱",
            "food" to "🍎", "travel" to "🚗", "activities" to "⚽", "objects" to "💡", "symbols" to "❤️", "flags" to "🏳️")
        var tab = "smileys"
        var query = ""
        val grid = GridView(this).apply { numColumns = 8; stretchMode = GridView.STRETCH_COLUMN_WIDTH; verticalSpacing = dp(2f) }
        val recent = { prefs.getString("recent_emoji", "")!!.split('\u0001').filter { it.isNotBlank() } }
        val favs = { prefs.getString("fav_emoji", "")!!.split('\u0001').filter { it.isNotBlank() } }
        var items: List<String> = emptyList()
        val adapter = object : BaseAdapter() {
            override fun getCount() = items.size
            override fun getItem(p: Int) = items[p]
            override fun getItemId(p: Int) = p.toLong()
            override fun getView(p: Int, convert: View?, parent: ViewGroup?): View {
                val tv = (convert as? TextView) ?: TextView(this@MAKeyboardService)
                tv.text = items[p]; tv.textSize = 24f; tv.gravity = Gravity.CENTER; tv.setTextColor(panelText(cfg))
                tv.layoutParams = AbsListView.LayoutParams(-1, dp(44f))
                return tv
            }
        }
        grid.adapter = adapter
        fun refresh() {
            items = when {
                query.isNotBlank() -> all.filter { it.name.contains(query, true) || it.ch.contains(query) }.map { it.ch }.take(400)
                tab == "recent" -> recent()
                tab == "fav" -> favs() + Repo.all(Kind.COMBO).map { it.content }
                else -> all.filter { it.cat == tab }.map { it.ch }
            }
            adapter.notifyDataSetChanged()
        }
        grid.setOnItemClickListener { _, _, pos, _ ->
            val s = items[pos]
            commit(s)
            val r = (listOf(s) + recent()).distinct().take(30)
            prefs.edit().putString("recent_emoji", r.joinToString("\u0001")).apply()
        }
        grid.setOnItemLongClickListener { _, _, pos, _ ->
            val s = items[pos]
            val f = favs()
            if (s !in f) prefs.edit().putString("fav_emoji", (f + s).joinToString("\u0001")).apply()
            toast("به موارد موردعلاقه اضافه شد"); refresh(); true
        }
        val top = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        top.addView(searchBox("جست‌وجوی ایموجی") { query = it; refresh() }, LinearLayout.LayoutParams(-1, -2))
        val tabRow = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val tr = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        tabRow.addView(tr)
        for (t in tabs) tr.addView(pill(tabLabel[t] ?: t) { tab = t; query = ""; refresh() }, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(2f), 0, dp(2f), 0) })
        top.addView(tabRow, LinearLayout.LayoutParams(-1, -2))
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(top)
        body.addView(grid, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(pill("ترکیب ایموجی جدید") { newCombo { refresh() } }, LinearLayout.LayoutParams(-1, -2))
        refresh()
        return wrapPanel("ایموجی", body)
    }

    private fun newCombo(done: () -> Unit) {
        val input = EditText(this).apply { hint = "ترکیب دلخواه، مثلاً ❤️🔥" }
        AlertDialog.Builder(this).setTitle("ذخیره ترکیب ایموجی").setView(input)
            .setPositiveButton("ذخیره") { _, _ ->
                val t = input.text.toString().trim()
                if (t.isNotEmpty()) {
                    Repo.upsert(Entry(id = Repo.newId(), kind = Kind.COMBO, title = t, content = t, created = System.currentTimeMillis(), updated = System.currentTimeMillis()))
                    done()
                }
            }.setNegativeButton("انصراف", null).show()
    }

    // -------- calculator --------

    /**
     * Calculator. Version 1 bug: the panel was a fixed 260dp while header + display + result +
     * 5 rows of 44dp needed about 320dp, so the bottom row ("درج نتیجه", "=") was cut off.
     * Now every height is computed (FitMath.calcPanel): the panel matches the keyboard height,
     * grows if the rows need more room, never exceeds the screen budget, and the rows share the
     * remaining space exactly, so the last row is always fully visible.
     */
    private fun calcPanel(): View {
        val cfg = Cfg.load(this)
        val p = cfg.palette
        var expr = ""
        val headerH = dp(40f); val displayH = dp(30f); val resultH = dp(32f)
        val rows = listOf(
            listOf("7", "8", "9", "÷", "⌫"),
            listOf("4", "5", "6", "×", "%"),
            listOf("1", "2", "3", "−", "("),
            listOf("0", ".", "+", ")", "C"),
            listOf("±", "درج نتیجه", "درج عبارت", "=")
        )
        val fit = FitMath.calcPanel(kb.preferredHeight().toFloat(), (headerH + displayH + resultH).toFloat(), rows.size,
            kb.rowHeight(), dp(34f).toFloat(), maxBodyHeight(cfg).toFloat())
        calcPanelHeight = fit[0].toInt()
        val rowH = fit[1].toInt().coerceAtLeast(1)
        val rowHdp = rowH / resources.displayMetrics.density
        val keyText = minOf(cfg.textSp * 0.9f, rowHdp * 0.42f).coerceAtLeast(11f)

        val display = TextView(this).apply { textSize = 18f; setTextColor(p.keyText); gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(10f), 0, dp(10f), 0); maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.START }
        val result = TextView(this).apply { textSize = 20f; setTextColor(Cfg.readable(p.accent, p.toolbar, 3.0)); gravity = Gravity.END or Gravity.CENTER_VERTICAL
            setPadding(dp(10f), 0, dp(10f), 0); maxLines = 1 }
        var lastValue: String? = null
        fun refresh() {
            display.text = expr.ifEmpty { "عبارت را بساز" }
            val (r, err) = Calc.tryEval(expr)
            lastValue = r
            result.text = r?.let { "= $it" } ?: (err ?: "")
        }
        fun capBg(base: Int): StateListDrawable {
            val rad = rowH * 0.5f * cfg.roundness
            fun d(c: Int) = GradientDrawable().apply { setColor(c); cornerRadius = rad; setStroke(dp(1.5f), p.border) }
            return StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_pressed), d(p.press))
                addState(intArrayOf(), d(base))
            }
        }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(p.toolbar) }
        col.addView(header("ماشین‌حساب"), LinearLayout.LayoutParams(-1, headerH))
        col.addView(display, LinearLayout.LayoutParams(-1, displayH))
        col.addView(result, LinearLayout.LayoutParams(-1, resultH))
        val m = dp(2f).coerceAtMost(rowH / 8)
        for (r in rows) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for (k in r) {
                val special = k.length > 1 || k in listOf("÷", "×", "−", "+", "%", "(", ")", "C", "⌫", "=", "±")
                val b = TextView(this).apply {
                    text = k; gravity = Gravity.CENTER
                    textSize = if (k.length > 2) keyText * 0.62f else keyText
                    typeface = if (KeyboardView.isArabicScript(k)) KeyboardView.vazir else KeyboardView.elite
                    setTextColor(if (special) p.specialText else p.keyText)
                    background = capBg(if (k == "=") blendColor(p.special, p.accent, 0.35f) else if (special) p.special else p.key)
                    maxLines = 1
                    setOnClickListener {
                        feedback.key(if (k == "⌫") Feedback.DEL else Feedback.KEY)
                        when (k) {
                            "⌫" -> expr = expr.dropLast(1)
                            "C" -> expr = ""
                            "=" -> lastValue?.let { expr = it }
                            "±" -> expr = toggleSign(expr)
                            "درج نتیجه" -> lastValue?.let { commit(it); showPanel(null) }
                            "درج عبارت" -> if (expr.isNotBlank()) { commit(expr + (lastValue?.let { " = $it" } ?: "")); showPanel(null) }
                            else -> expr += k
                        }
                        refresh()
                    }
                }
                val w = if (k.length > 2) 1.6f else 1f
                line.addView(b, LinearLayout.LayoutParams(0, -1, w).apply { setMargins(m, m, m, m) })
            }
            col.addView(line, LinearLayout.LayoutParams(-1, rowH))
        }
        KeyboardView.fonts(this)
        refresh()
        return col
    }

    private fun blendColor(a: Int, b: Int, t: Float): Int = android.graphics.Color.rgb(
        (android.graphics.Color.red(a) + (android.graphics.Color.red(b) - android.graphics.Color.red(a)) * t).toInt(),
        (android.graphics.Color.green(a) + (android.graphics.Color.green(b) - android.graphics.Color.green(a)) * t).toInt(),
        (android.graphics.Color.blue(a) + (android.graphics.Color.blue(b) - android.graphics.Color.blue(a)) * t).toInt())

    /** Negates the last number of the expression: "12+5" -> "12+(−5)", "12+(−5)" -> "12+5". */
    private fun toggleSign(expr: String): String {
        if (expr.isEmpty()) return "−"
        if (expr.endsWith(")")) {
            val open = expr.lastIndexOf("(−")
            if (open >= 0 && expr.substring(open + 2, expr.length - 1).all { it.isDigit() || it == '.' || it == '٫' }) {
                return expr.substring(0, open) + expr.substring(open + 2, expr.length - 1)
            }
            return expr
        }
        var i = expr.length
        while (i > 0 && (expr[i - 1].isDigit() || expr[i - 1] == '.' || expr[i - 1] == '٫')) i--
        if (i == expr.length) return expr + "−"
        return expr.substring(0, i) + "(−" + expr.substring(i) + ")"
    }

    // -------- notes / reminders --------

    private fun notesPanel(): View {
        val cfg = Cfg.load(this)
        val fmt = SimpleDateFormat("MM/dd HH:mm", Locale.US)
        val (scroll, col) = scrollList()
        fun refresh() {
            col.removeAllViews()
            val now = System.currentTimeMillis()
            val items = Repo.all(Kind.NOTE).sortedWith(
                compareByDescending<Entry> { it.pinned }.thenBy { if (it.dueAt == 0L) Long.MAX_VALUE else it.dueAt })
            if (items.isEmpty()) col.addView(rowText("یادداشتی نیست. دکمه + رو بزن.", panelText(cfg)))
            for (e in items) {
                val due = e.dueAt > 0 && e.dueAt <= now && !e.done
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                val label = buildString {
                    append(if (e.done) "✔ " else if (due) "🔔 " else "")
                    append(e.title.ifBlank { TextUtil.oneLine(e.content, 60) })
                    if (e.dueAt > 0) append("\n⏰ ").append(fmt.format(Date(e.dueAt)))
                }
                r.addView(rowText(label, if (due) cfg.palette.accent else panelText(cfg)).apply {
                    setOnClickListener { editNote(e) { refresh() } }
                    if (e.dueAt > 0 && !e.seen) { e.seen = true; Repo.upsert(e) }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                r.addView(icon("pin", cfg).apply { active = e.pinned
                    setOnClickListener { e.pinned = !e.pinned; Repo.upsert(e); refresh() } }, LinearLayout.LayoutParams(dp(36f), dp(36f)))
                r.addView(icon("check", cfg).apply {
                    setOnClickListener { e.done = !e.done; Repo.upsert(e); refresh() } }, LinearLayout.LayoutParams(dp(36f), dp(36f)))
                r.addView(icon("snooze", cfg).apply {
                    setOnClickListener { e.dueAt = (if (e.dueAt > 0) maxOf(e.dueAt, now) else now) + 10 * 60_000L; e.seen = false; e.done = false; Repo.upsert(e); refresh() } }, LinearLayout.LayoutParams(dp(36f), dp(36f)))
                r.addView(icon("trash", cfg).apply {
                    setOnClickListener { Repo.delete(Kind.NOTE, e.id); refresh() } }, LinearLayout.LayoutParams(dp(36f), dp(36f)))
                col.addView(r, LinearLayout.LayoutParams(-1, -2))
            }
        }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(pill("یادداشت جدید") { editNote(null) { refresh() } }, LinearLayout.LayoutParams(-1, -2))
        refresh()
        return wrapPanel("یادداشت و یادآور", body)
    }

    private fun editNote(e: Entry?, done: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16f), dp(8f), dp(16f), 0) }
        val title = EditText(this).apply { hint = "عنوان"; setText(e?.title ?: "") }
        val content = EditText(this).apply { hint = "متن یادداشت"; setText(e?.content ?: "") }
        val options = listOf("بدون یادآور", "۱۰ دقیقه دیگر", "یک ساعت دیگر", "فردا همین ساعت")
        val spin = Spinner(this).apply { adapter = ArrayAdapter(this@MAKeyboardService, android.R.layout.simple_spinner_dropdown_item, options) }
        box.addView(title); box.addView(content); box.addView(spin)
        AlertDialog.Builder(this).setTitle(if (e == null) "یادداشت جدید" else "ویرایش یادداشت").setView(box)
            .setPositiveButton("ذخیره") { _, _ ->
                val now = System.currentTimeMillis()
                val due = when (spin.selectedItemPosition) { 1 -> now + 600_000L; 2 -> now + 3_600_000L; 3 -> now + 86_400_000L; else -> 0L }
                val n = e ?: Entry(id = Repo.newId(), kind = Kind.NOTE, created = now)
                n.title = title.text.toString(); n.content = content.text.toString()
                if (due > 0 || spin.selectedItemPosition == 0) { n.dueAt = due; n.seen = false; n.done = false }
                n.updated = now
                Repo.upsert(n); done()
            }
            .setNegativeButton("انصراف", null)
            .setNeutralButton("حذف") { _, _ -> e?.let { Repo.delete(Kind.NOTE, it.id) }; done() }
            .show()
    }

    // -------- edit controls --------

    private fun editPanel(): View {
        val cfg = Cfg.load(this)
        val ic = { currentInputConnection }
        fun cursorPos(): Int = ic()?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.selectionStart ?: 0
        fun textLen(): Int = ic()?.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0)?.text?.length ?: 0
        fun moveTo(p: Int) { ic()?.setSelection(p.coerceAtLeast(0), p.coerceAtLeast(0)) }
        fun menu(id: Int, name: String) {
            val ok = ic()?.performContextMenuAction(id) ?: false
            if (!ok) toast("این برنامه اجازه $name نداد")
        }
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val rows = listOf(
            listOf("⏮" to { moveTo(0) }, "◀" to { moveTo(cursorPos() - 1) }, "▶" to { moveTo(cursorPos() + 1) }, "⏭" to { moveTo(textLen()) }),
            listOf("انتخاب همه" to { menu(android.R.id.selectAll, "انتخاب") }, "کپی" to { menu(android.R.id.copy, "کپی") },
                "برش" to { menu(android.R.id.cut, "برش") }, "جای‌گذاری" to { menu(android.R.id.paste, "جای‌گذاری") }),
            listOf("حذف کلمه" to { deleteWordBefore() }, "⌫" to { deleteOne() }, "⏎" to { enterPressed() }, "فاصله" to { commit(" ") }),
            listOf("(" to { commit("(") }, ")" to { commit(")") }, "«" to { commit("«") }, "»" to { commit("»") },
                "\"" to { commit("\"") }, "؟" to { commit("؟") }, "!" to { commit("!") }, "." to { commit(".") })
        )
        for (r in rows) {
            val line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            for ((label, act) in r) {
                line.addView(TextView(this).apply {
                    text = label; gravity = Gravity.CENTER; textSize = 14f; setTextColor(cfg.palette.specialText)
                    setBackgroundColor(cfg.palette.special)
                    setOnClickListener { feedback.key(Feedback.KEY); act() }
                }, LinearLayout.LayoutParams(0, dp(44f), 1f).apply { setMargins(dp(2f), dp(2f), dp(2f), dp(2f)) })
            }
            grid.addView(line, LinearLayout.LayoutParams(-1, -2))
        }
        return wrapPanel("کنترل متن", grid)
    }

    private fun deleteWordBefore() {
        val ic = currentInputConnection ?: return
        val sel = try { ic.getSelectedText(0) } catch (e: Exception) { null }
        if (!sel.isNullOrEmpty()) { ic.commitText("", 1); return }
        val before = ic.getTextBeforeCursor(60, 0)?.toString().orEmpty()
        val trimmed = before.trimEnd()
        val idx = trimmed.lastIndexOf(' ')
        val n = if (idx < 0) trimmed.length else before.length - (idx + 1)
        ic.deleteSurroundingText(maxOf(n, 1), 0)
    }

    // -------- writing mode --------

    private fun writingPanel(): View {
        val cfg = Cfg.load(this)
        val (scroll, col) = scrollList()
        fun section(t: String) = rowText("— $t —", cfg.palette.accent).apply { textSize = 13f; gravity = Gravity.CENTER }
        fun item(t: String, content: String) = rowText(TextUtil.oneLine(t.ifBlank { content }, 90), panelText(cfg)).apply {
            setOnClickListener { commit(content) }
        }
        col.addView(section("نشانه‌گذاری"))
        val punct = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for (p in listOf("،", "؛", "؟", "!", ".", "…", "«»", "\"", "(", ")")) {
            punct.addView(pill(p) { commit(p) }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        col.addView(punct)
        col.addView(section("جملات و نقل‌قول‌ها"))
        for (e in Repo.all(Kind.SNIP)) col.addView(item(e.title, e.content))
        col.addView(section("یادداشت‌های سنجاق‌شده"))
        for (e in Repo.all(Kind.NOTE).filter { it.pinned }) col.addView(item(e.title, e.content))
        col.addView(section("ترکیب ایموجی"))
        for (e in Repo.all(Kind.COMBO)) col.addView(item(e.title, e.content))
        col.addView(section("کلیپ‌بورد سنجاق‌شده"))
        if (!prefs.getBoolean("private", false))
            for (e in Repo.all(Kind.CLIP).filter { it.pinned }) col.addView(item("", e.content))
        return wrapPanel("حالت نویسندگی", scroll)
    }

    // -------- generic list panel for snippets and codes --------

    private fun codesGate(): View {
        if (prefs.getBoolean("private", false)) return rowText("حالت خصوصی روشن است.", panelText(Cfg.load(this)))
        if (Pin.locked(prefs) && !codesUnlocked) {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16f), dp(16f), dp(16f), dp(16f)) }
            val pin = EditText(this).apply { hint = "PIN"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
            box.addView(pin)
            box.addView(pill("باز کردن") {
                if (Pin.verify(prefs, pin.text.toString())) { codesUnlocked = true; showPanel("codes") }
                else toast("PIN اشتباه است")
            }, LinearLayout.LayoutParams(-1, -2))
            return wrapPanel("کدگذاری کلمه", box)
        }
        return entryPanel(Kind.CODE)
    }

    private fun entryPanel(kind: String): View {
        val cfg = Cfg.load(this)
        val title = if (kind == Kind.CODE) "کدگذاری کلمه" else "میان‌برها"
        val (scroll, col) = scrollList()
        var query = ""
        fun refresh() {
            col.removeAllViews()
            val items = Repo.all(kind).filter { query.isBlank() || it.trigger.contains(query, true) || it.title.contains(query, true) || it.content.contains(query, true) }
            if (items.isEmpty()) col.addView(rowText("موردی نیست. با دکمه افزودن بساز.", panelText(cfg)))
            for (e in items) {
                val shown = if (kind == Kind.CODE) TextUtil.mask(e.content) else TextUtil.oneLine(e.content, 60)
                val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                r.addView(rowText("${e.title.ifBlank { e.trigger }}  •  ${e.trigger}\n$shown", panelText(cfg)).apply {
                    setOnClickListener { commit(e.content) }
                    setOnLongClickListener { editEntry(kind, e) { refresh() }; true }
                }, LinearLayout.LayoutParams(0, -2, 1f))
                r.addView(icon("trash", cfg).apply {
                    setOnClickListener { Repo.delete(kind, e.id); refresh() } }, LinearLayout.LayoutParams(dp(38f), dp(38f)))
                col.addView(r, LinearLayout.LayoutParams(-1, -2))
            }
        }
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        body.addView(searchBox("جست‌وجو") { query = it; refresh() }, LinearLayout.LayoutParams(-1, -2))
        body.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body.addView(pill("افزودن") { editEntry(kind, null) { refresh() } }, LinearLayout.LayoutParams(-1, -2))
        refresh()
        return wrapPanel(title, body)
    }

    private fun editEntry(kind: String, e: Entry?, done: () -> Unit) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(16f), dp(8f), dp(16f), 0) }
        val name = EditText(this).apply { hint = "نام"; setText(e?.title ?: "") }
        val trig = EditText(this).apply { hint = if (kind == Kind.CODE) "عبارت محرک، مثلاً ملی" else "میان‌بر، مثلاً ;em"; setText(e?.trigger ?: "") }
        val content = EditText(this).apply { hint = "مقدار یا متن"; setText(e?.content ?: "") }
        val modeSpin = Spinner(this).apply {
            adapter = ArrayAdapter(this@MAKeyboardService, android.R.layout.simple_spinner_dropdown_item,
                listOf("تطبیق دقیق عبارت", "با میان‌بر ؛ (مثل ;ملی)"))
            setSelection(e?.mode ?: 0)
            visibility = if (kind == Kind.CODE) View.VISIBLE else View.GONE
        }
        box.addView(name); box.addView(trig); box.addView(content); box.addView(modeSpin)
        AlertDialog.Builder(this).setTitle(if (e == null) "افزودن" else "ویرایش").setView(box)
            .setPositiveButton("ذخیره") { _, _ ->
                var t = trig.text.toString().trim()
                if (kind == Kind.SNIP && !t.startsWith(";")) t = ";$t"
                val n = e ?: Entry(id = Repo.newId(), kind = kind, created = System.currentTimeMillis())
                n.title = name.text.toString(); n.trigger = t; n.content = content.text.toString()
                n.mode = if (kind == Kind.CODE) modeSpin.selectedItemPosition else 0
                n.updated = System.currentTimeMillis()
                if (t.isNotBlank() && n.content.isNotEmpty()) { Repo.upsert(n); done() }
                else toast("عبارت و مقدار هر دو لازم‌اند")
            }
            .setNegativeButton("انصراف", null)
            .setNeutralButton("حذف") { _, _ -> e?.let { Repo.delete(kind, it.id) }; done() }
            .show()
    }

    // ---------------- suggestions (never auto-inserted) ----------------

    private fun isSensitive(): Boolean {
        val t = currentInputEditorInfo?.inputType ?: return false
        val v = t and InputType.TYPE_MASK_VARIATION
        return v == InputType.TYPE_TEXT_VARIATION_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
            v == InputType.TYPE_NUMBER_VARIATION_PASSWORD || v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
    }

    private fun updateSuggestions() {
        if (!::suggestBar.isInitialized) return
        suggestBar.removeAllViews()
        if (prefs.getBoolean("private", false) || isSensitive()) return
        val before = currentInputConnection?.getTextBeforeCursor(40, 0)?.toString().orEmpty()
        val token = before.takeLastWhile { !it.isWhitespace() }
        if (token.isEmpty()) return
        val cfg = Cfg.load(this)
        val hits = mutableListOf<Entry>()
        for (e in Repo.all(Kind.CODE)) {
            if (!e.enabled || e.trigger.isEmpty()) continue
            val matches = if (e.mode == 1) token == ";" + e.trigger else TextUtil.norm(token) == TextUtil.norm(e.trigger)
            if (matches) hits.add(e)
        }
        if (token.startsWith(";")) for (e in Repo.all(Kind.SNIP)) if (e.enabled && e.trigger.startsWith(token) && e.trigger.length > 1) hits.add(e)
        if (hits.isEmpty()) return
        for (e in hits.take(4)) {
            suggestBar.addView(pill("${e.title.ifBlank { e.trigger }}: ${if (e.kind == Kind.CODE) TextUtil.mask(e.content) else TextUtil.oneLine(e.content, 20)}") {
                currentInputConnection?.deleteSurroundingText(token.length, 0)
                commit(e.content)
                suggestBar.removeAllViews()
            }, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(3f), dp(3f), dp(3f), dp(3f)) })
        }
    }

    private fun clearSuggest() { if (::suggestBar.isInitialized) suggestBar.removeAllViews() }

    // ---------------- key handling ----------------

    private fun commit(s: String) {
        currentInputConnection?.commitText(s, 1)
        updateSuggestions()
    }

    override fun onPress(k: Key) {
        feedback.key(when (k.type) {
            KT.SPACE -> Feedback.SPACE
            KT.DEL -> Feedback.DEL
            KT.ENTER -> Feedback.ENTER
            else -> Feedback.KEY
        })
    }

    override fun onKey(k: Key, out: String) {
        when (k.type) {
            KT.CHAR, KT.ZWNJ -> {
                currentInputConnection?.commitText(out, 1)
                if (shift == 1) { shift = 0; render() }
                updateSuggestions()
            }
            KT.SPACE -> { currentInputConnection?.commitText(" ", 1); clearSuggest() }
            KT.DEL -> { deleteOne(); updateSuggestions() }
            KT.ENTER -> { enterPressed(); clearSuggest() }
            KT.SHIFT -> { shift = when (shift) { 0 -> 1; 1 -> 2; else -> 0 }; render() }
            KT.SYM -> { mode = Mode.SYM1; render() }
            KT.PAGE2 -> { mode = Mode.SYM2; render() }
            KT.PAGE1 -> { mode = Mode.SYM1; render() }
            KT.ABC -> { mode = Mode.LETTERS; render() }
            KT.LANG -> switchLang()
            KT.CALC -> showPanel("calc")
            else -> {}
        }
    }

    override fun onLongKey(k: Key): Boolean = false

    /** Called only while the delete key is held (the tap's own deletion already happened). */
    override fun onDeleteRepeat(step: Int) {
        if (currentInputConnection == null) return
        if (step > 24) deleteWordBefore() else deleteOne()
        feedback.sound(Feedback.DEL)
        if (step % 6 == 0) updateSuggestions()
    }

    /**
     * Deletes exactly one user-visible character (or the current selection), Android-style:
     *  - selection -> removed as a whole (like the system keyboard)
     *  - emoji / ZWJ sequences / flags / keycaps -> removed as one unit (DeleteLogic)
     *  - fields without text access (terminals, some games, TYPE_NULL) or an empty field -> a real
     *    KEYCODE_DEL key event, which those apps expect
     */
    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        if (info != null && info.inputType == InputType.TYPE_NULL) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return }
        val sel = try { ic.getSelectedText(0) } catch (e: Exception) { null }
        if (!sel.isNullOrEmpty()) { ic.commitText("", 1); return }
        val before = ic.getTextBeforeCursor(48, 0)
        if (before.isNullOrEmpty()) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return }
        val n = DeleteLogic.charsToDelete(before)
        if (n <= 0) { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return }
        ic.deleteSurroundingText(n, 0)
    }

    override fun onCursorMove(steps: Int) {
        val ic = currentInputConnection ?: return
        val et = ic.getExtractedText(android.view.inputmethod.ExtractedTextRequest(), 0) ?: return
        val pos = (et.selectionStart + steps).coerceIn(0, et.text?.length ?: 0)
        ic.setSelection(pos, pos)
    }

    private fun enterPressed() {
        val ic = currentInputConnection ?: return
        val info = currentInputEditorInfo
        val opts = info?.imeOptions ?: 0
        val action = opts and EditorInfo.IME_MASK_ACTION
        val noEnter = opts and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0
        val multi = info != null && (info.inputType and InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0
        if (!noEnter && !multi && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else ic.commitText("\n", 1)
    }

    private fun switchLang() {
        val langs = Cfg.languages(prefs)
        val i = langs.indexOf(lang)
        lang = langs[(i + 1) % langs.size]
        prefs.edit().putString("last_lang", lang).apply()
        mode = Mode.LETTERS
        shift = 0
        render()
    }

    override fun onWindowHidden() {
        super.onWindowHidden()
        if (::kb.isInitialized) kb.cancelTouch()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        super.onFinishInputView(finishingInput)
        if (::kb.isInitialized) kb.cancelTouch()
        codesUnlocked = false
        clearSuggest()
    }

    override fun onDestroy() {
        try { clipMgr.removePrimaryClipChangedListener(clipListener) } catch (e: Exception) { }
        feedback.release()
        super.onDestroy()
    }
}
