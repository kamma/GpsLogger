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
            MainActivity.buttonReplayPause.callOnClick();
            MainActivity.refreshNotification();
        } else if (intent.getAction().equals(MainActivity.ACTION_REVERSE)) {
            MainActivity.buttonReverse.callOnClick();
            MainActivity.refreshNotification();
        } else if (intent.getAction().equals(MainActivity.ACTION_SPEED_PLUS)) {
            MainActivity.buttonSpeedPlus.callOnClick();
            MainActivity.refreshNotification();
        } else if (intent.getAction().equals(MainActivity.ACTION_SPEED_MINUS)) {
            MainActivity.buttonSpeedMinus.callOnClick();
            MainActivity.refreshNotification();
        }
    }
}
