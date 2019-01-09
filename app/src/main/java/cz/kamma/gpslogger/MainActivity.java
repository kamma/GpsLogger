package cz.kamma.gpslogger;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
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
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.v7.app.AppCompatActivity;
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
import com.google.android.gms.maps.MapFragment;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

public class MainActivity extends AppCompatActivity implements LocationListener, OnMapReadyCallback {

    static final String TAG = MainActivity.class.getCanonicalName();

	static String VERSION = "v0.30";

	private static final String[] INITIAL_PERMS = { Manifest.permission.ACCESS_FINE_LOCATION,
			Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.WRITE_EXTERNAL_STORAGE,
			Manifest.permission.READ_EXTERNAL_STORAGE };

	private static final int LOCATION_REQUEST = 1340;
	SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd-HHmmss");
	boolean running, paused, reverse = false;
	String fileName;
	char schar = '|';
	static Random gen = new Random();
	static int pos = 0;
	static boolean seeked = false;
	static String state = VERSION;
	static long start = -1;

	Button buttonStart, buttonStop, buttonReplay, buttonReplayStop, buttonReplayPause, buttonResetGps, buttonReverse;
	LocationManager locationManager;
	FileOutputStream f, logFile;
	TextView textView, timeView, fileNameView;
	SeekBar seekBar;
    private static GoogleMap mMap;

