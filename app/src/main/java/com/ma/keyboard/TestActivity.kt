package com.ma.keyboard

import android.app.Activity
import android.os.Bundle
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

/** Small field to try the keyboard and the local calculator engine. */
class TestActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(40, 60, 40, 40) }
        val out = TextView(this).apply { gravity = Gravity.START; textSize = 18f }
        val input = EditText(this).apply { hint = "عبارت را وارد کنید، مثلاً 1250 × 8" }
        col.addView(input)
        col.addView(out)
        input.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {
                val (r, err) = Calc.tryEval(s?.toString() ?: "")
                out.text = r?.let { "= $it" } ?: err ?: ""
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
        setContentView(col)
    }
}
