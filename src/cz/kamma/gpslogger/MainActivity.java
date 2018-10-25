package cz.kamma.gpslogger;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.location.GpsClock;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity implements LocationListener {

	private static final String[] INITIAL_PERMS = { Manifest.permission.ACCESS_FINE_LOCATION,
			Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE,
			Manifest.permission.READ_EXTERNAL_STORAGE };

	private static final int LOCATION_REQUEST = 1340;

	long start = -1;
	boolean running = false;

	String fileName;

	static final String TAG = MainActivity.class.getCanonicalName();

	Button buttonStart, buttonStop, buttonReplay, buttonReplayStop;
	LocationManager locationManager;
	FileOutputStream f;
	TextView textView;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		buttonStart = findViewById(R.id.buttonStart);
		buttonStop = findViewById(R.id.buttonStop);
		buttonStop.setEnabled(false);
		buttonReplay = findViewById(R.id.startReplay);
		buttonReplayStop = findViewById(R.id.stopReplay);
		buttonReplayStop.setEnabled(false);
		textView = findViewById(R.id.textView);

		buttonStart.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.i(TAG, "Start logging");
				buttonStop.setEnabled(true);
				buttonStart.setEnabled(false);
				buttonReplay.setEnabled(false);
				buttonReplayStop.setEnabled(false);
				textView.setText("");
				startLogging();
			}
		});

		buttonStop.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.i(TAG, "Stop logging");
				buttonStop.setEnabled(false);
				buttonStart.setEnabled(true);
				buttonReplay.setEnabled(true);
				buttonReplayStop.setEnabled(false);
				stopLogging();
			}
		});

		buttonReplay.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.i(TAG, "Start replay");
				openSelectFileDialog();
				if (fileName != null) {
					buttonReplayStop.setEnabled(true);
					buttonReplay.setEnabled(false);
					buttonStop.setEnabled(false);
					buttonStart.setEnabled(false);
					startReplay();
				}
			}
		});

		buttonReplayStop.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				Log.i(TAG, "Stop replay");
				buttonReplayStop.setEnabled(false);
				buttonReplay.setEnabled(true);
				buttonStop.setEnabled(false);
				buttonStart.setEnabled(true);
				stopReplay();
			}
		});
	}

	private void startReplay() {
		if (!canAccessLocation()) {
			requestPermissions(INITIAL_PERMS, LOCATION_REQUEST);
		}

		running = true;
		textView.setText("");

		AsyncTask.execute(new Runnable() {
			@Override
			public void run() {
				locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

				locationManager.addTestProvider(LocationManager.GPS_PROVIDER, true, true, true, false, true, true, true,
						3, 1);
				locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);

				File root = Environment.getExternalStorageDirectory();

				try {
					File file = new File(root, "Download/gpsdata.txt");
					BufferedReader br = new BufferedReader(new FileReader(file));
					String line = br.readLine();
					while (line != null && running == true) {
						String[] data = line.split(" ");
						if (data.length == 4) {
							long delay = Long.parseLong(data[0]);
							double latitude = Double.parseDouble(data[1]);
							double longitude = Double.parseDouble(data[2]);
							double altitude = Double.parseDouble(data[3]);
							Log.i(TAG, "Original location: " + latitude + ", " + longitude + ", " + altitude);
							long sleepTime = (long) (Math.random() * 50);
							Thread.sleep((Math.random() > 0.5 ? sleepTime + 1000 : 1000 - sleepTime));
							setMockLocation(randomizeValue(latitude, 0.000001), randomizeValue(longitude, 0.000001),
									randomizeAltitude(altitude, 0.1));
						}
						line = br.readLine();
					}
					br.close();
					buttonReplayStop.setEnabled(false);
					buttonReplay.setEnabled(true);
					buttonStop.setEnabled(false);
					buttonStart.setEnabled(true);
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(getApplicationContext(), "Cannot parse GPS data file.", Toast.LENGTH_SHORT).show();
				}
			}
		});

	}

	private static double randomizeValue(double start, double diff) {
		NumberFormat nf = NumberFormat.getInstance(Locale.US);
		DecimalFormat df = (DecimalFormat) nf;
		df.applyPattern("#.########");
		double rnd = Math.random();
		return Double.parseDouble(df.format(start + (rnd * diff)));
	}

	private static double randomizeAltitude(double start, double diff) {
		int rnd = (int) (Math.random() * 100);
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
			Log.i(TAG, "Setting modified location: " + latitude + ", " + longitude + ", " + altitude);
			textView.append(latitude + ", " + longitude + ", " + altitude);
			locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, newLocation);
		} catch (SecurityException e) {
			e.printStackTrace();
		}
	}

	private void openSelectFileDialog() {
		String THEME_PATH_PREFIX = "Download";
		File extStorage = Environment.getExternalStorageDirectory();
		File file = new File(extStorage, THEME_PATH_PREFIX);

		final String[] fileNames = file.list();
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
					}
				}).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
					public void onClick(DialogInterface dialog, int whichButton) {
						fileName = null;
					}
				}).create().show();
	}

	private void stopReplay() {
		running = false;
		locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
	}

	private void stopLogging() {
		locationManager.removeUpdates(this);
		getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		try {
			f.flush();
			f.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void startLogging() {
		if (!canAccessLocation()) {
			requestPermissions(INITIAL_PERMS, LOCATION_REQUEST);
		}
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		start = System.currentTimeMillis();
		File root = Environment.getExternalStorageDirectory();
		try {
			f = new FileOutputStream(new File(root, "gpsdata" + (new Date()) + ".txt"), true);
		} catch (Exception e) {
			e.printStackTrace();
		}
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
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
		Log.i(TAG, "time: " + time + " latitude: " + latitude + " longitude: " + longitude + " altitude: " + altitude);
		textView.append(data);
		try {
			f.write(data.getBytes());
		} catch (Exception e) {
			e.printStackTrace();
		}
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

}
