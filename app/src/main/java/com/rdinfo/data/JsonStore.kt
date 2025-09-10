// app/src/main/java/com/rdinfo/data/JsonStore.kt
package com.rdinfo.data

import android.content.Context
import android.content.res.AssetManager
import java.io.File
import java.nio.charset.Charset
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Liest/Schreibt die medications.json im internen App-Speicher.
 * - Lesen: wenn /files/medications.json existiert -> diese; sonst Fallback: assets/medications.json
 * - Schreiben: legt /files/medications.json an und erstellt vorher ein Backup
 */
object JsonStore {
    private const val FILE_NAME = "medications.json"

    private fun file(ctx: Context) = File(ctx.filesDir, FILE_NAME)

    fun exists(ctx: Context): Boolean = file(ctx).exists()

    fun readLocal(ctx: Context, charset: Charset = Charsets.UTF_8): String? =
        file(ctx).takeIf { it.exists() }?.readText(charset)

    fun readWithAssetsFallback(ctx: Context, assets: AssetManager, charset: Charset = Charsets.UTF_8): String {
        val local = readLocal(ctx, charset)
        if (local != null) return local
        return assets.open(FILE_NAME).bufferedReader(charset).use { it.readText() }
    }

    fun writeWithBackup(ctx: Context, json: String, charset: Charset = Charsets.UTF_8): Result<Unit> = runCatching {
        val target = file(ctx)
        // Backup der bisherigen Datei (falls vorhanden)
        if (target.exists()) {
            val ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
            val backup = File(ctx.filesDir, "medications_$ts.json.bak")
            target.copyTo(backup, overwrite = true)
        }
        target.writeText(json, charset)
    }
}