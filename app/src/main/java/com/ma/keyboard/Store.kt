package com.ma.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.AtomicFile
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

object Kind {
    const val CLIP = "clip"
    const val CODE = "code"
    const val SNIP = "snip"
    const val NOTE = "note"
    const val COMBO = "combo"
    val all = listOf(CLIP, CODE, SNIP, NOTE, COMBO)

    fun title(k: String) = when (k) {
        CLIP -> "کلیپ‌بورد"
        CODE -> "کدگذاری کلمه"
        SNIP -> "میان‌برها و متن‌های آماده"
        NOTE -> "یادداشت‌ها و یادآورها"
        else -> "ترکیب‌های ایموجی"
    }
}

class Entry(
    var id: Long = 0,
    var kind: String = "",
    var title: String = "",
    var trigger: String = "",
    var content: String = "",
    var category: String = "",
    var pinned: Boolean = false,
    var created: Long = 0,
    var updated: Long = 0,
    var dueAt: Long = 0,
    var done: Boolean = false,
    var seen: Boolean = false,
    var mode: Int = 0,
    var enabled: Boolean = true
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id); put("kind", kind); put("title", title); put("trigger", trigger)
        put("content", content); put("category", category); put("pinned", pinned)
        put("created", created); put("updated", updated); put("dueAt", dueAt)
        put("done", done); put("seen", seen); put("mode", mode); put("enabled", enabled)
    }

    fun copy(): Entry = from(toJson())

    companion object {
        fun from(o: JSONObject) = Entry(
            o.optLong("id"), o.optString("kind"), o.optString("title"), o.optString("trigger"),
            o.optString("content"), o.optString("category"), o.optBoolean("pinned"),
            o.optLong("created"), o.optLong("updated"), o.optLong("dueAt"),
            o.optBoolean("done"), o.optBoolean("seen"), o.optInt("mode"), o.optBoolean("enabled", true)
        )
    }
}

/** All user data lives in app-private storage, encrypted with a key from the Android Keystore. */
object Repo {
    private val lists = HashMap<String, MutableList<Entry>>()
    private var appCtx: Context? = null
    private var counter = 0
    private val listeners = ArrayList<() -> Unit>()

    @Synchronized
    fun init(c: Context) {
        if (appCtx != null) return
        appCtx = c.applicationContext
        for (k in Kind.all) load(k)
    }

    fun addListener(l: () -> Unit) { listeners.add(l) }
    fun removeListener(l: () -> Unit) { listeners.remove(l) }
    private fun notifyChanged() { for (l in ArrayList(listeners)) try { l() } catch (e: Exception) {} }

    private fun dir(): File = File(appCtx!!.filesDir, "data").apply { mkdirs() }
    private fun file(kind: String) = File(dir(), "$kind.dat")

    private fun list(kind: String): MutableList<Entry> = lists.getOrPut(kind) { mutableListOf() }

    @Synchronized
    fun all(kind: String): List<Entry> = list(kind).map { it }

    @Synchronized
    fun find(kind: String, id: Long): Entry? = list(kind).firstOrNull { it.id == id }

    @Synchronized
    fun newId(): Long { counter = (counter + 1) % 1000; return System.currentTimeMillis() * 1000 + counter }

    @Synchronized
    fun upsert(e: Entry) {
        val now = System.currentTimeMillis()
        if (e.id == 0L) e.id = newId()
        if (e.created == 0L) e.created = now
        e.updated = now
        val l = list(e.kind)
        val i = l.indexOfFirst { it.id == e.id }
        if (i >= 0) l[i] = e else l.add(0, e)
        save(e.kind)
        notifyChanged()
    }

    @Synchronized
    fun delete(kind: String, id: Long) {
        if (list(kind).removeAll { it.id == id }) { save(kind); notifyChanged() }
    }

    @Synchronized
    fun replaceAll(kind: String, entries: List<Entry>) {
        val l = list(kind); l.clear(); l.addAll(entries.map { it.kind = kind; it })
        save(kind); notifyChanged()
    }

