package pfc.ufmg.datacollector;

import android.Manifest;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import java.time.LocalDateTime;

import pfc.ufmg.datacollector.data.LogDataManager;
import pfc.ufmg.datacollector.sensors.AccelerometerDataCollector;
import pfc.ufmg.datacollector.sensors.GnssDataCollector;
import pfc.ufmg.datacollector.calculations.AttitudeEstimator;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final int PERMISSIONS_REQUEST_CODE = 99;
    private static final int SAMPLE_INTERVAL_MS = 100; // 10 Hz

    // Views
    private TextView tv_lat, tv_lon, tv_altitude, tv_accuracy, tv_speed, tv_timestamp;
    private TextView tv_satellites, tv_course, tv_fix;
    private TextView tv_accel_x, tv_accel_y, tv_accel_z;
    private TextView tv_attitude;
    private TextView tv_log_status, tv_record_count;
    private Button btn_start_log, btn_stop_log;

    // Coletores de dados
    private GnssDataCollector gnssCollector;
    private AccelerometerDataCollector accelerometerCollector;
    private LogDataManager logDataManager;

    // Handler para coleta uniforme
    private Handler handler = new Handler();
    private Runnable dataCollectorRunnable;

    // Cache dos últimos dados
    private GnssDataCollector.GnssData lastGnssData;
    private AccelerometerDataCollector.AccelerometerData lastAccelData;

    private boolean SavingAndUsingData = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Manter a tela sempre ligada
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        readFromFile();

        //starting app
        initializeViews();
        initializeCollectors();
        setupLogButtons();

        if (checkPermissions()) {
            startDataCollection();
        } else {
            requestPermissions();
        }
    }

    private void readFromFile(){
        // setup the alert builder
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Leitura de Arquivo");
        builder.setMessage("Deseja ler os dados gravados em um arquivo csv?");

        // add the buttons
        builder.setPositiveButton("Sim",  new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*"); // aceita qualquer tipo de arquivo
                startActivityForResult(intent, 1234);
            }
        });
        builder.setNegativeButton("Não",  new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                SavingAndUsingData = true;
            }
        });

        // create and show the alert dialog
        AlertDialog dialog = builder.create();
        dialog.show();
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == 1234 && resultCode == RESULT_OK && data != null) {
            Uri uri = data.getData();

            if (uri != null) {
                Toast.makeText(this, "Arquivo selecionado: " + uri.getPath(), Toast.LENGTH_LONG).show();

                // 🔹 Exemplo: ler conteúdo de texto do arquivo
                readFile(uri);
            }
        }
    }

    private void readFile(Uri uri) {

        try (InputStream inputStream = getContentResolver().openInputStream(uri);
             BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {

            StringBuilder conteudo = new StringBuilder();
            String linha = reader.readLine();
            AttitudeEstimator attitudeEstimator = new AttitudeEstimator();
            attitudeEstimator.reset();
            attitudeEstimator.setUpdateListener(new AttitudeEstimator.AttitudeUpdateListener() {
                @Override
                public void onAttitudeUpdate(AttitudeEstimator.AttitudeResult result) {
                    runOnUiThread(() -> {
                        tv_attitude.setText(result.toString());
                        Log.d(TAG, "Atitude atualizada: " + result.toString());
                    });
                }
            });
            while ((linha = reader.readLine()) != null) {
                // Processa os dados para estimação de atitude
                String[] data = linha.split(",");
                AttitudeEstimator.SensorData sensorData = new AttitudeEstimator.SensorData(
                        Double.parseDouble(data[1]),
                        Double.parseDouble(data[2]),
                        Double.parseDouble(data[3]),
                        Integer.parseInt(data[4]),
                        Double.parseDouble(data[5]),
                        Double.parseDouble(data[6]),
                        Double.parseDouble(data[7]),
                        Double.parseDouble(data[8])
                );
                attitudeEstimator.processSample(sensorData);
                conteudo.append(linha).append("\n");
            }

            Log.d(TAG, "Conteúdo do arquivo:\n" + conteudo);
            Toast.makeText(this, "Arquivo lido com sucesso!", Toast.LENGTH_SHORT).show();

        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Erro ao ler o arquivo!", Toast.LENGTH_SHORT).show();
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
        tv_log_status = findViewById(R.id.tv_log_status);
        tv_record_count = findViewById(R.id.tv_record_count);
        btn_start_log = findViewById(R.id.btn_start_log);
        btn_stop_log = findViewById(R.id.btn_stop_log);
        tv_attitude = findViewById(R.id.tv_attitude);
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

        // Inicializa o gerenciador de logs
        logDataManager = new LogDataManager(this);
        // Configura listener para atualização de atitude
        logDataManager.setAttitudeUpdateListener(new AttitudeEstimator.AttitudeUpdateListener() {
            @Override
            public void onAttitudeUpdate(AttitudeEstimator.AttitudeResult result) {
                runOnUiThread(() -> {
                    tv_attitude.setText(result.toString());
                    Log.d(TAG, "Atitude atualizada: " + result.toString());
                });
            }
        });
    }

    private void setupLogButtons() {
        btn_start_log.setOnClickListener(v -> startLogging());
        btn_stop_log.setOnClickListener(v -> stopLogging());

        updateLogButtonsState();
    }

    private void startLogging() {
        if(!SavingAndUsingData)
            return;

        if (logDataManager.startLogging()) {
            updateLogButtonsState();
            Toast.makeText(this, "Salvando em: " + LogDataManager.getLogDirectory(),
                    Toast.LENGTH_LONG).show();
        }
    }

    private void stopLogging() {
        logDataManager.stopLogging();
        updateLogButtonsState();
    }

    private void updateLogButtonsState() {
        boolean isLogging = logDataManager.isLogging();
        btn_start_log.setEnabled(!isLogging);
        btn_stop_log.setEnabled(isLogging);

        if (isLogging) {
            tv_log_status.setText("Gravando: " + logDataManager.getCurrentFileName());
        } else {
            tv_log_status.setText("Não está gravando");
            tv_record_count.setText("0 registros");
        }
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

        if(this.SavingAndUsingData) {
            // Salva no arquivo CSV se estiver logando
            if (logDataManager.isLogging()) {
                logDataManager.logData(lastGnssData, lastAccelData);
                tv_record_count.setText(logDataManager.getRecordCount() + " registros");
            }
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

        if (logDataManager != null && logDataManager.isLogging()) {
            logDataManager.stopLogging();
        }
    }

    private boolean checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                            == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 12 e anteriores
            return ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED &&
                    ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                            == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.READ_MEDIA_IMAGES
            }, PERMISSIONS_REQUEST_CODE);
        } else {
            requestPermissions(new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                    Manifest.permission.READ_EXTERNAL_STORAGE
            }, PERMISSIONS_REQUEST_CODE);
        }
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
        if (checkPermissions()) {
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

        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }

            if (allGranted) {
                startDataCollection();
            } else {
                Toast.makeText(this, "Permissões necessárias não concedidas.", Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }
}