package app.cliphistory;

import android.app.Activity;
import android.content.*;
import android.os.*;
import android.widget.TextView;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Standalone test-APK Activity: Android classes only, independent of the target's Kotlin runtime. */
public class SyntheticClipboardActivity extends Activity {
    public static final String ACTION="app.cliphistory.test.WRITE_SYNTHETIC";
    public static final String READ_HASH="app.cliphistory.test.READ_HASH";
    private boolean copied;
    private TextView message;
    private final BroadcastReceiver writer=new BroadcastReceiver() {
        public void onReceive(Context context,Intent event) { setIntent(event);copied=false;write(); }
    };
    public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        message=new TextView(this);message.setText("ClipHistory synthetic clipboard test");message.setTextSize(22f);setContentView(message);
        registerReceiver(writer,new IntentFilter(ACTION),Context.RECEIVER_EXPORTED);
    }
    public void onDestroy() { unregisterReceiver(writer);super.onDestroy(); }
    public void onNewIntent(Intent event) { super.onNewIntent(event);setIntent(event);copied=false;write(); }
    public void onWindowFocusChanged(boolean focused) { super.onWindowFocusChanged(focused);if(focused)write(); }
    private void write() {
        String action=getIntent().getAction();
        if(copied || !hasWindowFocus() || (!ACTION.equals(action) && !READ_HASH.equals(action)))return;
        copied=true;
        if(READ_HASH.equals(action)) {
            // Foreground test-only verification: do not alter or display clipboard text.
            ClipData current=getSystemService(ClipboardManager.class).getPrimaryClip();
            CharSequence value=current!=null && current.getItemCount()>0 ? current.getItemAt(0).getText() : null;
            if(value==null) { message.setText("Clipboard has no plain text");return; }
            try {
                byte[] digest=MessageDigest.getInstance("SHA-256").digest(value.toString().getBytes(StandardCharsets.UTF_8));
                StringBuilder hash=new StringBuilder();
                for(byte part:digest)hash.append(String.format(java.util.Locale.ROOT,"%02x",part & 255));
                message.setText("Clipboard SHA-256: "+hash);
            } catch(java.security.NoSuchAlgorithmException error) { throw new AssertionError(error); }
            return;
        }
        String value=getIntent().getStringExtra("text");if(value==null)value="";
        if(getIntent().getBooleanExtra("oversized",false)) { char[] chars=new char[65537];Arrays.fill(chars,'x');value=new String(chars); }
        ClipData clip=ClipData.newPlainText("Synthetic validation",value);
        if(getIntent().getBooleanExtra("sensitive",false)) {
            PersistableBundle extras=new PersistableBundle();extras.putBoolean("android.content.extra.IS_SENSITIVE",true);clip.getDescription().setExtras(extras);
        }
        getSystemService(ClipboardManager.class).setPrimaryClip(clip);
    }
}
