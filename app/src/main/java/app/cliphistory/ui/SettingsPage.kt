package app.cliphistory.ui

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.*
import app.cliphistory.R
import app.cliphistory.client.DaemonClient
import app.cliphistory.core.DuplicateMode

class SettingsPage(private val ui:Ui,private val pages:DetailPages,private val settings:AppSettings,private val client:DaemonClient) {
    enum class Action { LIMIT, DUPLICATES, START, STOP, TILE_ADD, APPEARANCE, BATTERY, WELCOME, SUPPORT, GUIDE, NOTIFICATIONS }
    data class Page(val view:View,val update:(Bundle)->Unit)
    fun create(status:Bundle,back:()->Unit,action:(Action)->Unit,localChanged:(String)->Unit):Page {
        var latest=status
        val mutations=ArrayList<View>()
        lateinit var paused:Switch
        lateinit var duplicate:TextView
        lateinit var limit:TextView
        lateinit var appearance:TextView
        lateinit var offlineNotice:TextView
        var updating=false
        val view=pages.page(ui.activity.getString(R.string.settings),back) { content,_ ->
            fun group(id:Int) {
                content.addView(ui.space(20));content.addView(ui.title(ui.activity.getString(id),18f).apply { isAccessibilityHeading=true })
                content.addView(ui.space(12))
            }
            fun toggle(id:Int,checked:Boolean,body:Int?=null,change:(Switch,Boolean)->Unit):Switch {
                val toggle=Switch(ui.activity).apply {
                    text=ui.activity.getString(id);textSize=16f;setTextColor(ui.ink);minimumHeight=ui.dp(52)
                    setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8));isChecked=checked
                    setOnCheckedChangeListener { _,value -> if(!updating)change(this,value) }
                }
                content.addView(toggle,LinearLayout.LayoutParams(-1,-2))
                body?.let { ui.paragraph(content,ui.activity.getString(it)) }
                return toggle
            }
            fun row(id:Int,which:Action,enabled:Boolean=true) {
                ui.actionRow(content,ui.activity.getString(id),enabled=enabled){action(which)}
            }
            group(R.string.recording)
            offlineNotice=ui.text(ui.activity.getString(R.string.audit_offline_controls),14f,true).apply { setPadding(0,0,0,ui.dp(12)) }
            content.addView(offlineNotice)
            paused=toggle(R.string.pause_recording,latest.getBoolean("paused")) { control,value ->
                control.isEnabled=false
                client.command({it.setPaused(value)}) { result ->
                    if(result.getBoolean("ok"))latest=result
                    updating=true;control.isChecked=latest.getBoolean("paused");updating=false
                    control.isEnabled=client.connected();localChanged(if(result.getBoolean("ok"))"" else result.getString("issue","PREFERENCES_NOT_SAVED").orEmpty())
                }
            };mutations.add(paused)
            duplicate=ui.text("",14f,true)
            row(R.string.duplicate_handling,Action.DUPLICATES,client.connected());mutations.add(content.getChildAt(content.childCount-1))
            content.addView(duplicate)
            row(R.string.history_limit,Action.LIMIT,client.connected());mutations.add(content.getChildAt(content.childCount-1))
            limit=ui.text("",14f,true);content.addView(limit)
            row(R.string.start_recorder,Action.START)
            row(R.string.stop_recorder,Action.STOP)

            group(R.string.audit_recovery_heading)
            val options=client.preferences.options()
            toggle(R.string.automatic_recovery,options.automatic,R.string.automatic_recovery_body) { control,value ->
                control.isEnabled=false
                client.updateRecovery(automatic=value) { saved ->
                    updating=true;control.isChecked=client.preferences.options().automatic;updating=false
                    control.isEnabled=true;localChanged(if(saved)"" else "PREFERENCES_NOT_SAVED")
                }
            }
            toggle(R.string.resume_after_boot,options.afterBoot,R.string.resume_after_boot_body) { control,value ->
                control.isEnabled=false
                client.updateRecovery(afterBoot=value) { saved ->
                    updating=true;control.isChecked=client.preferences.options().afterBoot;updating=false
                    control.isEnabled=true;localChanged(if(saved)"" else "PREFERENCES_NOT_SAVED")
                }
            }
            row(R.string.background_reliability,Action.BATTERY)
            row(R.string.recovery_notification,Action.NOTIFICATIONS)
            toggle(R.string.background_suggestion,settings.backgroundSuggestion) { _,value -> settings.backgroundSuggestion=value;localChanged("") }
            ui.paragraph(content,ui.activity.getString(R.string.background_optional))

