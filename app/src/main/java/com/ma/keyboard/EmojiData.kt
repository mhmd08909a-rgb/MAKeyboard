package com.ma.keyboard

import android.content.Context

/** Offline emoji list bundled in assets (category, emoji, keywords). */
object EmojiData {
    class E(val cat: String, val ch: String, val name: String)

    val CATS = listOf("smileys", "people", "animals", "food", "travel", "activities", "objects", "symbols", "flags")
    private var all: List<E>? = null

    fun load(c: Context): List<E> {
        all?.let { return it }
        val list = try {
            c.assets.open("emoji.tsv").bufferedReader().useLines { seq ->
                seq.mapNotNull { line ->
                    val p = line.split('\t')
                    if (p.size >= 3) E(p[0], p[1], p[2]) else null
                }.toList()
            }
        } catch (e: Exception) { emptyList() }
        all = list
        return list
    }
}
