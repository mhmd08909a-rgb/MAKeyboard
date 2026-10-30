package com.ma.keyboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Repo.init(this)
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 96, 48, 48)
            gravity = Gravity.CENTER_HORIZONTAL
        }
        col.addView(TextView(this).apply {
            text = "MAKeyboard"; textSize = 30f; gravity = Gravity.CENTER
            setTextColor(0xFF3B2F25.toInt())
        })
        col.addView(TextView(this).apply {
            text = "نسخه ۱٫۱: کیبورد واقعی با صدای ماشین‌تحریر، ظاهر قابل تنظیم و حالت‌های حروف و نمادها."
            gravity = Gravity.CENTER; textSize = 14f; setPadding(0, 24, 0, 40)
        })
        col.addView(Button(this).apply {
            text = "۱. فعال‌کردن MAKeyboard در تنظیمات اندروید"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })
        col.addView(Button(this).apply {
            text = "۲. انتخاب به‌عنوان کیبورد پیش‌فرض"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })
        col.addView(Button(this).apply {
            text = "ماشین‌حساب / تست کیبورد"
            setOnClickListener { startActivity(Intent(this@MainActivity, TestActivity::class.java)) }
        })
        setContentView(ScrollView(this).apply { addView(col, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)) })
    }
}
