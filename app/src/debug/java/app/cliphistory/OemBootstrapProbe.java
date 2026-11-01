package app.cliphistory;

import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.os.IBinder;
import android.os.Looper;
import android.os.Process;
import android.os.UserHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Validation-only reproduction of Shizuku 13.6's package-loading sequence. */
public final class OemBootstrapProbe {
    public static void main(String[] args) throws Exception {
        if (args.length != 1 || !args[0].equals("app.cliphistory.validation") || Process.myUid() != 2000) {
            throw new SecurityException("This probe requires the isolated validation package and shell UID");
        }
        Looper.prepareMainLooper();
        Class<?> threadClass = Class.forName("android.app.ActivityThread");
        Object thread = threadClass.getDeclaredMethod("systemMain").invoke(null);
        Context system = (Context) threadClass.getDeclaredMethod("getSystemContext").invoke(thread);
        Method createContext = Context.class.getMethod("createPackageContextAsUser",
                String.class, int.class, UserHandle.class);
        Context context = (Context) createContext.invoke(system, args[0],
                Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY, Process.myUserHandle());
        Field packageInfo = context.getClass().getDeclaredField("mPackageInfo");
        packageInfo.setAccessible(true);
        Object loadedApk = packageInfo.get(context);
        Method makeApplication = loadedApk.getClass().getDeclaredMethod("makeApplication", boolean.class, Instrumentation.class);
        makeApplication.setAccessible(true);
        Application application = (Application) makeApplication.invoke(loadedApk, true, null);
        String processName = Application.getProcessName();
        if (processName == null || processName.equals(args[0])) {
            throw new AssertionError("OEM process-name precondition is missing or names the app process");
        }
        Class<?> recorder = application.getClassLoader().loadClass("app.cliphistory.daemon.ClipboardUserService");
        if (!(recorder.getConstructor(Context.class).newInstance(application) instanceof IBinder)) {
            throw new AssertionError("Recorder was not constructed as a Binder");
        }
        System.out.println("PASS OEM startup precondition and recorder construction");
        System.exit(0);
    }
}
