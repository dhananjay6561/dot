package com.dj.dot.data

import android.content.ContextWrapper
import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy

class SettingsMigrationTest {
    private class MemoryPrefs(initial: Map<String, Any> = emptyMap()) {
        val values = initial.toMutableMap()
        var failNextCommit = false

        val prefs: SharedPreferences = proxy(SharedPreferences::class.java) { _, method, args ->
            when (method) {
                "getAll" -> values.toMap()
                "contains" -> values.containsKey(args[0])
                "getString", "getInt", "getLong", "getFloat", "getBoolean", "getStringSet" ->
                    values[args[0]] ?: args[1]
                "edit" -> editor()
                else -> error("Unexpected SharedPreferences call: $method")
            }
        }

        private fun editor(): SharedPreferences.Editor {
            val updates = mutableMapOf<String, Any?>()
            val removals = mutableSetOf<String>()
            var clear = false
            return proxy(SharedPreferences.Editor::class.java) { self, method, args ->
                when (method) {
                    "commit", "apply" -> {
                        if (failNextCommit) {
                            failNextCommit = false
                            if (method == "commit") false else Unit
                        } else {
                            if (clear) values.clear()
                            removals.forEach(values::remove)
                            updates.forEach { (key, value) ->
                                if (value == null) values.remove(key) else values[key] = value
                            }
                            if (method == "commit") true else Unit
                        }
                    }
                    "clear" -> { clear = true; self }
                    "remove" -> { removals += args[0] as String; self }
                    else -> {
                        require(method.startsWith("put")) { "Unexpected editor call: $method" }
                        updates[args[0] as String] = args[1]
                        self
                    }
                }
            }
        }

        private fun <T> proxy(type: Class<T>, handler: (Any, String, Array<Any?>) -> Any?): T =
            type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { self, method, raw ->
                handler(self, method.name, raw ?: emptyArray())
            })
    }

    @Test fun onlyPortableSettingsMoveAndDeviceDataStaysLocal() {
        val legacy = MemoryPrefs(mapOf(
            "crossfade_ms" to 4000, "speed" to 1.25f, "eq_on" to true,
            "eq_preset" to "Warm", "shuffle" to true, "repeat" to 2,
            "lyrics_folders" to "content://tree/old", "q_ids" to "12,13",
            "q_index" to 1, "q_pos" to 25L, "lrc_off_12" to 250,
            "future_device_value" to "private",
        ))
        val portable = MemoryPrefs()

        SettingsMigration.migrate(legacy.prefs, portable.prefs)

        assertEquals(mapOf(
            "crossfade_ms" to 4000, "speed" to 1.25f, "eq_on" to true,
            "eq_preset" to "Warm", "shuffle" to true, "repeat" to 2,
        ), portable.values)
        assertEquals(mapOf(
            "lyrics_folders" to "content://tree/old", "q_ids" to "12,13",
            "q_index" to 1, "q_pos" to 25L, "lrc_off_12" to 250,
            "future_device_value" to "private",
        ), legacy.values)
    }

    @Test fun existingPortableValuesWinAndRetryIsIdempotent() {
        val legacy = MemoryPrefs(mapOf("speed" to 1.5f, "eq_preset" to "Old"))
        val portable = MemoryPrefs(mapOf("speed" to 0.9f))

        SettingsMigration.migrate(legacy.prefs, portable.prefs)
        SettingsMigration.migrate(legacy.prefs, portable.prefs)

        assertEquals(mapOf("speed" to 0.9f, "eq_preset" to "Old"), portable.values)
        assertTrue(legacy.values.isEmpty())
    }

    @Test fun failedDestinationCommitLeavesOriginalSettingsForRetry() {
        val legacy = MemoryPrefs(mapOf("speed" to 1.5f, "q_ids" to "42"))
        val portable = MemoryPrefs().also { it.failNextCommit = true }

        SettingsMigration.migrate(legacy.prefs, portable.prefs)
        assertTrue(portable.values.isEmpty())
        assertEquals(1.5f, legacy.values["speed"])

        SettingsMigration.migrate(legacy.prefs, portable.prefs)
        assertEquals(1.5f, portable.values["speed"])
        assertFalse(legacy.values.containsKey("speed"))
        assertEquals("42", legacy.values["q_ids"])
    }

    @Test fun settingsKeepDeviceDataLocalAndExplicitlyOptPortableValuesIntoBackup() {
        val device = MemoryPrefs(mapOf(
            "crossfade_ms" to 5000, "q_ids" to "42", "lyrics_folders" to "content://tree/music",
        ))
        val portable = MemoryPrefs()
        val context = object : ContextWrapper(null) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                when (name) {
                    "dot" -> device.prefs
                    "dot_portable" -> portable.prefs
                    else -> error("Unexpected preferences file: $name")
                }
        }

        val settings = Settings(context)
        assertEquals(5000, settings.crossfadeMs.value)
        assertEquals("42", settings.queueIds.value)
        assertEquals("content://tree/music", settings.lyricsFolders.value)
        assertEquals(5000, portable.values["crossfade_ms"])
        assertFalse(portable.values.containsKey("q_ids"))
        assertFalse(portable.values.containsKey("lyrics_folders"))

        settings.crossfadeMs.set(5500)
        settings.queueIds.set("43")
        assertEquals(5500, portable.values["crossfade_ms"])
        assertEquals("43", device.values["q_ids"])
        assertFalse(device.values.containsKey("crossfade_ms"))
    }

    @Test fun portableDeclarationChecksFailOnMissingOrOrphanedMigrationKeys() {
        SettingsMigration.requirePortableKey("speed")
        SettingsMigration.verifyPortableDeclarations(SettingsMigration.portableKeys)

        assertThrows(IllegalStateException::class.java) {
            SettingsMigration.requirePortableKey("future_private_setting")
        }
        assertThrows(IllegalStateException::class.java) {
            SettingsMigration.verifyPortableDeclarations(SettingsMigration.portableKeys - "speed")
        }
        assertThrows(IllegalStateException::class.java) {
            SettingsMigration.verifyPortableDeclarations(SettingsMigration.portableKeys + "future_portable_setting")
        }
    }
}
