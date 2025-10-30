package pfc.ufmg.datacollector.sensors;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

public class AccelerometerDataCollector implements SensorEventListener {

    private static final String TAG = "AccelerometerCollector";

    private final SensorManager sensorManager;
    private final Sensor accelerometer;
    private final AccelerometerDataListener dataListener;

    private float accelX = 0;
    private float accelY = 0;
    private float accelZ = 0;
    private long lastTimestamp = 0;

    public interface AccelerometerDataListener {
        void onAccelerometerDataUpdated(AccelerometerData data);
    }

    public static class AccelerometerData {
        public float x;
        public float y;
        public float z;
        public long timestamp;

        public AccelerometerData(float x, float y, float z, long timestamp) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.timestamp = timestamp;
        }
    }

    public AccelerometerDataCollector(Context context, AccelerometerDataListener listener) {
        this.dataListener = listener;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public boolean start() {
        if (accelerometer == null) {
            Log.e(TAG, "Acelerômetro não disponível neste dispositivo");
            return false;
        }

        boolean registered = sensorManager.registerListener(
                this,
                accelerometer,
                SensorManager.SENSOR_DELAY_FASTEST
        );

        if (registered) {
            Log.i(TAG, "Acelerômetro iniciado com sucesso");
        } else {
            Log.e(TAG, "Falha ao registrar listener do acelerômetro");
        }

        return registered;
    }

    public void stop() {
        sensorManager.unregisterListener(this);
        Log.i(TAG, "Acelerômetro parado");
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            accelX = event.values[0];
            accelY = event.values[1];
            accelZ = event.values[2];
            lastTimestamp = event.timestamp;

            if (dataListener != null) {
                dataListener.onAccelerometerDataUpdated(getCurrentData());
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        Log.d(TAG, "Precisão do sensor alterada: " + accuracy);
    }

    public AccelerometerData getCurrentData() {
        return new AccelerometerData(accelX, accelY, accelZ, lastTimestamp);
    }

    public boolean isAvailable() {
        return accelerometer != null;
    }
}