    @Synchronized
    fun merge(kind: String, entries: List<Entry>): Int {
        val l = list(kind); var added = 0
        for (e in entries) {
            e.kind = kind
            val i = l.indexOfFirst { it.id == e.id }
            if (i >= 0) { if (e.updated >= l[i].updated) l[i] = e } else { l.add(e); added++ }
        }
        save(kind); notifyChanged()
        return added
    }

    /** Stores a clipboard text the keyboard was allowed to see. Returns true if stored. */
    @Synchronized
    fun addClip(text: String, max: Int): Boolean {
        val l = list(Kind.CLIP)
        val existing = l.firstOrNull { it.content == text }
        if (existing != null) {
            if (l.firstOrNull() === existing) return false
            l.remove(existing); existing.updated = System.currentTimeMillis(); l.add(0, existing)
        } else {
            val now = System.currentTimeMillis()
            l.add(0, Entry(newId(), Kind.CLIP, "", "", text, "", false, now, now))
        }
        val unpinned = l.filter { !it.pinned && it.category.isEmpty() }
        if (unpinned.size > max) {
            val drop = unpinned.drop(max).toSet()
            l.removeAll { it in drop }
        }
        save(Kind.CLIP); notifyChanged()
        return true
    }

    /** Clears the history but keeps pinned items and items filed in a folder. */
    @Synchronized
    fun clearClipHistory(): Int {
        val l = list(Kind.CLIP)
        val before = l.size
        l.removeAll { !it.pinned && it.category.isEmpty() }
        save(Kind.CLIP); notifyChanged()
        return before - l.size
    }

    @Synchronized
    fun wipeAll() {
        for (k in Kind.all) { list(k).clear(); save(k) }
        notifyChanged()
    }

    @Synchronized
    private fun save(kind: String) {
        val arr = JSONArray()
        for (e in list(kind)) arr.put(e.toJson())
        writeSecure(file(kind), arr.toString().toByteArray(Charsets.UTF_8))
    }

    private fun load(kind: String) {
        val f = file(kind)
        val l = list(kind); l.clear()
        if (!f.exists()) return
        try {
            val bytes = readSecure(f) ?: return
            val arr = JSONArray(String(bytes, Charsets.UTF_8))
            for (i in 0 until arr.length()) l.add(Entry.from(arr.getJSONObject(i)).also { it.kind = kind })
        } catch (e: Exception) {
            // Never silently destroy data: keep the unreadable file aside.
            f.renameTo(File(dir(), "$kind.unreadable.${System.currentTimeMillis()}"))
        }
    }

    fun writeSecure(f: File, plain: ByteArray) {
        val af = AtomicFile(f)
        val out = af.startWrite()
        try {
            out.write(Crypto.encryptLocal(plain))
            af.finishWrite(out)
        } catch (e: Exception) {
            af.failWrite(out)
        }
    }

    fun readSecure(f: File): ByteArray? {
        if (!f.exists()) return null
        return Crypto.decryptLocal(AtomicFile(f).readFully())
    }

    fun safetyFile(): File = File(dir(), "safety_before_restore.dat")
}

object Crypto {
    private const val ALIAS = "makeyboard_data_key"
    private const val ITER = 120_000

    private fun keystoreKey(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry
        if (existing != null) return existing.secretKey
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    /** Format: [1][ivLen][iv][ciphertext]  or  [0][plain] if the keystore is unavailable. */
    fun encryptLocal(plain: ByteArray): ByteArray {
        return try {
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.ENCRYPT_MODE, keystoreKey())
            val iv = c.iv
            val ct = c.doFinal(plain)
            byteArrayOf(1, iv.size.toByte()) + iv + ct
        } catch (e: Exception) {
            throw IllegalStateException("keystore unavailable", e)
        }
    }

