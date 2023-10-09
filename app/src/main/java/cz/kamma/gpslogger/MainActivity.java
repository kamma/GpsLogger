package cz.kamma.gpslogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.StringReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import android.Manifest;
import android.app.AlertDialog;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.provider.ProviderProperties;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends AppCompatActivity implements LocationListener, OnMapReadyCallback {

    static final String TAG = MainActivity.class.getCanonicalName();
    public static final String ACTION_PAUSE = "GPS_PLAYER_PAUSE";
    public static final String ACTION_REVERSE = "GPS_PLAYER_REVERSE";
    public static final String ACTION_SPEED_PLUS = "GPS_PLAYER_SPEED_PLUS";
    public static final String ACTION_SPEED_MINUS = "GPS_PLAYER_SPEED_MINUS";
    private static final String CHANNEL_ID = "AAA";
    private static final int notificationId = 10001;
    private static final int GPS_ACCURACY = ProviderProperties.ACCURACY_FINE;
    private static final String GPS_PROVIDER_NAME = LocationManager.GPS_PROVIDER;

    static String VERSION = "v0.7";

    private static final String[] INITIAL_PERMS = {Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.READ_EXTERNAL_STORAGE};

    private static final int LOCATION_REQUEST = 1340;
    SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
    volatile static boolean running, paused, reverse = false;
    String fileName;
    char schar = '|';
    static Random gen = new Random();
    volatile static int pos = 0;
    static boolean seeked = false;
    static String state = VERSION;
    static long start = -1;
    static int speed = 1;
    PowerManager.WakeLock wakeLock;

    LocationManager locationManager;
    static Button buttonStart, buttonStop, buttonReplay, buttonReplayStop, buttonReplayPause, buttonResetGps, buttonReverse, buttonSpeedPlus, buttonSpeedMinus;
    FileOutputStream f;
    TextView textView, timeView, fileNameView;
    SeekBar seekBar;
    private static GoogleMap mMap;

    MainActivity activity;

    static NotificationCompat.Builder builder;
    static NotificationManagerCompat notificationManager;
    Intent snoozeIntentPause, snoozeIntentReverse, snoozeIntentSpeedPlus, snoozeIntentSpeedMinus;
    PendingIntent snoozePendingIntentPause, snoozePendingIntentReverse, snoozePendingIntentSpeedPlus, snoozePendingIntentSpeedMinus;
    BroadcastReceiver br = new MyBroadcastReceiver();
    IntentFilter filter = new IntentFilter();

    public static void refreshNotification() {
        String textTmp = "Now GPS Player is PAUSED";
        if (!paused)
            textTmp = "Now GPS Player is PLAYING/";

        if (reverse)
            textTmp = textTmp + "going Backward/";
        else
            textTmp = textTmp + "going Forward/";

        textTmp = textTmp + "Speed " + speed;
        builder.setContentText(textTmp);
        notificationManager.notify(notificationId, builder.build());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.main);
        createNotificationChannel();

        snoozeIntentPause = new Intent(this, MyBroadcastReceiver.class);
        snoozeIntentPause.setAction(ACTION_PAUSE);

        snoozeIntentReverse = new Intent(this, MyBroadcastReceiver.class);
        snoozeIntentReverse.setAction(ACTION_REVERSE);

        snoozeIntentSpeedPlus = new Intent(this, MyBroadcastReceiver.class);
        snoozeIntentSpeedPlus.setAction(ACTION_SPEED_PLUS);

        snoozeIntentSpeedMinus = new Intent(this, MyBroadcastReceiver.class);
        snoozeIntentSpeedMinus.setAction(ACTION_SPEED_MINUS);

        snoozePendingIntentPause = PendingIntent.getBroadcast(this, 0, snoozeIntentPause, 0);
        snoozePendingIntentReverse = PendingIntent.getBroadcast(this, 0, snoozeIntentReverse, 0);
        snoozePendingIntentSpeedPlus = PendingIntent.getBroadcast(this, 0, snoozeIntentSpeedPlus, 0);
        snoozePendingIntentSpeedMinus = PendingIntent.getBroadcast(this, 0, snoozeIntentSpeedMinus, 0);

        builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.btn_plus)
                .setContentTitle("GPS Player")
                .setContentText("Now GPS Player is PLAYING/going Forward/Speed 1")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(false)
                .setOngoing(true)
                .setContentIntent(snoozePendingIntentPause)
                .addAction(android.R.drawable.ic_media_rew, "Reverse",
                        snoozePendingIntentReverse)
                .addAction(android.R.drawable.ic_media_rew, "Speed+",
                        snoozePendingIntentSpeedPlus)
                .addAction(android.R.drawable.ic_media_rew, "Speed-",
                        snoozePendingIntentSpeedMinus);

        notificationManager = NotificationManagerCompat.from(this);

        filter.addAction(Intent.ACTION_AIRPLANE_MODE_CHANGED);
        this.registerReceiver(br, filter);

        activity = this;

        buttonStart = findViewById(R.id.buttonStart);
        buttonStop = findViewById(R.id.buttonStop);
        buttonStop.setEnabled(false);
        buttonResetGps = findViewById(R.id.buttonResetGps);
        buttonReplay = findViewById(R.id.startReplay);
        buttonReplayStop = findViewById(R.id.stopReplay);
        buttonReplayStop.setEnabled(false);
        buttonSpeedPlus = findViewById(R.id.buttonSpeedPlus);
        buttonSpeedPlus.setEnabled(false);
        buttonSpeedMinus = findViewById(R.id.buttonSpeedMinus);
        buttonSpeedMinus.setEnabled(false);
        textView = findViewById(R.id.textView);
        timeView = findViewById(R.id.timeView);
        fileNameView = findViewById(R.id.fileNameView);
        buttonReplayPause = findViewById(R.id.pauseReplay);
        buttonReplayPause.setEnabled(false);
        buttonReverse = findViewById(R.id.buttonReverse);
        buttonReverse.setEnabled(false);
        seekBar = findViewById(R.id.seekBar);
        seekBar.setEnabled(false);
        seekBar.setProgress(0);

        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        textView.setText(VERSION);
        timeView.setText("0:00");

        buttonStart.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveAsFileDialog();
            }
        });

        buttonStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                buttonStop.setEnabled(false);
                buttonStart.setEnabled(true);
                buttonReplay.setEnabled(true);
                buttonReplayStop.setEnabled(false);
                buttonReverse.setEnabled(false);
                textView.setText("STOPPED");
                stopLogging();
            }
        });

        buttonReplay.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openSelectFileDialog();
            }
        });

        buttonResetGps.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearGps();
            }
        });

        buttonReplayStop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopReplay();
            }
        });
        buttonSpeedPlus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (paused)
                    paused = false;
                else
                    speed++;
                buttonSpeedPlus.setText("SPEED+ (" + speed + ")");
            }
        });
        buttonSpeedMinus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                    speed--;
                    if (speed < 1) {
                        speed = 1;
                        paused = true;
                        MainActivity.refreshNotification();
                    }
                    buttonSpeedPlus.setText("SPEED+ (" + speed + ")");
                }
        });
        buttonReplayPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                paused = !paused;
                MainActivity.refreshNotification();
            }
        });

        buttonReverse.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (reverse) {
                    reverse = false;
                    buttonReverse.setText("Reverse (going Forward)");
                } else {
                    reverse = true;
                    buttonReverse.setText("Reverse (going Backward)");
                }
                MainActivity.refreshNotification();
            }
        });

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {

            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {

            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                final int tmp = seekBar.getProgress();
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Set position")
                        .setMessage("Do you really want to set this position?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                pos = tmp;
                                seeked = true;
                            }
                        })
                        .setNegativeButton(android.R.string.no, null).show();
            }
        });


        if (!canAccessLocation()) {
            requestPermissions(INITIAL_PERMS, LOCATION_REQUEST);
        }
    }

    @Override
    public void onDestroy() {
        notificationManager.cancelAll();
        super.onDestroy();
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "PAUSE";
            String description = "GPS Pause";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    private void clearGps() {
        if (locationManager != null && locationManager.getProvider(GPS_PROVIDER_NAME) != null) {
            locationManager.clearTestProviderStatus(GPS_PROVIDER_NAME);
            locationManager.clearTestProviderEnabled(GPS_PROVIDER_NAME);
            locationManager.clearTestProviderLocation(GPS_PROVIDER_NAME);
            locationManager.setTestProviderEnabled(GPS_PROVIDER_NAME, false);
            locationManager.removeTestProvider(GPS_PROVIDER_NAME);
            locationManager = null;
            locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            locationManager.addTestProvider(GPS_PROVIDER_NAME, false, true, false, false, true, true, true,
                    ProviderProperties.POWER_USAGE_LOW, GPS_ACCURACY);
            locationManager.setTestProviderEnabled(GPS_PROVIDER_NAME, true);
        }
        Toast.makeText(MainActivity.this, "GPS Provider cleared.", Toast.LENGTH_SHORT).show();
    }

    private void startReplay() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "GPSLogger::wakeLock");
        wakeLock.acquire();
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        locationManager.addTestProvider(GPS_PROVIDER_NAME, false, true, false, false, true, true, true,
                ProviderProperties.POWER_USAGE_LOW, GPS_ACCURACY);
        locationManager.setTestProviderEnabled(GPS_PROVIDER_NAME, true);

        running = true;
        paused = false;
        pos = 0;

        notificationManager.notify(notificationId, builder.build());

        new AsyncTask() {
            @Override
            protected Object doInBackground(Object[] objects) {
                try {
                    String THEME_PATH_PREFIX = "Download";
                    File extStorage = Environment.getExternalStorageDirectory();
                    File root = new File(extStorage, THEME_PATH_PREFIX);
                    File file = new File(root, fileName);
                    ArrayList<String> fullFile = new ArrayList<>();
                    FileInputStream fis = new FileInputStream(file);
                    byte[] b = new byte[fis.available()];
                    fis.read(b);
                    StringReader sr = new StringReader(new String(b));
                    BufferedReader br = new BufferedReader(sr);
                    String lineTmp = br.readLine();
                    while (lineTmp != null) {
                        fullFile.add(lineTmp);
                        lineTmp = br.readLine();
                    }
                    br.close();

                    seekBar.setMax(fullFile.size());

                    pos = 0;
                    while (running == true) {
                        rotateChar();

                        DataLine dl = new DataLine(fullFile.get(pos).split(" "));

                        if (dl != null) {
                            DataLine next;
                            int nextPos;
                            if (reverse) {
                                nextPos = pos - 1;
                                if (nextPos < 0)
                                    paused = true;
                            } else {
                                nextPos = pos + 1;
                                if (nextPos >= fullFile.size())
                                    paused = true;
                            }

                            if (paused) {
                                state = "PAUSED " + schar;

                                setMockLocation(randomizeValue(dl.getLatitude()), randomizeValue(dl.getLongitude()), randomizeAltitude(dl.getAltitude(), 0.001), dl.getAcc());
                                Thread.sleep(1000);
                            } else {
                                state = "PLAYING " + schar;

                                next = new DataLine(fullFile.get(nextPos).split(" "));

                                long waitTime = Math.abs(dl.getTime() - next.getTime());

                                if (seeked) {
                                    seeked = false;
                                    waitTime = 1000;
                                }

                                long waitDiff = gen.nextInt(100);
                                waitDiff = gen.nextBoolean() ? waitDiff * -1 : waitDiff;
                                waitTime = waitTime + waitDiff;

                                waitTime = waitTime / speed;

                                Thread.sleep(waitTime < 0 ? 0 : waitTime);

                                setMockLocation(randomizeValue(dl.getLatitude()), randomizeValue(dl.getLongitude()), randomizeAltitude(dl.getAltitude(), 0.1), dl.getAcc());

                                if (reverse) {
                                    pos--;
                                    if (pos < 0)
                                        paused = true;
                                } else {
                                    pos++;
                                    if (pos >= fullFile.size())
                                        paused = true;
                                }
                            }
                        }
                        publishProgress(dl.getLatitude(), dl.getLongitude(), dl.getTime());
                    }
                } catch (Exception e) {
                    Log.e(TAG, "", e);
                }
                running = false;
                reverse = false;
                return null;
            }

            @Override
            protected void onPostExecute(Object o) {
                buttonReplayStop.setEnabled(false);
                buttonReplay.setEnabled(true);
                buttonReplayPause.setEnabled(false);
                buttonSpeedPlus.setEnabled(false);
                buttonSpeedMinus.setEnabled(false);
                buttonStop.setEnabled(false);
                buttonStart.setEnabled(true);
                buttonReverse.setEnabled(false);
                buttonReverse.setText("Reverse");
                textView.setText("STOPPED");
                seekBar.setProgress(0);
                seekBar.setEnabled(false);
                fileNameView.setText("");
                if (locationManager != null && locationManager.getProvider(GPS_PROVIDER_NAME) != null) {
                    locationManager.clearTestProviderStatus(GPS_PROVIDER_NAME);
                    locationManager.clearTestProviderEnabled(GPS_PROVIDER_NAME);
                    locationManager.clearTestProviderLocation(GPS_PROVIDER_NAME);
                    locationManager.setTestProviderEnabled(GPS_PROVIDER_NAME, false);
                    locationManager.removeTestProvider(GPS_PROVIDER_NAME);
                    locationManager = null;
                }
                super.onPostExecute(o);
            }

            @Override
            protected void onPreExecute() {
                textView.setText("PLAYING");
                super.onPreExecute();
            }

            @Override
            protected void onProgressUpdate(Object[] values) {
                textView.setText(state);
                seekBar.setProgress(pos);
                if (values != null && values.length > 2) {
                    double latitude = (double) values[0];
                    double longitude = (double) values[1];
                    long time = (long) values[2];
                    mMap.clear();
                    LatLng mapPos = new LatLng(latitude, longitude);
                    MarkerOptions markerOptions = new MarkerOptions();
                    markerOptions.position(mapPos);
                    mMap.addMarker(markerOptions);
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(mapPos));
                    timeView.setText("" + String.format("%dm:%ds",
                            TimeUnit.MILLISECONDS.toMinutes(time),
                            TimeUnit.MILLISECONDS.toSeconds(time) -
                                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))));

                }
                super.onProgressUpdate(values);
            }
        }.execute();
    }

    private static double randomizeValue(double start) {
        int rnd = gen.nextInt(Integer.MAX_VALUE);
        double tmp = (rnd * 0.00000000000001);
        double res = round(start + tmp);
        return res;
    }

    private static double round(double d) {
        String tmp = "" + d;
        String[] parts = tmp.split("\\.");
        if (parts[1].length() > 14)
            return Double.parseDouble(parts[0] + "." + parts[1].substring(0, 14));
        return d;
    }

    private static double randomizeAltitude(double start, double diff) {
        int rnd = gen.nextInt(100);
        return start + (rnd * diff * 0.1);
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
            locationManager.setTestProviderLocation(GPS_PROVIDER_NAME, newLocation);
        } catch (Exception e) {
            Log.e(TAG, "Error: " + e.getMessage());
        }
    }

    private void openSelectFileDialog() {
        if (!canAccessLocation()) {
            requestPermissions(INITIAL_PERMS, LOCATION_REQUEST);
        }
        String THEME_PATH_PREFIX = "Download";
        File extStorage = Environment.getExternalStorageDirectory();
        File file = new File(extStorage, THEME_PATH_PREFIX);

        final String[] fileNames = file.list();
        Arrays.sort(fileNames);
        if (fileNames == null || fileNames.length < 1) {
            Toast.makeText(MainActivity.this, "No file found on " + THEME_PATH_PREFIX, Toast.LENGTH_SHORT).show();
            return;
        }
        new AlertDialog.Builder(MainActivity.this).setTitle("Choose file")
                .setSingleChoiceItems(fileNames, -1, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        fileName = fileNames[whichButton];
                    }
                }).setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        if (fileName == null || fileName.length() < 1) {
                            Toast.makeText(MainActivity.this, "No file selected.", Toast.LENGTH_SHORT).show();
                        }
                        buttonReplayStop.setEnabled(true);
                        buttonReplay.setEnabled(false);
                        buttonStop.setEnabled(false);
                        buttonStart.setEnabled(false);
                        buttonReplayPause.setEnabled(true);
                        seekBar.setEnabled(true);
                        buttonReverse.setEnabled(true);
                        buttonSpeedPlus.setEnabled(true);
                        buttonSpeedMinus.setEnabled(true);
                        fileNameView.setText(fileName);
                        startReplay();
                    }
                }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int whichButton) {
                        fileName = null;
                    }
                }).create().show();
    }

    private void stopReplay() {
        running = false;
        paused = false;
        wakeLock.release();
        notificationManager.cancel(notificationId);
    }

    private void stopLogging() {
        locationManager.removeUpdates(this);
        try {
            f.flush();
            f.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        start = -1;
        textView.setText("STOPPED");
        wakeLock.release();
    }

    private void startLogging() {
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "GPSLogger::wakeLock");
        wakeLock.acquire();
        textView.setText("RECORDING");
        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        File root = Environment.getExternalStorageDirectory();
        try {
            f = new FileOutputStream(new File(root, fileName), true);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            return;
        else
            locationManager.requestLocationUpdates(GPS_PROVIDER_NAME, 0, 0, this);
    }

    private void saveAsFileDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Save as");

        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        String tmpName = "gpsdata-" + (sdf.format(new Date())) + ".txt";
        input.setText(tmpName);

        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                fileName = input.getText().toString();
                buttonStop.setEnabled(true);
                buttonStart.setEnabled(false);
                buttonReplay.setEnabled(false);
                buttonReplayStop.setEnabled(false);
                startLogging();
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                fileName = null;
                dialog.cancel();
            }
        });

        builder.setView(input);
        input.setSelection(0, tmpName.indexOf(".txt"));
        builder.show();
    }

    @Override
    public void onLocationChanged(Location location) {
        if (start == -1)
            start = location.getTime();
        double latitude = location.getLatitude();
        double longitude = location.getLongitude();
        double altitude = location.getAltitude();
        long time = location.getTime() - start;
        float acc = location.getAccuracy();

        logData(time, latitude, longitude, altitude, acc);
    }

    private void logData(Object... vars) {
        String data = vars[0] + " " + vars[1] + " " + vars[2] + " " + vars[3] + " " + vars[4] + "\n";

        textView.setText("RECORDING " + schar);
        rotateChar();

        try {
            f.write(data.getBytes());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void rotateChar() {
        if (schar == '|')
            schar = '/';
        else if (schar == '/')
            schar = '-';
        else if (schar == '-')
            schar = '\\';
        else if (schar == '\\')
            schar = '|';
    }

    @Override
    public void onProviderDisabled(String provider) {
    }

    @Override
    public void onProviderEnabled(String provider) {
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {

        switch (requestCode) {
            case LOCATION_REQUEST:
                if (canAccessLocation()) {
                    Toast.makeText(this, "GPS permission allowed", Toast.LENGTH_LONG).show();
                } else {
                    Toast.makeText(this, "Cannot log GPS", Toast.LENGTH_LONG).show();
                }
                break;
        }
    }

    private boolean canAccessLocation() {
        for (String perm : INITIAL_PERMS) {
            if (!hasPermission(perm))
                return false;
        }
        return true;
    }

    private boolean hasPermission(String perm) {
        return (PackageManager.PERMISSION_GRANTED == checkSelfPermission(perm));
    }

    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.setMaxZoomPreference(18f);
        mMap.setMinZoomPreference(18f);
    }
}
