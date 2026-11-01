package app.cliphistory;

import android.app.AppComponentFactory;
import android.app.Application;
import android.content.pm.ApplicationInfo;
import android.os.Process;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

/** Supplies the process name missing from Shizuku's shell-only application bootstrap. */
public final class ClipAppComponentFactory extends AppComponentFactory {
    public ClipAppComponentFactory() {
        if (Process.myUid() != Process.SHELL_UID || Application.getProcessName() != null) return;
        try {
            Class<?> threadClass = Class.forName("android.app.ActivityThread");
            Object thread = threadClass.getDeclaredMethod("currentActivityThread").invoke(null);
            if (thread == null) return;
            Field bound = threadClass.getDeclaredField("mBoundApplication");
            bound.setAccessible(true);
            if (bound.get(thread) != null) return;

            Class<?> dataClass = Class.forName("android.app.ActivityThread$AppBindData");
            Constructor<?> constructor = dataClass.getDeclaredConstructor();
            constructor.setAccessible(true);
            Object data = constructor.newInstance();
            Field processName = dataClass.getDeclaredField("processName");
            processName.setAccessible(true);
            processName.set(data, BuildConfig.APPLICATION_ID + ":clipboard");
            // currentPackageName() also reads appInfo; an empty value preserves its prior null result.
            Field appInfo = dataClass.getDeclaredField("appInfo");
            appInfo.setAccessible(true);
            appInfo.set(data, new ApplicationInfo());
            bound.set(thread, data);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Leave Shizuku's existing startup and error handling unchanged if Android differs.
        }
    }
}
