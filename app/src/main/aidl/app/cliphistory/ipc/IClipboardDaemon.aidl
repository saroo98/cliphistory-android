package app.cliphistory.ipc;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import app.cliphistory.ipc.IHistoryObserver;
interface IClipboardDaemon {
    Bundle attach(in ParcelFileDescriptor first, in ParcelFileDescriptor second, IBinder shizukuServer) = 0;
    Bundle status() = 1;
    Bundle page(String query, int offset, int count) = 2;
    @nullable String text(long id) = 3;
    Bundle deleteEntry(long id) = 4;
    Bundle clearHistory() = 5;
    Bundle setLimit(int limit) = 6;
    Bundle setPaused(boolean paused) = 7;
    void registerObserver(IHistoryObserver observer) = 8;
    void unregisterObserver(IHistoryObserver observer) = 9;
    Bundle armTest(String nonce) = 10;
    Bundle verifyStorage() = 11;
    Bundle entry(long id) = 12;
    void destroy() = 16777114;
}
