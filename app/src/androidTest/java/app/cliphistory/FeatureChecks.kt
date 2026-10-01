package app.cliphistory

import android.app.*
import android.content.*
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.os.*
import android.view.*
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.*
import app.cliphistory.client.*
import app.cliphistory.core.*
import app.cliphistory.daemon.FdAccess
import app.cliphistory.ui.*
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Isolated test package only; no runtime debug entry point is shipped. */
class FeatureChecks(private val test:Instrumentation,private val arguments:Bundle=Bundle()) {
    private val context get()=test.targetContext
    private val client get()=(context.applicationContext as ClipApplication).daemon
    private lateinit var activity:MainActivity
    private val log=StringBuilder()
    private var count=0
    private fun main(block:()->Unit) { if(Looper.myLooper()==Looper.getMainLooper())block() else test.runOnMainSync { block() } }
    private fun checkThat(value:Boolean,message:String) {
        check(value){message};count++;log.append("PASS $message\n")
        test.sendStatus(0,Bundle().apply { putString("stream","PASS $message\n") })
    }
    private fun await(message:String,condition:()->Boolean) {
        val end=SystemClock.elapsedRealtime()+15_000
        while(SystemClock.elapsedRealtime()<end) { if(condition()){checkThat(true,message);return};SystemClock.sleep(60) }
        error(message)
    }
    private fun call(name:String,vararg values:Any) {
        MainActivity::class.java.declaredMethods.single { it.name==name && it.parameterCount==values.size }
            .apply { isAccessible=true }.invoke(activity,*values)
    }
    private fun field(name:String)=MainActivity::class.java.getDeclaredField(name).apply { isAccessible=true }.get(activity)
    private fun views(view:View):List<View> = listOf(view)+(if(view is ViewGroup)(0 until view.childCount).flatMap { views(view.getChildAt(it)) } else emptyList())
    private fun capture(name:String,window:Window=activity.window) {
        main {
            val view=window.decorView
            val bitmap=Bitmap.createBitmap(view.width,view.height,Bitmap.Config.ARGB_8888)
            view.draw(android.graphics.Canvas(bitmap))
            File(context.filesDir,"ui-evidence").apply { mkdirs() }.resolve("$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
        }
    }
    private fun seed(paused:Boolean=false,theme:String="light") {
        // These suites intentionally use offline data. Stop only the isolated validation helper.
        stopRecorderForFixture(test)
        checkThat(true,"Validation helper removed before seeding")
        val entries=listOf(Entry(3,3,"Synthetic newest text"),Entry(2,2,"Synthetic second\nwith exact whitespace  "),Entry(1,1,"Synthetic third text"))
        val (a,b)=PrivateHistory.openForDaemon(context)
        listOf(a,b).forEach { FramedSlot(FdAccess(it,context.applicationInfo.uid)).use { slot -> slot.writeAndSync(SnapshotCodec.encode(Snapshot(1,100,4,paused,entries))) } }
        PrivateHistory.invalidate()
        AppSettings(context).apply { welcome="never";backgroundSuggestion=false;allowScreenshots=false;hideRecents=true;appearance=theme;tileMode="quick";tileEnabled=true;motion=true }
        activity=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        await("Synthetic offline history loaded") { var ready=false;main { ready=(field("home") as HistoryHome).adapter.count==3 };ready }
    }
    private fun settings() {
        seed()
        main { call("showSettings") }
        test.waitForIdleSync();capture("settings-recording")
        fun toggle(label:String):Switch=views(activity.window.decorView).filterIsInstance<Switch>().single { it.text.toString()==label }
        main {
            val labels=views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            listOf("Recording","Quick access","Privacy","Appearance","About").forEach { checkThat(labels.contains(it),"Settings group: $it") }
            checkThat(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0,"Screenshots initially blocked")
            toggle("Allow screenshots").performClick()
            checkThat(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE==0,"Screenshots enabled live")
            call("showWelcome")
            val dialog=(field("dialogs") as Set<*>).filterIsInstance<Dialog>().last()
            checkThat(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE==0,"Welcome follows enabled screenshots")
            dialog.dismiss()
            toggle("Allow screenshots").performClick()
            checkThat(activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0,"Screenshots disabled live")
            toggle("Hide history in Recents").performClick()
            checkThat(!AppSettings(context).hideRecents,"Recents privacy independently persisted")
            toggle("Motion: follow system").performClick()
            checkThat(!ScreenPrivacy.motionEnabled(AppSettings(context)),"Motion off overrides system motion")
            checkThat(!toggle("Pause recording").isEnabled,"Recorder mutation disabled offline")
        }
        val page=field("page") as View
        main { views(page).filterIsInstance<ScrollView>().first().scrollTo(0,700) }
        val monitor=test.addMonitor(MainActivity::class.java.name,null,false)
        val orientation=if(activity.resources.configuration.orientation==android.content.res.Configuration.ORIENTATION_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT else ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        main { activity.requestedOrientation=orientation }
        activity=test.waitForMonitorWithTimeout(monitor,10_000) as? MainActivity ?: error("Settings rotation did not recreate")
        test.removeMonitor(monitor)
        main {
            checkThat(field("pageKind")=="settings","Settings page survives rotation")
            checkThat(!AppSettings(context).allowScreenshots && !AppSettings(context).motion,"Settings survive recreation")
        }
        if(orientation==ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
            val portrait=test.addMonitor(MainActivity::class.java.name,null,false)
            main { activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
            activity=test.waitForMonitorWithTimeout(portrait,10_000) as? MainActivity ?: error("Portrait rotation did not recreate")
            test.removeMonitor(portrait)
        }
        checkThat(BackgroundSettings(activity).status().contains("App background restriction"),"Battery states described separately")
        val completed=CountDownLatch(2)
        main {
            client.requestPage("",0) { checkThat(it.rows.size==3,"Activity page not cancelled by independent tile read");completed.countDown() }
            client.requestLatest { checkThat(it.rows.size==3 && it.offline,"Tile read uses valid offline snapshot");completed.countDown() }
        }
        check(completed.await(8,TimeUnit.SECONDS))
        screenshots()
    }
    private fun screenshots() {
        val directory=File(context.filesDir,"ui-evidence").apply { mkdirs() }
        main { AppSettings(context).allowScreenshots=false;ScreenPrivacy.apply(activity,AppSettings(context)) }
        SystemClock.sleep(300)
        val secure=test.uiAutomation.takeScreenshot() ?: error("Secure system screenshot unavailable")
        main { AppSettings(context).allowScreenshots=true;ScreenPrivacy.apply(activity,AppSettings(context)) }
        SystemClock.sleep(300)
        val allowed=test.uiAutomation.takeScreenshot() ?: error("Allowed system screenshot unavailable")
        val x=secure.width/2;val y=secure.height/2
        checkThat(secure.getPixel(x,y) and 0x00ffffff==0 && allowed.getPixel(x,y) and 0x00ffffff!=0,
            "Actual OS captures hide the app surface when screenshots are blocked")
        File(directory,"os-screenshots-blocked.png").outputStream().use { secure.compress(Bitmap.CompressFormat.PNG,100,it) }
        File(directory,"os-screenshots-allowed.png").outputStream().use { allowed.compress(Bitmap.CompressFormat.PNG,100,it) }
        secure.recycle();allowed.recycle()
        main { AppSettings(context).allowScreenshots=false;ScreenPrivacy.apply(activity,AppSettings(context)) }
    }
    private fun modalPrivacy() {
        seed()
        main { call("showSettings") }
        val screenshotControl=views(activity.window.decorView).filterIsInstance<Switch>().single { it.text.toString()=="Allow screenshots" }
        for((name,open) in listOf<Pair<String,()->Unit>>(
            "sheet" to { call("showWelcome") },
            "confirmation" to { call("message",R.string.not_connected,R.string.connect_mutation) }
        )) {
            main { AppSettings(context).allowScreenshots=false;open() }
            test.waitForIdleSync()
            val dialog=(field("dialogs") as Set<*>).filterIsInstance<Dialog>().single()
            val bounds=android.graphics.Rect()
            main {
                val decor=dialog.window!!.decorView;val location=IntArray(2);decor.getLocationOnScreen(location)
                bounds.set(location[0],location[1],location[0]+decor.width,location[1]+decor.height)
            }
            check(!bounds.isEmpty)
            val medians=ArrayList<Int>()
            for(allow in listOf(false,true,false)) {
                // Exercise the Settings callback that updates every tracked window,
                // rather than bypassing that controller with a raw preference write.
                main { if(screenshotControl.isChecked!=allow)screenshotControl.performClick() }
                SystemClock.sleep(300)
                val image=test.uiAutomation.takeScreenshot() ?: error("OS modal capture unavailable")
                val samples=(1..3).flatMap { x -> (1..3).map { y -> android.graphics.Color.red(image.getPixel(bounds.left+bounds.width()*x/4,bounds.top+bounds.height()*y/4)) } }
                medians.add(samples.sorted()[4]);image.recycle()
                checkThat((dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE==0)==allow,"$name window follows live screenshot control")
            }
            checkThat(medians[0]<=16 && medians[1]>=100 && medians[2]<=16,"Actual OS capture protects, exposes and protects $name surface")
            main { dialog.dismiss() }
        }
        main { call("showSettings") }
        val accessible=views(activity.window.decorView).filterIsInstance<Switch>()
        checkThat(accessible.all { it.text.isNotBlank() && it.minimumHeight>=activity.resources.displayMetrics.density*48 } &&
            accessible.filter { it.isEnabled }.all { it.isFocusable },"Settings expose labelled switches with 48dp targets and enabled controls are focusable")
    }
    private fun shell(command:String):String=test.uiAutomation.executeShellCommand(command).use { fd ->
        ParcelFileDescriptor.AutoCloseInputStream(fd).bufferedReader().use { it.readText() }
    }
    private fun node(text:String):AccessibilityNodeInfo? {
        // Some Compose SystemUI providers do not implement find-by-text for descriptions.
        fun find(root:AccessibilityNodeInfo):AccessibilityNodeInfo? {
            if(root.isVisibleToUser && (root.text?.toString()==text || root.contentDescription?.toString()?.contains(text)==true))return root
            for(index in 0 until root.childCount)root.getChild(index)?.let { find(it)?.let { found -> return found } }
            return null
        }
        for(root in test.uiAutomation.windows.mapNotNull { it.root }+listOfNotNull(test.uiAutomation.rootInActiveWindow))find(root)?.let { return it }
        return null
    }
    private fun tile(performance:Boolean=false) {
        seed()
        test.uiAutomation.serviceInfo=test.uiAutomation.serviceInfo.apply {
            flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        // This part runs through SystemUI's actual QS token, rather than a plain Dialog fixture.
        val component="${context.packageName}/app.cliphistory.ui.ClipboardTileService"
        val tileTitle=context.packageManager.getServiceInfo(ComponentName(context,ClipboardTileService::class.java),0).loadLabel(context.packageManager).toString()
        fun shadeCollapsed():Boolean {
            val dump=shell("dumpsys activity service com.android.systemui/.SystemUIService")
            return if(dump.contains("isShadeOrQsExpanded="))dump.contains("isShadeOrQsExpanded=false")
            else dump.contains("mPanelExpanded=false") && dump.contains("mQsExpanded=false")
        }
        fun tap(label:String) {
            val bounds=android.graphics.Rect()
            node(label)?.getBoundsInScreen(bounds) ?: error("Missing native control: $label")
            check(!bounds.isEmpty){"Native control bounds unavailable"}
            val time=SystemClock.uptimeMillis()
            android.util.Log.d("ClipHistoryTileTrace","$time test tap $label")
            for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                val event=MotionEvent.obtain(time,SystemClock.uptimeMillis(),action,bounds.centerX().toFloat(),bounds.centerY().toFloat(),0)
                event.source=android.view.InputDevice.SOURCE_TOUCHSCREEN
                try { check(test.uiAutomation.injectInputEvent(event,true)){"Native touch rejected"} } finally { event.recycle() }
                if(action==MotionEvent.ACTION_DOWN)SystemClock.sleep(50)
            }
        }
        fun expand() {
            shell("cmd statusbar collapse")
            await("Previous SystemUI shade collapse complete") {
                shadeCollapsed()
            }
            if(Build.VERSION.SDK_INT>=37) {
                val bounds=activity.windowManager.maximumWindowMetrics.bounds
                // Exercise the actual shade gesture, including its touch lifecycle.
                repeat(2) { shell("input touchscreen swipe ${bounds.centerX()} 1 ${bounds.centerX()} ${(bounds.height()*.72).toInt()} 350") }
            } else shell("cmd statusbar expand-settings")
            await("System tile state ready for interaction") {
                val dump=shell("dumpsys activity service com.android.systemui/.SystemUIService")
                dump.contains("mExpansionFraction=1.0") && dump.lineSequence()
                    .any { it.contains("spec=custom($component)") && it.contains("state=1,") }
            }
            test.uiAutomation.waitForIdle(200,5000)
            if(arguments.getString("tileCommand")=="true")return
            repeat((arguments.getString("tilePage")?.toIntOrNull()?:1)-1) {
                if(node(tileTitle)==null) {
                    fun pager(root:AccessibilityNodeInfo):AccessibilityNodeInfo? {
                        if(root.viewIdResourceName=="com.android.systemui:id/qs_pager")return root
                        for(index in 0 until root.childCount)root.getChild(index)?.let { pager(it)?.let { found -> return found } }
                        return null
                    }
                    val target=test.uiAutomation.windows.mapNotNull { it.root }.firstNotNullOfOrNull { pager(it) }
                    val bounds=android.graphics.Rect()
                    if(target!=null)target.getBoundsInScreen(bounds)
                    else {
                        val screen=activity.windowManager.maximumWindowMetrics.bounds
                        bounds.set((screen.width()*.04).toInt(),(screen.height()*.23).toInt(),(screen.width()*.96).toInt(),(screen.height()*.55).toInt())
                    }
                    // Swipe through the pager centre, not the first row's tile
                    // controls, which consume horizontal gestures on Pixel API 37.
                    shell("input touchscreen swipe ${bounds.right-80} ${bounds.centerY()} ${bounds.left+80} ${bounds.centerY()} 500")
                }
            }
            await("Correct quick-copy tile visible") { node(tileTitle)?.isVisibleToUser==true }
            // Visible nodes can arrive before the QS pager finishes moving them.
            var previous=android.graphics.Rect();var stable=0
            await("Quick-copy tile bounds settled") {
                val current=android.graphics.Rect()
                node(tileTitle)?.getBoundsInScreen(current)
                stable=if(!current.isEmpty && current==previous)stable+1 else 0
                previous=current;stable>=4
            }
        }
        fun clickTile() {
            android.util.Log.d("ClipHistoryTileTrace","${SystemClock.uptimeMillis()} test invoke tile")
            if(arguments.getString("tileCommand")=="true")shell("cmd statusbar click-tile $component")
            else tap(tileTitle)
        }
        fun closePanel() {
            // Presence of a node does not mean the collapsing shade has released
            // touch input. Verify the actual system transition before tapping Close.
            await("Shade collapsed before closing panel") {
                shadeCollapsed()
            }
            var previous=android.graphics.Rect();var stable=0
            await("Panel Close bounds settled") {
                val current=android.graphics.Rect()
                node(context.getString(R.string.quick_close))?.getBoundsInScreen(current)
                stable=if(!current.isEmpty && current==previous)stable+1 else 0
                previous=current;stable>=4
            }
            tap(context.getString(R.string.quick_close))
            await("Close removes actual dialog window") {
                node(context.getString(R.string.quick_close))==null && shell("dumpsys activity activities").lineSequence()
                    .none { it.contains("ResumedActivity") && it.contains("QuickCopyActivity") }
            }
        }
        val existing=arguments.getString("tilePresent")=="true"
        try {
            shell("cmd statusbar collapse")
            // Native addition is exercised manually on the Pixel. Test setup on the
            // disposable emulator uses SystemUI's command, not an app debug hook.
            if(!existing)shell("cmd statusbar add-tile $component")
            test.uiAutomation.waitForIdle(200,5000)
            // Activity launch has no transient QS dialog token to wait out.
            main { activity.startActivity(Intent().setClassName(test.context.packageName,SyntheticClipboardActivity::class.java.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            test.uiAutomation.waitForIdle(200,5000)
            expand()
            clickTile()
            await("System tile presents newest synthetic entry") { node("Synthetic newest text")!=null }
            checkThat(node("Synthetic third text")!=null,"Floating panel shows three entries")
            checkThat(node("Quick copy")!=null,"System dialog heading present")
            if(performance) {
                // No clipboard write on a personal phone: its production recorder may be active.
                val folder=File(context.filesDir,"ui-evidence").apply { mkdirs() }
                val bounds=android.graphics.Rect()
                test.uiAutomation.windows.single { it.root?.packageName?.toString()==context.packageName }.getBoundsInScreen(bounds)
                shell("cmd statusbar collapse");test.uiAutomation.waitForIdle(300,5000)
                val blocked=test.uiAutomation.takeScreenshot() ?: error("Quick-copy OS screenshot unavailable")
                main { AppSettings(context).allowScreenshots=true };SystemClock.sleep(200)
                val allowed=test.uiAutomation.takeScreenshot() ?: error("Quick-copy allowed screenshot unavailable")
                // Store only the owned synthetic panel, never the underlying notification shade.
                for((name,bitmap) in listOf("quick-blocked.png" to blocked,"quick-allowed.png" to allowed)) {
                    val panel=Bitmap.createBitmap(bitmap,bounds.left,bounds.top,bounds.width(),bounds.height())
                    File(folder,name).outputStream().use { panel.compress(Bitmap.CompressFormat.PNG,100,it) };panel.recycle()
                }
                val x=bounds.centerX();val y=bounds.centerY()
                // SystemUI's dim layer can make a protected surface dark grey rather than RGB zero.
                checkThat(android.graphics.Color.red(blocked.getPixel(x,y))<=16 &&
                    android.graphics.Color.red(allowed.getPixel(x,y))>=100,"Actual OS capture follows quick-panel privacy")
                blocked.recycle();allowed.recycle();main { AppSettings(context).allowScreenshots=false }
                // Re-tap while a panel is open: Android must collapse the reopened
                // shade and retain exactly one panel, rather than refreshing behind it.
                expand();clickTile()
                await("Repeated tile click collapses shade") {
                    shadeCollapsed()
                }
                checkThat(test.uiAutomation.windows.count { it.root?.packageName?.toString()==context.packageName }==1,"Re-tap keeps exactly one quick panel")
                closePanel()
                await("Initial performance panel dismissed") { node("Synthetic newest text")==null }
                val timings=ArrayList<Long>()
                val loading=ArrayList<Double>();val results=org.json.JSONArray()
                repeat(30) { index ->
                    expand()
                    val start=SystemClock.elapsedRealtime()
                    FrameEvidence.begin()
                    clickTile()
                    val deadline=start+2000
                    while(node("Synthetic newest text")==null && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(10)
                    check(node("Synthetic newest text")!=null){"Warm quick-copy entry unavailable"}
                    timings.add(SystemClock.elapsedRealtime()-start)
                    val evidence=FrameEvidence.snapshot()
                    val layouts=evidence.getJSONArray("layouts")
                    checkThat(layouts.length()>0 && (0 until layouts.length()).all {
                        layouts.getJSONObject(it).getInt("height")==layouts.getJSONObject(0).getInt("height") &&
                            layouts.getJSONObject(it).getInt("width")==layouts.getJSONObject(0).getInt("width")
                    },"Loading keeps the floating window size stable")
                    check(evidence.getDouble("loading_ms")>=0){"Actual loaded frame was not observed"}
                    loading.add(evidence.getDouble("loading_ms"))
                    evidence.put("trial",index+1);evidence.put("accessibility_available_ms",timings.last())
                    results.put(evidence)
                    File(folder,"tile-warm-frames.json").writeText(results.toString(2))
                    test.sendStatus(0,Bundle().apply { putString("stream","Trial ${index+1}: app loading=${loading.last()} ms; accessibility availability=${timings.last()} ms; ${evidence.getJSONArray("frames").length()} reported app frames\n") })
                    closePanel()
                    await("Performance panel dismissed ${index+1}") { node("Synthetic newest text")==null }
                }
                val sorted=timings.sorted();val p95=sorted[28]
                log.append("Actual SystemUI tile invocation (${if(arguments.getString("tileCommand")=="true")"statusbar command" else "native touch"}) to available entries, 30 warm openings: p50=${sorted[14]} ms, p95=$p95 ms, max=${sorted.last()} ms\n")
                test.sendStatus(0,Bundle().apply { putString("stream",log.lines().last { it.contains("p50=") }+"\n") })
                val loaded95=loading.sorted()[28]
                test.sendStatus(0,Bundle().apply { putString("stream","Actual app latest-three loading p95=$loaded95 ms; SystemUI/accessibility availability p95=$p95 ms\n") })
                checkThat(loaded95<=500,"Thirty warm latest-three loads meet p95 <= 500 ms")
                return
            }
            node("Synthetic newest text")!!.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            await("Single tap dismisses quick copy") { node("Quick copy")==null }
            await("Underlying application remains foreground after copying") {
                shell("dumpsys activity activities").lineSequence().any { it.contains("ResumedActivity") && it.contains("SyntheticClipboardActivity") }
            }
            expand()
            clickTile()
            await("Tile can reopen one panel") { node("Quick copy")!=null }
            closePanel()
            await("Close dismisses floating panel") { node("Quick copy")==null }
            main { AppSettings(context).tileMode="history";call("showSettings") }
            context.startActivity(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            await("Settings remains open before full-history tile") { var ready=false;main { ready=field("pageKind")=="settings" };ready }
            expand()
            await("Published tile reflects the new History mode") { node(context.getString(R.string.tile_subtitle))!=null }
            clickTile()
            await("Full-history tile returns existing Settings to History") {
                var ready=false;main { ready=field("pageKind")=="" && (field("home") as HistoryHome).root.visibility==View.VISIBLE };ready
            }
            checkThat(node("Quick copy")==null,"Full-history mode does not open a floating panel")
            main { AppSettings(context).tileMode="quick" }
            expand()
            await("Published tile reflects the restored Quick copy mode") { node(context.getString(R.string.quick_copy))!=null }
            clickTile()
            await("Changing back to Quick copy opens the floating panel") { node("Synthetic newest text")!=null && node("Quick copy")!=null }
            closePanel()
        } finally { main { AppSettings(context).allowScreenshots=false };node(context.getString(R.string.quick_close))?.performAction(AccessibilityNodeInfo.ACTION_CLICK);shell("cmd statusbar collapse");if(!existing)shell("cmd statusbar remove-tile $component") }
    }
    /** Actual fork/official Shizuku attachment without writing the phone clipboard. */
    private fun pausedConnection() {
        seed(paused=true)
        val prefs=client.preferences
        val configured=CountDownLatch(1)
        main { client.updateRecovery(automatic=false,afterBoot=false) { checkThat(it,"Paused validation recovery disabled");configured.countDown() } }
        check(configured.await(8,TimeUnit.SECONDS))
        check(prefs.activate() && prefs.completeSetup())
        try {
            main { client.connect(true) }
            test.sendStatus(0,Bundle().apply { putString("stream","Waiting for isolated validation Shizuku permission and paused attachment\n") })
            val deadline=SystemClock.elapsedRealtime()+60_000
            while(!client.connected() && SystemClock.elapsedRealtime()<deadline)SystemClock.sleep(80)
            checkThat(client.connected(),"Actual paused Shizuku attachment succeeds")
            val daemon=DaemonClient::class.java.getDeclaredField("remote").apply { isAccessible=true }.get(client) as app.cliphistory.ipc.IClipboardDaemon
            val state=daemon.status()
            checkThat(state.getInt("uid")==2000 && state.getBoolean("listening") && state.getBoolean("leaseAlive"),"Authorized shell listener and Shizuku lease are live")
            checkThat(state.getBoolean("paused") && !state.getBoolean("active") && state.getInt("count")==3,"Paused attachment retains only the synthetic fixture")
            checkThat(state.getBoolean("storageVerified") && state.getBoolean("ownerStopVerified") && state.getString("issue").isNullOrEmpty(),"Real private storage and owner-stop checks succeed")
            checkThat(state.getLong("saved")==0L && state.getLong("queueDrops")==0L,"No captures or queue drops during paused attachment")
        } finally {
            val stopped=CountDownLatch(1)
            main { client.stopRecording { stopped.countDown() } }
            check(stopped.await(10,TimeUnit.SECONDS))
            checkThat(prefs.options().explicitlyStopped,"Paused validation recorder stopped durably")
        }
    }
    private fun recovery() {
        seed()
        val preferences=RecorderPreferences(context)
        checkThat(preferences.options().explicitlyStopped,"Explicit stop persisted before helper shutdown")
        val done=CountDownLatch(1)
        main { client.updateRecovery(automatic=false,afterBoot=false) { checkThat(it,"Recovery controls saved durably");done.countDown() } }
        check(done.await(8,TimeUnit.SECONDS))
        checkThat(!preferences.options().automatic && !preferences.options().afterBoot,"Recovery and reboot controls persist independently")
        main { client.connect(false) }
        SystemClock.sleep(300)
        checkThat(!client.connected(),"Background connect cannot bypass explicit stop or recovery off")
        checkThat(!client.recoveryAllowed(),"No foreground recovery requested after explicit stop")
        checkThat(PrivateHistory.readOffline(context).entries.size==3,"Stop retains history for offline reading")
        val restored=CountDownLatch(1)
        main { client.updateRecovery(automatic=true,afterBoot=true) { restored.countDown() } }
        check(restored.await(8,TimeUnit.SECONDS))
        val directory=File(context.applicationInfo.dataDir,"shared_prefs")
        val file=File(directory,"recorder_recovery.xml")
        val previous=preferences.options()
        check(directory.setWritable(false,true) && file.setWritable(false,true))
        try {
            checkThat(!preferences.setAutomatic(false),"Failed durable preference write reports failure")
            checkThat(preferences.options()==previous,"Failed preference write restores in-memory recording options")
        } finally { directory.setWritable(true,true);file.setWritable(true,true) }
        checkThat(preferences.setAutomatic(previous.automatic),"Recording preferences remain writable after failure recovery")
    }
    private fun onboarding() {
        seed()
        val settings=AppSettings(context)
        fun launcher() {
            main { activity.finish() }
            activity=test.startActivitySync(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setClass(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
        }
        fun shown():List<Dialog> { var value=emptyList<Dialog>();main { value=(field("dialogs") as Set<*>).filterIsInstance<Dialog>() };return value }
        fun press(label:String) { main { views(shown().single().window!!.decorView).filterIsInstance<TextView>().single { it.text.toString()==label }.performClick() } }
        settings.welcome="once";settings.welcomeSeen=false;settings.backgroundSuggestion=true;settings.backgroundSeen=false
        check(client.preferences.completeSetup())
        launcher()
        await("First launcher opening presents welcome") { shown().size==1 }
        test.waitForIdleSync();capture("welcome",shown().single().window!!)
        main {
            val dialog=shown().single()
            checkThat(views(dialog.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString()==context.getString(R.string.welcome_title) },"Welcome appears before background guidance")
            checkThat(dialog.window!!.attributes.flags and WindowManager.LayoutParams.FLAG_SECURE!=0,"Welcome respects screenshot protection")
        }
        press("Continue")
        await("Background guidance follows deliberate welcome acknowledgement") {
            shown().singleOrNull()?.let { d -> var found=false;main { found=views(d.window!!.decorView).filterIsInstance<TextView>().any { it.text.toString()=="Background reliability" } };found }==true
        }
        checkThat(settings.welcomeSeen,"Welcome acknowledgement persists")
        press("Not now")
        await("Background guidance can be dismissed") { shown().isEmpty() }
        checkThat(settings.backgroundSeen,"Optional background suggestion acknowledged")
        launcher();test.uiAutomation.waitForIdle(200,5000)
        checkThat(shown().isEmpty(),"Once mode does not repeat acknowledged welcome")
        settings.welcome="each";launcher()
        await("Each-launch mode presents welcome") { shown().size==1 };press("Continue")
        val monitor=test.addMonitor(MainActivity::class.java.name,null,false)
        main { activity.recreate() }
        activity=test.waitForMonitorWithTimeout(monitor,10_000) as? MainActivity ?: error("Onboarding recreation failed")
        test.removeMonitor(monitor);test.uiAutomation.waitForIdle(200,5000)
        checkThat(shown().isEmpty(),"Rotation/recreation does not count as another launcher opening")
        settings.welcome="never";settings.welcomeSeen=false;settings.backgroundSuggestion=false;launcher()
        test.uiAutomation.waitForIdle(200,5000)
        checkThat(shown().isEmpty(),"Off mode allows launching without optional prompts")
        main { call("support") }
        main {
            val labels=views(shown().single().window!!.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            listOf("Leave feedback","Star on GitHub","Share app").forEach { checkThat(labels.contains(it),"Real optional support action: $it") }
            shown().single().dismiss();call("help")
            val labelsGuide=views(activity.window.decorView).filterIsInstance<TextView>().map { it.text.toString() }
            listOf(R.string.setup_install,R.string.setup_debugging,R.string.setup_pair,R.string.setup_start,R.string.setup_authorise,R.string.setup_background,R.string.setup_reboot)
                .forEach { checkThat(labelsGuide.contains(context.getString(it)),"Guide includes step ${labelsGuide.count { text -> text==context.getString(it) }}: ${context.getString(it).first()}") }
            checkThat(labelsGuide.contains("Open Shizuku") && labelsGuide.contains("Connection test"),"Guide exposes Shizuku and connection-test actions")
        }
    }
    private fun customization() {
        seed();main { call("showSettings") }
        main {
            val controls=views(activity.window.decorView).filterIsInstance<Switch>()
            listOf(R.string.tile_enabled,R.string.return_after_copy,R.string.motion_system).forEach { id ->
                controls.single { it.text.toString()==context.getString(id) }.apply { if(isChecked)performClick() }
            }
        }
        val completed=CountDownLatch(1)
        main { client.updateRecovery(automatic=false,afterBoot=false) { checkThat(it,"All-off recovery choices saved");completed.countDown() } }
        check(completed.await(8,TimeUnit.SECONDS))
        val settings=AppSettings(context)
        settings.welcome="never";settings.backgroundSuggestion=false;settings.appearance="system"
        checkThat(!settings.tileEnabled && !settings.motion && !settings.returnAfterCopy,"Optional quick access and motion controls persist Off")
        checkThat(context.packageManager.getComponentEnabledSetting(ComponentName(context,ClipboardTileService::class.java))==android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,"Disabled tile is unavailable to SystemUI")
        checkThat(!client.recoveryAllowed(),"All-off preferences do not start recovery")
        val raw=context.getSharedPreferences("appearance",Context.MODE_PRIVATE)
        check(raw.edit().putInt("mode",7).putString("return_after_copy","invalid").commit())
        checkThat(settings.appearance=="system" && settings.returnAfterCopy,"Malformed legacy presentation values use compatible defaults")
        settings.returnAfterCopy=false;settings.appearance="system"
        val monitor=test.addMonitor(MainActivity::class.java.name,null,false)
        main { activity.recreate() }
        activity=test.waitForMonitorWithTimeout(monitor,10_000) as? MainActivity ?: error("All-off recreation failed")
        test.removeMonitor(monitor);test.waitForIdleSync()
        main { checkThat((field("dialogs") as Set<*>).isEmpty(),"All-off recreation shows no optional popup") }
        checkThat(!AppSettings(context).tileEnabled && !AppSettings(context).motion && AppSettings(context).welcome=="never","All-off settings survive recreation")
        // Restore the test component for subsequent suites, not the owner's phone tile.
        main { context.packageManager.setComponentEnabledSetting(ComponentName(context,ClipboardTileService::class.java),android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED,android.content.pm.PackageManager.DONT_KILL_APP);settings.tileEnabled=true }
    }
    private fun quickErrors() {
        check(Build.FINGERPRINT.contains("generic") || Build.MODEL.contains("sdk")) { "Storage error fixtures require a disposable emulator" }
        val theme=arguments.getString("theme","light")!!
        check(theme in listOf("light","dark")) { "Error capture theme must be light or dark" }
        seed(theme=theme)
        val valid=SnapshotCodec.encode(PrivateHistory.readOffline(context))
        fun replace(payload:ByteArray) {
            val (a,b)=PrivateHistory.openForDaemon(context)
            listOf(a,b).forEach { FramedSlot(FdAccess(it,context.applicationInfo.uid)).use { slot -> slot.writeAndSync(payload) } }
            PrivateHistory.invalidate()
        }
        fun texts(quick:QuickCopyActivity):List<String> {
            var result=emptyList<String>()
            main { result=views(quick.window.decorView).filterIsInstance<TextView>().map { it.text.toString() } }
            return result
        }
        fun openError():QuickCopyActivity {
            replace(byteArrayOf(1,2,3))
            val quick=test.startActivitySync(Intent(context,QuickCopyActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as QuickCopyActivity
            await("Real quick panel shows synthetic storage-read error") { texts(quick).contains(context.getString(R.string.quick_failed)) }
            main {
                val rows=QuickCopyActivity::class.java.getDeclaredField("rows").apply { isAccessible=true }.get(quick) as LinearLayout
                checkThat(rows.minimumHeight==0 && rows.childCount==2,"Error panel releases absent entries and exposes two recovery actions")
            }
            return quick
        }
        var quick:QuickCopyActivity?=null
        try {
            main { AppSettings(context).allowScreenshots=true }
            quick=openError();capture("quick-error-recovery",quick.window)
            replace(valid)
            checkThat(node(context.getString(R.string.quick_retry))?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true,"Actual Try again accessibility control accepts activation")
            val recovered=quick
            await("Try again reloads all three exact valid entries") {
                val values=texts(recovered)
                listOf("Synthetic newest text","Synthetic second\nwith exact whitespace  ","Synthetic third text").all { values.contains(it) }
            }
            capture("quick-error-recovered",quick.window)
            main { recovered.finish() };test.waitForIdleSync()
            quick=openError()
            checkThat(node(context.getString(R.string.open_app))?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true,"Actual error Open app accessibility control accepts activation")
            val opened=quick
            await("Error Open app closes floating panel and restores Main focus") {
                var ready=false;main { ready=opened.isFinishing && activity.hasWindowFocus() && !activity.isFinishing };ready
            }
            // Direct file injection has no daemon event. A fresh launch reads this
            // separate Home error fixture through the normal startup path.
            activity=test.startActivitySync(Intent(context,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)) as MainActivity
            await("Main exposes failed-history Try again and Diagnostics controls") {
                node(context.getString(R.string.try_again))!=null && node(context.getString(R.string.diagnostics))!=null
            }
            capture("home-history-error")
            checkThat(node(context.getString(R.string.diagnostics))?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true,"Actual failed-history Diagnostics control accepts activation")
            await("Failed-history Diagnostics opens its real page") {
                var ready=false;main { ready=field("pageKind")=="diagnostics" };ready
            }
            test.sendKeyDownUpSync(KeyEvent.KEYCODE_BACK)
            await("Actual Back restores failed-history recovery controls") { node(context.getString(R.string.try_again))!=null }
            replace(valid)
            checkThat(node(context.getString(R.string.try_again))?.performAction(AccessibilityNodeInfo.ACTION_CLICK)==true,"Actual failed-history Try again control accepts activation")
            await("Failed-history Try again reloads the three saved entries") {
                var ready=false;main { ready=(field("home") as HistoryHome).adapter.count==3 };ready
            }
        } finally {
            replace(valid)
            main { quick?.finish();AppSettings(context).apply { allowScreenshots=false;appearance="light" };ScreenPrivacy.apply(activity,AppSettings(context)) }
        }
        checkThat(SnapshotCodec.encode(PrivateHistory.readOffline(context)).contentEquals(valid),"Valid synthetic history is restored after error controls")
    }
    private fun accessibility() {
        seed()
        val clipboard=context.getSystemService(ClipboardManager::class.java)
        val marker="Synthetic accessibility action clipboard sentinel"
        main { clipboard.setPrimaryClip(ClipData.newPlainText("Validation",marker)) }
        fun descendants(node:AccessibilityNodeInfo):List<AccessibilityNodeInfo> =
            listOf(node)+(0 until node.childCount).flatMap { index -> node.getChild(index)?.let { descendants(it) }?:emptyList() }
        var actionNode:AccessibilityNodeInfo?=null
        await("Actual history accessibility node exposes the full-text action") {
            actionNode=test.uiAutomation.rootInActiveWindow?.let { root -> descendants(root).firstOrNull { node ->
                node.actionList.any { it.id==R.id.action_view_text } &&
                    descendants(node).any { it.text?.toString()=="Synthetic newest text" }
            } }
            actionNode!=null
        }
        val node=checkNotNull(actionNode)
        checkThat(node.actionList.single { it.id==R.id.action_view_text }.label.toString()==context.getString(R.string.view_full),"History custom action has the exact visible full-text label")
        checkThat(node.performAction(R.id.action_view_text),"Actual accessibility node accepts the full-text action")
        await("History custom accessibility action opens the exact saved text") {
            var ready=false
            main { ready=field("pageKind")=="preview" && views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString()=="Synthetic newest text" } }
            ready
        }
        main {
            checkThat(clipboard.primaryClip?.getItemAt(0)?.text?.toString()==marker,"Viewing through the accessibility action leaves clipboard unchanged")
            call("closePage")
        }
        main { call("appearance") }
        main {
            val dialog=(field("dialogs") as Set<*>).filterIsInstance<Dialog>().single()
            val nodes=views(dialog.window!!.decorView)
            checkThat(nodes.any { it.accessibilityPaneTitle==context.getString(R.string.appearance) },"Appearance sheet exposes its pane title")
            checkThat(nodes.filterIsInstance<TextView>().any { it.text.toString()==context.getString(R.string.appearance) && it.isAccessibilityHeading },"Appearance title is an accessibility heading")
            dialog.dismiss();call("help")
            val headings=views(activity.window.decorView).filterIsInstance<TextView>().filter { it.isAccessibilityHeading }.map { it.text.toString() }
            listOf(R.string.help_setup,R.string.help_gboard,R.string.help_restart,R.string.help_local,R.string.help_sensitive,R.string.help_limits,R.string.help_screen)
                .forEach { checkThat(headings.contains(context.getString(it)),"Help heading: ${context.getString(it)}") }
            call("diagnostics")
            var control=views(activity.window.decorView).first { it.contentDescription==context.getString(R.string.advanced_details) }
            checkThat(control.stateDescription==context.getString(R.string.details_collapsed),"Advanced details exposes collapsed state")
            control.performClick()
            checkThat(control.stateDescription==context.getString(R.string.details_expanded),"Advanced details exposes expanded state after activation")
            control.performClick()
            checkThat(control.stateDescription==context.getString(R.string.details_collapsed),"Advanced details can collapse again")
            call("closePage")
            AppSettings(context).returnAfterCopy=false
            call("copyEntry",3L)
        }
        await("Copy feedback appears") { var found=false;main { found=views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString()==context.getString(R.string.copied) } };found }
        val timeout=context.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            .getRecommendedTimeoutMillis(2200,android.view.accessibility.AccessibilityManager.FLAG_CONTENT_TEXT)
        if(timeout>=5000) {
            SystemClock.sleep(2700)
            main { checkThat(views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString()==context.getString(R.string.copied) },"Feedback respects extended accessibility timeout") }
        }
        main { call("notifyUser",R.string.history_read_failed) }
        SystemClock.sleep(2700)
        main {
            val present=views(activity.window.decorView).filterIsInstance<TextView>().any { it.text.toString()==context.getString(R.string.history_read_failed) }
            if(timeout>=3500)checkThat(present,"Error feedback respects extended accessibility timeout")
            else if(timeout<=2200)checkThat(!present,"Default error feedback expires after its timeout")
            activity.finish()
        }
        await("Leaving the Activity removes transient feedback") { var removed=false;main { removed=field("snackbar")==null };removed }
    }
    fun run(suite:String):String {
        try { when(suite){"quick-errors"->quickErrors();"accessibility"->accessibility();"settings"->settings();"privacy"->modalPrivacy();"tile"->tile();"tile-performance"->tile(true);"paused-connection"->pausedConnection();"recovery"->recovery();"onboarding"->onboarding();"customization"->customization();else->error("Unknown feature suite") } }
        finally { if(::activity.isInitialized)main { activity.finish() } }
        return log.append("PASS $count feature assertions ($suite), API ${Build.VERSION.SDK_INT}. Synthetic validation only.\n").toString()
    }
}
