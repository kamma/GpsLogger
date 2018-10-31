package cz.kamma.gpslogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Collections;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

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
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements LocationListener {

	static String VERSION = "v0.8";

	private static final String[] INITIAL_PERMS = { Manifest.permission.ACCESS_FINE_LOCATION,
			Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE,
			Manifest.permission.READ_EXTERNAL_STORAGE };

	private static final int LOCATION_REQUEST = 1340;
	SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");

	long start = -1;
	boolean running = false;

	String fileName;

	char schar = '|';

	static final String TAG = MainActivity.class.getCanonicalName();

	static Random gen = new Random();

	Button buttonStart, buttonStop, buttonReplay, buttonReplayStop, clearLog;
	LocationManager locationManager;
	FileOutputStream f;
	TextView textView, logView;

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
		logView = findViewById(R.id.logView);
		clearLog = findViewById(R.id.clearLog);
		
		textView.setText(VERSION);

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

		clearLog.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				logView.setText("");
				log("Log cleared");
			}
		});


		if (!canAccessLocation()) {
			requestPermissions(INITIAL_PERMS, LOCATION_REQUEST);
		}
	}

	private void startReplay() {
		running = true;

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
					BufferedReader br = new BufferedReader(new FileReader(file));
					String line = br.readLine();
					while (line != null && running == true) {
						textView.setText("PLAYING "+schar);
						rotateChar();

						String[] data = line.split(" ");
						if (data.length == 4) {
							long delay = Long.parseLong(data[0]);
							double latitude = Double.parseDouble(data[1]);
							double longitude = Double.parseDouble(data[2]);
							double altitude = Double.parseDouble(data[3]);
							log("Original location: " + latitude + ", " + longitude + ", " + altitude);
							long sleepTime = (long) (Math.random() * 50);
							Thread.sleep((Math.random() > 0.5 ? sleepTime + 1000 : 1000 - sleepTime));
							setMockLocation(randomizeValue(latitude, 0.000001), randomizeValue(longitude, 0.000001),
									randomizeAltitude(altitude, 0.1));
						}
						line = br.readLine();
					}
					br.close();
				} catch (Exception e) {
					e.printStackTrace();
					Toast.makeText(MainActivity.this, "Cannot parse GPS data file.", Toast.LENGTH_SHORT).show();
				}

                running = false;
				return null;
			}

			@Override
			protected void onPostExecute(Object o) {
				buttonReplayStop.setEnabled(false);
				buttonReplay.setEnabled(true);
				buttonStop.setEnabled(false);
				buttonStart.setEnabled(true);
				textView.setText("STOPPED");
                locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
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
		} catch (SecurityException e) {
			e.printStackTrace();
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
		//logView.append(line+"\n");
	}

}
