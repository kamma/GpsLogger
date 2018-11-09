package cz.kamma.gpslogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.StringReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.TimeUnit;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;

public class MainActivity extends Activity implements LocationListener, OnMapReadyCallback {

    static final String TAG = MainActivity.class.getCanonicalName();

	static String VERSION = "v0.11";

	private static final String[] INITIAL_PERMS = { Manifest.permission.ACCESS_FINE_LOCATION,
			Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE,
			Manifest.permission.READ_EXTERNAL_STORAGE };

	private static final int LOCATION_REQUEST = 1340;
	SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
	long start = -1;
	boolean running, paused = false;
	String fileName;
	char schar = '|';
	static Random gen = new Random();
	static int pos = 0;
	static boolean seeked = false;

	Button buttonStart, buttonStop, buttonReplay, buttonReplayStop, buttonReplayPause;
	LocationManager locationManager;
	FileOutputStream f;
	TextView textView, timeView;
	SeekBar seekBar;
	MapView mapView;
    private GoogleMap gmap;
    private static final String MAP_VIEW_BUNDLE_KEY = "MapViewBundleKey";

	MainActivity activity;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		activity = this;

		buttonStart = findViewById(R.id.buttonStart);
		buttonStop = findViewById(R.id.buttonStop);
		buttonStop.setEnabled(false);
		buttonReplay = findViewById(R.id.startReplay);
		buttonReplayStop = findViewById(R.id.stopReplay);
		buttonReplayStop.setEnabled(false);
		textView = findViewById(R.id.textView);
        timeView = findViewById(R.id.timeView);
		buttonReplayPause = findViewById(R.id.pauseReplay);
        buttonReplayPause.setEnabled(false);
        seekBar = findViewById(R.id.seekBar);
        seekBar.setEnabled(false);
        seekBar.setProgress(0);

        Bundle mapViewBundle = null;
        if (savedInstanceState != null) {
            mapViewBundle = savedInstanceState.getBundle(MAP_VIEW_BUNDLE_KEY);
        }

        mapView = findViewById(R.id.mapView);
        mapView.onCreate(mapViewBundle);
        mapView.getMapAsync(this);

		textView.setText(VERSION);
        timeView.setText("0:00");

