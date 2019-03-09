package rish.crearo.sensorfusionlib;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import rish.crearo.sensorfusionlib.listeners.RotationListener;

/**
 * All this does is gives you the rotation matrix as obtained from
 * {@link android.hardware.SensorManager#getRotationMatrix(float[], float[], float[], float[])}//
 */
public class RotationSensor implements SensorEventListener {

    private Context context;
    private RotationListener rotationListener;
    private SensorManager sensorManager;

    private Sensor sensorAcc;
    private Sensor sensorMag;

    private float[] magData = new float[3];
    private float[] accData = new float[3];
    private float[] accMagRotationMatrix = new float[9];

    public RotationSensor(Context context, RotationListener rotationListener) {
        this.context = context;
        this.rotationListener = rotationListener;
        sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        sensorAcc = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorMag = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
    }

    public void start() {
        sensorManager.registerListener(this, sensorAcc, SensorManager.SENSOR_DELAY_FASTEST);
        sensorManager.registerListener(this, sensorMag, SensorManager.SENSOR_DELAY_FASTEST);
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == sensorMag.getType()) {
            System.arraycopy(event.values, 0, magData, 0, event.values.length);
        } else if (event.sensor.getType() == sensorAcc.getType()) {
            System.arraycopy(event.values, 0, accData, 0, event.values.length);
            calculateRotationMatrix(event.timestamp);
        }
    }

    private void calculateRotationMatrix(long timestamp) {
        SensorManager.getRotationMatrix(accMagRotationMatrix, null, accData, magData);
        rotationListener.onRotationMatrix(accMagRotationMatrix, timestamp);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {

    }
}
