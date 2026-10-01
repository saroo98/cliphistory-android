package app.cliphistory.ui

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration

class AppSettings(private val context: Context) {
    private val prefs = context.getSharedPreferences("appearance", Context.MODE_PRIVATE)
    var appearance: String
        get() = choice("mode", "system", modes)
        set(value) { require(value in modes); prefs.edit().putString("mode", value).apply() }
    var returnAfterCopy: Boolean
        get() = flag("return_after_copy", true)
        set(value) { prefs.edit().putBoolean("return_after_copy", value).apply() }
    var allowScreenshots: Boolean
        get() = flag("allow_screenshots", false)
        set(value) { put("allow_screenshots", value) }
    var hideRecents: Boolean
        get() = flag("hide_recents", true)
        set(value) { put("hide_recents", value) }
    var tileEnabled: Boolean
        get() = flag("tile_enabled", true)
        set(value) { put("tile_enabled", value) }
    var tileMode: String
        get() = choice("tile_mode", "quick", listOf("quick", "history"))
        set(value) {
            require(value in listOf("quick", "history"));prefs.edit().putString("tile_mode", value).apply()
        }
    var motion: Boolean
        get() = flag("motion", true)
        set(value) { put("motion", value) }
    var welcome: String
        get() = choice("welcome", "once", listOf("once", "each", "never"))
        set(value) { require(value in listOf("once", "each", "never"));prefs.edit().putString("welcome", value).apply() }
    var welcomeSeen: Boolean
        get() = flag("welcome_seen", false)
        set(value) { put("welcome_seen", value) }
    var backgroundSuggestion: Boolean
        get() = flag("background_suggestion", true)
        set(value) { put("background_suggestion", value) }
    var backgroundSeen: Boolean
        get() = flag("background_seen", false)
        set(value) { put("background_seen", value) }
    fun register(listener:SharedPreferences.OnSharedPreferenceChangeListener) { prefs.registerOnSharedPreferenceChangeListener(listener) }
    fun unregister(listener:SharedPreferences.OnSharedPreferenceChangeListener) { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    private fun flag(key:String,default:Boolean)=try { prefs.getBoolean(key,default) } catch (_:ClassCastException) { default }
    private fun choice(key:String,default:String,values:List<String>)=try { prefs.getString(key,default)?.takeIf { it in values }?:default } catch (_:ClassCastException) { default }
    private fun put(key:String,value:Boolean) { prefs.edit().putBoolean(key,value).apply() }

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
