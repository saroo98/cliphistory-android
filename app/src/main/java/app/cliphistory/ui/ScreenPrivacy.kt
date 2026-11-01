package app.cliphistory.ui

import android.app.Activity
import android.os.Build
import android.view.Window
import android.view.WindowManager

object ScreenPrivacy {
    fun apply(window:Window?,settings:AppSettings) {
        if(settings.allowScreenshots)window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
    fun apply(activity:Activity,settings:AppSettings) {
        apply(activity.window,settings)
        if(Build.VERSION.SDK_INT>=33)activity.setRecentsScreenshotEnabled(!settings.hideRecents)
    }
    fun motionEnabled(settings:AppSettings)=settings.motion && android.animation.ValueAnimator.areAnimatorsEnabled()
}
