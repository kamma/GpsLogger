package cz.kamma.gpslogger;

import android.util.Log;

public class DataLine {
    private static final String TAG = DataLine.class.getSimpleName();
    private long time;
    private double latitude;
    private double longitude;
    private double altitude;
    private float acc;

    public DataLine(String[] parts) {
        if (parts.length < 5) {
            throw new IllegalArgumentException("Invalid data line: not enough parts");
        }
        try {
            this.time = Long.parseLong(parts[0]);
            this.latitude = Double.parseDouble(parts[1]);
            this.longitude = Double.parseDouble(parts[2]);
            this.altitude = Double.parseDouble(parts[3]);
            this.acc = Float.parseFloat(parts[4]);
        } catch (NumberFormatException e) {
            Log.e(TAG, "Failed to parse data line", e);
            // Initialize with safe defaults
            this.time = 0;
            this.latitude = 0;
            this.longitude = 0;
            this.altitude = 0;
            this.acc = 0;
        }
    }

    public long getTime() {
        return time;
    }

    public double getLatitude() {
        return latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public float getAcc() {
        return acc;
    }
}