            group(R.string.quick_access)
            toggle(R.string.tile_enabled,settings.tileEnabled) { control,value ->
                try {
                    ui.activity.packageManager.setComponentEnabledSetting(ComponentName(ui.activity,ClipboardTileService::class.java),
                        if(value)PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP)
                    settings.tileEnabled=value
                    localChanged("")
                } catch (_:Exception) {
                    updating=true;control.isChecked=settings.tileEnabled;updating=false;localChanged("TILE_CHANGE_FAILED")
                }
            }
            row(R.string.add_tile_menu,Action.TILE_ADD)
            val modes=RadioGroup(ui.activity)
            listOf("quick" to R.string.quick_copy,"history" to R.string.open_full_history).forEach { (mode,label) ->
                modes.addView(RadioButton(ui.activity).apply {
                    id=View.generateViewId();tag=mode;text=ui.activity.getString(label);textSize=16f;setTextColor(ui.ink)
                    minimumHeight=ui.dp(52);isChecked=settings.tileMode==mode
                })
            }
            modes.setOnCheckedChangeListener { group,id -> settings.tileMode=group.findViewById<RadioButton>(id).tag as String;localChanged("") }
            content.addView(modes);ui.paragraph(content,ui.activity.getString(R.string.quick_copy_body))
            toggle(R.string.return_after_copy,settings.returnAfterCopy) { _,value -> settings.returnAfterCopy=value;localChanged("") }

            group(R.string.privacy)
            toggle(R.string.allow_screenshots,settings.allowScreenshots,R.string.allow_screenshots_body) { _,value -> settings.allowScreenshots=value;localChanged("") }
            toggle(R.string.hide_recents,settings.hideRecents) { _,value -> settings.hideRecents=value;localChanged("") }
            ui.paragraph(content,ui.activity.getString(R.string.privacy_mandatory))

            group(R.string.appearance)
            row(R.string.appearance,Action.APPEARANCE)
            appearance=ui.text("",14f,true);content.addView(appearance)
            toggle(R.string.motion_system,settings.motion) { _,value -> settings.motion=value;localChanged("") }

            group(R.string.about)
            val welcome=RadioGroup(ui.activity)
            listOf("once" to R.string.welcome_once,"each" to R.string.welcome_each,"never" to R.string.welcome_off).forEach { (mode,label) ->
                welcome.addView(RadioButton(ui.activity).apply {
                    id=View.generateViewId();tag=mode;text=ui.activity.getString(label);textSize=16f;setTextColor(ui.ink)
                    minimumHeight=ui.dp(52);isChecked=settings.welcome==mode
                })
            }
            welcome.setOnCheckedChangeListener { group,id -> settings.welcome=group.findViewById<RadioButton>(id).tag as String;localChanged("") }
            content.addView(welcome)
            row(R.string.show_welcome,Action.WELCOME);row(R.string.support_project,Action.SUPPORT);row(R.string.always_on_guide,Action.GUIDE)
        }
        val update:(Bundle)->Unit={ new ->
            latest=new;updating=true;paused.isChecked=new.getBoolean("paused");updating=false
            mutations.forEach { it.isEnabled=client.connected();it.isFocusable=client.connected() }
            offlineNotice.visibility=if(client.connected())View.GONE else View.VISIBLE
            duplicate.text=ui.activity.getString(if(!new.containsKey("duplicateMode"))R.string.not_confirmed
                else if(new.getInt("duplicateMode")==DuplicateMode.CONSECUTIVE_ONLY.value)R.string.duplicates_consecutive else R.string.duplicates_unique)
            limit.text=if(new.containsKey("limit"))ui.activity.getString(R.string.audit_limit_summary,new.getInt("limit"))
                else ui.activity.getString(R.string.not_confirmed)
            appearance.text=ui.activity.getString(when(settings.appearance) { "light"->R.string.light;"dark"->R.string.dark;else->R.string.system })
        }
        update(status)
        return Page(view,update)
    }
}
