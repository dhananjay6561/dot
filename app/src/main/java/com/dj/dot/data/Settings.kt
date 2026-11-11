package com.dj.dot.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

class Settings(context: Context) {
    // Keep the original file on-device: it holds MediaStore IDs and SAF grants.
    private val deviceSp = context.getSharedPreferences("dot", Context.MODE_PRIVATE)
    private val sp = context.getSharedPreferences("dot_portable", Context.MODE_PRIVATE)
    private val declaredPortableKeys = mutableSetOf<String>()

    init { SettingsMigration.migrate(deviceSp, sp) }

    inner class Pref<T>(
        private val prefs: SharedPreferences,
        private val key: String,
        default: T,
        read: SharedPreferences.(String, T) -> T,
        private val write: SharedPreferences.Editor.(String, T) -> Unit,
    ) {
        private val state = MutableStateFlow(prefs.read(key, default))
        val flow: StateFlow<T> = state
        val value: T get() = state.value
        fun set(v: T) {
            state.value = v
            prefs.edit().apply { write(key, v) }.apply()
        }
    }

    // Preferences stay on-device unless their declaration explicitly opts into backup.
    private fun <T> pref(
        key: String,
        default: T,
        portable: Boolean,
        read: SharedPreferences.(String, T) -> T,
        write: SharedPreferences.Editor.(String, T) -> Unit,
    ): Pref<T> {
        val prefs = if (portable) {
            SettingsMigration.requirePortableKey(key)
            check(declaredPortableKeys.add(key)) { "Duplicate portable setting: $key" }
            sp
        } else deviceSp
        return Pref(prefs, key, default, read, write)
    }

    private fun int(k: String, d: Int, portable: Boolean = false) = pref(k, d, portable, { a, b -> getInt(a, b) }, { a, b -> putInt(a, b) })
    private fun long(k: String, d: Long, portable: Boolean = false) = pref(k, d, portable, { a, b -> getLong(a, b) }, { a, b -> putLong(a, b) })
    private fun bool(k: String, d: Boolean, portable: Boolean = false) = pref(k, d, portable, { a, b -> getBoolean(a, b) }, { a, b -> putBoolean(a, b) })
    private fun float(k: String, d: Float, portable: Boolean = false) = pref(k, d, portable, { a, b -> getFloat(a, b) }, { a, b -> putFloat(a, b) })
    private fun string(k: String, d: String, portable: Boolean = false) = pref(k, d, portable, { a, b -> getString(a, b) ?: b }, { a, b -> putString(a, b) })

    private fun portableInt(k: String, d: Int) = int(k, d, portable = true)
    private fun portableBool(k: String, d: Boolean) = bool(k, d, portable = true)
    private fun portableFloat(k: String, d: Float) = float(k, d, portable = true)
    private fun portableString(k: String, d: String) = string(k, d, portable = true)

    // Playback
    val crossfadeMs = portableInt("crossfade_ms", 6000)
    val skipFadeMs = portableInt("skip_fade_ms", 1200)
    val fadeOnSkip = portableBool("fade_skip", true)
    val fadeOnPause = portableBool("fade_pause", true)
    val beatAlignedFade = portableBool("beat_fade", true)
    val speed = portableFloat("speed", 1f)

    // Sound
    val eqEnabled = portableBool("eq_on", false)
    val eqLevels = portableString("eq_levels", "")
    val eqPreset = portableString("eq_preset", "Flat")
    val bassBoost = portableInt("bass", 0)
    val loudness = portableInt("loudness", 0)
    val virtualizer = portableInt("virtualizer", 0)
    val reverb = portableInt("reverb", 0)
    val soundEffect = portableString("sound_effect", "Hi-Fi")
    val levelling = portableBool("levelling", false)
    val balance = portableInt("balance", 0)
    val mono = portableBool("mono", false)
    val vibe = portableInt("vibe", 0)

    // Library & UI
    val minDurationSec = portableInt("min_duration", 30)
    val hideWhatsApp = portableBool("hide_whatsapp", true)
    val songSort = portableInt("song_sort", 0)
    val visualizer = portableBool("visualizer", true)
    val stripStyle = portableInt("strip_style", 0)
    val artStyle = portableInt("art_style", 0)
    val glyphMode = portableInt("glyph_mode", 1)
    val shakeForRandom = portableBool("shake_random", true)
    val playOnConnect = portableBool("play_on_connect", true)
    val bedsideWhenCharging = portableBool("bedside_charging", true)
    val glyphBrightness = portableInt("glyph_brightness", 100)
    val glyphSensitivity = portableInt("glyph_sensitivity", 100)
    val glyphBeatFlash = portableBool("glyph_beat_flash", true)
    /** Folders the user granted for reading .lrc files, one tree uri per line. */
    val lyricsFolders = string("lyrics_folders", "")
    fun lyricsFolderUris(): List<android.net.Uri> =
        lyricsFolders.value.lines().filter { it.isNotBlank() }.map(android.net.Uri::parse)

    /** Per-song lyrics timing shift in ms; positive shows lines later. */
    fun lyricsOffset(songId: Long): Int = deviceSp.getInt("lrc_off_$songId", 0)
    fun setLyricsOffset(songId: Long, ms: Int) = deviceSp.edit().putInt("lrc_off_$songId", ms).apply()

    /** Bumped when learned tempos from older versions can't be trusted and must be relearned. */
    val tempoVersion = portableInt("tempo_version", 1)

    // Restored session
    val queueIds = string("q_ids", "")
    val queueIndex = int("q_index", 0)
    val queuePos = long("q_pos", 0)
    val shuffle = portableBool("shuffle", false)
    val repeat = portableInt("repeat", 0)

    init { SettingsMigration.verifyPortableDeclarations(declaredPortableKeys) }
}

/** Move only portable settings into the backed-up file, leaving device-bound data untouched. */
internal object SettingsMigration {
    internal val portableKeys = setOf(
        "crossfade_ms", "skip_fade_ms", "fade_skip", "fade_pause", "beat_fade", "speed",
        "eq_on", "eq_levels", "eq_preset", "bass", "loudness", "virtualizer", "reverb",
        "sound_effect", "levelling", "balance", "mono", "vibe",
        "min_duration", "hide_whatsapp", "song_sort", "visualizer", "strip_style",
        "art_style", "glyph_mode", "shake_random", "play_on_connect", "bedside_charging",
        "glyph_brightness", "glyph_sensitivity", "glyph_beat_flash", "tempo_version",
        "shuffle", "repeat",
    )

    fun requirePortableKey(key: String) {
        check(key in portableKeys) { "$key is portable but not included in migration" }
    }

    fun verifyPortableDeclarations(declaredKeys: Set<String>) {
        check(declaredKeys == portableKeys) {
            "Portable settings and migration disagree: missing=${declaredKeys - portableKeys}, " +
                "unused=${portableKeys - declaredKeys}"
        }
    }

    fun migrate(legacy: SharedPreferences, portable: SharedPreferences) {
        val toMove = legacy.all.filter { (key, value) ->
            key in portableKeys && (value is Boolean || value is Int || value is Long ||
                value is Float || value is String)
        }
        if (toMove.isEmpty()) return

        // Commit the destination first. If it fails, the old file stays intact for a retry.
        val editor = portable.edit()
        var changed = false
        toMove.forEach { (key, value) ->
            if (portable.contains(key)) return@forEach
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
            }
            changed = true
        }
        if (changed && !editor.commit()) return

        // A crash after the first commit is safe: the destination wins on the next launch.
        legacy.edit().apply { toMove.keys.forEach { remove(it) } }.commit()
    }
}
