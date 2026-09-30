package app.cliphistory;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.widget.TextView;
import java.util.Arrays;

/** Standalone test-APK Activity: Android classes only, independent of the target's Kotlin runtime. */
public class SyntheticClipboardActivity extends Activity {
    public static final String ACTION="app.cliphistory.test.WRITE_SYNTHETIC";
    private boolean copied;
    private final BroadcastReceiver writer=new BroadcastReceiver() {
        public void onReceive(Context context,Intent event) { setIntent(event);copied=false;write(); }
    };
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        TextView text=new TextView(this);text.setText("ClipHistory synthetic clipboard test");text.setTextSize(22f);setContentView(text);
        registerReceiver(writer,new IntentFilter(ACTION),Context.RECEIVER_EXPORTED);
    }
    public void onDestroy() { unregisterReceiver(writer);super.onDestroy(); }
    public void onNewIntent(Intent event) { super.onNewIntent(event);setIntent(event);copied=false;write(); }
    public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused);if(focused)write(); }
    private void write() {
        if(copied || !hasWindowFocus() || !ACTION.equals(getIntent().getAction()))return;
        copied=true;
        String value=getIntent().getStringExtra("text");if(value==null)value="";
        if(getIntent().getBooleanExtra("oversized",false)) { char[] chars=new char[65537];Arrays.fill(chars,'x');value=new String(chars); }
        ClipData clip=ClipData.newPlainText("Synthetic validation",value);
        if(getIntent().getBooleanExtra("sensitive",false)) {
            PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);
        }
        getSystemService(ClipboardManager.class).setPrimaryClip(clip);
    }
}
