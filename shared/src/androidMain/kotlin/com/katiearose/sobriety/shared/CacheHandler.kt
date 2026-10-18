package com.katiearose.sobriety.shared

import android.content.Context
import android.net.Uri
import android.widget.Toast
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.*
import java.util.*

class CacheHandler(private val context: Context) {

    companion object {
        private val JsonSerializer = Json { ignoreUnknownKeys = true }
        private val listSerializer = ListSerializer(AddictionSerializer)
    }

    fun readCache(input: InputStream): List<Addiction> {
        BufferedInputStream(input).use { stream ->
            val text = try {
                Scanner(stream).useDelimiter("\\A").use { if (it.hasNext()) it.next() else "" }
            } catch (e: Exception) {
                return emptyList()
            }
            if (text.isBlank()) return emptyList()
            return try {
                JsonSerializer.decodeFromString(text)
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    fun writeCache(addictions: List<Addiction>) {
        val live = File(context.filesDir, "Sobriety.cache")
        val tmp = File(context.filesDir, "Sobriety.cache.tmp")
        BufferedWriter(FileWriter(tmp)).use {
            it.write(JsonSerializer.encodeToString(listSerializer, addictions))
        }
        if (!tmp.renameTo(live)) {
            tmp.copyTo(live, overwrite = true)
            tmp.delete()
        }
    }

    fun exportData(addictions: List<Addiction>, output: Uri) {
        BufferedWriter(OutputStreamWriter(context.contentResolver.openOutputStream(output))).use {
            it.write(JsonSerializer.encodeToString(listSerializer, addictions))
        }
    }

    fun importData(input: Uri): List<Addiction>? {
        val stream = context.contentResolver.openInputStream(input)
        if (stream == null) {
            Toast.makeText(context, R.string.cant_import_data, Toast.LENGTH_SHORT).show()
            return null
        }
        return runCatching { readCache(stream) }.getOrElse {
            Toast.makeText(context, R.string.cant_import_data, Toast.LENGTH_SHORT).show()
            null
        }
    }

    fun backupCache() {
        val cache = File(context.filesDir, "Sobriety.cache")
        if (cache.exists())
            cache.copyTo(File(context.filesDir, "Sobriety.cache.bak"), overwrite = true)
    }

    fun backupCacheIfMissing() {
        val cache = File(context.filesDir, "Sobriety.cache")
        val backup = File(context.filesDir, "Sobriety.cache.bak")
        if (cache.exists() && !backup.exists())
            cache.copyTo(backup, overwrite = true)
    }

    fun readBackup(): List<Addiction>? {
        val backup = File(context.filesDir, "Sobriety.cache.bak")
        if (!backup.exists()) return null
        return runCatching {
            backup.inputStream().use { readCache(it) }.takeIf { it.isNotEmpty() }
        }.getOrNull()
    }
}
