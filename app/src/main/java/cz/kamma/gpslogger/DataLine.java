package cz.kamma.gpslogger;

public class DataLine {

    long time = 0;
    double latitude = 0;
    double longitude = 0;
    double altitude = 0;
    float acc = 4;

    public DataLine(String[] data) {
        if (data.length > 3) {
            time = Long.parseLong(data[0]);
            latitude = Double.parseDouble(data[1]);
            longitude = Double.parseDouble(data[2]);
            altitude = Double.parseDouble(data[3]);
            acc = 4;
            if (data.length > 4)
                acc = Float.parseFloat(data[4]);
        }
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    public double getLatitude() {
        return latitude;
    }

    public void setLatitude(double latitude) {
        this.latitude = latitude;
    }

    public double getLongitude() {
        return longitude;
    }

    public void setLongitude(double longitude) {
        this.longitude = longitude;
    }

    public double getAltitude() {
        return altitude;
    }

    public void setAltitude(double altitude) {
        this.altitude = altitude;
    }

    public float getAcc() {
        return acc;
    }

    public void setAcc(float acc) {
        this.acc = acc;
    }
}
