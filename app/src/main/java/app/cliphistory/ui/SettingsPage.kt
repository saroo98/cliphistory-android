package app.cliphistory.ui

import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import app.cliphistory.BuildConfig
import app.cliphistory.R
import app.cliphistory.client.DaemonClient
import app.cliphistory.core.*

class SettingsPage(private val ui:Ui,private val pages:DetailPages,private val settings:AppSettings,private val client:DaemonClient) {
    enum class Action { LIMIT, DUPLICATES, START, STOP, TILE_ADD, APPEARANCE, BATTERY, WELCOME, SUPPORT, GUIDE, NOTIFICATIONS, TILE_MODE, MOTION, WELCOME_FREQUENCY, SOURCE, LICENSES }
    data class Page(val view:View,val update:(Bundle)->Unit)
    private fun s(id:Int)=ui.activity.getString(id)
    fun create(status:Bundle,back:()->Unit,action:(Action)->Unit,localChanged:(String)->Unit):Page {
        var latest=status
        var updating=false
        var pauseSaving=false
        var recoverySaving=false
        lateinit var paused:Switch
        lateinit var automatic:Switch
        lateinit var afterBoot:Switch
        lateinit var bootBody:TextView
        lateinit var duplicate:Ui.PreferenceRow
        lateinit var limit:Ui.PreferenceRow
        lateinit var appearance:Ui.PreferenceRow
        lateinit var motion:Ui.PreferenceRow
        lateinit var welcome:Ui.PreferenceRow
        lateinit var tileAdd:Ui.PreferenceRow
        lateinit var tileMode:Ui.PreferenceRow
        lateinit var offlineNotice:TextView
        lateinit var recorderLabel:TextView
        lateinit var recorderDot:View
        lateinit var start:Button
        lateinit var stop:View

        fun enabled(view:View,value:Boolean) { view.isEnabled=value;view.isFocusable=value }
        fun recoveryState() {
            val options=client.preferences.options()
            updating=true;automatic.isChecked=options.automatic;afterBoot.isChecked=options.afterBoot;updating=false
            enabled(automatic,!recoverySaving);enabled(afterBoot,options.automatic && !recoverySaving)
            bootBody.text=s(if(options.automatic)R.string.resume_after_boot_body else R.string.html_port_boot_disabled)
            afterBoot.contentDescription="${s(R.string.resume_after_boot)}. ${bootBody.text}"
        }
        val view=pages.page(s(R.string.settings),back) { content,_ ->
            fun group(id:Int) {
                content.addView(ui.space(if(content.childCount==0)4 else 28))
                content.addView(ui.title(s(id),18f).apply { isAccessibilityHeading=true });content.addView(ui.space(12))
            }
            fun subheading(id:Int) {
                content.addView(ui.space(20));content.addView(ui.title(s(id),16f).apply {
                    isAccessibilityHeading=true;setPadding(ui.dp(12),0,ui.dp(12),0)
                });content.addView(ui.space(10))
            }
            fun toggle(id:Int,checked:Boolean,body:Int?=null,change:(Switch,Boolean)->Unit):Pair<Switch,TextView?> {
                val control=Switch(ui.activity).apply {
                    text=s(id);textSize=16f;minimumHeight=ui.dp(52)
                    setPadding(ui.dp(12),ui.dp(12),ui.dp(12),ui.dp(12));isChecked=checked
                    setOnCheckedChangeListener { _,value -> if(!updating)change(this,value) }
                }
                ui.styleSwitch(control);content.addView(control,LinearLayout.LayoutParams(-1,-2))
                val summary=body?.let { ui.text(s(it),14f,true).apply {
                    setPadding(ui.dp(12),0,ui.dp(12),ui.dp(12));contentDescription=null
                } }
                summary?.let { content.addView(it);control.contentDescription="${s(id)}. ${it.text}" }
                return control to summary
            }
            fun preference(id:Int,which:Action,summary:String="",available:Boolean=true)=
                ui.preferenceRow(content,s(id),summary,available){action(which)}.apply { view.tag=which.name }
            fun row(id:Int,which:Action)=ui.actionRow(content,s(id)){action(which)}.apply { tag=which.name }

            group(R.string.recording)
            val recorder=LinearLayout(ui.activity).apply { gravity=Gravity.CENTER_VERTICAL;setPadding(ui.dp(12),0,ui.dp(12),ui.dp(12)) }
            recorderDot=View(ui.activity)
            recorder.addView(recorderDot,LinearLayout.LayoutParams(ui.dp(7),ui.dp(7)).apply { marginEnd=ui.dp(8) })
            recorderLabel=ui.text("",14f,true);recorder.addView(recorderLabel,LinearLayout.LayoutParams(0,-2,1f));content.addView(recorder)
            offlineNotice=ui.text(s(R.string.audit_offline_controls),14f,true).apply { setPadding(ui.dp(12),0,ui.dp(12),ui.dp(12)) };content.addView(offlineNotice)
            paused=toggle(R.string.pause_recording,latest.getBoolean("paused"),R.string.html_port_pause_body) { control,value ->
                pauseSaving=true;enabled(control,false)
                client.command({it.setPaused(value)}) { result ->
                    if(result.getBoolean("ok"))latest=result
                    updating=true;control.isChecked=latest.getBoolean("paused");updating=false
                    pauseSaving=false;enabled(control,client.connected())
                    localChanged(if(result.getBoolean("ok"))"" else result.getString("issue","PREFERENCES_NOT_SAVED").orEmpty())
                }
            }.first
            duplicate=preference(R.string.duplicate_handling,Action.DUPLICATES,available=client.connected())
            limit=preference(R.string.history_limit,Action.LIMIT,available=client.connected())
            start=ui.button(s(R.string.connect),primary=true){action(Action.START)}.apply { tag=Action.START.name };content.addView(start)
            stop=ui.actionRow(content,s(R.string.stop_recorder),danger=true){action(Action.STOP)}.apply { tag=Action.STOP.name }

            group(R.string.audit_recovery_heading)
            subheading(R.string.html_port_automatic_resumption)
            val options=client.preferences.options()
            automatic=toggle(R.string.automatic_recovery,options.automatic,R.string.automatic_recovery_body) { _,value ->
                recoverySaving=true;enabled(automatic,false);enabled(afterBoot,false)
                client.updateRecovery(automatic=value) { saved ->
                    recoverySaving=false;recoveryState();localChanged(if(saved)"" else "PREFERENCES_NOT_SAVED")
                }
            }.first
            val boot=toggle(R.string.resume_after_boot,options.afterBoot,R.string.resume_after_boot_body) { _,value ->
                recoverySaving=true;enabled(automatic,false);enabled(afterBoot,false)
                client.updateRecovery(afterBoot=value) { saved ->
                    recoverySaving=false;recoveryState();localChanged(if(saved)"" else "PREFERENCES_NOT_SAVED")
                }
            }
            afterBoot=boot.first;bootBody=boot.second!!
            subheading(R.string.html_port_optional_setup)
            preference(R.string.background_reliability,Action.BATTERY,s(R.string.html_port_battery_summary))
            preference(R.string.recovery_notification,Action.NOTIFICATIONS,s(R.string.html_port_notification_summary))
            toggle(R.string.background_suggestion,settings.backgroundSuggestion,R.string.html_port_suggestion_body) { _,value -> settings.backgroundSuggestion=value;localChanged("") }
            row(R.string.always_on_guide,Action.GUIDE)

            group(R.string.quick_access)
            toggle(R.string.tile_enabled,settings.tileEnabled) { control,value ->
                try {
                    ui.activity.packageManager.setComponentEnabledSetting(ComponentName(ui.activity,ClipboardTileService::class.java),
                        if(value)PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP)
                    settings.tileEnabled=value
                    enabled(tileAdd.view,value);tileAdd.summary.text=s(if(value)R.string.html_port_tile_add_summary else R.string.html_port_tile_disabled_summary)
                    localChanged("")
                } catch (_:Exception) {
                    updating=true;control.isChecked=settings.tileEnabled;updating=false;localChanged("TILE_CHANGE_FAILED")
                }
            }
            tileAdd=preference(R.string.add_tile_menu,Action.TILE_ADD,available=settings.tileEnabled)
            tileMode=preference(R.string.html_port_tile_opens,Action.TILE_MODE)
            toggle(R.string.return_after_copy,settings.returnAfterCopy,R.string.return_body) { _,value -> settings.returnAfterCopy=value;localChanged("") }

            group(R.string.privacy)
            toggle(R.string.allow_screenshots,settings.allowScreenshots,R.string.allow_screenshots_body) { _,value -> settings.allowScreenshots=value;localChanged("") }
            toggle(R.string.hide_recents,settings.hideRecents,R.string.html_port_recents_body) { _,value -> settings.hideRecents=value;localChanged("") }
            content.addView(ui.text(s(R.string.privacy_mandatory),15f,true).apply { setPadding(ui.dp(12),ui.dp(8),ui.dp(12),0) })

            group(R.string.appearance)
            appearance=preference(R.string.appearance,Action.APPEARANCE)
            motion=preference(R.string.html_port_motion_preference,Action.MOTION)
            group(R.string.html_port_welcome_heading)
            welcome=preference(R.string.html_port_welcome_frequency,Action.WELCOME_FREQUENCY)
            row(R.string.show_welcome,Action.WELCOME)
            group(R.string.about)
            content.addView(ui.brandIdentity().apply { setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8)) })
            content.addView(ui.text(ui.activity.getString(R.string.version_license,BuildConfig.VERSION_NAME),15f,true).apply { setPadding(ui.dp(12),ui.dp(8),ui.dp(12),ui.dp(8)) })
            row(R.string.source_code,Action.SOURCE);row(R.string.licenses,Action.LICENSES);row(R.string.support_project,Action.SUPPORT)
        }
        val update:(Bundle)->Unit={ new ->
            latest=new
            val connected=client.connected()
            val state=recorderState(client.connectionStage(),new.getBoolean("paused"),new.getBoolean("active"),new.getString("selfTest")=="PASS",new.getString("issue").orEmpty().isNotEmpty())
            val stopped=new.getBoolean("explicitStop")
            val label=if(stopped)R.string.user_stopped_title else when(state) {
                RecorderState.RECORDING->R.string.recording;RecorderState.LISTENING->R.string.listening;RecorderState.PAUSED->R.string.paused
                RecorderState.CONNECTING->R.string.connecting;RecorderState.MISSING->R.string.missing_title;RecorderState.PERMISSION->R.string.permission_title
                RecorderState.DENIED->R.string.denied_title;RecorderState.UNSUPPORTED->R.string.unsupported_title;RecorderState.ATTENTION->R.string.attention_title
                else->R.string.unavailable_title
            }
            recorderLabel.text=s(label);recorderDot.background=ui.shape(if(state==RecorderState.RECORDING || state==RecorderState.LISTENING)ui.accent else ui.muted,4)
            updating=true;paused.isChecked=new.getBoolean("paused");updating=false
            enabled(paused,connected && !pauseSaving);enabled(duplicate.view,connected);enabled(limit.view,connected)
            offlineNotice.visibility=if(connected)View.GONE else View.VISIBLE
            start.visibility=if(connected)View.GONE else View.VISIBLE;stop.visibility=if(connected)View.VISIBLE else View.GONE
            start.text=s(if(stopped)R.string.start_recorder else R.string.connect);enabled(start,state!=RecorderState.CONNECTING)
            duplicate.summary.text=s(if(!new.containsKey("duplicateMode"))R.string.not_confirmed
                else if(new.getInt("duplicateMode")==DuplicateMode.CONSECUTIVE_ONLY.value)R.string.duplicates_consecutive else R.string.duplicates_unique)
            limit.summary.text=if(new.containsKey("limit"))ui.activity.getString(R.string.audit_limit_summary,new.getInt("limit")) else s(R.string.not_confirmed)
            appearance.summary.text=s(when(settings.appearance) { "light"->R.string.light;"dark"->R.string.dark;else->R.string.system })
            motion.summary.text=s(if(settings.motion)R.string.motion_system else R.string.html_port_motion_reduced)
            welcome.summary.text=s(when(settings.welcome) { "each"->R.string.welcome_each;"never"->R.string.welcome_off;else->R.string.welcome_once })
            tileMode.summary.text=s(if(settings.tileMode=="history")R.string.open_full_history else R.string.quick_copy)
            enabled(tileAdd.view,settings.tileEnabled);tileAdd.summary.text=s(if(settings.tileEnabled)R.string.html_port_tile_add_summary else R.string.html_port_tile_disabled_summary)
            recoveryState()
        }
        update(status)
        return Page(view,update)
    }
}
