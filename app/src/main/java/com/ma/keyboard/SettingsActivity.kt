package com.ma.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import android.widget.HorizontalScrollView
import org.json.JSONArray
import org.json.JSONObject
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File

/** Full settings: live keyboard preview, appearance, sound/vibration, privacy, backup and reset. */
/**
 * Settings with a real live preview (same KeyboardView + toolbar code as the keyboard).
 * Appearance, size, toolbar and colour changes go to a draft first: the preview updates
 * immediately, the keyboard only after "ذخیره". Sound, privacy and backup save directly as before.
 */
class SettingsActivity : Activity() {

    private lateinit var preview: KeyboardView
    private lateinit var previewBar: LinearLayout
    private lateinit var previewBox: LinearLayout
    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var draft: DraftPrefs
    private lateinit var status: TextView
    private val syncers = mutableListOf<() -> Unit>()
    private var pendingBackupPw = ""
    private var pendingExport: String? = null

    companion object {
        const val REQ_EXPORT = 41
        const val REQ_IMPORT = 42
        const val REQ_ART = 43
        val EN_ART = "qwertyuiopasdfghjklzxcvbnm".map { it.toString() } + "space"
        val FA_ART = "ضصثقفغعهخحجچشسیبلاتنمکگظطزرذدپو".map { it.toString() } + "1234567890" + "space"
        val SWATCHES = listOf("#EFE5CF", "#FBF6EA", "#1C1815", "#2E2A24", "#3B2F25", "#D9A441", "#B5473A", "#3F6FD8", "#E1D2B3", "#F0E6D2", "#FFFFFF", "#000000")
        val SWATCH_NAMES = listOf("کرم کاغذی", "کرم روشن", "مشکی قهوه‌ای", "خاکستری تیره", "قهوه‌ای تیره", "طلایی", "قرمز آجری", "آبی", "بژ", "کرم ملایم", "سفید", "مشکی")
        val COLOR_LABELS = Cfg.COLOR_KEYS
        val SHAPES = listOf("round", "circle", "square")
        val SHAPE_TITLES = listOf("گرد ماشین‌تحریری (پهن و بزرگ)", "دایره کلاسیک", "مربع گوشه‌گرد")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        prefs = Cfg.sp(this)
        draft = DraftPrefs(prefs)
        KeyboardView.fonts(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // ---- live preview (fixed at the top, does not scroll away) ----
        previewBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        previewBar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        previewBox.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(previewBar) },
            LinearLayout.LayoutParams(-1, -2))
        preview = KeyboardView(this)
        preview.maxHeightPx = (resources.displayMetrics.heightPixels * 0.42f).toInt()
        previewBox.addView(preview, LinearLayout.LayoutParams(-1, -2))
        root.addView(TextView(this).apply { text = "پیش‌نمایش زنده (همان کیبورد واقعی)"; textSize = 13f; setPadding(dp(14f), dp(8f), dp(14f), dp(4f)) })
        root.addView(previewBox, LinearLayout.LayoutParams(-1, -2))

