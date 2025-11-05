package pfc.ufmg.datacollector.sensors;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.location.OnNmeaMessageListener;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.core.app.ActivityCompat;

public class GnssDataCollector {

    private static final String TAG = "GnssDataCollector";

    private final Context context;
    private final LocationManager locationManager;
    private final GnssDataListener dataListener;

    private Location lastLocation;
    private int satelliteCount = 0;
    private OnNmeaMessageListener nmeaListener;
    private LocationListener locationListener;
    private GnssStatus.Callback gnssStatusCallback;

    public interface GnssDataListener {
        void onGnssDataUpdated(GnssData data);
    }

    public static class GnssData {
        public double latitude;
        public double longitude;
        public double altitude;
        public float accuracy;
        public float speed;
        public float bearing;
        public int satelliteCount;
        public boolean hasAltitude;
        public boolean hasSpeed;
        public boolean hasFix;
        public long timestamp;

        public GnssData(Location location, int satelliteCount) {
            if (location != null) {
                this.latitude = location.getLatitude();
                this.longitude = location.getLongitude();
                this.accuracy = location.getAccuracy();
                this.bearing = location.getBearing();
                this.satelliteCount = satelliteCount;
                this.hasFix = location.hasAccuracy();
                this.timestamp = location.getTime();

                this.hasAltitude = location.hasAltitude();
                if (hasAltitude) {
                    this.altitude = location.getAltitude();
                }

                this.hasSpeed = location.hasSpeed();
                if (hasSpeed) {
                    this.speed = location.getSpeed() * (float)3.6;
                }
            }
        }
    }

    public GnssDataCollector(Context context, GnssDataListener listener) {
        this.context = context;
        this.dataListener = listener;
        this.locationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    public boolean start() {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Permissão de localização não concedida");
            return false;
        }

        try {
            // Location Listener
            locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    lastLocation = location;
                    notifyDataUpdate();
                }
            };

            locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    0,
                    0,
                    locationListener
            );

            // GNSS Status Callback (contagem de satélites)
            gnssStatusCallback = new GnssStatus.Callback() {
                @Override
                public void onSatelliteStatusChanged(@NonNull GnssStatus status) {
                    satelliteCount = status.getSatelliteCount();
                }
            };
            locationManager.registerGnssStatusCallback(gnssStatusCallback);

            // NMEA Listener
            nmeaListener = new OnNmeaMessageListener() {
                @Override
                public void onNmeaMessage(String message, long timestamp) {
                    if (message.startsWith("$GPGGA") || message.startsWith("$GPRMC")) {
                        Log.d(TAG, "NMEA: " + message);
                    }
                }
            };
            locationManager.addNmeaListener(nmeaListener);

            Log.i(TAG, "GNSS data collection iniciado");
            return true;

        } catch (Exception e) {
            Log.e(TAG, "Erro ao iniciar coleta GNSS", e);
            return false;
        }
    }

    public void stop() {
        try {
            if (locationListener != null) {
                locationManager.removeUpdates(locationListener);
            }
            if (gnssStatusCallback != null) {
                locationManager.unregisterGnssStatusCallback(gnssStatusCallback);
            }
            if (nmeaListener != null) {
                locationManager.removeNmeaListener(nmeaListener);
            }
            Log.i(TAG, "GNSS data collection parado");
        } catch (Exception e) {
            Log.e(TAG, "Erro ao parar coleta GNSS", e);
        }
    }

    public GnssData getCurrentData() {
        return new GnssData(lastLocation, satelliteCount);
    }

    private void notifyDataUpdate() {
        if (dataListener != null) {
            dataListener.onGnssDataUpdated(getCurrentData());
        }
    }

    public boolean hasLocation() {
        return lastLocation != null;
    }
}