	MainActivity activity;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
		setContentView(R.layout.main);

		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		locationManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true,
				Criteria.POWER_LOW, Criteria.ACCURACY_HIGH);
		locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);

		activity = this;

		buttonStart = findViewById(R.id.buttonStart);
		buttonStop = findViewById(R.id.buttonStop);
		buttonStop.setEnabled(false);
		buttonResetGps = findViewById(R.id.buttonResetGps);
		buttonReplay = findViewById(R.id.startReplay);
		buttonReplayStop = findViewById(R.id.stopReplay);
		buttonReplayStop.setEnabled(false);
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

        SupportMapFragment mapFragment = (SupportMapFragment)getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

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
				buttonReverse.setEnabled(false);
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

		buttonResetGps.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				log("Reset GPS");
				clearGps();
			}
		});

		buttonReplayStop.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				log("Stop replay");
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

		buttonReverse.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				log("Reverse");
				if (reverse) {
					reverse = false;
					buttonReverse.setText("Reverse");
				} else {
					reverse = true;
					buttonReverse.setText("Normal");
				}
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
								log("Position: "+pos);
								seeked = true;
							}})
						.setNegativeButton(android.R.string.no, null).show();
            }
        });


		if (!canAccessLocation()) {
			requestPermissions(INITIAL_PERMS, LOCATION_REQUEST);
		}
	}

	private void clearGps() {
		if (locationManager!=null) {
			locationManager.clearTestProviderStatus(LocationManager.GPS_PROVIDER);
			locationManager.clearTestProviderEnabled(LocationManager.GPS_PROVIDER);
			locationManager.clearTestProviderLocation(LocationManager.GPS_PROVIDER);
			locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, false);
			locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
			locationManager = null;
			locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			locationManager.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true,
					Criteria.POWER_LOW, Criteria.ACCURACY_HIGH);
			locationManager.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
			Toast.makeText(MainActivity.this, "GPS Provider cleared.", Toast.LENGTH_SHORT).show();
		} else {
			Toast.makeText(MainActivity.this, "No GPS Provider found.", Toast.LENGTH_SHORT).show();
		}
	}

	private void startReplay() {
		running = true;
		paused = false;
        pos = 0;

		new AsyncTask(){
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
					while (lineTmp!=null) {
					    fullFile.add(lineTmp);
                        lineTmp = br.readLine();
                    }
                    br.close();
					double lastLatitude = -1;
					double lastLongitude = -1;
					double lastAltitude = -1;
                    long lastTime = 0;
                    float lastAcc = 4;
                    long startTime = 0;

                    seekBar.setMax(fullFile.size());

                    pos = -1;
                    while (running == true) {
						if (reverse) {
							pos--;
							if (pos<0)
								pos = 0;
						} else {
							pos++;
							if (pos>=fullFile.size())
								pos = fullFile.size()-1;
						}
						rotateChar();
                        Location newLocation = new Location(LocationManager.GPS_PROVIDER);
						if (paused) {
							state = "PAUSED " + schar;

							log("Original location: " + lastLatitude + ", " + lastLongitude + ", " + lastAltitude);
							Thread.sleep(956);

                            newLocation.setLatitude(randomizeValue(lastLatitude));
                            newLocation.setLongitude(randomizeValue(lastLongitude));
                            newLocation.setAltitude(randomizeAltitude(lastAltitude, 0.001));
							newLocation.setAccuracy(4);
							long gpsTime = (System.currentTimeMillis()/1000);
							newLocation.setTime(gpsTime*1000);

                            setMockLocation("P", newLocation);
						} else {
							state = "PLAYING " + schar;
                            String line = fullFile.get(pos);

							String[] data = line.split(" ");
							if (data.length >3) {
                                long time = Long.parseLong(data[0]);
								double latitude = Double.parseDouble(data[1]);
								double longitude = Double.parseDouble(data[2]);
								double altitude = Double.parseDouble(data[3]);
								float acc = 4;
								if (data.length>4)
									acc = Float.parseFloat(data[4]);

								if (pos==0)
									startTime = time - 1000;

								long waitTime = time - lastTime-44;
								if (seeked) {
								    seeked = false;
								    waitTime = 1000;
                                }
								lastLatitude = latitude;
								lastLongitude = longitude;
								lastAltitude = altitude;
								lastAcc = acc;
								lastTime = time;

								if (waitTime<956)
									waitTime = 956;

								log("Original location: " + latitude + ", " + longitude + ", " + altitude);
								Thread.sleep(waitTime);

                                newLocation.setLatitude(randomizeValue(latitude));
                                newLocation.setLongitude(randomizeValue(longitude));
                                newLocation.setAltitude(randomizeAltitude(altitude, 0.1));
                                newLocation.setAccuracy(acc);
								long gpsTime = (System.currentTimeMillis()/1000);
								newLocation.setTime(gpsTime*1000);

                                setMockLocation("N", newLocation);
							}
						}
						publishProgress(newLocation, lastTime);
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
				seekBar.setProgress(0);
                seekBar.setEnabled(false);
				fileNameView.setText("");
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
				if (values!=null && values.length>0 && values[0] instanceof Location) {
                    Location loc = (Location)values[0] ;
                    mMap.clear();
                    LatLng mapPos = new LatLng(loc.getLatitude(), loc.getLongitude());
					MarkerOptions markerOptions = new MarkerOptions();
                    markerOptions.position(mapPos);
                    mMap.addMarker(markerOptions);
                    mMap.animateCamera(CameraUpdateFactory.newLatLng(mapPos));
                    long time = loc.getTime();
				}
				if (values!=null && values.length>1 && values[1] instanceof Long) {
					long time = (long)values[1];
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
        if (parts[1].length()>14)
            return Double.parseDouble(parts[0]+"."+parts[1].substring(0, 14));
        return d;
    }

	private static double randomizeAltitude(double start, double diff) {
		int rnd = gen.nextInt(100);
		return start + (rnd * diff * 0.1);
	}

	private void setMockLocation(String type, Location newLocation) {
		log("time: "+newLocation.getTime());

		if ( Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
			newLocation.setElapsedRealtimeNanos(SystemClock.elapsedRealtimeNanos());
		}

		try {
			log("Setting modified location: " + type + ", " +newLocation.getLatitude() + ", " + newLocation.getLongitude() + ", " + newLocation.getAltitude());
            //locationManager.clearTestProviderLocation(LocationManager.GPS_PROVIDER);
            locationManager.setTestProviderLocation(LocationManager.GPS_PROVIDER, newLocation);
		} catch (Exception e) {
			Toast.makeText(MainActivity.this, "ERROR: Cannot set location."+e.getMessage(), Toast.LENGTH_SHORT).show();
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
						buttonReverse.setEnabled(true);
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
	}

	private void startLogging() {
		textView.setText("RECORDING");
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
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
		if (start==-1)
			start = location.getTime();
		double latitude = location.getLatitude();
		double longitude = location.getLongitude();
		double altitude = location.getAltitude();
		long time = location.getTime()-start;
		float acc = location.getAccuracy();

		logData(time, latitude, longitude, altitude, acc);
	}

	private void logData(Object... vars) {
		String data = vars[0] + " " + vars[1] + " " + vars[2] + " " + vars[3] + " " +vars[4] + "\n";

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
		try {
			if (logFile == null) {
				String THEME_PATH_PREFIX = "Download";
				File extStorage = Environment.getExternalStorageDirectory();
				File root = new File(extStorage, THEME_PATH_PREFIX);
				File file = new File(root, "gpslogger.log");
				logFile = new FileOutputStream(file, true);
			}
			logFile.write(line.getBytes());
			logFile.flush();
		} catch (Exception e) {
		}
		Log.i(TAG, line);
	}

	@Override
	protected void onDestroy() {
		try {
			locationManager.removeTestProvider(LocationManager.GPS_PROVIDER);
			if (logFile == null) {
				logFile.flush();
				logFile.close();
			}
		} catch (Exception e) {
		}
		super.onDestroy();
	}

	@Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
		mMap.setMaxZoomPreference(18f);
		mMap.setMinZoomPreference(18f);
    }
}
