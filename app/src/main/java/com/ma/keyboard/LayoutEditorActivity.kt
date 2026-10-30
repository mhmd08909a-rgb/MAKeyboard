package com.ma.keyboard

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*

/**
 * «ویرایش چیدمان کیبورد»
 *
 * Shows the real KeyboardView (same sizes, colours and fonts as the keyboard) in edit mode.
 * Long-press a key to pick it up, drag it and lift the finger where it should go
 * (or tap a key, then tap the target spot). Rules come from LayoutModel and are shown on screen:
 *  - letters and symbols may move between all rows except the bottom row
 *  - Shift, Delete and other system keys can move only inside their own row
 *  - the bottom row (space, Enter, ?123, language, ...) can only be reordered inside itself
 *  - a row may hold at most 13 keys and can never become empty
 * Changes are kept in memory until «ذخیره»; «لغو تغییرات» goes back to the saved order and
 * «بازگردانی چیدمان پیش‌فرض» restores the factory order of this page.
 */
class LayoutEditorActivity : Activity(), KeyboardView.EditListener {

    private lateinit var prefs: android.content.SharedPreferences
    private lateinit var kbView: KeyboardView
    private lateinit var info: TextView
    private lateinit var status: TextView

    private var page = "fa"
    private var lower = false
    private var baseKeys: List<List<Key>> = emptyList()
    private var baseIds: List<List<String>> = emptyList()
    private var home: Map<String, Int> = emptyMap()
    private var work: MutableList<MutableList<String>> = mutableListOf()
    private var saved: List<List<String>> = emptyList()
    private val undo = ArrayList<List<List<String>>>()
    private var maxRowWeight = 10f

