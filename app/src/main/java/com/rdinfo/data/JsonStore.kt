// File: app/src/main/java/com/rdinfo/data/JsonStore.kt
package com.rdinfo.data

import android.content.Context
import android.content.res.AssetManager
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Persistenz der Medikamenten-Daten.
 * - Primärdatei: filesDir/medications.json
 * - Fallback:     assets/medications.json
 * - Backups:      filesDir/backups/medications_yyyyMMdd_HHmmss.json (Rotation)
 *
 * Bereits vorhandene API:
 *  - readWithAssetsFallback(ctx, assets)
 *  - writeWithBackup(ctx, json)
 *
 * Neu:
 *  - createManualBackup(ctx)
 *  - listBackups(ctx)
 *  - restoreBackup(ctx, file)
 *  - exportToUri(ctx, dest)
 *  - importFromUri(ctx, src)
 */
object JsonStore {

    private const val FILE_NAME = "medications.json"
    private const val ASSET_NAME = "medications.json"
    private const val BACKUP_DIR = "backups"
    private const val BACKUP_PREFIX = "medications_"
    private const val BACKUP_SUFFIX = ".json"
    private const val MAX_BACKUPS = 5

    // ---------- Public API ----------

    /** Liest User-Datei (filesDir) oder fällt auf Assets zurück. */
    fun readWithAssetsFallback(ctx: Context, assets: AssetManager): String {
        val f = currentFile(ctx)
        return if (f.exists()) {
            f.readText(Charsets.UTF_8)
        } else {
            assets.open(ASSET_NAME).use { it.readBytes().toString(Charsets.UTF_8) }
        }
    }

    /**
     * Schreibt in die User-Datei **atomar** und legt dabei direkt einen rotierten Backup-Snapshot an.
     */
    fun writeWithBackup(ctx: Context, json: String): Result<Unit> = runCatching {
        require(isValidJson(json)) { "Ungültiges JSON (Top-Level 'medications' fehlt)." }

        // 1) Zielordner sicherstellen
        val file = currentFile(ctx)
        if (!file.parentFile!!.exists()) file.parentFile!!.mkdirs()

        // 2) Atomar schreiben (tmp → rename)
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        FileOutputStream(tmp).use { it.channel.truncate(0); it.write(json.toByteArray(Charsets.UTF_8)) }
        if (file.exists()) file.delete()
        if (!tmp.renameTo(file)) {
            // Fallback: copy
            FileInputStream(tmp).use { input ->
                FileOutputStream(file).use { out -> input.copyTo(out) }
            }
            tmp.delete()
        }

        // 3) Snapshot-Backup erstellen + rotieren
        snapshotBackup(ctx, json)
        rotateBackups(ctx, MAX_BACKUPS)
    }

    /** Erzeugt manuell einen Backup-Snapshot der aktuellen User-Datei. */
    fun createManualBackup(ctx: Context): Result<File> = runCatching {
        val json = readWithAssetsFallback(ctx, ctx.assets) // auch ok, wenn User-Datei noch nicht existiert
        val f = snapshotBackup(ctx, json)
        rotateBackups(ctx, MAX_BACKUPS)
        f
    }

    /** Listet alle Backups (neu → alt). */
    fun listBackups(ctx: Context): List<File> {
        val dir = backupDir(ctx)
        if (!dir.exists()) return emptyList()
        return dir.listFiles { f -> f.isFile && f.name.startsWith(BACKUP_PREFIX) && f.name.endsWith(BACKUP_SUFFIX) }
            ?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /** Stellt ein Backup wieder her (kopiert es als aktuelle medications.json und legt dabei einen Backup-Snapshot an). */
    fun restoreBackup(ctx: Context, file: File): Result<Unit> = runCatching {
        require(file.exists()) { "Backupdatei nicht gefunden." }
        val json = file.readText(Charsets.UTF_8)
        writeWithBackup(ctx, json).getOrThrow()
    }

    /** Exportiert die aktuelle Datenbank (User-Datei oder Asset-Fallback) in eine vom Nutzer gewählte Zieldatei (SAF-URI). */
    fun exportToUri(ctx: Context, dest: Uri): Result<Unit> = runCatching {
        val json = readWithAssetsFallback(ctx, ctx.assets)
        ctx.contentResolver.openOutputStream(dest, "w").use { out ->
            requireNotNull(out) { "Ziel konnte nicht geöffnet werden." }
            out.write(json.toByteArray(Charsets.UTF_8))
            out.flush()
        }
    }

    /** Importiert eine JSON-Datei (SAF-URI), validiert minimal und setzt sie als neue User-Datei (mit Backup/Rotation). */
    fun importFromUri(ctx: Context, src: Uri): Result<Unit> = runCatching {
        val json = ctx.contentResolver.openInputStream(src).use { inp ->
            requireNotNull(inp) { "Quelldatei konnte nicht geöffnet werden." }
            inp.readBytes().toString(Charsets.UTF_8)
        }
        require(isValidJson(json)) { "Ungültiges JSON – 'medications' fehlt." }
        writeWithBackup(ctx, json).getOrThrow()
    }

    // ---------- Helpers ----------

    private fun currentFile(ctx: Context): File = File(ctx.filesDir, FILE_NAME)

    private fun backupDir(ctx: Context): File = File(ctx.filesDir, BACKUP_DIR)

    private fun snapshotBackup(ctx: Context, json: String): File {
        val dir = backupDir(ctx)
        if (!dir.exists()) dir.mkdirs()
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val f = File(dir, "$BACKUP_PREFIX$ts$BACKUP_SUFFIX")
        FileOutputStream(f).use { it.write(json.toByteArray(Charsets.UTF_8)) }
        return f
    }

    private fun rotateBackups(ctx: Context, keep: Int) {
        val files = listBackups(ctx)
        if (files.size <= keep) return
        files.drop(keep).forEach { runCatching { it.delete() } }
    }

    /** Minimale Schema-Prüfung: Top-Level-Objekt mit Array "medications". */
    private fun isValidJson(json: String): Boolean = runCatching {
        val obj = JSONObject(json)
        obj.has("medications") && obj.optJSONArray("medications") != null
    }.getOrDefault(false)
}
