package cz.kamma.gpslogger;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final String VERSION = "v1.0.0";
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.READ_EXTERNAL_STORAGE
    };
    private static final int PERMISSIONS_REQUEST_CODE = 1340;
    private static final int OVERLAY_PERMISSION_REQUEST_CODE = 1341;

    // Views
    private Button buttonReplay, buttonReplayStop, buttonReplayPause, buttonReverse, buttonSpeedPlus, buttonSpeedMinus;
    private CheckBox showMap;
    private TextView textView, timeView, fileNameView;
    private SeekBar seekBar;
    private GoogleMap mMap;

    private String selectedFileName;
    private boolean isSeeking = false;
    private ArrayList<String> replayData;

    private final BroadcastReceiver statusUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (GpsLoggerService.BROADCAST_STATUS_UPDATE.equals(intent.getAction())) {
                String status = intent.getStringExtra(GpsLoggerService.EXTRA_STATUS);
                long time = intent.getLongExtra(GpsLoggerService.EXTRA_TIME, -1);
                double latitude = intent.getDoubleExtra(GpsLoggerService.EXTRA_LATITUDE, 0);
                double longitude = intent.getDoubleExtra(GpsLoggerService.EXTRA_LONGITUDE, 0);
                ReplayState replayState = (ReplayState) intent.getSerializableExtra(GpsLoggerService.EXTRA_REPLAY_STATE);

                if (status != null) {
                    textView.setText(status);
                }

                if (time != -1 && !isSeeking) {
                    timeView.setText(String.format(Locale.getDefault(), "%dm:%ds",
                            TimeUnit.MILLISECONDS.toMinutes(time),
                            TimeUnit.MILLISECONDS.toSeconds(time) -
                                    TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))));
                }

                if (replayState != null && replayState.isReplaying()) {
                    updateUiWithReplayState(replayState);
                    if (latitude != 0 && longitude != 0) {
                        updateMap(latitude, longitude);
                    }
                } else {
                    resetUiToDefault();
                }
            }
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.main);

        initViews();
        initMap();
        initClickListeners();

        textView.setText(VERSION);

        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        LocalBroadcastManager.getInstance(this).registerReceiver(statusUpdateReceiver,
                new IntentFilter(GpsLoggerService.BROADCAST_STATUS_UPDATE));
        sendCommandToService(GpsLoggerService.ACTION_REQUEST_UPDATE);
    }

    @Override
    protected void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(this).unregisterReceiver(statusUpdateReceiver);
    }

    private void initViews() {
        buttonReplay = findViewById(R.id.startReplay);
        buttonReplayStop = findViewById(R.id.stopReplay);
        buttonSpeedPlus = findViewById(R.id.buttonSpeedPlus);
        buttonSpeedMinus = findViewById(R.id.buttonSpeedMinus);
        buttonReplayPause = findViewById(R.id.pauseReplay);
        buttonReverse = findViewById(R.id.buttonReverse);
        showMap = findViewById(R.id.showMap);
        textView = findViewById(R.id.textView);
        timeView = findViewById(R.id.timeView);
        fileNameView = findViewById(R.id.fileNameView);
        seekBar = findViewById(R.id.seekBar);

        resetUiToDefault();
    }

    private void initMap() {
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }
    }

    private void initClickListeners() {
        buttonReplay.setOnClickListener(v -> openSelectFileDialog());
        buttonReplayStop.setOnClickListener(v -> sendCommandToService(GpsLoggerService.ACTION_STOP_REPLAY));
        buttonReplayPause.setOnClickListener(v -> sendCommandToService(GpsLoggerService.ACTION_PAUSE_REPLAY));
        buttonReverse.setOnClickListener(v -> sendCommandToService(GpsLoggerService.ACTION_TOGGLE_REVERSE));
        buttonSpeedPlus.setOnClickListener(v -> sendCommandToService(GpsLoggerService.ACTION_SPEED_PLUS));
        buttonSpeedMinus.setOnClickListener(v -> sendCommandToService(GpsLoggerService.ACTION_SPEED_MINUS));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && replayData != null && progress < replayData.size()) {
                    try {
                        String line = replayData.get(progress);
                        DataLine dataLine = new DataLine(line.split(" "));
                        long time = dataLine.getTime();
                        timeView.setText(String.format(Locale.getDefault(), "%dm:%ds",
                                TimeUnit.MILLISECONDS.toMinutes(time),
                                TimeUnit.MILLISECONDS.toSeconds(time) -
                                        TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))));
                    } catch (Exception e) {
                        // Ignore parsing errors during scrub
                    }
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Set position")
                        .setMessage("Do you really want to set this position?")
                        .setIcon(android.R.drawable.ic_dialog_alert)
                        .setPositiveButton(android.R.string.yes, (dialog, which) -> {
                            Intent intent = new Intent(MainActivity.this, GpsLoggerService.class);
                            intent.setAction(GpsLoggerService.ACTION_SEEK);
                            intent.putExtra("position", seekBar.getProgress());
                            sendCommandToService(intent);
                            isSeeking = false;
                        })
                        .setNegativeButton(android.R.string.no, (dialog, which) -> isSeeking = false)
                        .setOnCancelListener(dialog -> isSeeking = false)
                        .show();
            }
        });
    }

    private void sendCommandToService(String action) {
        Intent intent = new Intent(this, GpsLoggerService.class);
        intent.setAction(action);
        sendCommandToService(intent);
    }

    private void sendCommandToService(Intent intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
    }

    private void resetUiToDefault() {
        buttonReplay.setEnabled(true);

        buttonReplayStop.setEnabled(false);
        buttonReplayPause.setEnabled(false);
        buttonReverse.setEnabled(false);
        buttonSpeedPlus.setEnabled(false);
        buttonSpeedMinus.setEnabled(false);
        seekBar.setEnabled(false);

        fileNameView.setText("");
        timeView.setText("0:00");
        textView.setText(VERSION);
        seekBar.setProgress(0);
        if (replayData != null) {
            replayData.clear();
            replayData = null;
        }
    }

    private void updateUiWithReplayState(ReplayState state) {
        boolean isReplaying = state.isReplaying();
        buttonReplay.setEnabled(!isReplaying);

        buttonReplayStop.setEnabled(isReplaying);
        buttonReplayPause.setEnabled(isReplaying);
        buttonReverse.setEnabled(isReplaying);
        buttonSpeedPlus.setEnabled(isReplaying);
        buttonSpeedMinus.setEnabled(isReplaying);
        seekBar.setEnabled(isReplaying);

        fileNameView.setText(state.getFileName());
        buttonReverse.setText(String.format("Reverse (%s)", state.isReverse() ? "Backward" : "Forward"));
        buttonSpeedPlus.setText(String.format("SPEED+ (%d)", state.getReplaySpeed()));
        if (seekBar.getMax() != state.getMaxPosition()) {
            seekBar.setMax(state.getMaxPosition());
        }

        if (!isSeeking) {
            seekBar.setProgress(state.getCurrentPosition());
        }
    }

    private void loadReplayFile(String fileName) {
        replayData = new ArrayList<>();
        File extStorage = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        File file = new File(extStorage, fileName);

        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                replayData.add(line);
            }
        } catch (IOException e) {
            Toast.makeText(this, "Failed to read replay file for preview.", Toast.LENGTH_SHORT).show();
            replayData = null; // Clear data on failure
        }
    }

    private void openSelectFileDialog() {
        if (!hasPermissions()) {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
            return;
        }

        File downloadDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloadDir.exists()) {
            Toast.makeText(this, "Download directory not found.", Toast.LENGTH_SHORT).show();
            return;
        }
        final String[] fileNames = downloadDir.list((dir, name) -> name.toLowerCase().endsWith(".txt"));
        if (fileNames == null || fileNames.length == 0) {
            Toast.makeText(this, "No .txt files found in Download folder.", Toast.LENGTH_SHORT).show();
            return;
        }
        Arrays.sort(fileNames);

        new AlertDialog.Builder(this).setTitle("Choose file")
                .setSingleChoiceItems(fileNames, -1, (dialog, which) -> selectedFileName = fileNames[which])
                .setPositiveButton("OK", (dialog, which) -> {
                    if (selectedFileName != null) {
                        loadReplayFile(selectedFileName);
                        Intent intent = new Intent(this, GpsLoggerService.class);
                        intent.setAction(GpsLoggerService.ACTION_START_REPLAY);
                        intent.putExtra("fileName", selectedFileName);
                        sendCommandToService(intent);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        mMap.getUiSettings().setZoomControlsEnabled(true);
        mMap.setMaxZoomPreference(18f);
        mMap.setMinZoomPreference(12f);
    }

    private void updateMap(double latitude, double longitude) {
        if (mMap != null && showMap.isChecked()) {
            LatLng position = new LatLng(latitude, longitude);
            mMap.clear();
            mMap.addMarker(new MarkerOptions().position(position));
            mMap.animateCamera(CameraUpdateFactory.newLatLngZoom(position, 17f));
        }
    }

    private boolean hasPermissions() {
        for (String perm : REQUIRED_PERMISSIONS) {
            if (ActivityCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            if (!hasPermissions()) {
                Toast.makeText(this, "Permissions are required to run the app.", Toast.LENGTH_LONG).show();
                finish(); // Close the app if permissions are denied
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == OVERLAY_PERMISSION_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!Settings.canDrawOverlays(this)) {
                    Toast.makeText(this, "Overlay permission is required to show controls over other apps.", Toast.LENGTH_LONG).show();
                }
            }
        }
    }
}
