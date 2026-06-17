package com.nexbytes.h7skertool.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

// ─── Data Model ──────────────────────────────────────────────────────────────

enum class ModType { REQUEST, RESPONSE, HEADER }
enum class ModRuleAction { SET, DELETE }

data class ModRule(
    val field: String = "",
    val value: String = "",
    val action: String = ModRuleAction.SET.name
)

data class ModFile(
    val name: String = "",
    val endpoint: String = "",
    val enabled: Boolean = true,
    val type: String = ModType.RESPONSE.name,
    val rules: List<ModRule> = emptyList(),
    val rawContent: String = "",   // raw JSON override (legacy support + advanced editing)
    val createdAt: Long = 0L,
    val version: Int = 2
) {
    /** Build the override map from rules, or fall back to rawContent */
    fun buildOverrideMap(): Map<Int, String> {
        if (rules.isNotEmpty()) {
            val result = mutableMapOf<Int, String>()
            for (rule in rules) {
                if (rule.action == ModRuleAction.SET.name && rule.field.isNotBlank()) {
                    val fieldNum = rule.field.trim().toIntOrNull()
                    if (fieldNum != null) {
                        result[fieldNum] = rule.value
                    }
                }
            }
            return result
        }
        if (rawContent.isNotBlank()) {
            return ProtoModifier.parseModFields(rawContent)
        }
        return emptyMap()
    }

    fun toJsonString(): String = GSON.toJson(this)
}

// ─── Manager ─────────────────────────────────────────────────────────────────

object ModManager {
    private const val TAG = "ModManager"
    const val MODS_DIR_NAME = "mods"
    private val GSON: Gson = GsonBuilder().setPrettyPrinting().create()

    fun getModsDir(context: Context): File {
        val ext = try {
            context.getExternalFilesDir(null)?.let { File(it, MODS_DIR_NAME) }
        } catch (_: Exception) { null }
        val chosen = if (ext != null && (ext.exists() || ext.mkdirs())) ext
                     else File(context.filesDir, MODS_DIR_NAME).also { it.mkdirs() }
        Log.d(TAG, "Mods dir: ${chosen.absolutePath}")
        return chosen
    }

    fun saveMod(context: Context, mod: ModFile): Boolean {
        return try {
            val dir = getModsDir(context)
            val file = File(dir, "${mod.name.sanitize()}.json")
            file.writeText(GSON.toJson(mod), Charsets.UTF_8)
            Log.i(TAG, "Saved: ${file.absolutePath}")
            true
        } catch (e: Exception) { Log.e(TAG, "saveMod failed: ${e.message}"); false }
    }

    fun saveModFromContent(
        context: Context, name: String, endpoint: String,
        content: String, type: ModType = ModType.RESPONSE
    ): Boolean {
        val mod = ModFile(
            name = name, endpoint = endpoint, enabled = true,
            type = type.name, rawContent = content,
            createdAt = System.currentTimeMillis(), version = 2
        )
        return saveMod(context, mod)
    }

    fun loadMods(context: Context): List<ModFile> {
        return try {
            val dir = getModsDir(context)
            if (!dir.exists()) { dir.mkdirs(); return emptyList() }
            dir.listFiles { f -> f.extension == "json" }
                ?.mapNotNull { f ->
                    try {
                        val t = f.readText(Charsets.UTF_8)
                        if (t.isBlank()) null
                        else GSON.fromJson(t, ModFile::class.java)
                    } catch (e: Exception) { Log.w(TAG, "Skip ${f.name}: ${e.message}"); null }
                }
                ?.sortedByDescending { it.createdAt }
                ?: emptyList()
        } catch (e: Exception) { Log.e(TAG, "loadMods: ${e.message}"); emptyList() }
    }

    fun deleteMod(context: Context, name: String): Boolean {
        return try {
            File(getModsDir(context), "${name.sanitize()}.json").delete()
        } catch (_: Exception) { false }
    }

    fun setEnabled(context: Context, name: String, enabled: Boolean): Boolean {
        val dir = getModsDir(context)
        val file = File(dir, "${name.sanitize()}.json")
        return try {
            val mod = GSON.fromJson(file.readText(Charsets.UTF_8), ModFile::class.java)
            file.writeText(GSON.toJson(mod.copy(enabled = enabled)), Charsets.UTF_8)
            true
        } catch (e: Exception) { Log.e(TAG, "setEnabled: ${e.message}"); false }
    }

    fun exportMod(context: Context, mod: ModFile): File? {
        return try {
            val dir = context.getExternalFilesDir("ModExports")
                ?: File(context.filesDir, "ModExports")
            dir.mkdirs()
            val out = File(dir, "${mod.name.sanitize()}.json")
            out.writeText(GSON.toJson(mod), Charsets.UTF_8)
            out
        } catch (e: Exception) { Log.e(TAG, "exportMod: ${e.message}"); null }
    }

    fun importMod(context: Context, uri: Uri): ModFile? {
        return try {
            val text = context.contentResolver.openInputStream(uri)
                ?.bufferedReader()?.readText() ?: return null
            val mod = GSON.fromJson(text.trim(), ModFile::class.java) ?: return null
            saveMod(context, mod)
            mod
        } catch (e: Exception) { Log.e(TAG, "importMod: ${e.message}"); null }
    }

    private val GSON_LOCAL: Gson get() = GSON
}

private val GSON = GsonBuilder().setPrettyPrinting().create()
private fun String.sanitize(): String =
    replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(64)
