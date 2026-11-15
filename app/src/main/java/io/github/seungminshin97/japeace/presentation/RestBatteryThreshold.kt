package io.github.seungminshin97.japeace.presentation

import android.content.Context
import android.content.SharedPreferences

internal class RestBatteryThreshold(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences("vehicle-rest-battery", Context.MODE_PRIVATE)

    fun read(): Int = preferences.getInt(key, defaultPercent).takeIf(::isValid) ?: defaultPercent

    fun save(percent: Int): Boolean {
        if (!isValid(percent)) return false
        preferences.edit().putInt(key, percent).apply()
        return true
    }

    fun observe(onChange: (Int) -> Unit): AutoCloseable {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, changed ->
            if (changed == key || changed == null) onChange(read())
        }
        preferences.registerOnSharedPreferenceChangeListener(listener)
        return AutoCloseable { preferences.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    companion object {
        private const val key = "threshold"
        private const val defaultPercent = 20

        fun isValid(percent: Int) = percent in 1..99
    }
}