    fun decryptLocal(data: ByteArray): ByteArray {
        if (data.isEmpty()) return data
        if (data[0].toInt() == 0) return data.copyOfRange(1, data.size)
        val ivLen = data[1].toInt()
        val iv = data.copyOfRange(2, 2 + ivLen)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, keystoreKey(), GCMParameterSpec(128, iv))
        return c.doFinal(data, 2 + ivLen, data.size - 2 - ivLen)
    }

    private fun derive(pw: String, salt: ByteArray): SecretKeySpec {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val k = f.generateSecret(PBEKeySpec(pw.toCharArray(), salt, ITER, 256)).encoded
        return SecretKeySpec(k, "AES")
    }

    fun encryptWithPassword(plain: ByteArray, pw: String): String {
        val rnd = SecureRandom()
        val salt = ByteArray(16).also { rnd.nextBytes(it) }
        val iv = ByteArray(12).also { rnd.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, derive(pw, salt), GCMParameterSpec(128, iv))
        val ct = c.doFinal(plain)
        return JSONObject().apply {
            put("format", "MAKeyboard-backup")
            put("encrypted", true)
            put("kdf", "PBKDF2WithHmacSHA256"); put("iter", ITER)
            put("salt", b64(salt)); put("iv", b64(iv)); put("data", b64(ct))
        }.toString()
    }

    fun decryptWithPassword(o: JSONObject, pw: String): ByteArray {
        val salt = unb64(o.getString("salt"))
        val iv = unb64(o.getString("iv"))
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, derive(pw, salt), GCMParameterSpec(128, iv))
        return c.doFinal(unb64(o.getString("data")))
    }

    fun b64(b: ByteArray): String = Base64.encodeToString(b, Base64.NO_WRAP)
    fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    fun pinHash(pin: String, salt: ByteArray): String {
        val f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return b64(f.generateSecret(PBEKeySpec(pin.toCharArray(), salt, 20_000, 256)).encoded)
    }
}

object Pin {
    fun isSet(p: SharedPreferences) = p.getString("pin_hash", null) != null

    fun set(p: SharedPreferences, pin: String) {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        p.edit().putString("pin_salt", Crypto.b64(salt)).putString("pin_hash", Crypto.pinHash(pin, salt)).apply()
    }

    fun clear(p: SharedPreferences) {
        p.edit().remove("pin_salt").remove("pin_hash").putBoolean("codes_lock", false).apply()
    }

    fun verify(p: SharedPreferences, pin: String): Boolean {
        val salt = p.getString("pin_salt", null) ?: return false
        val hash = p.getString("pin_hash", null) ?: return false
        return try { Crypto.pinHash(pin, Crypto.unb64(salt)) == hash } catch (e: Exception) { false }
    }

    fun locked(p: SharedPreferences) = isSet(p) && p.getBoolean("codes_lock", false)
}

object TextUtil {
    /** Normalises Arabic/Persian variants so «ملي» and «ملی» match the same trigger. */
    fun norm(s: String): String = s.replace('ي', 'ی').replace('ى', 'ی').replace('ك', 'ک')
        .replace("ـ", "").lowercase()

    fun toNative(s: String, lang: String): String {
        val target = when (lang) { "fa" -> "۰۱۲۳۴۵۶۷۸۹"; "ar" -> "٠١٢٣٤٥٦٧٨٩"; else -> return s }
        val sb = StringBuilder()
        for (ch in s) {
            if (ch in '0'..'9') sb.append(target[ch - '0'])
            else if (ch == '.' && lang == "fa") sb.append('٫')
            else sb.append(ch)
        }
        return sb.toString()
    }

    fun mask(v: String): String = when {
        v.length <= 2 -> "••"
        v.length <= 6 -> v.take(1) + "•••"
        else -> v.take(2) + "•••" + v.takeLast(2)
    }

    fun oneLine(s: String, max: Int = 80): String {
        val t = s.replace('\n', ' ').trim()
        return if (t.length > max) t.take(max) + "…" else t
    }
}
