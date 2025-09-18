// File: app/src/main/java/com/rdinfo/prefs/ThemePrefs.kt
package com.rdinfo.prefs

import android.content.Context
import android.content.Context.MODE_PRIVATE

object ThemePrefs {
    private const val PREFS = "rdinfo_prefs"
    private const val KEY_DARK = "dark_mode"

    fun get(ctx: Context): Boolean =
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_DARK, false)

    fun set(ctx: Context, dark: Boolean) {
        ctx.getSharedPreferences(PREFS, MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_DARK, dark)
            .apply()
    }
}
