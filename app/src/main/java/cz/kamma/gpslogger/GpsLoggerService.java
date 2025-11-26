package cz.kamma.gpslogger;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.PixelFormat;
import android.location.Location;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.Binder;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.support.annotation.Nullable;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class GpsLoggerService extends Service {

    private static final String TAG = GpsLoggerService.class.getCanonicalName();
    private static final String CHANNEL_ID = "GpsLoggerServiceChannel";
    private static final int NOTIFICATION_ID = 10001;
    private static final int GPS_ACCURACY = ProviderProperties.ACCURACY_FINE;
    private static final String GPS_PROVIDER_NAME = LocationManager.GPS_PROVIDER;

    // Intent Actions
    public static final String ACTION_START_REPLAY = "ACTION_START_REPLAY";
    public static final String ACTION_STOP_REPLAY = "ACTION_STOP_REPLAY";
    public static final String ACTION_PAUSE_REPLAY = "ACTION_PAUSE_REPLAY";
    public static final String ACTION_TOGGLE_REVERSE = "ACTION_TOGGLE_REVERSE";
    public static final String ACTION_SPEED_PLUS = "ACTION_SPEED_PLUS";
    public static final String ACTION_SPEED_MINUS = "ACTION_SPEED_MINUS";
    public static final String ACTION_SEEK = "ACTION_SEEK";
    public static final String ACTION_REQUEST_UPDATE = "ACTION_REQUEST_UPDATE";

    // Broadcasts to UI
    public static final String BROADCAST_STATUS_UPDATE = "BROADCAST_STATUS_UPDATE";
    public static final String EXTRA_STATUS = "EXTRA_STATUS";
    public static final String EXTRA_REPLAY_STATE = "EXTRA_REPLAY_STATE";
    public static final String EXTRA_TIME = "EXTRA_TIME";
    public static final String EXTRA_LATITUDE = "EXTRA_LATITUDE";
    public static final String EXTRA_LONGITUDE = "EXTRA_LONGITUDE";

    private final IBinder binder = new LocalBinder();
    private LocationManager locationManager;
    private PowerManager.WakeLock wakeLock;

    // Replay state
    private ExecutorService executorService;
    private volatile boolean isReplaying = false;
    private volatile boolean isPaused = false;
    private volatile boolean isReverse = false;
    private int replaySpeed = 1;
    private volatile int replayPosition = 0;
    private volatile boolean seeked = false;
    private String replayFileName;
    private ArrayList<String> replayData;
    private final Random random = new Random();
    private char statusChar = '|';

    // Overlay
    private WindowManager windowManager;
    private View overlayView;

    public class LocalBinder extends Binder {
        GpsLoggerService getService() {
            return GpsLoggerService.this;
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "GPSLogger::wakeLock");
        executorService = Executors.newSingleThreadExecutor();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        startForeground(NOTIFICATION_ID, createReplayNotification());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || intent.getAction() == null) {
            return START_NOT_STICKY;
        }

        String action = intent.getAction();
        Log.d(TAG, "Received action: " + action);

        switch (action) {
            case ACTION_START_REPLAY:
                replayFileName = intent.getStringExtra("fileName");
                startReplay();
                break;
            case ACTION_STOP_REPLAY:
                stopReplay();
                break;
            case ACTION_SEEK:
                seekTo(intent.getIntExtra("position", 0));
                break;
            case ACTION_PAUSE_REPLAY:
                togglePause();
                break;
            case ACTION_TOGGLE_REVERSE:
                toggleReverse();
                break;
            case ACTION_SPEED_PLUS:
                changeSpeed(1);
                break;
            case ACTION_SPEED_MINUS:
                changeSpeed(-1);
                break;
            case ACTION_REQUEST_UPDATE:
                if (!isReplaying) {
                    stopSelf();
                } else {
                    broadcastReplayProgress(null);
                }
                break;
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        stopReplay();
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        executorService.shutdownNow();
        clearGpsProvider();
        removeOverlay();
        super.onDestroy();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public ReplayState getReplayState() {
        return new ReplayState(isReplaying, isPaused, isReverse, replaySpeed, replayPosition, replayData != null ? replayData.size() : 0, replayFileName);
    }

    private void startReplay() {
        if (isReplaying) {
            Log.w(TAG, "Already replaying.");
            return;
        }
        if (replayFileName == null) {
            Log.e(TAG, "Replay file name is null.");
            stopSelf();
            return;
        }

        try {
            loadReplayFile();
            setupGpsProvider();
            isReplaying = true;
            isPaused = false;
            isReverse = false;
            replayPosition = 0;
            wakeLock.acquire();
            updateNotification();
            executorService.submit(new GpsReplayTask());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                showOverlay();
            }
        } catch (IOException e) {
            Log.e(TAG, "Failed to start replay", e);
            broadcastStatus("Error: " + e.getMessage());
            stopSelf();
        }
    }

    private void stopReplay() {
        if (!isReplaying) return;
        isReplaying = false;
        if (wakeLock.isHeld()) {
            wakeLock.release();
        }
        clearGpsProvider();
        broadcastStatus("STOPPED");
        stopForeground(true);
        removeOverlay();
        stopSelf();
    }

    private void togglePause() {
        if (isReplaying) {
            isPaused = !isPaused;
            if (!isPaused && replaySpeed==0) {
                replaySpeed = 1;
            }
            updateNotification();
            updateOverlay();
            broadcastReplayProgress(null);
        }
    }

    private void toggleReverse() {
        if (isReplaying) {
            isReverse = !isReverse;
            updateNotification();
            updateOverlay();
            broadcastReplayProgress(null);
        }
    }

    private void changeSpeed(int delta) {
        if (isReplaying) {
            if (isPaused && delta > 0) {
                isPaused = false; // Unpause when increasing speed
            } else if (isPaused && delta < 0 && replaySpeed == 1) {
                 // do nothing, speed is already at minimum
            } else {
                replaySpeed += delta;
                if (replaySpeed < 1) {
                    replaySpeed = 1;
                }
            }
            updateNotification();
            updateOverlay();
            broadcastReplayProgress(null);
        }
    }

    private void seekTo(int position) {
        if (isReplaying && replayData != null && position >= 0 && position < replayData.size()) {
            this.replayPosition = position;
            this.seeked = true;
        }
    }

    private void rotateChar() {
        if (statusChar == '|') statusChar = '/';
        else if (statusChar == '/') statusChar = '-';
        else if (statusChar == '-') statusChar = '\\';
        else if (statusChar == '\\') statusChar = '|';
    }

    private void loadReplayFile() throws IOException {
        File extStorage = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(extStorage, replayFileName);

        replayData = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                replayData.add(line);
            }
        }
        if (replayData.isEmpty()) {
            throw new IOException("File is empty or could not be read.");
        }
    }

    private void setupGpsProvider() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            locationManager.addTestProvider(GPS_PROVIDER_NAME, false, true, false, false, true, true, true,
                    ProviderProperties.POWER_USAGE_LOW, GPS_ACCURACY);
            if (!locationManager.isProviderEnabled(GPS_PROVIDER_NAME)) {
                locationManager.setTestProviderEnabled(GPS_PROVIDER_NAME, true);
            }
        } catch (SecurityException e) {
            Log.e(TAG, "Mock location permissions not enabled on device.", e);
        }
    }

    private void clearGpsProvider() {
        try {
            if (locationManager.getProvider(GPS_PROVIDER_NAME) != null) {
                locationManager.removeTestProvider(GPS_PROVIDER_NAME);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to remove test provider", e);
        }
    }

    private void setMockLocation(double latitude, double longitude, double altitude, float acc) {
        Location newLocation = new Location(GPS_PROVIDER_NAME);
        newLocation.setLatitude(latitude);
        newLocation.setLongitude(longitude);
        newLocation.setAltitude(altitude);
        newLocation.setAccuracy(acc);
        newLocation.setTime(System.currentTimeMillis());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            newLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
        }

        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                return;
            }
            locationManager.setTestProviderLocation(GPS_PROVIDER_NAME, newLocation);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Error setting mock location: " + e.getMessage());
        }
    }

    private void updateNotification() {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(NOTIFICATION_ID, createReplayNotification());
    }

    private Notification createReplayNotification() {
        createNotificationChannel();
        String text;
        if (isReplaying) {
            text = isPaused ? "Replay is PAUSED" : "Replaying / " + (isReverse ? "Backward" : "Forward") + " / Speed " + replaySpeed;
        } else {
            text = "GPS Logger is waiting";
        }

        int pauseIcon = isPaused ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play;

        Intent pauseIntent = new Intent(this, GpsLoggerService.class).setAction(ACTION_PAUSE_REPLAY);
        PendingIntent pausePendingIntent = PendingIntent.getService(this, 1, pauseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent reverseIntent = new Intent(this, GpsLoggerService.class).setAction(ACTION_TOGGLE_REVERSE);
        PendingIntent reversePendingIntent = PendingIntent.getService(this, 2, reverseIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent speedPlusIntent = new Intent(this, GpsLoggerService.class).setAction(ACTION_SPEED_PLUS);
        PendingIntent speedPlusPendingIntent = PendingIntent.getService(this, 3, speedPlusIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent speedMinusIntent = new Intent(this, GpsLoggerService.class).setAction(ACTION_SPEED_MINUS);
        PendingIntent speedMinusPendingIntent = PendingIntent.getService(this, 4, speedMinusIntent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("GPS Replay")
                .setContentText(text)
                .setSmallIcon(pauseIcon)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pausePendingIntent);

        if(isReplaying) {
            builder.addAction(android.R.drawable.ic_media_rew, "Reverse", reversePendingIntent)
                    .addAction(android.R.drawable.arrow_up_float, "Speed+", speedPlusPendingIntent)
                    .addAction(android.R.drawable.arrow_down_float, "Speed-", speedMinusPendingIntent);
        }
        return builder.build();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(CHANNEL_ID, "GPS Logger Service Channel", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(serviceChannel);
        }
    }

    private void broadcastStatus(String status) {
        Intent intent = new Intent(BROADCAST_STATUS_UPDATE);
        intent.putExtra(EXTRA_STATUS, status);
        intent.putExtra(EXTRA_REPLAY_STATE, getReplayState());
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    private void broadcastReplayProgress(@Nullable DataLine dl) {
        Intent intent = new Intent(BROADCAST_STATUS_UPDATE);
        rotateChar();
        String status = isPaused ? "PAUSED " : "PLAYING ";
        intent.putExtra(EXTRA_STATUS, status + statusChar);
        intent.putExtra(EXTRA_REPLAY_STATE, getReplayState());

        if (dl != null) {
            intent.putExtra(EXTRA_LATITUDE, dl.getLatitude());
            intent.putExtra(EXTRA_LONGITUDE, dl.getLongitude());
            intent.putExtra(EXTRA_TIME, dl.getTime());
        }
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void showOverlay() {
        if (overlayView != null) return;

        final WindowManager.LayoutParams params;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        } else {
            params = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT);
        }

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 100;

        overlayView = LayoutInflater.from(this).inflate(R.layout.overlay_controls, null);

        overlayView.findViewById(R.id.overlay_button_speed_minus).setOnClickListener(v -> sendCommandToService(ACTION_SPEED_MINUS));
        overlayView.findViewById(R.id.overlay_button_reverse).setOnClickListener(v -> sendCommandToService(ACTION_TOGGLE_REVERSE));
        overlayView.findViewById(R.id.overlay_button_play_pause).setOnClickListener(v -> sendCommandToService(ACTION_PAUSE_REPLAY));
        overlayView.findViewById(R.id.overlay_button_speed_plus).setOnClickListener(v -> sendCommandToService(ACTION_SPEED_PLUS));

        overlayView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_UP:
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(overlayView, params);
                        return true;
                }
                return false;
            }
        });

        windowManager.addView(overlayView, params);
        updateOverlay();
    }

    private void updateOverlay() {
        if (overlayView != null) {
            ImageButton playPauseButton = overlayView.findViewById(R.id.overlay_button_play_pause);
            playPauseButton.setImageResource(isPaused ? android.R.drawable.ic_media_play : android.R.drawable.ic_media_pause);

            ImageButton reverseButton = overlayView.findViewById(R.id.overlay_button_reverse);
            reverseButton.setImageResource(isReverse ? android.R.drawable.ic_media_rew : android.R.drawable.ic_media_ff);

            TextView speedIndicator = overlayView.findViewById(R.id.overlay_speed_indicator);
            speedIndicator.setText(String.format(Locale.getDefault(), "%dx", replaySpeed));
        }
    }

    private void sendCommandToService(String action) {
        Intent intent = new Intent(this, GpsLoggerService.class);
        intent.setAction(action);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void removeOverlay() {
        if (overlayView != null) {
            windowManager.removeView(overlayView);
            overlayView = null;
        }
    }

    private class GpsReplayTask implements Runnable {
        @Override
        public void run() {
            while (isReplaying) {
                try {
                    if (seeked) {
                        seeked = false;
                        Thread.sleep(200); // Give a moment for the system to process
                        continue;
                    }

                    if (replayData == null || replayData.isEmpty() || replayPosition < 0 || replayPosition >= replayData.size()) {
                        new Handler(Looper.getMainLooper()).post(() -> isPaused = true);
                        Thread.sleep(1000);
                        continue;
                    }

                    if (isPaused) {
                        DataLine currentDataLine = new DataLine(replayData.get(replayPosition).split(" "));
                        setMockLocation(randomizeValue(currentDataLine.getLatitude()), randomizeValue(currentDataLine.getLongitude()), randomizeAltitude(currentDataLine.getAltitude(), 0.001), currentDataLine.getAcc());
                        Thread.sleep(1000);
                        continue;
                    }

                    DataLine currentDataLine = new DataLine(replayData.get(replayPosition).split(" "));
                    broadcastReplayProgress(currentDataLine);

                    int nextPos = isReverse ? replayPosition - 1 : replayPosition + 1;
                    if (nextPos < 0 || nextPos >= replayData.size()) {
                        new Handler(Looper.getMainLooper()).post(() -> isPaused = true);
                        continue;
                    }

                    DataLine nextDataLine = new DataLine(replayData.get(nextPos).split(" "));
                    long waitTime = Math.abs(currentDataLine.getTime() - nextDataLine.getTime());

                    waitTime = waitTime / replaySpeed;
                    waitTime += (random.nextInt(100) - 50);

                    if (waitTime > 0) {
                        Thread.sleep(waitTime);
                    }

                    if (seeked) {
                        // Check again, as a seek might have happened during sleep
                        continue;
                    }

                    setMockLocation(randomizeValue(currentDataLine.getLatitude()), randomizeValue(currentDataLine.getLongitude()), randomizeAltitude(currentDataLine.getAltitude(), 0.1), currentDataLine.getAcc());

                    if (!isPaused) {
                        replayPosition = nextPos;
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    isReplaying = false;
                } catch (Exception e) {
                    Log.e(TAG, "Error in replay task", e);
                    isReplaying = false;
                }
            }
            new Handler(Looper.getMainLooper()).post(GpsLoggerService.this::stopReplay);
        }

        private double randomizeValue(double start) {
            return start + (random.nextDouble() * 0.00001 - 0.000005);
        }

        private double randomizeAltitude(double start, double diff) {
            return start + (random.nextDouble() * diff - (diff / 2));
        }
    }
}