    private fun dp(v: Float): Int = (v * resources.displayMetrics.density).toInt()
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Cfg.sp(this)
        val cfg = Cfg.load(this)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(8f), 0, dp(8f)) }

        root.addView(TextView(this).apply { text = "ویرایش چیدمان کیبورد"; textSize = 20f; setPadding(dp(14f), 0, dp(14f), dp(4f)) })
        root.addView(TextView(this).apply {
            textSize = 13f; setPadding(dp(14f), 0, dp(14f), dp(6f))
            text = "روی یک کلید انگشت را نگه دار تا برداشته شود، بکش و جای جدید رها کن. (یا یک بار روی کلید بزن و بعد روی جای مقصد.)\n" +
                "• حروف و نمادها بین همهٔ ردیف‌ها جابه‌جا می‌شوند، به‌جز ردیف پایین.\n" +
                "• Shift و پاک‌کردن و کلیدهای سیستمی فقط داخل ردیف خودشان جابه‌جا می‌شوند.\n" +
                "• ردیف پایین (فاصله، Enter، نمادها، زبان، …) فقط داخل خودش مرتب می‌شود.\n" +
                "• هر ردیف حداکثر ${Layouts.MAX_KEYS_PER_ROW} کلید دارد و هیچ ردیفی خالی نمی‌شود. ردیف‌های ممنوع هنگام کشیدن تیره می‌شوند."
        })

        val pages = Layouts.EDITABLE_PAGES
        root.addView(Spinner(this).apply {
            adapter = ArrayAdapter(this@LayoutEditorActivity, android.R.layout.simple_spinner_dropdown_item, pages.map { Layouts.pageTitle(it) })
            setSelection(0, false)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                var cur = 0
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (pos == cur) return
                    val go = { cur = pos; loadPage(pages[pos]) }
                    if (isDirty()) {
                        AlertDialog.Builder(this@LayoutEditorActivity).setMessage("تغییرات این صفحه ذخیره شود؟")
                            .setPositiveButton("ذخیره") { _, _ -> savePage(); go() }
                            .setNegativeButton("دور بریز") { _, _ -> go() }
                            .setOnCancelListener { setSelection(cur, false) }
                            .show()
                    } else go()
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }, LinearLayout.LayoutParams(-1, -2).apply { setMargins(dp(10f), 0, dp(10f), dp(6f)) })

        kbView = KeyboardView(this).apply {
            editMode = true
            editListener = this@LayoutEditorActivity
            setConfig(cfg)
            maxHeightPx = (resources.displayMetrics.heightPixels * 0.5f).toInt()
        }
        val kbBox = FrameLayout(this).apply { setBackgroundColor(cfg.palette.bg) }
        kbBox.addView(kbView, FrameLayout.LayoutParams(-1, -2))
        root.addView(kbBox, LinearLayout.LayoutParams(-1, -2))

        info = TextView(this).apply { textSize = 13f; setPadding(dp(14f), dp(6f), dp(14f), 0) }
        status = TextView(this).apply { textSize = 13f; setPadding(dp(14f), dp(2f), dp(14f), dp(6f)) }
        root.addView(info); root.addView(status)

        val buttons = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10f), 0, dp(10f), 0) }
        val row1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        row1.addView(Button(this).apply { text = "ذخیره"; setOnClickListener { savePage(); toast("چیدمان ذخیره شد") } }, LinearLayout.LayoutParams(0, -2, 1f))
        row1.addView(Button(this).apply { text = "برگشت یک قدم"; setOnClickListener { undoStep() } }, LinearLayout.LayoutParams(0, -2, 1f))
        row1.addView(Button(this).apply { text = "لغو تغییرات"; setOnClickListener { revert() } }, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.addView(row1)
        buttons.addView(Button(this).apply { text = "بازگردانی چیدمان پیش‌فرض (این صفحه)"; setOnClickListener {
            AlertDialog.Builder(this@LayoutEditorActivity).setMessage("ترتیب کلیدهای «${Layouts.pageTitle(page)}» به حالت پیش‌فرض برگردد؟")
                .setPositiveButton("بله") { _, _ -> resetPage() }.setNegativeButton("نه", null).show()
        } })
        root.addView(buttons)

        setContentView(ScrollView(this).apply { addView(root) })
        loadPage(page)
    }

    private fun loadPage(p: String) {
        page = p
        val (lang, mode, shift) = Layouts.pageArgs(p)
        lower = lang == "en" && mode == Mode.LETTERS
        baseKeys = Layouts.editableBase(lang, mode, shift, prefs.getBoolean("native_digits", false))
        baseIds = Layouts.ids(baseKeys, lower)
        home = LayoutModel.baseRows(baseIds)
        maxRowWeight = baseKeys.dropLast(1).maxOfOrNull { r -> r.sumOf { it.weight.toDouble() }.toFloat() } ?: 10f
        saved = LayoutModel.reconcile(baseIds, Layouts.fromJson(Cfg.layoutJson(prefs, p)), Layouts.MAX_KEYS_PER_ROW)
        work = saved.map { it.toMutableList() }.toMutableList()
        undo.clear()
        info.text = "صفحه: ${Layouts.pageTitle(p)}"
        show()
    }

    private fun show() {
        kbView.setRows(Layouts.arrange(baseKeys, work, lower))
        status.text = if (isDirty()) "تغییرات ذخیره نشده" else "مطابق نسخهٔ ذخیره‌شده"
    }

    private fun isDirty() = work != saved

    private fun weightOf(row: List<String>): Float {
        val byId = HashMap<String, Float>()
        for (r in baseKeys.indices) for (i in baseKeys[r].indices) byId[baseIds[r][i]] = baseKeys[r][i].weight
        return row.sumOf { (byId[it] ?: 1f).toDouble() }.toFloat()
    }

    override fun canDrop(fromRow: Int, fromIdx: Int, toRow: Int): Boolean {
        val id = work.getOrNull(fromRow)?.getOrNull(fromIdx) ?: return false
        val h = home[id] ?: return false
        if (!LayoutModel.canMove(h, id, toRow, work.size)) return false
        if (fromRow != toRow && work[fromRow].size <= 1) return false
        return true
    }

    override fun onPicked(key: Key) {
        val name = when (key.type) {
            KT.CHAR -> "«${key.label}»"
            KT.DEL -> "پاک‌کردن"; KT.SHIFT -> "Shift"; KT.ENTER -> "Enter"; KT.SPACE -> "فاصله"
            KT.LANG -> "تغییر زبان"; KT.ZWNJ -> "نیم‌فاصله"; else -> "«${key.label}»"
        }
        info.text = "کلید $name انتخاب شد؛ جای جدیدش را انتخاب کن."
    }

    override fun onDrop(fromRow: Int, fromIdx: Int, toRow: Int, toIdx: Int) {
        val before = work.map { it.toList() }
        val rows: MutableList<MutableList<String>> = work.map { it.toMutableList() }.toMutableList()
        @Suppress("UNCHECKED_CAST")
        val ok = LayoutModel.move(rows as MutableList<List<String>>, home, fromRow, fromIdx, toRow, toIdx, Layouts.MAX_KEYS_PER_ROW)
        if (!ok) { info.text = "این جابه‌جایی مجاز نیست (قانون ردیف‌ها را ببین)."; show(); return }
        if (toRow != fromRow && toRow != rows.size - 1 && weightOf(rows[toRow]) > maxRowWeight + 1.01f) {
            info.text = "این ردیف جای کلید دیگری ندارد؛ کلیدها خیلی کوچک می‌شدند."
            show(); return
        }
        undo.add(before)
        work = rows
        info.text = "جابه‌جا شد. برای ماندگاری «ذخیره» را بزن."
        show()
    }

    private fun undoStep() {
        if (undo.isEmpty()) { toast("قدمی برای برگشت نیست"); return }
        work = undo.removeAt(undo.size - 1).map { it.toMutableList() }.toMutableList()
        show()
    }

    private fun savePage() {
        val e = prefs.edit()
        if (work == baseIds) e.remove(Cfg.LAYOUT_PREFIX + page) else e.putString(Cfg.LAYOUT_PREFIX + page, Layouts.toJson(work))
        e.apply()
        saved = work.map { it.toList() }
        undo.clear()
        show()
    }

    private fun revert() {
        work = saved.map { it.toMutableList() }.toMutableList()
        undo.clear()
        info.text = "به آخرین نسخهٔ ذخیره‌شده برگشت."
        show()
    }

    private fun resetPage() {
        prefs.edit().remove(Cfg.LAYOUT_PREFIX + page).apply()
        saved = LayoutModel.copy(baseIds)
        work = saved.map { it.toMutableList() }.toMutableList()
        undo.clear()
        info.text = "چیدمان پیش‌فرض این صفحه برگشت."
        show()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (!isDirty()) { super.onBackPressed(); return }
        AlertDialog.Builder(this).setMessage("تغییرات چیدمان ذخیره شود؟")
            .setPositiveButton("ذخیره") { _, _ -> savePage(); finish() }
            .setNegativeButton("دور بریز") { _, _ -> finish() }
            .setNeutralButton("بمان", null).show()
    }
}
