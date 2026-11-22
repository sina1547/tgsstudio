package ir.sina.tgsstudio.data

import android.content.Context
import ir.sina.tgsstudio.editor.EffectPreset
import ir.sina.tgsstudio.editor.EffectPresets
import java.io.File

/** The user's own effect presets, kept in a small JSON file in app storage. */
class PresetStore(context: Context) {
    private val file = File(context.filesDir, "effect_presets.json")

    fun load(): List<EffectPreset> = if (file.exists()) EffectPresets.fromJson(file.readText()) else emptyList()

    fun save(list: List<EffectPreset>) { file.writeText(EffectPresets.toJson(list)) }
}
