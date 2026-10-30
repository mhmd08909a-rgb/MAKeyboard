package com.ma.keyboard

import android.content.SharedPreferences

/**
 * In-memory layer over the real preferences. The settings screen edits this layer so the live
 * preview shows every change immediately, but nothing reaches the keyboard until "ذخیره".
 * "لغو تغییرات" simply drops the layer. A removed key is stored as a null override.
 */
class DraftPrefs(private val base: SharedPreferences) : SharedPreferences {
    private val over = HashMap<String, Any?>()

    val dirty: Boolean get() = over.isNotEmpty()

    fun discard() = over.clear()

    /** Writes the layer to the real preferences in one apply(). */
    fun save() {
        val e = base.edit()
        for ((k, v) in over) {
            when (v) {
                null -> e.remove(k)
                is String -> e.putString(k, v)
                is Int -> e.putInt(k, v)
                is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v)
                is Boolean -> e.putBoolean(k, v)
                is Set<*> -> e.putStringSet(k, v.map { it.toString() }.toMutableSet())
            }
        }
        e.apply()
        over.clear()
    }

    private inline fun <T> read(key: String?, def: T, real: () -> T): T {
        if (key != null && over.containsKey(key)) {
            @Suppress("UNCHECKED_CAST")
            return (over[key] as? T) ?: def
        }
        return real()
    }

    override fun getAll(): MutableMap<String, *> {
        val m = HashMap<String, Any?>(base.all)
        for ((k, v) in over) if (v == null) m.remove(k) else m[k] = v
        return m
    }

    override fun getString(key: String?, defValue: String?): String? = read(key, defValue) { base.getString(key, defValue) }
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? =
        read(key, defValues) { base.getStringSet(key, defValues) }
    override fun getInt(key: String?, defValue: Int): Int = read(key, defValue) { base.getInt(key, defValue) }
    override fun getLong(key: String?, defValue: Long): Long = read(key, defValue) { base.getLong(key, defValue) }
    override fun getFloat(key: String?, defValue: Float): Float = read(key, defValue) { base.getFloat(key, defValue) }
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = read(key, defValue) { base.getBoolean(key, defValue) }

    override fun contains(key: String?): Boolean =
        if (key != null && over.containsKey(key)) over[key] != null else base.contains(key)

    override fun edit(): SharedPreferences.Editor = Ed()

    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) {}

    private inner class Ed : SharedPreferences.Editor {
        private val pending = LinkedHashMap<String, Any?>()
        private var clear = false
        override fun putString(key: String?, value: String?): SharedPreferences.Editor { if (key != null) pending[key] = value; return this }
        override fun putStringSet(key: String?, values: MutableSet<String>?): SharedPreferences.Editor { if (key != null) pending[key] = values?.toSet(); return this }
        override fun putInt(key: String?, value: Int): SharedPreferences.Editor { if (key != null) pending[key] = value; return this }
        override fun putLong(key: String?, value: Long): SharedPreferences.Editor { if (key != null) pending[key] = value; return this }
        override fun putFloat(key: String?, value: Float): SharedPreferences.Editor { if (key != null) pending[key] = value; return this }
        override fun putBoolean(key: String?, value: Boolean): SharedPreferences.Editor { if (key != null) pending[key] = value; return this }
        override fun remove(key: String?): SharedPreferences.Editor { if (key != null) pending[key] = null; return this }
        override fun clear(): SharedPreferences.Editor { clear = true; return this }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            if (clear) for (k in getAll().keys) over[k] = null
            for ((k, v) in pending) over[k] = v
            pending.clear(); clear = false
        }
    }
}
