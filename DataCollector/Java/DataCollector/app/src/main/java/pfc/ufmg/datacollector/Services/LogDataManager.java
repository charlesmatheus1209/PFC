package pfc.ufmg.datacollector.data;

import android.content.Context;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import pfc.ufmg.datacollector.sensors.AccelerometerDataCollector;
import pfc.ufmg.datacollector.sensors.GnssDataCollector;

public class LogDataManager {

    private static final String TAG = "LogDataManager";
    private static final String CSV_HEADER = "contreg,eixox,eixoy,eixoz,gps_fix,gps_speed,gps_direction,gps_alt,gps_rtc\n";

    private final Context context;
    private FileWriter fileWriter;
    private File currentFile;
    private boolean isLogging = false;
    private int recordCount = 0;
    private SimpleDateFormat dateFormat;
    private long lastTimestamp = 0;
    private long firstTimestamp = 0;

    public LogDataManager(Context context) {
        this.context = context;
        this.dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault());
    }

    /**
     * Inicia o logging criando um novo arquivo CSV
     * @return true se iniciou com sucesso, false caso contrário
     */
    public boolean startLogging() {
        if (isLogging) {
            Log.w(TAG, "Logging já está ativo");
            return false;
        }

        try {
            // Cria o arquivo CSV
            currentFile = createCsvFile();
            fileWriter = new FileWriter(currentFile, true);

            // Escreve o cabeçalho
            fileWriter.write(CSV_HEADER);
            fileWriter.flush();

            isLogging = true;
            recordCount = 0;

            Log.i(TAG, "Logging iniciado: " + currentFile.getAbsolutePath());
            Toast.makeText(context, "Gravação iniciada: " + currentFile.getName(),
                    Toast.LENGTH_SHORT).show();

            firstTimestamp = System.currentTimeMillis();
            return true;

        } catch (IOException e) {
            Log.e(TAG, "Erro ao iniciar logging", e);
            Toast.makeText(context, "Erro ao criar arquivo de log", Toast.LENGTH_SHORT).show();
            return false;
        }
    }

    /**
     * Para o logging e fecha o arquivo
     */
    public void stopLogging() {
        if (!isLogging) {
            return;
        }

        try {
            if (fileWriter != null) {
                fileWriter.flush();
                fileWriter.close();
            }

            isLogging = false;

            Log.i(TAG, "Logging parado. Total de registros: " + recordCount);
            Toast.makeText(context,
                    "Gravação finalizada: " + recordCount + " registros salvos",
                    Toast.LENGTH_LONG).show();

        } catch (IOException e) {
            Log.e(TAG, "Erro ao parar logging", e);
        }
    }

    /**
     * Loga os dados coletados no arquivo CSV
     */
    public void logData(GnssDataCollector.GnssData gnssData,
                        AccelerometerDataCollector.AccelerometerData accelData) {
        if (!isLogging || fileWriter == null) {
            return;
        }

        try {
            long timestamp = System.currentTimeMillis();

            //Não é possivel fazer logs com uma frequência menor que 50ms
            if(timestamp - lastTimestamp < 50){
                return;
            }

            String datetime = dateFormat.format(new Date(timestamp));

            StringBuilder csvLine = new StringBuilder();
            csvLine.append(recordCount).append(",");


            //csvLine.append(timestamp).append(",");
            //csvLine.append(datetime).append(",");

            double rtcTime = ((double)timestamp - (double)firstTimestamp) /1000.0;
            // Dados do acelerômetro
            if (accelData != null) {
                csvLine.append(accelData.x).append(",");
                csvLine.append(accelData.y).append(",");
                csvLine.append(accelData.z).append(",");;
            } else {
                csvLine.append(",,,");
            }

            // Dados GNSS
            if (gnssData != null) {
                csvLine.append(gnssData.hasFix ? "3" : "0").append(",");
                csvLine.append(gnssData.hasSpeed ? gnssData.speed : "").append(",");
                csvLine.append(gnssData.bearing).append(","); // GPS direction
                csvLine.append(gnssData.hasAltitude ? gnssData.altitude : "").append(",");
                csvLine.append(rtcTime);
            } else {
                csvLine.append(" , , , ,");
                csvLine.append(rtcTime);
            }

            csvLine.append("\n");

            fileWriter.write(csvLine.toString());
            fileWriter.flush(); // Garante que os dados sejam escritos imediatamente

            recordCount++;

            // Log a cada 50 registros
            if (recordCount % 50 == 0) {
                Log.d(TAG, "Registros salvos: " + recordCount);
            }

            lastTimestamp = timestamp;

        } catch (IOException e) {
            Log.e(TAG, "Erro ao escrever dados no CSV", e);
            stopLogging();
        }
    }

    /**
     * Cria um novo arquivo CSV com timestamp no nome
     */
    private File createCsvFile() {
        // Pasta Documents para fácil acesso
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        File appDir = new File(documentsDir, "DataCollector");

        // Cria a pasta se não existir
        if (!appDir.exists()) {
            appDir.mkdirs();
        }

        // Nome do arquivo com timestamp
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                .format(new Date());
        String filename = "data_" + timestamp + ".csv";

        return new File(appDir, filename);
    }

    /**
     * Retorna o caminho do arquivo atual
     */
    public String getCurrentFilePath() {
        return currentFile != null ? currentFile.getAbsolutePath() : null;
    }

    /**
     * Retorna o nome do arquivo atual
     */
    public String getCurrentFileName() {
        return currentFile != null ? currentFile.getName() : null;
    }

    /**
     * Verifica se está logando
     */
    public boolean isLogging() {
        return isLogging;
    }

    /**
     * Retorna o número de registros salvos
     */
    public int getRecordCount() {
        return recordCount;
    }

    /**
     * Retorna a pasta onde os arquivos são salvos
     */
    public static String getLogDirectory() {
        File documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS);
        return new File(documentsDir, "DataCollector").getAbsolutePath();
    }
}