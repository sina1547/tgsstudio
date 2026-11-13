package ir.sina.tgsstudio.data

import android.content.Context
import ir.sina.tgsstudio.ui.Lang
import java.util.Locale

/** Small persisted UI state: language and which editor panels were open. */
class AppPrefs(context: Context) {
    private val sp = context.getSharedPreferences("studio", Context.MODE_PRIVATE)

    /** "system", "fa" or "en". */
    var languageSetting: String
        get() = sp.getString("lang", "system") ?: "system"
        set(v) { sp.edit().putString("lang", v).apply() }

    fun language(): Lang = when (languageSetting) {
        "fa" -> Lang.FA
        "en" -> Lang.EN
        else -> if (Locale.getDefault().language == "fa") Lang.FA else Lang.EN
    }

    var openPanel: String
        get() = sp.getString("panel", "layers") ?: "layers"
        set(v) { sp.edit().putString("panel", v).apply() }
    var panelOpen: Boolean
        get() = sp.getBoolean("panelOpen", false)
        set(v) { sp.edit().putBoolean("panelOpen", v).apply() }
    var panelHeightDp: Float
        get() = sp.getFloat("panelHeight", 260f)
        set(v) { sp.edit().putFloat("panelHeight", v).apply() }
    var workspace: String
        get() = sp.getString("workspace", "design") ?: "design"
        set(v) { sp.edit().putString("workspace", v).apply() }
}
