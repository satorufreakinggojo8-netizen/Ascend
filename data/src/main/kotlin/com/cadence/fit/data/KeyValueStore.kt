package com.cadence.fit.data

import android.content.SharedPreferences

/**
 * The small slice of `SharedPreferences` this app uses.
 *
 * Keeping it behind an interface means the preference *logic* (defaults, clamping,
 * enum fallbacks) can be tested on the JVM without an Android runtime.
 */
interface KeyValueStore {
    fun getString(key: String, defaultValue: String? = null): String?

    fun putString(key: String, value: String?)

    fun getBoolean(key: String, defaultValue: Boolean): Boolean

    fun putBoolean(key: String, value: Boolean)

    fun getInt(key: String, defaultValue: Int): Int

    fun putInt(key: String, value: Int)

    fun getLong(key: String, defaultValue: Long): Long

    fun putLong(key: String, value: Long)

    fun contains(key: String): Boolean

    fun remove(key: String)

    fun clear()
}

class SharedPreferencesStore(private val preferences: SharedPreferences) : KeyValueStore {

    override fun getString(key: String, defaultValue: String?): String? =
        preferences.getString(key, defaultValue)

    override fun putString(key: String, value: String?) {
        preferences.edit().putString(key, value).apply()
    }

    override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
        preferences.getBoolean(key, defaultValue)

    override fun putBoolean(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    override fun getInt(key: String, defaultValue: Int): Int = preferences.getInt(key, defaultValue)

    override fun putInt(key: String, value: Int) {
        preferences.edit().putInt(key, value).apply()
    }

    override fun getLong(key: String, defaultValue: Long): Long = preferences.getLong(key, defaultValue)

    override fun putLong(key: String, value: Long) {
        preferences.edit().putLong(key, value).apply()
    }

    override fun contains(key: String): Boolean = preferences.contains(key)

    override fun remove(key: String) {
        preferences.edit().remove(key).apply()
    }

    override fun clear() {
        preferences.edit().clear().apply()
    }
}
