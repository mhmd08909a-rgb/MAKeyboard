package com.ma.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlin.random.Random

/** Low-latency typewriter sounds (SoundPool, preloaded) and physical haptics. */
class Feedback(private val ctx: Context) {
    companion object {
        const val KEY = 0; const val SPACE = 1; const val ENTER = 2; const val DEL = 3
        val SETS = listOf("mawriter", "classic", "soft", "heavy", "vintage")
        fun setTitle(s: String) = when (s) {
            "mawriter" -> "MA Writer (صدای اصلی)"
            "classic" -> "کلاسیک فلزی"
            "soft" -> "نرم و آرام"
            "heavy" -> "سنگین قدیمی"
            else -> "پرطنین وینتیج"
        }
        private val RES = mapOf(
            "mawriter" to intArrayOf(R.raw.snd_mawriter_key, R.raw.snd_mawriter_space, R.raw.snd_mawriter_enter, R.raw.snd_mawriter_del),
            "classic" to intArrayOf(R.raw.snd_classic_key, R.raw.snd_classic_space, R.raw.snd_classic_enter, R.raw.snd_classic_del),
            "soft" to intArrayOf(R.raw.snd_soft_key, R.raw.snd_soft_space, R.raw.snd_soft_enter, R.raw.snd_soft_del),
            "heavy" to intArrayOf(R.raw.snd_heavy_key, R.raw.snd_heavy_space, R.raw.snd_heavy_enter, R.raw.snd_heavy_del),
            "vintage" to intArrayOf(R.raw.snd_vintage_key, R.raw.snd_vintage_space, R.raw.snd_vintage_enter, R.raw.snd_vintage_del)
        )
    }

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(8)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        ).build()
    private val ids = HashMap<String, IntArray>()
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val vibrator: Vibrator? = findVibrator()

    private var soundOn = true; private var volume = 0.7f; private var set = "mawriter"
    private var perType = booleanArrayOf(true, true, true, true)
    private var respectSilent = true
    private var vibOn = true; private var vibAmp = 60; private var vibMs = 18

    init { reload(Cfg.sp(ctx)) }

    @Suppress("DEPRECATION")
    private fun findVibrator(): Vibrator? = if (Build.VERSION.SDK_INT >= 31) {
        (ctx.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        ctx.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun reload(p: SharedPreferences) {
        soundOn = p.getBoolean("sound_on", true)
        volume = p.getInt("volume", 70) / 100f
        set = p.getString("sound_set", "mawriter") ?: "mawriter"
        if (set !in SETS) set = "mawriter"
        perType = booleanArrayOf(
            p.getBoolean("snd_key", true), p.getBoolean("snd_space", true),
            p.getBoolean("snd_enter", true), p.getBoolean("snd_del", true)
        )
        respectSilent = p.getBoolean("silent_respect", true)
        vibOn = p.getBoolean("vib_on", true)
        vibAmp = p.getInt("vib_amp", 60)
        vibMs = p.getInt("vib_ms", 18)
        if (soundOn) ensure(set)
    }

    private fun ensure(s: String): IntArray {
        ids[s]?.let { return it }
        val res = RES[s] ?: RES.getValue("mawriter")
        val loaded = IntArray(4) { pool.load(ctx, res[it], 1) }
        ids[s] = loaded
        return loaded
    }

    fun key(type: Int) {
        sound(type)
        vibrate()
    }

    fun sound(type: Int, force: Boolean = false, setOverride: String? = null) {
        if (!force && (!soundOn || !perType[type])) return
        if (!force && respectSilent && audio.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        if (volume <= 0f) return
        val arr = ensure(setOverride ?: set)
        val rate = if (type == KEY || type == DEL) 0.94f + Random.nextFloat() * 0.14f else 1f
        pool.play(arr[type], volume, volume, 1, 0, rate)
    }

    fun vibrate(force: Boolean = false) {
        if (!force && !vibOn) return
        val v = vibrator ?: return
        if (!v.hasVibrator()) return
        val amp = if (v.hasAmplitudeControl()) (vibAmp * 255 / 100).coerceIn(1, 255) else VibrationEffect.DEFAULT_AMPLITUDE
        try { v.vibrate(VibrationEffect.createOneShot(vibMs.toLong().coerceIn(1, 200), amp)) } catch (e: Exception) {}
    }

    fun release() { pool.release() }
}
