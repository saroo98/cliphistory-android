package app.cliphistory.ui

import android.content.Context
import android.content.res.Configuration

class AppSettings(context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    var appearance: String
        get() = prefs.getString("mode", "system")?.takeIf { it in modes } ?: "system"
        set(value) { require(value in modes); prefs.edit().putString("mode", value).apply() }
    var returnAfterCopy: Boolean
        get() = prefs.getBoolean("return_after_copy", true)
        set(value) { prefs.edit().putBoolean("return_after_copy", value).apply() }

    companion object {
        val modes = listOf("system", "light", "dark")
        fun themedContext(base: Context): Context {
            val mode = AppSettings(base).appearance
            if (mode == "system") return base
            val config = Configuration(base.resources.configuration)
            config.uiMode = (config.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (mode == "dark") Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
            return base.createConfigurationContext(config)
        }
    }
}
