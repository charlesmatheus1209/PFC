package pfc.ufmg.datacollector;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.time.LocalDateTime;

import pfc.ufmg.datacollector.sensors.AccelerometerDataCollector;
import pfc.ufmg.datacollector.sensors.GnssDataCollector;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_FINE_LOCATION = 99;
    private static final int SAMPLE_INTERVAL_MS = 200; // 5 Hz

    // Views
    private TextView tv_lat, tv_lon, tv_altitude, tv_accuracy, tv_speed, tv_timestamp;
    private TextView tv_satellites, tv_course, tv_fix;
    private TextView tv_accel_x, tv_accel_y, tv_accel_z;

    // Coletores de dados
    private GnssDataCollector gnssCollector;
    private AccelerometerDataCollector accelerometerCollector;

    // Handler para coleta uniforme
    private Handler handler = new Handler();
    private Runnable dataCollectorRunnable;

    // Cache dos últimos dados
    private GnssDataCollector.GnssData lastGnssData;
    private AccelerometerDataCollector.AccelerometerData lastAccelData;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeViews();
        initializeCollectors();

        if (checkLocationPermission()) {
            startDataCollection();
        } else {
            requestLocationPermission();
        }
    }

    private void initializeViews() {
        tv_lat = findViewById(R.id.tv_lat);
        tv_lon = findViewById(R.id.tv_lon);
        tv_altitude = findViewById(R.id.tv_altitude);
        tv_accuracy = findViewById(R.id.tv_accuracy);
        tv_speed = findViewById(R.id.tv_speed);
        tv_timestamp = findViewById(R.id.tv_timestamp);
        tv_course = findViewById(R.id.tv_course);
        tv_satellites = findViewById(R.id.tv_satellites);
        tv_fix = findViewById(R.id.tv_fix);
        tv_accel_x = findViewById(R.id.tv_accel_x);
        tv_accel_y = findViewById(R.id.tv_accel_y);
        tv_accel_z = findViewById(R.id.tv_accel_z);
    }

    private void initializeCollectors() {
        // Inicializa o coletor GNSS com callback
        gnssCollector = new GnssDataCollector(this, new GnssDataCollector.GnssDataListener() {
            @Override
            public void onGnssDataUpdated(GnssDataCollector.GnssData data) {
                lastGnssData = data;
            }
        });

        // Inicializa o coletor de acelerômetro com callback
        accelerometerCollector = new AccelerometerDataCollector(this,
                new AccelerometerDataCollector.AccelerometerDataListener() {
                    @Override
                    public void onAccelerometerDataUpdated(AccelerometerDataCollector.AccelerometerData data) {
                        lastAccelData = data;
                    }
                });
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private void startDataCollection() {
        // Inicia coleta GNSS
        boolean gnssStarted = gnssCollector.start();
        if (!gnssStarted) {
            Toast.makeText(this, "Falha ao iniciar GNSS", Toast.LENGTH_SHORT).show();
        }

        // Inicia acelerômetro
        boolean accelStarted = accelerometerCollector.start();
        if (!accelStarted) {
            Toast.makeText(this, "Acelerômetro não disponível", Toast.LENGTH_SHORT).show();
        }

        // Inicia coleta uniforme de dados (5 Hz)
        startUniformDataCollection();
    }

    private void startUniformDataCollection() {
        dataCollectorRunnable = new Runnable() {
            @Override
            public void run() {
                collectAndDisplayData();
                handler.postDelayed(this, SAMPLE_INTERVAL_MS);
            }
        };
        handler.post(dataCollectorRunnable);
    }

    private void collectAndDisplayData() {
        String timeString = String.valueOf(LocalDateTime.now());
        tv_timestamp.setText(timeString);

        // Atualiza dados GNSS
        if (lastGnssData != null && gnssCollector.hasLocation()) {
            updateGnssDisplay(lastGnssData);
        } else {
            clearGnssDisplay();
        }

        // Atualiza dados do acelerômetro
        if (lastAccelData != null) {
            updateAccelerometerDisplay(lastAccelData);
        }

        // Log para debug
        logCollectedData(timeString);
    }

    private void updateGnssDisplay(GnssDataCollector.GnssData data) {
        tv_lat.setText(String.format("%.6f", data.latitude));
        tv_lon.setText(String.format("%.6f", data.longitude));
        tv_accuracy.setText(String.format("%.2f", data.accuracy));
        tv_course.setText(String.format("%.2f", data.bearing));
        tv_satellites.setText(String.valueOf(data.satelliteCount));
        tv_fix.setText(data.hasFix ? "GPS Fix" : "No Fix");

        if (data.hasAltitude) {
            tv_altitude.setText(String.format("%.2f", data.altitude));
        } else {
            tv_altitude.setText("N/A");
        }

        if (data.hasSpeed) {
            tv_speed.setText(String.format("%.2f", data.speed));
        } else {
            tv_speed.setText("N/A");
        }
    }

    private void clearGnssDisplay() {
        tv_lat.setText("N/A");
        tv_lon.setText("N/A");
        tv_altitude.setText("N/A");
        tv_speed.setText("N/A");
        tv_accuracy.setText("N/A");
        tv_course.setText("N/A");
        tv_satellites.setText("0");
        tv_fix.setText("No Fix");
    }

    private void updateAccelerometerDisplay(AccelerometerDataCollector.AccelerometerData data) {
        tv_accel_x.setText(String.format("%.3f", data.x));
        tv_accel_y.setText(String.format("%.3f", data.y));
        tv_accel_z.setText(String.format("%.3f", data.z));
    }

    private void logCollectedData(String timeString) {
        StringBuilder logBuilder = new StringBuilder("DATA: Time=").append(timeString);

        if (lastGnssData != null) {
            logBuilder.append(" | Lat=").append(lastGnssData.latitude)
                    .append(" | Lon=").append(lastGnssData.longitude);
        }

        if (lastAccelData != null) {
            logBuilder.append(" | AccX=").append(lastAccelData.x)
                    .append(" | AccY=").append(lastAccelData.y)
                    .append(" | AccZ=").append(lastAccelData.z);
        }

        Log.d(TAG, logBuilder.toString());
    }

    private void stopAllCollectors() {
        if (handler != null && dataCollectorRunnable != null) {
            handler.removeCallbacks(dataCollectorRunnable);
        }

        if (gnssCollector != null) {
            gnssCollector.stop();
        }

        if (accelerometerCollector != null) {
            accelerometerCollector.stop();
        }
    }

    private boolean checkLocationPermission() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestLocationPermission() {
        requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                PERMISSIONS_FINE_LOCATION);
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopAllCollectors();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    protected void onResume() {
        super.onResume();
        if (checkLocationPermission()) {
            startDataCollection();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopAllCollectors();
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == PERMISSIONS_FINE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startDataCollection();
            } else {
                Toast.makeText(this, "Permissão de localização necessária.", Toast.LENGTH_SHORT).show();
                finish();
            }
        }
    }
}