        // ---- save bar ----
        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10f), dp(4f), dp(10f), dp(4f)) }
        status = TextView(this).apply { textSize = 13f }
        bar.addView(status, LinearLayout.LayoutParams(0, -2, 1f))
        bar.addView(Button(this).apply { text = "ذخیره"; setOnClickListener { saveDraft() } })
        bar.addView(Button(this).apply { text = "لغو تغییرات"; setOnClickListener { discardDraft() } })
        root.addView(bar, LinearLayout.LayoutParams(-1, -2))

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(14f), dp(6f), dp(14f), dp(40f)) }

        section(col, "اندازهٔ کلیدها و کیبورد")
        seek(col, "اندازهٔ کلی (٪)", "scale", 60, 160, Cfg.DEF_SCALE)
        seek(col, "ارتفاع ردیف کلیدها (dp)", "row_h", 32, 110, Cfg.DEF_ROW_H)
        seek(col, "ارتفاع خود کلید (٪ از ردیف)", "key_hp", 50, 100, Cfg.DEF_KEY_HP)
        seek(col, "عرض کلید (٪ از خانه)", "key_w", 50, 100, Cfg.DEF_KEY_W)
        seek(col, "فاصلهٔ افقی کلیدها (dp)", "gap_h", 0, 16, Cfg.DEF_GAP_H)
        seek(col, "فاصلهٔ عمودی کلیدها (dp)", "gap_v", 0, 20, Cfg.DEF_GAP_V)
        seek(col, "اندازهٔ حروف (sp)", "text_sp", 10, 44, Cfg.DEF_TEXT)
        seek(col, "عرض کلی کیبورد (٪ صفحه)", "kb_w", 60, 100, Cfg.DEF_KB_W)
        seek(col, "ارتفاع کلی کیبورد (٪)", "kb_hp", 70, 150, Cfg.DEF_KB_HP)
        spinner(col, "شکل کلید", SHAPE_TITLES, { SHAPES.indexOf(draft.getString("key_shape", Cfg.DEF_SHAPE)).coerceAtLeast(0) }) {
            draft.edit().putString("key_shape", SHAPES[it]).apply()
        }
        seek(col, "گردی کلیدها (٪)", "round", 0, 100, Cfg.DEF_ROUND)
        col.addView(note("اگر همهٔ ردیف‌ها در صفحه جا نشوند، کیبورد ارتفاع ردیف‌ها را خودکار کم می‌کند تا هیچ کلیدی بیرون نرود."))
        col.addView(Button(this).apply { text = "بازگرداندن اندازه‌های پیش‌فرض"; setOnClickListener { resetDraft(Cfg.SIZE_KEYS, "اندازه‌ها") } })

        section(col, "نوار ابزار بالای حروف")
        seek(col, "ارتفاع نوار ابزار (dp)", "tb_h", 30, 80, Cfg.DEF_TB_H)
        seek(col, "اندازهٔ آیکون‌ها (٪)", "tb_icon", 45, 95, Cfg.DEF_TB_ICON)
        seek(col, "ضخامت خطوط آیکون (×۰٫۱)", "tb_stroke", 14, 40, Cfg.DEF_TB_STROKE)
        seek(col, "فاصلهٔ بین آیکون‌ها (dp)", "tb_gap", 0, 20, Cfg.DEF_TB_GAP)
        switch(col, "زمینهٔ گرد پشت آیکون‌ها", "tb_plate", true)
        col.addView(note("رنگ آیکون‌ها از تم می‌آید، اما اگر کنتراستش با نوار کم باشد خودکار پررنگ‌تر می‌شود. اگر آیکون‌ها جا نشوند، نوار قابل اسکرول است."))
        col.addView(Button(this).apply { text = "بازگرداندن نوار ابزار"; setOnClickListener { resetDraft(Cfg.TOOLBAR_KEYS, "نوار ابزار") } })

        section(col, "تم و رنگ")
        val themeNames = Themes.ids
        spinner(col, "تم", themeNames.map { Themes.title(it) }, { themeNames.indexOf(draft.getString("theme", "mawriter")).coerceAtLeast(0) }) {
            draft.edit().putString("theme", themeNames[it]).apply()
        }
        col.addView(Button(this).apply { text = "انتخاب رنگ‌ها"; setOnClickListener { pickColors() } })
        switch(col, "نمایش راهنمای کلید (حروف کوچک گوشه)", "hints", true)
        switch(col, "کلیدهای سه‌بعدی با حلقه فلزی", "key_3d", true)
        switch(col, "لوگوی باشگاه روی حروف انگلیسی", "key_art_en", true)
        switch(col, "عکس بازیکنان روی حروف فارسی", "key_art_fa", true)
        spinner(col, "پک تصاویر حروف فارسی", listOf("بازیکنان فوتبال", "بازیگران و هنرمندان"),
            { listOf("fa", "star").indexOf(draft.getString("fa_pack", "fa")).coerceAtLeast(0) }) {
            draft.edit().putString("fa_pack", listOf("fa", "star")[it]).apply()
        }
        col.addView(Button(this).apply { text = "تغییر تصاویر کلیدها"; setOnClickListener { pickArt() } })
        switch(col, "نمایش ردیف اعداد", "num_row", false)
        spinner(col, "کیبورد یک‌دستی", listOf("خاموش", "راست", "چپ"),
            { listOf("off", "right", "left").indexOf(draft.getString("one_hand", "off")).coerceAtLeast(0) }) {
            draft.edit().putString("one_hand", listOf("off", "right", "left")[it]).apply()
        }
        seek(col, "عرض حالت یک‌دستی (٪)", "one_hand_pct", 60, 95, 82)
        col.addView(note("تغییر تم فقط رنگ‌ها را عوض می‌کند؛ چیدمان و اندازه‌ها دست نمی‌خورند."))
        col.addView(Button(this).apply { text = "بازگرداندن تم و رنگ‌ها"; setOnClickListener { resetDraft(Cfg.THEME_KEYS, "رنگ‌ها") } })

        section(col, "چیدمان کلیدها")
        col.addView(Button(this).apply { text = "ویرایش چیدمان کیبورد"; setOnClickListener {
            startActivity(Intent(this@SettingsActivity, LayoutEditorActivity::class.java)) } })
        col.addView(Button(this).apply { text = "بازگردانی همهٔ چیدمان‌ها به پیش‌فرض"; setOnClickListener {
            AlertDialog.Builder(this@SettingsActivity).setMessage("ترتیب کلیدهای همهٔ صفحه‌ها به حالت پیش‌فرض برگردد؟ (رنگ و اندازه تغییر نمی‌کند)")
                .setPositiveButton("بله") { _, _ -> Cfg.resetLayouts(prefs); refreshPreview(); toast("چیدمان پیش‌فرض شد") }
                .setNegativeButton("نه", null).show() } })

        section(col, "صدا و لرزش")
        directSwitch(col, "صدای کلیدها", "sound_on", true)
        directSeek(col, "شدت صدا", "volume", 0, 100, 70)
        directSpinner(col, "صدای تایپ", Feedback.SETS.map { Feedback.setTitle(it) }, Feedback.SETS.indexOf(prefs.getString("sound_set", "mawriter")).coerceAtLeast(0)) {
            prefs.edit().putString("sound_set", Feedback.SETS[it]).apply()
        }
        directSwitch(col, "صدای کلید فاصله", "snd_space", true)
        directSwitch(col, "صدای کلید حذف", "snd_del", true)
        directSwitch(col, "صدای ENTER", "snd_enter", true)
        directSwitch(col, "صدای کلیدهای حروف", "snd_key", true)
        directSwitch(col, "در حالت بی‌صدا، صدا پخش نشود", "silent_respect", true)
        directSwitch(col, "لرزش فیزیکی", "vib_on", true)
        directSeek(col, "شدت لرزش", "vib_amp", 1, 255, 60)
        directSeek(col, "مدت لرزش (ms)", "vib_ms", 5, 60, 18)

        section(col, "حریم خصوصی و امنیت")
        directSwitch(col, "حالت خصوصی فوری", "private", false)
        directSwitch(col, "قفل کدگذاری کلمه با PIN", "codes_lock", false)
        col.addView(Button(this).apply { text = if (Pin.isSet(prefs)) "تغییر PIN" else "تعیین PIN"; setOnClickListener { setPin() } })
        col.addView(Button(this).apply { text = "حذف PIN"; setOnClickListener { Pin.clear(prefs); toast("PIN حذف شد") } })
        col.addView(TextView(this).apply { textSize = 13f; setPadding(0, dp(6f), 0, dp(6f))
            text = "هیچ داده‌ای به اینترنت فرستاده نمی‌شود. کلیپ‌بورد فقط وقتی کپی می‌شود ذخیره می‌شود و در حالت خصوصی ذخیره نمی‌شود." })

        section(col, "پشتیبان‌گیری")
        val pw = EditText(this).apply { hint = "رمز پشتیبان (اجباری)"; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        col.addView(pw)
        col.addView(Button(this).apply { text = "ذخیره پشتیبان رمزگذاری‌شده"; setOnClickListener {
            val p = pw.text.toString(); if (p.length < 6) { toast("رمز حداقل ۶ کاراکتر"); return@setOnClickListener }
            pendingBackupPw = p
            pendingExport = buildBackupJson()
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                .setType("application/json").putExtra(Intent.EXTRA_TITLE, "makeyboard-backup.json"), REQ_EXPORT)
        } })
        col.addView(Button(this).apply { text = "بازیابی از فایل پشتیبان"; setOnClickListener {
            val p = pw.text.toString(); if (p.isEmpty()) { toast("رمز پشتیبان را وارد کن"); return@setOnClickListener }
            pendingBackupPw = p
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), REQ_IMPORT)
        } })
        col.addView(TextView(this).apply { textSize = 13f; text = "فایل پشتیبان فقط روی همین گوشی ساخته و در جای انتخابی‌ات ذخیره می‌شود (مثلاً Downloads). ما جایی آپلودش نمی‌کنیم." })

        section(col, "بازگرداندن کل تنظیمات")
        col.addView(Button(this).apply { text = "بازگرداندن همه تنظیمات و داده‌ها (PIN می‌ماند)"; setOnClickListener {
            AlertDialog.Builder(this@SettingsActivity).setMessage("همه کلیپ‌بورد، یادداشت‌ها، میان‌برها، چیدمان و تنظیمات پاک شوند؟")
                .setPositiveButton("بله، پاک کن") { _, _ -> Cfg.resetAll(this@SettingsActivity); draft.discard(); toast("انجام شد"); recreate() }
                .setNegativeButton("نه", null).show() } })

        root.addView(ScrollView(this).apply { addView(col) }, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        refreshPreview()
    }

    override fun onResume() {
        super.onResume()
        if (::preview.isInitialized) refreshPreview()   // layout may have changed in the editor
    }

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    private fun note(t: String) = TextView(this).apply { text = t; textSize = 12f; setPadding(0, dp(2f), 0, dp(6f)); alpha = 0.8f }

    private fun refreshPreview() {
        val cfg = Cfg.load(draft)
        preview.setConfig(cfg)
        preview.setRows(Layouts.build("fa", Mode.LETTERS, false, draft.getBoolean("num_row", false), false, Cfg.layoutJson(prefs, "fa")))
        previewBox.setBackgroundColor(cfg.palette.bg)
        previewBar.setBackgroundColor(cfg.palette.toolbar)
        val screenW = resources.displayMetrics.widthPixels
        val w = if (cfg.oneHanded != "off") (screenW * cfg.oneHandPct).toInt() else (screenW * cfg.kbWidthPct).toInt()
        preview.layoutParams = LinearLayout.LayoutParams(w, -2).apply {
            gravity = when (cfg.oneHanded) { "right" -> Gravity.END; "left" -> Gravity.START; else -> Gravity.CENTER_HORIZONTAL }
        }
        val ids = cfg.toolbarOrder.filter { it !in cfg.toolbarHidden }.ifEmpty { listOf("settings") }
        ToolbarUi.fill(this, previewBar, cfg, w, ids) { IconView(this, it) }
        status.text = if (draft.dirty) "تغییرات ذخیره نشده" else "همه تغییرات ذخیره شده"
    }

    private fun saveDraft() {
        draft.save()
        refreshPreview()
        toast("ذخیره شد؛ دفعهٔ بعد که کیبورد باز شود اعمال می‌شود")
    }

    private fun discardDraft() {
        draft.discard()
        for (s in syncers) s()
        refreshPreview()
        toast("تغییرات لغو شد")
    }

    private fun resetDraft(keys: List<String>, what: String) {
        Cfg.resetKeys(draft, keys)
        for (s in syncers) s()
        refreshPreview()
        toast("$what پیش‌فرض شد؛ برای اعمال «ذخیره» را بزن")
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!draft.dirty) { super.onBackPressed(); return }
        AlertDialog.Builder(this).setMessage("تغییرات ظاهر ذخیره شوند؟")
            .setPositiveButton("ذخیره") { _, _ -> draft.save(); finish() }
            .setNegativeButton("دور بریز") { _, _ -> draft.discard(); finish() }
            .setNeutralButton("بمان", null).show()
    }

    private fun section(col: LinearLayout, t: String) {
        col.addView(TextView(this).apply { text = t; textSize = 17f; setPadding(0, dp(18f), 0, dp(4f)); setTextColor(Color.parseColor("#5A4634")) })
    }

    /** Slider bound to the draft (live preview, saved with «ذخیره»). */
    private fun seek(col: LinearLayout, label: String, key: String, min: Int, max: Int, def: Int) {
        val v = TextView(this).apply { textSize = 13f }
        val sb = SeekBar(this).apply { this.max = max - min }
        val sync = {
            val cur = draft.getInt(key, def).coerceIn(min, max)
            v.text = "$label: $cur"
            sb.progress = cur - min
        }
        sync()
        syncers.add(sync)
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val nv = p + min
                v.text = "$label: $nv"
                if (fromUser) { draft.edit().putInt(key, nv).apply(); refreshPreview() }
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        col.addView(v); col.addView(sb)
    }

    private fun switch(col: LinearLayout, label: String, key: String, def: Boolean) {
        val sw = Switch(this).apply { text = label }
        var syncing = false
        val sync = { syncing = true; sw.isChecked = draft.getBoolean(key, def); syncing = false }
        sync(); syncers.add(sync)
        sw.setOnCheckedChangeListener { _, on -> if (!syncing) { draft.edit().putBoolean(key, on).apply(); refreshPreview() } }
        col.addView(sw)
    }

    private fun spinner(col: LinearLayout, label: String, items: List<String>, current: () -> Int, onPick: (Int) -> Unit) {
        col.addView(TextView(this).apply { text = label; textSize = 13f })
        val sp = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, items)
            setSelection(current(), false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (pos == current()) return          // programmatic / unchanged selection
                    onPick(pos); refreshPreview()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        syncers.add { sp.setSelection(current(), false) }
        col.addView(sp)
    }

    // ---- controls that save immediately (sound, privacy): unchanged behaviour ----

    private fun directSeek(col: LinearLayout, label: String, key: String, min: Int, max: Int, def: Int) {
        val v = TextView(this).apply { textSize = 13f }
        val cur = prefs.getInt(key, def)
        v.text = "$label: $cur"
        val sb = SeekBar(this).apply { this.max = max - min; progress = cur - min }
        sb.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) {
                val nv = p + min
                v.text = "$label: $nv"
                if (fromUser) prefs.edit().putInt(key, nv).apply()
            }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        col.addView(v); col.addView(sb)
    }

    private fun directSwitch(col: LinearLayout, label: String, key: String, def: Boolean) {
        col.addView(Switch(this).apply {
            text = label; isChecked = prefs.getBoolean(key, def)
            setOnCheckedChangeListener { _, on -> prefs.edit().putBoolean(key, on).apply() }
        })
    }

    private fun directSpinner(col: LinearLayout, label: String, items: List<String>, sel: Int, onPick: (Int) -> Unit) {
        col.addView(TextView(this).apply { text = label; textSize = 13f })
        col.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, items)
            setSelection(sel, false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                var last = sel
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (pos == last) return
                    last = pos; onPick(pos)
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        })
    }

    private var artTarget: Pair<String, String>? = null

    /** Lets the user replace any key picture with their own image. */
    private fun pickArt() {
        val packs = arrayOf("لوگوی باشگاه (انگلیسی)", "عکس بازیکن (فارسی)", "بازیگران و هنرمندان (فارسی)")
        AlertDialog.Builder(this).setTitle("کدام پک؟").setItems(packs) { _, p ->
            val dir = listOf("en", "fa", "star")[p]
            val names = if (p == 0) EN_ART else FA_ART
            val items = (names + listOf("بازگرداندن همه به پیش‌فرض")).toTypedArray()
            AlertDialog.Builder(this).setTitle("کدام کلید؟").setItems(items) { _, i ->
                if (i == names.size) {
                    File(filesDir, "keyart/$dir").deleteRecursively()
                    toast("برگشت به تصاویر پیش‌فرض")
                } else {
                    artTarget = dir to names[i]
                    startActivityForResult(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), REQ_ART)
                }
            }.show()
        }.show()
    }

    private fun pickColors() {
        val names = COLOR_LABELS.map { it.second }.toTypedArray()
        AlertDialog.Builder(this).setTitle("کدام رنگ؟").setItems(names) { _, i ->
            val key = COLOR_LABELS[i].first
            val hexes = SWATCHES.toTypedArray()
            AlertDialog.Builder(this).setTitle(names[i]).setItems(SWATCH_NAMES.toTypedArray()) { _, j ->
                Cfg.setCustomColor(draft, key, Color.parseColor(hexes[j]))
                for (s in syncers) s()
                refreshPreview()
            }.show()
        }.show()
    }

    private fun setPin() {
        val input = EditText(this).apply { hint = "PIN عددی (حداقل ۴ رقم)"; inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD }
        AlertDialog.Builder(this).setTitle("PIN").setView(input).setPositiveButton("ذخیره") { _, _ ->
            val p = input.text.toString()
            if (p.length >= 4) { Pin.set(prefs, p); toast("PIN ذخیره شد") } else toast("حداقل ۴ رقم")
        }.setNegativeButton("انصراف", null).show()
    }

    // ----- backup -----

    private fun buildBackupJson(): String {
        val root = JSONObject()
        val entries = JSONObject()
        for (k in Kind.all) {
            val arr = JSONArray()
            for (e in Repo.all(k)) arr.put(e.toJson())
            entries.put(k, arr)
        }
        root.put("entries", entries)
        val p = JSONObject()
        for ((k, v) in prefs.all) {
            if (k.startsWith("pin_") || k == "codes_lock") continue
            if (v == null || v is Set<*>) continue
            p.put(k, v)
        }
        root.put("prefs", p)
        root.put("version", 1)
        return root.toString()
    }

    private fun restoreFrom(text: String, pw: String) {
        try {
            val outer = JSONObject(text)
            val plain = if (outer.optBoolean("encrypted")) String(Crypto.decryptWithPassword(outer, pw), Charsets.UTF_8) else text
            val root = JSONObject(plain)
            val safety = buildBackupJson()
            Repo.writeSecure(Repo.safetyFile(), safety.toByteArray())
            val entries = root.getJSONObject("entries")
            for (k in Kind.all) {
                val arr = entries.optJSONArray(k) ?: continue
                Repo.replaceAll(k, List(arr.length()) { Entry.from(arr.getJSONObject(it)) })
            }
            val ed = prefs.edit()
            val p = root.getJSONObject("prefs")
            for (k in p.keys()) {
                if (k.startsWith("pin_")) continue
                when (val v = p.get(k)) {
                    is Boolean -> ed.putBoolean(k, v)
                    is Int -> ed.putInt(k, v)
                    is Long -> ed.putLong(k, v)
                    is Double -> ed.putFloat(k, v.toFloat())
                    is String -> ed.putString(k, v)
                }
            }
            ed.apply()
            draft.discard()
            toast("پشتیبان بازیابی شد")
            recreate()
        } catch (e: Exception) {
            toast("بازیابی ناموفق: رمز یا فایل درست نیست")
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK || data?.data == null) return
        val uri: Uri = data.data!!
        when (requestCode) {
            REQ_EXPORT -> try {
                val plain = pendingExport?.toByteArray(Charsets.UTF_8) ?: return
                val enc = Crypto.encryptWithPassword(plain, pendingBackupPw)
                contentResolver.openOutputStream(uri)?.use { it.write(enc.toByteArray(Charsets.UTF_8)) }
                toast("پشتیبان ذخیره شد")
            } catch (e: Exception) { toast("ذخیره نشد") }
            REQ_ART -> try {
                val (dir, name) = artTarget ?: return
                val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return
                val f = File(filesDir, "keyart/$dir/$name.png")
                f.parentFile?.mkdirs()
                f.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                toast("تصویر ذخیره شد")
            } catch (e: Exception) { toast("تصویر ذخیره نشد") }
            REQ_IMPORT -> try {
                val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return
                restoreFrom(text, pendingBackupPw)
            } catch (e: Exception) { toast("فایل خوانده نشد") }
        }
    }
}
