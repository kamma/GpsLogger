package cz.kamma.gpslogger;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class MyBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = "MyBroadcastReceiver";
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(MainActivity.ACTION_PAUSE)) {
            MainActivity.paused = !MainActivity.paused;
            MainActivity.refreshNotification();
        }
    }
}