		buttonStart.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				log("Start logging");
                saveAsFileDialog();
			}
		});

		buttonStop.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				log("Stop logging");
				buttonStop.setEnabled(false);
				buttonStart.setEnabled(true);
				buttonReplay.setEnabled(true);
				buttonReplayStop.setEnabled(false);
				textView.setText("STOPPED");
				stopLogging();
			}
		});

		buttonReplay.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				log("Start replay");
				openSelectFileDialog();
			}
		});

		buttonReplayStop.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				log("Stop replay");
				buttonReplayStop.setEnabled(false);
				buttonReplay.setEnabled(true);
				buttonStop.setEnabled(false);
				buttonStart.setEnabled(true);
				stopReplay();
			}
		});

		buttonReplayPause.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				log("Paused");
				paused = !paused;
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
                pos = seekBar.getProgress();
                log("Position: "+pos);
                seeked = true;
            }
        });


		if (!canAccessLocation()) {
			requestPermissions(INITIAL_PERMS, LOCATION_REQUEST);
		}
	}

	private void startReplay() {
		running = true;
		paused = false;

		new AsyncTask(){
			@Override
			protected Object doInBackground(Object[] objects) {
				locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

				locationManager.addTestProvider(LocationManager.GPS_PROVIDER, true, true, true, false, true, true, true,
						3, 1);
				locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);

				try {
					String THEME_PATH_PREFIX = "Download";
					File extStorage = Environment.getExternalStorageDirectory();
					File root = new File(extStorage, THEME_PATH_PREFIX);
					File file = new File(root, fileName);
                    ArrayList<String> fullFile = new ArrayList<>();
                    StringReader sr = new StringReader(new String(Files.readAllBytes(file.toPath())));
                    BufferedReader br = new BufferedReader(sr);
					String lineTmp = br.readLine();
					while (lineTmp!=null) {
					    fullFile.add(lineTmp);
                        lineTmp = br.readLine();
                    }
                    br.close();
					double lastLatitude = -1;
					double lastLongitude = -1;
					double lastAltitude = -1;
                    long lastTime = 0;

                    seekBar.setMin(0);
                    seekBar.setMax(fullFile.size());

                    pos = 0;
                    while (pos < fullFile.size() && running == true) {
                        rotateChar();
						if (paused) {
							textView.setText("PAUSED " + schar);

							log("Original location: " + lastLatitude + ", " + lastLongitude + ", " + lastAltitude);
							long sleepTime = (long) (Math.random() * 15);
							Thread.sleep((Math.random() > 0.5 ? sleepTime + 1000 : 1000 - sleepTime));
							setMockLocation(randomizeValue(lastLatitude, 0.0000003), randomizeValue(lastLongitude, 0.0000003),
									randomizeAltitude(lastAltitude, 0.001));
						} else {
                            String line = fullFile.get(pos);
                            seekBar.setProgress(pos++);

                            textView.setText("PLAYING " + schar);

							String[] data = line.split(" ");
							if (data.length == 4) {
                                long time = Long.parseLong(data[0]);
								double latitude = Double.parseDouble(data[1]);
								double longitude = Double.parseDouble(data[2]);
								double altitude = Double.parseDouble(data[3]);

								long waitTime = time - lastTime;
								if (seeked) {
								    seeked = false;
								    waitTime = 1005;
                                }
								lastLatitude = latitude;
								lastLongitude = longitude;
								lastAltitude = altitude;
								lastTime = time;

								timeView.setText(""+String.format("%dm:%ds",
                                        TimeUnit.MILLISECONDS.toMinutes(time),
                                        TimeUnit.MILLISECONDS.toSeconds(time) -
                                                TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(time))));

								log("Original location: " + latitude + ", " + longitude + ", " + altitude);
								long sleepTime = (long) (Math.random() * 15);
								Thread.sleep((Math.random() > 0.5 ? sleepTime + waitTime : waitTime - sleepTime));
								setMockLocation(randomizeValue(latitude, 0.000001), randomizeValue(longitude, 0.000001),
										randomizeAltitude(altitude, 0.1));

							}
						}
					}
				} catch (Exception e) {
					log(e.getMessage());
					//Toast.makeText(MainActivity.this, "Cannot parse GPS data file.", Toast.LENGTH_SHORT).show();
				}
                log("Final position: "+pos);
                running = false;
				return null;
			}

			@Override
			protected void onPostExecute(Object o) {
				buttonReplayStop.setEnabled(false);
				buttonReplay.setEnabled(true);
				buttonReplayPause.setEnabled(false);
				buttonStop.setEnabled(false);
				buttonStart.setEnabled(true);
				textView.setText("STOPPED");
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
                seekBar.setEnabled(false);
				super.onPostExecute(o);
			}

			@Override
			protected void onPreExecute() {
				textView.setText("PLAYING");
				super.onPreExecute();
			}
		}.execute();

	}

	private static double randomizeValue(double start, double diff) {
		int rnd = gen.nextInt(100);
		double tmp = (rnd * diff * 0.1);
		return start + tmp;
	}

	private static double randomizeAltitude(double start, double diff) {
		int rnd = gen.nextInt(100);
		return start + (rnd * diff * 0.1);
	}

	private void setMockLocation(double latitude, double longitude, double altitude) {
		Location newLocation = new Location(LocationManager.GPS_PROVIDER);

		newLocation.setLatitude(latitude);
		newLocation.setLongitude(longitude);
		newLocation.setAltitude(altitude);
		long time = System.currentTimeMillis();
		newLocation.setTime(time);
		newLocation.setAccuracy(1);
		Method locationJellyBeanFixMethod = null;
		try {
			locationJellyBeanFixMethod = Location.class.getMethod("makeComplete");
		} catch (NoSuchMethodException e) {
			e.printStackTrace();
		}
		if (locationJellyBeanFixMethod != null) {
			try {
				locationJellyBeanFixMethod.invoke(newLocation);
			} catch (IllegalAccessException e) {

			} catch (InvocationTargetException e) {
				e.printStackTrace();
			}
		}
		try {
			log("Setting modified location: " + latitude + ", " + longitude + ", " + altitude);
			locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, newLocation);
            //LatLng mapPos = new LatLng(latitude, longitude);
            //gmap.moveCamera(CameraUpdateFactory.newLatLng(mapPos));
		} catch (Exception e) {
			Log.e(TAG, "Error: "+e.getMessage());
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
	}

	private void stopLogging() {
		locationManager.removeUpdates(this);
		try {
			f.flush();
			f.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
		textView.setText("STOPPED");
	}

	private void startLogging() {
		textView.setText("RECORDING");
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		start = System.currentTimeMillis();
		File root = Environment.getExternalStorageDirectory();
		try {
			f = new FileOutputStream(new File(root, fileName), true);
		} catch (Exception e) {
			e.printStackTrace();
		}
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
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
		double latitude = location.getLatitude();
		double longitude = location.getLongitude();
		double altitude = location.getAltitude();
		long time = System.currentTimeMillis() - start;

		logData(time, latitude, longitude, altitude);
	}

	private void logData(long time, double latitude, double longitude, double altitude) {
		String data = time + " " + latitude + " " + longitude + " " + altitude + "\n";
		log("time: " + time + " latitude: " + latitude + " longitude: " + longitude + " altitude: " + altitude);

		textView.setText("RECORDING "+schar);
		rotateChar();

		try {
			f.write(data.getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void rotateChar() {
		if (schar=='|')
			schar='/';
		else if (schar=='/')
			schar='-';
		else if (schar=='-')
			schar='\\';
		else if (schar=='\\')
			schar='|';
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

	private void log(String line) {
		Log.i(TAG, line);
	}

    @Override
    public void onMapReady(GoogleMap googleMap) {
        gmap = googleMap;
        gmap.setMinZoomPreference(12);
    }
}
